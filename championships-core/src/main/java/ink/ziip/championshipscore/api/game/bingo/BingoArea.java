package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.game.RoundOutcome;
import ink.ziip.championshipscore.api.game.bingo.gui.BingoCardMapRenderer;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoSpectatorService;
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
import ink.ziip.championshipscore.util.world.WorldManager;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Bingo arena: every team plays the same generated card across persistent vanilla-generated
 * overworld/nether/end dimensions. Players explore the world in survival, and a
 * card cell is claimed when a team member collects the item, reaches the statistic, or earns the
 * advancement. Fixed points mode: completing a cell scores by claim rank, completing a line scores a
 * bonus; the round ends on the timer (or when the board is fully claimed) and is settled by score.
 * Participants are scattered asynchronously around the overworld spawn before the final countdown.
 */
public class BingoArea extends BaseMultiTeamGameInstance {
    @Getter
    @Nullable
    private BingoRound round;
    @Getter
    private int timer;
    private long roundStartMillis;

    private BukkitTask startGameProgressTask;
    /** Scheduled re-enable of world PvP after the 3-minute grace; cancelled if the round ends early. */
    private BukkitTask pvpEnableTask;
    private boolean pvpEnabled;

    /** One recycled MapView per team, reused every round so the server's map-id counter stays bounded. */
    private final Map<ChampionshipTeam, MapView> teamMapViews = new HashMap<>();

    /**
     * Last recorded position of each participant who disconnected mid-round, so a reconnect restores
     * them to where they logged out instead of re-scattering them. Cleared on reset.
     */
    private final Map<UUID, Location> lastQuitLocations = new HashMap<>();

    private final SpawnScatterManager scatterManager;

    /**
     * Parsed permanent potion effects for the current round (empty outside a running round).
     * Re-parsed from {@link BingoConfig#getPermanentEffects()} each {@link #startGameProgress}.
     */
    private List<PotionEffect> permanentEffects = List.of();

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
        startGameProgressTask = null;
        if (pvpEnableTask != null) {
            pvpEnableTask.cancel();
            pvpEnableTask = null;
        }
        pvpEnabled = false;
        lastQuitLocations.clear();
        permanentEffects = List.of();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: gather at the spectator spawn, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.BINGO_START_PREPARATION,
                MessageConfig.BINGO_START_PREPARATION_TITLE, MessageConfig.BINGO_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    protected void startGameProgress() {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) {
            // The persistent bingo world should have been created at startup; without it we can't play.
            logGame(Level.WARNING, "世界", "世界=" + getWorldName() + " 不存在，无法开始");
            endGame();
            return;
        }

        // Design doc: game-start world time is 9000 (noon); time then flows naturally (doDaylightCycle
        // stays at its default true). Set on the overworld only - nether has no time, end is static.
        world.setTime(9000);

        // Build the round's shared card and per-team card maps.
        round = new BingoRound(
                ink.ziip.championshipscore.api.game.bingo.card.CardSize.fromWidth(getGameConfig().getCardWidth()),
                0L,
                Set.of(TaskData.TaskType.ITEM, TaskData.TaskType.ITEM_SET,
                        TaskData.TaskType.ADVANCEMENT, TaskData.TaskType.STATISTIC, TaskData.TaskType.EVENT),
                Set.of(),
                Map.of("kill", 2),
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
        for (Player spectator : getOnlineSpectators()) {
            applySpectatorGameMode(spectator);
        }

        // Parse the round's permanent effects once; handed out per-player below and re-ensured by the
        // tracker (see beginRunningAfterScatter). Done after the round exists but before the state reset
        // so the effects land on a clean player alongside the starter kit.
        permanentEffects = BingoPermanentEffects.parse(getGameConfig().getPermanentEffects());

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
            // Permanent effects go on last (and after the clear above) so they survive into the round.
            ensurePermanentEffects(player);
        }

        // Random scatter around the bingo world spawn; the round only begins once everyone is placed.
        BingoConfig config = getGameConfig();
        scatterManager.performScatterAsync(world, players,
                config.getScatterRadius(), config.getScatterMaxTries(),
                this::beginRunningAfterScatter);
    }

    /** Called once the async scatter has placed every participant: starts the authoritative final countdown. */
    private void beginRunningAfterScatter() {
        if (round == null) return;

        startFinalCountdown(MessageConfig.BINGO_START_PREPARATION_TITLE,
                MessageConfig.BINGO_GAME_START_TITLE, MessageConfig.BINGO_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        roundStartMillis = System.currentTimeMillis();

        // PvP grace (design doc: first 3 minutes PvP off, then on). Enforced at the world level via the
        // PVP gamerule - one toggle covers melee + projectiles, so no per-event cancellation is needed.
        // Same-team friendly fire stays cancelled in BingoHandler throughout the whole round.
        setBingoPvP(false);
        pvpEnabled = false;
        if (pvpEnableTask != null) pvpEnableTask.cancel();
        pvpEnableTask = scheduler.runTaskLater(plugin, () -> {
            pvpEnableTask = null;
            setBingoPvP(true);
            pvpEnabled = true;
            sendActionBarToAllGamePlayers(MessageConfig.BINGO_PVP_STARTED);
        }, PVP_GRACE_TICKS);

        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            if (round == null) return;

            int elapsed = Math.max(0, getGameConfig().getTimer() - timer);
            int graceRemaining = Math.max(0, PVP_GRACE_SECONDS - elapsed);
            String timerTitle = MessageConfig.BINGO_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer));
            timerTitle += " &#bababa• " + (pvpEnabled
                    ? MessageConfig.BINGO_PVP_ACTIVE
                    : MessageConfig.BINGO_PVP_PROTECTION
                    .replace("%time%", String.valueOf(graceRemaining)));
            updateGameTimerBossBar(timerTitle, timer, getGameConfig().getTimer());
            if (!pvpEnabled && graceRemaining >= 1 && graceRemaining <= 10) {
                sendActionBarToAllGamePlayers(MessageConfig.BINGO_PVP_START_COUNT_DOWN
                        .replace("%time%", String.valueOf(graceRemaining)));
            }
            if (timer == 0)
                return;

            for (UUID uuid : gamePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                // Self-heal permanent effects (death/reconnect/temp-potion-overwrite all drop them).
                ensurePermanentEffects(player);
                checkPlayerProgress(player);
            }

            if (round.boardFullyClaimed()) {
                endGame();
            }
        }, this::endGame);
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
        for (GameTask done : round.tryCompletePollableEvents(player, team, gameTime)) {
            announceCompletion(player, done);
        }
    }

    /** Routes a discrete EventTask signal through the same authoritative completion path. */
    public void onEventSignal(Player player, String trigger, String param) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null || player == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        round.tryCompleteEventSignal(player, team, trigger, param == null ? "" : param, roundSeconds())
                .ifPresent(task -> announceCompletion(player, task));
    }

    public void recordEventDistinct(Player player, String bucket, String value) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null || player == null) return;
        if (notAreaPlayer(player)) return;
        round.eventTracker().recordDistinct(player, bucket, value);
        checkPlayerProgress(player);
    }

    public void recordEventCount(Player player, String bucket) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null || player == null) return;
        if (notAreaPlayer(player)) return;
        round.eventTracker().increment(player, bucket);
        checkPlayerProgress(player);
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
        round.eventTracker().increment(player, "advancement_count");
        round.tryCompleteAdvancement(player, team, advancement, roundSeconds())
                .ifPresent(task -> announceCompletion(player, task));
        for (GameTask done : round.tryCompletePollableEvents(player, team, roundSeconds())) {
            announceCompletion(player, done);
        }
    }

    /** Credits the completing player with the cell's points, credits any line bonus to every team
     * member, and broadcasts the completion. */
    private void announceCompletion(Player player, GameTask task) {
        if (round == null) return;
        int cellDelta = round.lastCellDelta();
        int lineDelta = round.lastLineDelta();
        if (cellDelta != 0) addPlayerPoints(player.getUniqueId(), cellDelta);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        // Line bonus goes to every team member ("队内所有成员+50/+25"), not just the completer.
        if (lineDelta != 0 && team != null) {
            addPlayerPointsToAllTeamMembers(team, lineDelta);
        }
        if (team != null && getRunMode() == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY) {
            long elapsedMillis = Math.max(0L, System.currentTimeMillis() - roundStartMillis);
            int completedLines = round.countCompletedLines(team);
            int completedTasks = round.completedCount(team);
            plugin.getDailyManager().statsManager().recordBingoProgress(
                    this, team, completedLines, completedTasks, round.countFirstCompletions(team));
            if (completedLines > 0) {
                plugin.getDailyManager().statsManager().recordTeamMilestone(this, team,
                        DailyRecordType.BINGO_FIRST_LINE, elapsedMillis, player.getUniqueId());
            }
            if (completedTasks >= getGameConfig().getCardWidth() * getGameConfig().getCardWidth()) {
                plugin.getDailyManager().statsManager().recordTeamMilestone(this, team,
                        DailyRecordType.BINGO_FULL_CARD, elapsedMillis, player.getUniqueId());
            }
        }

        int delta = cellDelta + lineDelta;
        Component message = Utils.toComponent(MessageConfig.BINGO_TASK_COMPLETED
                .replace("%player%", Utils.formatPlayerName(player))
                .replace("%points%", String.valueOf(delta)))
                .replaceText(builder -> builder.matchLiteral("%task%").replacement(task.data.getName()));
        Set<UUID> audienceIds = new HashSet<>(gamePlayers);
        audienceIds.addAll(spectators);
        for (UUID audienceId : audienceIds) {
            Player audience = Bukkit.getPlayer(audienceId);
            if (audience != null) audience.sendMessage(message);
        }
    }

    /**
     * Ensures a participant is holding their team's card map: a no-op if one is already present,
     * otherwise a fresh copy is placed in the off-hand (empty at round start — the kit never fills it),
     * falling back to any free slot, and dropped at their feet only when the inventory is full.
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
            if (isEmpty(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(card);
            } else if (!inv.addItem(card).isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), card);
            }
        });
    }

    /** True when an inventory slot holds nothing (null or an air stack). */
    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private long roundSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - roundStartMillis) / 1000L);
    }

    /** First-3-minutes PvP grace, in ticks (180s). After it elapses world PvP is re-enabled. */
    private static final int PVP_GRACE_SECONDS = 180;
    private static final long PVP_GRACE_TICKS = PVP_GRACE_SECONDS * 20L;

    /** Toggles PvP on every bingo dimension (overworld/nether/end) via the PVP gamerule (World.setPVP
     *  is deprecated since 1.21.9). The gamerule covers melee and projectiles in one toggle, so the
     *  grace needs no per-event cancellation. */
    private void setBingoPvP(boolean enabled) {
        for (String name : new String[]{WorldManager.BINGO_OVERWORLD, WorldManager.BINGO_NETHER, WorldManager.BINGO_END}) {
            World w = Bukkit.getWorld(name);
            if (w != null) w.setGameRule(GameRules.PVP, enabled);
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location set = gameConfig.getSpectatorSpawnPoint();
        if (set != null) return set;
        // Default to the bingo world spawn when no explicit spectator point is configured.
        World world = Bukkit.getWorld(getWorldName());
        return world != null ? world.getSpawnLocation() : CCConfig.LOBBY_LOCATION;
    }

    /** Bingo spectators need normal item rendering so they can hold and inspect every team's live card. */
    @Override
    protected void applySpectatorGameMode(@NotNull Player player) {
        BingoSpectatorService.apply(player);
        ensureCardsForSpectator(player);
    }

    @Override
    protected void clearSpectatorGameMode(@NotNull Player player) {
        removeSpectatorCards(player);
        BingoSpectatorService.clear(player);
    }

    private void ensureCardsForSpectator(Player player) {
        removeSpectatorCards(player);
        if (round == null) return;

        var inventory = player.getInventory();
        boolean offHandAvailable = isEmpty(inventory.getItemInOffHand());
        for (ChampionshipTeam team : round.teams()) {
            ItemStack card = round.mapItem(team).map(ItemStack::clone).orElse(null);
            if (card == null) continue;
            if (offHandAvailable) {
                inventory.setItemInOffHand(card);
                offHandAvailable = false;
            } else {
                inventory.addItem(card);
            }
        }
        player.updateInventory();
    }

    private static void removeSpectatorCards(Player player) {
        var inventory = player.getInventory();
        if (CardMapItem.isCard(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (CardMapItem.isCard(inventory.getItem(slot))) inventory.setItem(slot, null);
        }
    }

    /**
     * Where a participant respawns after a death mid-round: the bingo overworld's spawn point. Vanilla
     * would send a bedless player to the main-world spawn (the lobby); this keeps them in the game.
     */
    public Location getRespawnLocation() {
        World world = Bukkit.getWorld(getWorldName());
        return world != null ? world.getSpawnLocation() : null;
    }

    /** True for the bingo overworld and its nether/end dimensions. */
    private boolean isBingoWorld(World world) {
        return WorldManager.isBingoWorld(world);
    }

    /**
     * Bingo spans the whole world, so there is no area bounding box. A spectator is "in area" anywhere
     * in any bingo dimension (overworld/nether/end); only leaving the bingo worlds entirely counts as
     * out of area (and would pull them back). Uses the unified {@link #isBingoWorld} so nether/end
     * spectators aren't wrongly yanked back to the overworld spawn.
     */
    @Override
    public boolean notInArea(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return !isBingoWorld(location.getWorld());
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (pvpEnableTask != null) {
            pvpEnableTask.cancel();
            pvpEnableTask = null;
        }
        setBingoPvP(true); // restore world PvP for between rounds / next round

        // Resolve the winner and stamp the outcome so the card-map renderer paints the win overlay.
        if (isSettlementAllowed() && round != null) {
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
        announceGameEnd(MessageConfig.BINGO_GAME_END_TITLE, MessageConfig.BINGO_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        if (isSettlementAllowed()) sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
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
        // KEEP_INVENTORY is on, so the player's items (incl. the card map) survive the death. The
        // respawn location is redirected into the bingo world by BingoHandler#onRespawn, which also
        // re-issues the card there as a safety net.
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        // Record where the participant logged out so a mid-round reconnect puts them back here rather
        // than re-scattering them. Only captured while in a bingo world - a player who crashed during
        // the lobby->world teleport (still in the lobby) has no spot to return to and is scattered in.
        if (getGameStageEnum() == GameStageEnum.PROGRESS && isBingoWorld(player.getWorld())) {
            lastQuitLocations.put(player.getUniqueId(), player.getLocation());
        }
    }

    /** Bingo spectators survive a disconnect and are restored on reconnect; endGame releases them. */
    @Override
    public boolean keepSpectatorAcrossReconnect() {
        return true;
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;

        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.PREPARATION) {
            // Reintegrate a participant who disconnected before/while the lobby->world teleport ran.
            resetPlayerHealthFoodEffectLevelInventory();
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
            return;
        }
        if (stage == GameStageEnum.COUNTDOWN || stage == GameStageEnum.PROGRESS) {
            // Mid-round reconnect: a participant is returning to a round already in progress. KEEP_INVENTORY
            // is on and the inventory is restored from playerdata, so do NOT wipe it (that would discard
            // collected card items). Only re-issue the kit if they never received it (e.g. crashed before
            // the round-start handout) - hasKit gates that to avoid duplicating the non-stacking tools.
            // Earned advancements and card progress are preserved (no prepareParticipant revoke); only
            // missing statistic baselines are filled in.
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
            if (team == null) return;
            // Countdown and live play both use the participant survival mode. The shared
            // reconnect path below also refreshes vitals, but applying it here prevents a
            // one-tick lobby/adventure state while an async scatter is pending.
            player.setGameMode(GameMode.SURVIVAL);
            if (round != null) round.ensureStatBaselines(player);
            ensureKitAndCard(player);
            refreshVitals(player);

            if (isBingoWorld(player.getWorld())) {
                // Reconnecting inside a bingo dimension: send them back to where they logged out, or
                // scatter in if no spot was recorded (e.g. crashed during the lobby->world teleport).
                Location last = lastQuitLocations.remove(player.getUniqueId());
                if (last != null && last.getWorld() != null) {
                    scheduler.runTask(plugin, () -> {
                        player.teleport(last);
                        player.setFallDistance(0f);
                        player.setFireTicks(0);
                    });
                } else {
                    scatterIntoBingo(player);
                }
            } else {
                // Reconnecting outside any bingo world (e.g. lobby, or stranded in another world): bring
                // them into the bingo overworld with a fresh scatter. No inventory clear - hasKit gates
                // the kit so nothing duplicates.
                lastQuitLocations.remove(player.getUniqueId());
                scatterIntoBingo(player);
            }
            return;
        }
        // END / LOADING / WAITING (transitional, normally unreachable for a reconnect): benign default.
        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
    }

    /**
     * Re-issues the starter kit (only when the player doesn't already have it - see
     * {@link BingoStarterKit#hasKit}) and ensures they hold their team's card map. The single kit
     * (re-)issuance path used on both death-respawn and mid-round reconnect, so the gating lives in
     * one place and the non-stacking tools can never duplicate.
     */
    public void ensureKitAndCard(Player player) {
        if (player == null || notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        if (!BingoStarterKit.hasKit(player)) {
            BingoStarterKit.give(player, team);
        }
        ensureCardFor(player);
        // Death clears potion effects in vanilla; restore the permanent ones on respawn.
        ensurePermanentEffects(player);
    }

    /**
     * Re-applies any permanent effect the participant is currently missing (no-op outside a round
     * where {@link #permanentEffects} is empty). Delegates to {@link BingoPermanentEffects#ensure},
     * which never overwrites active temporary buffs. Called from the per-second tracker,
     * {@link #ensureKitAndCard} (respawn) and {@link #refreshVitals} (reconnect) so effects survive
     * every kind of mid-round drop.
     */
    private void ensurePermanentEffects(Player player) {
        if (permanentEffects.isEmpty() || player == null) return;
        BingoPermanentEffects.ensure(player, permanentEffects);
    }

    /** Sets a participant to SURVIVAL with full vitals and no potion effects (used on reconnect). */
    private void refreshVitals(Player player) {
        scheduler.runTask(plugin, () -> {
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setFireTicks(0);
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            // The strip above just cleared the permanent effects too; re-apply them now (deferred, so
            // this runs after the kit/card handed out synchronously in handlePlayerJoin).
            BingoPermanentEffects.ensure(player, permanentEffects);
        });
    }

    /** Scatters a single participant into the bingo overworld around its spawn. */
    private void scatterIntoBingo(Player player) {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return;
        BingoConfig config = getGameConfig();
        scatterManager.performScatterAsync(world, List.of(player),
                config.getScatterRadius(), config.getScatterMaxTries(), null);
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
