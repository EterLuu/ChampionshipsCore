package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.game.RoundOutcome;
import ink.ziip.championshipscore.api.game.bingo.gui.BingoCardMapRenderer;
import ink.ziip.championshipscore.api.game.bingo.gui.CardMapItem;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.game.bingo.world.SpawnScatterManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bingo arena: every team plays the same generated card in a pre-built static world (copied from
 * {@code plugin/maps/bingo}, like the other CC games). Players explore the world in survival, and a
 * card cell is claimed when a team member collects the item, reaches the statistic, or earns the
 * advancement. Fixed points mode: completing a cell scores by claim rank, completing a line scores a
 * bonus; the round ends on the timer (or when the board is fully claimed) and is settled by score.
 *
 * <p>TODO(待确认): the spawn mechanism at round start is a placeholder — players are cycled across the
 * configured {@code player-spawn-points} in survival mode. The final mechanism is pending the user's
 * decision.
 */
public class BingoArea extends BaseSingleTeamArea {
    @Getter
    @Nullable
    private BingoRound round;
    @Getter
    private int timer;
    private long roundStartMillis;

    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;

    /** One recycled MapView per team, reused every round so the server's map-id counter stays bounded. */
    private final Map<ChampionshipTeam, MapView> teamMapViews = new HashMap<>();

    private final SpawnScatterManager scatterManager;

    public BingoArea(ChampionshipsCore plugin, BingoConfig bingoConfig) {
        super(plugin, GameTypeEnum.Bingo, new BingoHandler(plugin), bingoConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setBingoArea(this);
        getGameHandler().register();

        this.scatterManager = new SpawnScatterManager(plugin);

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void resetArea() {
        // Bingo spans the whole world, so there is no area bounding-box to sweep (and area-pos may be
        // unset). The static world is reloaded from the template each round via loadMap, which already
        // wipes any dropped items, so no explicit cleanup is needed here.
        round = null;
        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BINGO_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.BINGO_START_PREPARATION_TITLE, MessageConfig.BINGO_START_PREPARATION_SUBTITLE);

        timer = getGameConfig().getPrepareTime();
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) {
            // The persistent bingo world should have been created at startup; without it we can't play.
            plugin.getLogger().warning("[Bingo] 世界 " + getWorldName() + " 不存在，无法开始游戏。");
            endGame();
            return;
        }

        // Build the round's shared card and per-team card maps.
        round = new BingoRound(
                ink.ziip.championshipscore.api.game.bingo.card.CardSize.fromWidth(getGameConfig().getCardWidth()),
                0L,
                Set.of(TaskData.TaskType.ITEM, TaskData.TaskType.ITEM_SET,
                        TaskData.TaskType.ADVANCEMENT, TaskData.TaskType.STATISTIC),
                Set.of(),
                Map.of(),
                gameTeams,
                getGameConfig().pointsArray(),
                getGameConfig().getLineBonus(),
                getGameConfig().getLineBonusMajorCount(),
                getGameConfig().getLineBonusMinor());

        for (ChampionshipTeam team : gameTeams) {
            MapView view = teamMapViews.computeIfAbsent(team, t -> Bukkit.createMap(world));
            round.cardFor(team).ifPresent(card ->
                    round.setMapItem(team, CardMapItem.create(view, world, card, team, 0)));
        }

        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        List<Player> players = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            players.add(player);
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
            if (team == null) continue;
            // Hand out the starter kit after the inventory clear but before the card, so the card lands
            // in a free slot rather than being blocked by kit items.
            BingoStarterKit.give(player, team);
            round.prepareParticipant(player, team);
            ensureCardFor(player);
        }

        // Random scatter around the bingo world spawn; the round only begins once everyone is placed.
        BingoConfig config = getGameConfig();
        scatterManager.performScatterAsync(world, players,
                config.getScatterRingRadius(), config.getScatterRingJitter(), config.getScatterMaxTries(),
                this::beginRunningAfterScatter);
    }

    /** Called once the async scatter has placed every participant: starts the live round + tracker. */
    private void beginRunningAfterScatter() {
        if (round == null) return;

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BINGO_GAME_START);
        sendTitleToAllGamePlayers(MessageConfig.BINGO_GAME_START_TITLE, MessageConfig.BINGO_GAME_START_SUBTITLE);
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);

        setGameStageEnum(GameStageEnum.PROGRESS);
        roundStartMillis = System.currentTimeMillis();
        timer = getGameConfig().getTimer();

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {
            if (round == null) return;

            for (UUID uuid : gamePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) checkPlayerProgress(player);
            }

            sendActionBarToAllGameSpectators(MessageConfig.BINGO_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer <= 0 || round.boardFullyClaimed()) {
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    /** Item + statistic progress check for one player; called every tracker tick and on inventory events. */
    public void checkPlayerProgress(Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null || player == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;

        long gameTime = roundSeconds();

        Map<org.bukkit.Material, Integer> held = new EnumMap<>(org.bukkit.Material.class);
        // Effect-specific potions are matched by (material, base effect); keyed "MATERIAL|effect".
        Map<String, Integer> heldPotions = new HashMap<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            held.merge(stack.getType(), stack.getAmount(), Integer::sum);
            String potionEffect = basePotionEffect(stack);
            if (potionEffect != null) {
                heldPotions.merge(stack.getType().name() + "|" + potionEffect, stack.getAmount(), Integer::sum);
            }
        }
        for (Map.Entry<org.bukkit.Material, Integer> entry : held.entrySet()) {
            round.tryCompleteItem(player, team, entry.getKey(), entry.getValue(), gameTime)
                    .ifPresent(task -> announceCompletion(player, task));
        }
        for (Map.Entry<String, Integer> entry : heldPotions.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            round.tryCompletePotion(player, team, org.bukkit.Material.valueOf(parts[0]), parts[1],
                            entry.getValue(), gameTime)
                    .ifPresent(task -> announceCompletion(player, task));
        }

        for (GameTask done : round.tryCompleteStatistics(player, team, gameTime)) {
            announceCompletion(player, done);
        }
    }

    /**
     * Base effect key of a potion item ("strength", "night_vision", …) with any strong/long modifier
     * collapsed, or {@code null} when the stack isn't an effect potion. Matches {@code PotionTask.effect}.
     */
    private static String basePotionEffect(ItemStack stack) {
        org.bukkit.Material t = stack.getType();
        if (t != org.bukkit.Material.POTION && t != org.bukkit.Material.SPLASH_POTION
                && t != org.bukkit.Material.LINGERING_POTION) {
            return null;
        }
        if (!(stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm)) return null;
        org.bukkit.potion.PotionType type = pm.getBasePotionType();
        if (type == null) return null;
        return type.name().toLowerCase(java.util.Locale.ROOT).replaceFirst("^(strong|long)_", "");
    }

    /** Advancement progress check; called from the advancement-done event via the handler. */
    public void onAdvancement(Player player, Advancement advancement) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null || player == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        round.tryCompleteAdvancement(player, team, advancement, roundSeconds())
                .ifPresent(task -> announceCompletion(player, task));
    }

    /** Credits the completing player with the cell's points and broadcasts the completion. */
    private void announceCompletion(Player player, GameTask task) {
        if (round == null) return;
        int delta = round.lastScoreDelta();
        if (delta != 0) addPlayerPoints(player.getUniqueId(), delta);

        String taskName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(task.data.getName());
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        String teamColor = team == null ? "" : team.getColoredColor();
        sendMessageToAllGamePlayers(MessageConfig.BINGO_TASK_COMPLETED
                .replace("%player%", teamColor + player.getName())
                .replace("%task%", taskName)
                .replace("%points%", String.valueOf(delta)));
    }

    /**
     * Ensures a participant is holding their team's card map: a no-op if one is already present,
     * otherwise a fresh copy is added (dropped at their feet if the inventory is full).
     */
    public void ensureCardFor(Player player) {
        if (player == null || round == null || player.isDead()) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        Inventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (CardMapItem.isCard(item)) return;
        }
        round.mapItem(team).ifPresent(map -> {
            ItemStack card = map.clone();
            if (!inv.addItem(card).isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), card);
            }
        });
    }

    private long roundSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - roundStartMillis) / 1000L);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location set = gameConfig.getSpectatorSpawnPoint();
        if (set != null) return set;
        // Default to the bingo world spawn when no explicit spectator point is configured.
        World world = Bukkit.getWorld(getWorldName());
        return world != null ? world.getSpawnLocation() : CCConfig.LOBBY_LOCATION;
    }

    /**
     * Bingo spans the whole world, so there is no area bounding box. A spectator is "in area" anywhere
     * in the bingo world; only leaving the bingo world entirely counts as out of area (and would pull
     * them back). This also avoids dereferencing the optional, possibly-unset {@code area-pos}.
     */
    @Override
    public boolean notInArea(Location location) {
        Location spawn = getSpectatorSpawnLocation();
        if (location == null || location.getWorld() == null || spawn == null || spawn.getWorld() == null)
            return false;
        return !location.getWorld().getName().equals(spawn.getWorld().getName());
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        // Resolve the winner and stamp the outcome so the card-map renderer paints the win overlay.
        if (round != null) {
            ChampionshipTeam winner = round.resolveTopScore();
            RoundOutcome.OutcomeType type = winner == null
                    ? RoundOutcome.OutcomeType.DRAW : RoundOutcome.OutcomeType.TOP_SCORE;
            String winnerId = winner == null ? null : BingoTeamAdapter.id(winner);
            round.setOutcome(new RoundOutcome(winnerId,
                    winner == null ? null : BingoTeamAdapter.color(winner), type));
            forceCardMapRedraw();
            if (winner != null) {
                sendMessageToAllGamePlayers(MessageConfig.BINGO_GAME_WINNER
                        .replace("%team%", winner.getColoredName())
                        .replace("%points%", String.valueOf(round.score(winner))));
            }
        }

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BINGO_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BINGO_GAME_END_TITLE, MessageConfig.BINGO_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        resetGame();
    }

    /** Swaps every team's card-map renderer for one bound to the final outcome so the overlay paints. */
    private void forceCardMapRedraw() {
        if (round == null) return;
        for (ChampionshipTeam team : round.teams()) {
            MapView view = teamMapViews.get(team);
            if (view == null) continue;
            round.cardFor(team).ifPresent(card -> {
                for (org.bukkit.map.MapRenderer existing : new java.util.ArrayList<>(view.getRenderers())) {
                    view.removeRenderer(existing);
                }
                view.addRenderer(new BingoCardMapRenderer(card, BingoTeamAdapter.id(team),
                        BingoTeamAdapter.color(team), 0, round));
            });
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        // Survival respawn handled by vanilla; re-issue the card on the next tick after respawn.
        scheduler.runTaskLater(plugin, () -> ensureCardFor(player), 2L);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            ensureCardFor(player);
        }
    }

    @Override
    public BingoConfig getGameConfig() {
        return (BingoConfig) gameConfig;
    }

    @Override
    public BingoHandler getGameHandler() {
        return (BingoHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return "bingo";
    }
}
