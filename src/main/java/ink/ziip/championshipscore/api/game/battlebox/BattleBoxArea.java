package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.battlebox.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Battle Box arena hosting several independent team-vs-team matches in parallel, one per stamped arena copy
 * (see {@link BattleBoxLayout}). It extends {@link BaseSingleTeamArea} (a team-list area): the area owns all
 * participating teams for the round and a {@link BattleBoxMatch} list provides the per-copy geometry, win
 * detection and freezing.
 * The championship's Swiss scheduler feeds one round's pairings in via {@link #tryStartMatches(List)}.
 */
public class BattleBoxArea extends BaseSingleTeamArea {
    @Getter
    private volatile int timer;
    private volatile ScheduledTask startGamePreparationTask;
    private volatile ScheduledTask startGameProgressTask;
    private volatile ScheduledTask woolCheckerTask;

    private final List<BattleBoxMatch> matches = new CopyOnWriteArrayList<>();
    private final Map<UUID, BattleBoxMatch> matchByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BBWeaponKitEnum> playerWeaponKit = new ConcurrentHashMap<>();
    private final AtomicBoolean woolCheckInProgress = new AtomicBoolean();

    public BattleBoxArea(ChampionshipsCore plugin, BattleBoxConfig battleBoxConfig) {
        super(plugin, GameTypeEnum.BattleBox, new BattleBoxHandler(plugin), battleBoxConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setBattleBoxArea(this);

        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    /**
     * Starts a round of parallel matches from {@code pairs} (each a team-vs-team pairing). Pair {@code i}
     * runs in arena copy {@code i}, so {@code prepare} must have stamped at least {@code pairs.size()} copies.
     */
    public synchronized boolean tryStartMatches(@NotNull List<TwoVTwoVector> pairs) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        matches.clear();
        matchByPlayer.clear();
        int copyIndex = 0;
        for (TwoVTwoVector pair : pairs) {
            BattleBoxMatch match = new BattleBoxMatch(copyIndex, pair.getTeamOne(), pair.getTeamTwo(), getGameConfig());
            matches.add(match);
            gameTeams.add(pair.getTeamOne());
            gameTeams.add(pair.getTeamTwo());
            gamePlayers.addAll(pair.getTeamOne().getMembers());
            gamePlayers.addAll(pair.getTeamTwo().getMembers());
            for (UUID uuid : pair.getTeamOne().getMembers()) matchByPlayer.put(uuid, match);
            for (UUID uuid : pair.getTeamTwo().getMembers()) matchByPlayer.put(uuid, match);
            copyIndex++;
        }

        startGamePreparation();
        return true;
    }

    /** The match a player belongs to this round, or {@code null} for non-participants. */
    @Nullable
    public BattleBoxMatch matchOf(@NotNull Player player) {
        return matchByPlayer.get(player.getUniqueId());
    }

    @Override
    public void resetArea() {
        for (BattleBoxMatch match : matches) {
            match.resetWoolAsync(plugin, Material.WHITE_WOOL);
        }
        cleanDroppedItems();

        matches.clear();
        matchByPlayer.clear();
        playerWeaponKit.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        woolCheckerTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        for (BattleBoxMatch match : matches) {
            match.getRight().teleportAllPlayers(match.getRightPreSpawn());
            match.getLeft().teleportAllPlayers(match.getLeftPreSpawn());
            match.resetCenterWoolAsync(plugin);
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_START_PREPARATION_TITLE, MessageConfig.BATTLE_BOX_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(() -> {
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
        summonPotions();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE);

        timer = getGameConfig().getTimer() + 5;

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        for (BattleBoxMatch match : matches) {
            match.getRight().teleportAllPlayers(match.getRightSpawn());
            match.getLeft().teleportAllPlayers(match.getLeftSpawn());
        }
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(() -> {
            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.BATTLE_BOX_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.BATTLE_BOX_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);

        woolCheckerTask = scheduler.runTaskTimer(() -> {
            if (timer == -1) {
                changeLevelForAllGamePlayers(0);
                endGame();
                if (woolCheckerTask != null)
                    woolCheckerTask.cancel();
                return;
            }

            if (getGameStageEnum() != GameStageEnum.PROGRESS)
                return;

            checkMatchWoolAsync();
        }, 0, 1L);
    }

    private void checkMatchWoolAsync() {
        if (!woolCheckInProgress.compareAndSet(false, true)) return;
        List<CompletableFuture<Void>> checks = new ArrayList<>();
        for (BattleBoxMatch match : List.copyOf(matches)) {
            if (match.isFinished()) continue;
            checks.add(match.countWoolAsync(plugin).thenAccept(blockCount -> {
                if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
                int rightWool = blockCount.getOrDefault(match.getRight().getWool().getType(), 0);
                int leftWool = blockCount.getOrDefault(match.getLeft().getWool().getType(), 0);
                if (rightWool == 9 || leftWool == 9) finishMatch(match);
            }));
        }
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) -> {
            woolCheckInProgress.set(false);
            if (throwable != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to check Battle Box wool", throwable);
                return;
            }
            if (getGameStageEnum() == GameStageEnum.PROGRESS && !matches.isEmpty()
                    && matches.stream().allMatch(BattleBoxMatch::isFinished)) {
                changeLevelForAllGamePlayers(0);
                endGame();
            }
        });
    }

    /** Freezes a settled match: its players go spectator at their copy's viewpoint; scoring waits for end. */
    private void finishMatch(BattleBoxMatch match) {
        if (!match.tryFinish()) return;
        for (ChampionshipTeam team : List.of(match.getRight(), match.getLeft())) {
            for (Player player : team.getOnlinePlayers()) {
                scheduler.runEntity(player, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                    if (match.getSpectatorSpawn() != null)
                        player.teleportAsync(match.getSpectatorSpawn());
                });
            }
            team.sendMessageToAll(MessageConfig.BATTLE_BOX_GAME_END);
        }
    }

    @Override
    public synchronized void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        setGameStageEnum(GameStageEnum.END);

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (woolCheckerTask != null)
            woolCheckerTask.cancel();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        teleportAllPlayers(getLobbyLocation());
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        List<CompletableFuture<HashMap<Material, Integer>>> counts = new ArrayList<>();
        for (BattleBoxMatch match : List.copyOf(matches)) {
            counts.add(match.countWoolAsync(plugin).exceptionally(throwable -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to settle Battle Box match", throwable);
                return new HashMap<>();
            }));
        }
        CompletableFuture.allOf(counts.toArray(CompletableFuture[]::new))
                .thenRun(() -> scheduler.runTask(() -> finishEndGame(counts)));
    }

    private synchronized void finishEndGame(List<CompletableFuture<HashMap<Material, Integer>>> counts) {
        for (int i = 0; i < matches.size(); i++) {
            calculatePoints(matches.get(i), counts.get(i).join());
        }
        addPlayerPointsToDatabase();
        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        resetGame();
    }

    /** Settles one match: compares its wool floor, awards win/draw points and announces to its two teams. */
    private void calculatePoints(BattleBoxMatch match, HashMap<Material, Integer> blockCount) {
        ChampionshipTeam right = match.getRight();
        ChampionshipTeam left = match.getLeft();
        int rightWool = blockCount.getOrDefault(right.getWool().getType(), 0);
        int leftWool = blockCount.getOrDefault(left.getWool().getType(), 0);

        String message;
        if (rightWool > leftWool) {
            for (UUID uuid : right.getMembers()) addPlayerPoints(uuid, 40);
            message = MessageConfig.BATTLE_BOX_WIN.replace("%team%", right.getColoredName());
        } else if (leftWool > rightWool) {
            for (UUID uuid : left.getMembers()) addPlayerPoints(uuid, 40);
            message = MessageConfig.BATTLE_BOX_WIN.replace("%team%", left.getColoredName());
        } else {
            for (UUID uuid : right.getMembers()) addPlayerPoints(uuid, 15);
            for (UUID uuid : left.getMembers()) addPlayerPoints(uuid, 15);
            message = MessageConfig.BATTLE_BOX_DRAW;
        }

        messageMatch(match, message);
        right.sendTitleToAll(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);
        left.sendTitleToAll(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);

        String points = MessageConfig.BATTLE_BOX_SHOW_POINTS
                .replace("%team%", right.getColoredName())
                .replace("%team_points%", String.valueOf(getTeamPoints(right)))
                .replace("%rival%", left.getColoredName())
                .replace("%rival_points%", String.valueOf(getTeamPoints(left)));
        messageMatch(match, points);
    }

    private void messageMatch(BattleBoxMatch match, String message) {
        match.getRight().sendMessageToAll(message);
        match.getLeft().sendMessageToAll(message);
    }

    @Override
    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Double> entry : playerPoints.entrySet()) {
            if (entry.getValue() == 0)
                continue;
            BattleBoxMatch match = matchByPlayer.get(entry.getKey());
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(entry.getKey());
            ChampionshipTeam rival = match == null ? null : match.rivalOf(team);
            plugin.getRankManager().addPlayerPoints(entry.getKey(), rival, gameTypeEnum, gameConfig.getAreaName(), entry.getValue());
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player))
            return;

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            Player killer = player.getKiller();
            if (killer != null) {
                ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam killerTeam = plugin.getTeamManager().getTeamByPlayer(killer);
                if (playerTeam != null && killerTeam != null) {
                    addPlayerPoints(killer.getUniqueId(), 15);
                    event.deathMessage(null);
                    killer.playSound(killer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);
                    sendMessageToAllGamePlayers(MessageConfig.BATTLE_BOX_KILL_PLAYER
                            .replace("%player%", playerTeam.getColoredColor() + player.getName())
                            .replace("%killer%", killerTeam.getColoredColor() + killer.getName()));
                }
            }
        }

        BattleBoxMatch match = matchOf(player);
        Location respawn = match != null && match.getSpectatorSpawn() != null
                ? match.getSpectatorSpawn() : getSpectatorSpawnLocation();
        scheduler.runEntity(player, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleportAsync(respawn);
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player) || getGameStageEnum() != GameStageEnum.PROGRESS)
            return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team != null) {
            sendMessageToAllGamePlayers(MessageConfig.BATTLE_BOX_PLAYER_LEAVE
                    .replace("%player%", team.getColoredColor() + player.getName()));
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player))
            return;

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            teleportPlayerToPreSpawnLocation(player);
            return;
        }

        player.teleportAsync(getSpectatorSpawnLocation());
        scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    private void teleportPlayerToPreSpawnLocation(Player player) {
        BattleBoxMatch match = matchOf(player);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (match != null && team != null) {
            Location target = team.equals(match.getRight()) ? match.getRightPreSpawn()
                    : team.equals(match.getLeft()) ? match.getLeftPreSpawn() : null;
            if (target != null) {
                player.teleportAsync(target);
                scheduler.runEntity(player, () -> player.setGameMode(GameMode.ADVENTURE));
                return;
            }
        }
        player.teleportAsync(getSpectatorSpawnLocation());
        scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    private void giveItemToAllGamePlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scheduler.runEntity(player, () -> setWeaponKit(player));
            }
        }
    }

    public synchronized boolean setPlayerWeaponKit(@NotNull Player player, @NotNull BBWeaponKitEnum type) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null)
            return false;
        for (UUID uuid : championshipTeam.getMembers()) {
            if (playerWeaponKit.get(uuid) == type) {
                return uuid.equals(player.getUniqueId());
            }
        }
        playerWeaponKit.put(player.getUniqueId(), type);
        return true;
    }

    public BBWeaponKitEnum getPlayerCurrentWeaponKit(@NotNull Player player) {
        return playerWeaponKit.get(player.getUniqueId());
    }

    public synchronized BBWeaponKitEnum getPlayerWeaponKit(@NotNull Player player) {
        BBWeaponKitEnum bbWeaponKitEnum = playerWeaponKit.get(player.getUniqueId());
        if (bbWeaponKitEnum != null)
            return bbWeaponKitEnum;

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        List<BBWeaponKitEnum> kits = new ArrayList<>(List.of(BBWeaponKitEnum.values()));
        if (championshipTeam != null) {
            for (UUID uuid : championshipTeam.getMembers()) {
                BBWeaponKitEnum selected = playerWeaponKit.get(uuid);
                if (selected != null)
                    kits.remove(selected);
            }
            BBWeaponKitEnum selected = kits.iterator().next();
            if (selected != null) {
                playerWeaponKit.put(player.getUniqueId(), selected);
                return selected;
            }
            return BBWeaponKitEnum.getRandomEnum();
        }
        return null;
    }

    public void setWeaponKit(@NotNull Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null)
            return;

        player.getInventory().clear();
        PlayerInventory inventory = player.getInventory();
        ItemStack arrows = new ItemStack(Material.ARROW);
        arrows.setAmount(8);
        inventory.addItem(new ItemStack(Material.STONE_SWORD));
        inventory.addItem(new ItemStack(Material.BOW));
        inventory.addItem(arrows);

        BBWeaponKitEnum type = getPlayerWeaponKit(player);
        if (type == BBWeaponKitEnum.ARMOR) {
            inventory.addItem(new ItemStack(Material.GOLDEN_LEGGINGS));
        }
        if (type == BBWeaponKitEnum.SPEED) {
            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1), true);
                potion.setItemMeta(potionMeta);
            }
            inventory.addItem(potion);
        }
        if (type == BBWeaponKitEnum.HEAL) {
            ItemStack potion = new ItemStack(Material.SPLASH_POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 600, 2), true);
                potion.setItemMeta(potionMeta);
            }
            potion.setAmount(2);
            inventory.addItem(potion);
        }
        if (type == BBWeaponKitEnum.PULL) {
            ItemStack moreArrows = new ItemStack(Material.ARROW);
            moreArrows.setAmount(8);
            inventory.addItem(moreArrows);
        }

        inventory.addItem(new ItemStack(Material.SHEARS));
        inventory.addItem(championshipTeam.getWool());
        inventory.setBoots(championshipTeam.getBoots());
        inventory.setHelmet(championshipTeam.getHelmet());
    }

    private void summonPotions() {
        for (BattleBoxMatch match : matches) {
            for (Location location : match.getPotionLocations()) {
                World world = location.getWorld();
                if (world == null)
                    continue;
                ItemStack item = new ItemStack(Material.SPLASH_POTION);
                PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
                if (potionMeta != null) {
                    potionMeta.setBasePotionType(PotionType.STRONG_HARMING);
                    item.setItemMeta(potionMeta);
                    ItemStack potion = item.clone();
                    scheduler.runAtLocation(location, () -> {
                        Item dropped = world.dropItem(location, potion);
                        dropped.setGlowing(true);
                    });
                }
            }
        }
    }

    @Override
    public void cleanDroppedItems() {
        for (BattleBoxMatch match : matches) {
            World world = match.getSpectatorSpawn() != null ? match.getSpectatorSpawn().getWorld() : null;
            if (world == null)
                continue;
            cleanEntities(world, match.getAreaBox(), Item.class);
        }
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName()))
            return true;
        Vector point = location.toVector();
        for (BattleBoxMatch match : matches) {
            if (match.isInArea(point))
                return false;
        }
        return true;
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public BattleBoxConfig getGameConfig() {
        return (BattleBoxConfig) gameConfig;
    }

    @Override
    public BattleBoxHandler getGameHandler() {
        return (BattleBoxHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return "battlebox";
    }
}
