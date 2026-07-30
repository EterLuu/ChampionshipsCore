package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public abstract class BaseGameInstance {
    private static final String SPECTATOR_TIMER_BOSS_BAR = "spectator-game-timer";
    protected final HashSet<UUID> spectators = new HashSet<>();
    protected final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    protected final ChampionshipsCore plugin;
    protected final BukkitScheduler scheduler;
    protected final GameInstanceHandler gameInstanceHandler;
    protected final PlayerManager playerManager;
    protected final Map<String, BossBar> bossBars = new ConcurrentHashMap<>();

    /** Duration (seconds) of the optional rule-introduction phase preceding the normal preparation. */
    protected static final int INTRODUCTION_DURATION = 45;

    /** True while players are gathered at the introduction spawn point for the rules broadcast. */
    protected volatile boolean introductionPhase = false;
    protected BukkitTask introductionTask;
    private boolean introductionEnabledForNextStart = true;

    /** Final five-second countdown, isolated from every game's live timer. */
    protected BukkitTask finalCountdownTask;

    private static final Note BIT_C4 = Note.natural(0, Note.Tone.C);
    private static final Note BIT_C5 = Note.natural(1, Note.Tone.C);

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseGameInstance(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler,
                            BaseGameConfig gameConfig) {
        this.playerManager = plugin.getPlayerManager();

        this.gameStageEnum = GameStageEnum.END;
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
        this.gameTypeEnum = gameTypeEnum;

        this.gameHandler = gameHandler;
        this.gameConfig = gameConfig;

        gameInstanceHandler = new GameInstanceHandler(plugin, this);
        gameInstanceHandler.register();
    }

    public void resetGame() {
        cancelIntroduction();
        cancelFinalCountdown();
        resetBaseArea();
        playerPoints.clear();
        clearBossBars();

        setGameStageEnum(GameStageEnum.WAITING);
        logGame(Level.INFO, "流程", "场地已重置，等待下一场");
    }

    /** Permanently releases listeners and UI owned by this instance when its manager unloads it. */
    public void dispose() {
        cancelIntroduction();
        cancelFinalCountdown();
        clearBossBars();
        getGameHandler().unRegister();
        gameInstanceHandler.unRegister();
    }

    public void resetPlayerHealthFoodEffectLevelInventory() {
        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        clearEffectsForAllGamePlayers();
        cleanInventoryForAllGamePlayers();
        changeLevelForAllGamePlayers(0);
    }

    public void addPlayerPoints(UUID uuid, double points) {
        playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0d) + points);
        logGame(Level.INFO, "积分", "玩家=" + plugin.getPlayerManager().getPlayerName(uuid)
                + " uuid=" + uuid + " 变更=" + formatPointChange(points));
        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
        if (championshipPlayer != null)
            championshipPlayer.sendActionBar("&e[+] " + points);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0d) + points);
            logGame(Level.INFO, "积分", "玩家=" + plugin.getPlayerManager().getPlayerName(uuid)
                    + " uuid=" + uuid + " 变更=" + formatPointChange(points));
        }
    }

    protected void logGame(Level level, String event, String message) {
        String area = gameConfig == null || gameConfig.getAreaName() == null ? "-" : gameConfig.getAreaName();
        String stage = gameStageEnum == null ? "-" : gameStageEnum.name();
        plugin.getLogger().log(level, Utils.formatGameLog(gameTypeEnum, area, stage, event, message));
    }

    private String formatPointChange(double points) {
        return (points >= 0 ? "+" : "") + Utils.formatPoints(points);
    }

    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0)
                plugin.getRankManager().addPlayerPoints(playerPointEntry.getKey(), null, gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
        }
        plugin.getRankManager().refreshAfterPendingPointWrites();
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0d);
        }

        return points;
    }

    public String getPlayerPointsRank() {
        ArrayList<Map.Entry<UUID, Double>> list;
        list = new ArrayList<>(playerPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_BOARD_BAR
                        .replace("%game%", gameTypeEnum.toString()))
                .append("\n");

        int i = 1;
        for (Map.Entry<UUID, Double> entry : list) {
            String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                    .replace("%player_rank%", String.valueOf(i))
                    .replace("%player%", Utils.formatPlayerName(entry.getKey()))
                    .replace("%player_point%", Utils.formatPoints(entry.getValue()));

            stringBuilder.append(row).append("\n");
            i++;
        }

        return stringBuilder.toString();
    }

    public GameStageEnum getGameStageEnum() {
        synchronized (this) {
            return this.gameStageEnum;
        }
    }

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        synchronized (this) {
            this.gameStageEnum = gameStageEnum;
        }
    }

    public void loadMap(World.Environment environment) {
        if (!plugin.isLoaded())
            return;

        clearBossBars();
        teleportAllSpectators(getLobbyLocation());

        setGameStageEnum(GameStageEnum.END);
        getGameHandler().unRegister();
        logGame(Level.INFO, "世界", "开始加载 " + getWorldName());

        File target = plugin.getWorldManager().getWorldFolder(getWorldName());

        // If already has a same world, delete it.
        if (target.isDirectory()) {
            String[] list = target.list();
            if (list != null && list.length > 0) {
                plugin.getWorldManager().deleteWorld(getWorldName(), true);
            }
        }

        File maps = new File(plugin.getDataFolder(), "maps");
        File source = new File(maps, getWorldName());

        // Copy world files to destination
        if (!plugin.getWorldManager().copyWorldFiles(source, target)) {
            logGame(Level.SEVERE, "世界", "加载失败：无法从地图模板复制 " + getWorldName());
            return;
        }

        // Load world
        if (!plugin.getWorldManager().loadWorld(getWorldName(), environment, false)) {
            logGame(Level.SEVERE, "世界", "加载失败：Bukkit 无法加载 " + getWorldName());
            return;
        }

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
        logGame(Level.INFO, "世界", "加载完成 " + getWorldName());

        teleportAllSpectators(getSpectatorSpawnLocation());
    }

    /** True only when every instance backed by this same world is idle and the map can be reloaded safely. */
    public boolean canSaveMap() {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        BaseGameInstanceManager<? extends BaseGameInstance> manager =
                plugin.getGameManager().getAreaManager(gameTypeEnum);
        if (manager == null)
            return true;
        return manager.getRuntimeInstances().stream()
                .filter(instance -> getWorldName().equals(instance.getWorldName()))
                .allMatch(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING);
    }

    public boolean saveMap(World.Environment environment) {
        if (!canSaveMap()) {
            logGame(Level.WARNING, "世界", "保存被拒绝：同一地图仍有运行中的游戏实例");
            return false;
        }

        setGameStageEnum(GameStageEnum.END);
        logGame(Level.INFO, "世界", "开始保存 " + getWorldName());
        teleportAllSpectators(getLobbyLocation());

        World editWorld = plugin.getServer().getWorld(getWorldName());
        if (editWorld == null) {
            setGameStageEnum(GameStageEnum.WAITING);
            logGame(Level.WARNING, "世界", "保存失败：世界未加载 " + getWorldName());
            return false;
        }
        for (Player player : editWorld.getPlayers()) {
            player.teleport(CCConfig.LOBBY_LOCATION);
        }

        // Unload world but not remove files
        plugin.getWorldManager().unloadWorld(getWorldName(), true);

        File dataDirectory = new File(plugin.getDataFolder(), "maps");
        File target = new File(dataDirectory, getWorldName());

        // Delete old world files stored in maps
        plugin.getWorldManager().deleteWorldFiles(target);

        File source = plugin.getWorldManager().getWorldFolder(getWorldName());

        if (!plugin.getWorldManager().copyWorldFiles(source, target)) {
            plugin.getWorldManager().deleteWorldFiles(target);
            plugin.getWorldManager().loadWorld(getWorldName(), environment, false);
            setGameStageEnum(GameStageEnum.WAITING);
            logGame(Level.SEVERE, "世界", "保存失败：无法写入地图模板，已保留并重新加载编辑世界 " + getWorldName());
            return false;
        }
        plugin.getWorldManager().deleteWorldFiles(source);

        loadMap(environment);
        return getGameStageEnum() == GameStageEnum.WAITING
                && plugin.getServer().getWorld(getWorldName()) != null;
    }

    private BossBar createBossBar(String title, BarColor color, BarStyle style) {
        return Bukkit.createBossBar(Utils.translateColorCodes(title), color, style);
    }

    public BossBar createBossBar(String name, String title, BarColor color, BarStyle style) {
        BossBar bossBar = createBossBar(title, color, style);
        if (bossBars.containsKey(name))
            removeBossBar(name);

        bossBars.put(name, bossBar);
        return bossBar;
    }

    public void removeBossBar(String name) {
        BossBar bossBar = bossBars.remove(name);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /** Removes every area-owned bar and all of its viewers. Safe to call repeatedly. */
    public void clearBossBars() {
        for (BossBar bossBar : new ArrayList<>(bossBars.values()))
            bossBar.removeAll();
        bossBars.clear();
    }

    private void removePlayerFromBossBars(Player player) {
        for (BossBar bossBar : bossBars.values())
            bossBar.removePlayer(player);
    }

    /** Updates the shared live timer bar and synchronizes it to current in-game and external spectators. */
    protected void updateSpectatorTimerBossBar(String title, int remainingSeconds, int durationSeconds) {
        double progress = durationSeconds <= 0 ? 0D : remainingSeconds / (double) durationSeconds;
        updateSpectatorTimerBossBar(title, progress);
    }

    /** Variant for non-countdown clocks, where the caller supplies the semantic progress directly. */
    protected void updateSpectatorTimerBossBar(String title, double progress) {
        BossBar bossBar = bossBars.computeIfAbsent(SPECTATOR_TIMER_BOSS_BAR,
                ignored -> createBossBar(title, BarColor.YELLOW, BarStyle.SOLID));
        bossBar.setTitle(Utils.translateColorCodes(title));
        bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));

        Set<Player> viewers = new LinkedHashSet<>(getOnlineParticipantSpectators());
        viewers.addAll(getOnlineSpectators());
        for (Map.Entry<String, BossBar> entry : bossBars.entrySet()) {
            if (SPECTATOR_TIMER_BOSS_BAR.equals(entry.getKey()))
                continue;
            for (Player viewer : viewers)
                entry.getValue().removePlayer(viewer);
        }
        for (Player current : new ArrayList<>(bossBar.getPlayers())) {
            if (!viewers.contains(current))
                bossBar.removePlayer(current);
        }
        for (Player viewer : viewers)
            bossBar.addPlayer(viewer);
    }

    public void setBossBar(String name, String title) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.setTitle(Utils.translateColorCodes(title));
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public void addBossBarPlayer(String name, Player player) {
        if (player == null)
            return;

        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.addPlayer(player);
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public void removeBossBarPlayer(String name, Player player) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public void setBossBarProgress(String name, double progress) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.setProgress(progress);
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public Location getLobbyLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    public boolean isIntroductionPhase() {
        return introductionPhase;
    }

    /** Whether participants should be held in place during the final five-second countdown. */
    public boolean freezeMovementDuringCountdown() {
        return true;
    }

    public void setIntroductionEnabledForNextStart(boolean enabled) {
        introductionEnabledForNextStart = enabled;
    }

    /**
     * Runs the optional rule-introduction phase. When the area config provides an introduction spawn
     * point and at least one rule section, every player is teleported there (still in PREPARATION
     * stage, free to walk around inside the area) while the rule sections are broadcast one at a time
     * in chat over {@link #INTRODUCTION_DURATION} seconds; afterwards {@code onComplete} (the normal
     * preparation: spawn assignment + countdown) runs. Without such config the introduction is skipped
     * and {@code onComplete} runs immediately.
     */
    protected void startGameIntroduction(@NotNull Runnable onComplete) {
        boolean showIntroduction = introductionEnabledForNextStart;
        introductionEnabledForNextStart = true;
        if (!showIntroduction) {
            onComplete.run();
            return;
        }

        List<List<String>> rules = getIntroductionRules();
        Location introductionSpawnPoint = gameConfig.getIntroductionSpawnPoint();
        if (rules == null || rules.isEmpty() || introductionSpawnPoint == null) {
            onComplete.run();
            return;
        }

        introductionPhase = true;
        teleportAllPlayers(introductionSpawnPoint);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        final int sectionCount = rules.size();
        // First broadcast at t=10s (players get a moment to look around after teleporting in), then one
        // section every 10s (with 3 sections: t=10s/20s/30s, the last one stays up for 15s). Section
        // counts that wouldn't fit fall back to a tighter even distribution.
        final int interval = Math.max(1, Math.min(10, INTRODUCTION_DURATION / (sectionCount + 1)));
        final int[] remain = {INTRODUCTION_DURATION};

        introductionTask = scheduler.runTaskTimer(plugin, () -> {
            int elapsed = INTRODUCTION_DURATION - remain[0];
            if (remain[0] > 0 && elapsed > 0 && elapsed % interval == 0) {
                int section = elapsed / interval - 1;
                if (section < sectionCount)
                    broadcastRuleSection(rules.get(section));
            }

            showPreparationCountdown(remain[0]);

            if (remain[0] == 0) {
                cancelIntroduction();
                // The game may have been ended during the introduction (stop command / force end).
                if (getGameStageEnum() == GameStageEnum.PREPARATION)
                    onComplete.run();
                return;
            }

            remain[0]--;
        }, 0, 20L);
    }

    /** Variant-aware games override this without coupling the base lifecycle to a concrete config model. */
    protected List<List<String>> getIntroductionRules() {
        return gameConfig.getRules();
    }

    private void broadcastRuleSection(@NotNull List<String> lines) {
        for (String line : lines)
            sendMessageToAllGamePlayers(Utils.translateColorCodes(line));
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1F);
    }

    /** One durable chat line at preparation; later phase changes stay out of chat. */
    protected void announceGamePreparation(String message, String title, String subtitle) {
        sendMessageToAllGamePlayers(message);
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "进入场地准备");
    }

    protected void showPreparationCountdown(int seconds) {
        sendActionBarToAllGamePlayers(MessageConfig.GAME_PREPARATION_COUNT_DOWN
                .replace("%game%", gameTypeEnum.toString())
                .replace("%time%", String.valueOf(Math.max(0, seconds))));
    }

    protected void announceGameStartSoon(String title, String subtitle) {
        sendActionBarToAllGamePlayers(subtitle);
        sendTitleToAllGamePlayers(title, subtitle);
    }

    /** Runs the default five-second final countdown. */
    protected void startFinalCountdown(String gameTitle, String startTitle, String startSubtitle,
                                       @NotNull Runnable onStart) {
        startFinalCountdown(5, gameTitle, startTitle, startSubtitle, onStart);
    }

    /**
     * Runs the authoritative final countdown. The supplied callback starts live game systems at T0;
     * the stage transition, start title and high cue all happen in that same server tick.
     */
    protected void startFinalCountdown(int countdownSeconds, String gameTitle, String startTitle,
                                       String startSubtitle, @NotNull Runnable onStart) {
        cancelFinalCountdown();
        setGameStageEnum(GameStageEnum.COUNTDOWN);
        int duration = Math.max(0, countdownSeconds);
        logGame(Level.INFO, "流程", "开始 " + duration + " 秒开赛倒计时");
        final int[] remaining = {duration};

        finalCountdownTask = scheduler.runTaskTimer(plugin, () -> {
            int seconds = remaining[0];
            if (seconds > 0) {
                String title = MessageConfig.GAME_START_COUNT_DOWN_TITLE
                        .replace("%time%", String.valueOf(seconds));
                String subtitle = getFinalCountdownSubtitle(gameTitle);
                String actionBar = MessageConfig.GAME_START_COUNT_DOWN_ACTION_BAR
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%time%", String.valueOf(seconds));
                sendTitleToAllGamePlayers(title, subtitle);
                sendActionBarToAllGamePlayers(actionBar);
                changeLevelForAllGamePlayers(seconds);
                playCountdownBit(BIT_C4);
                remaining[0]--;
                return;
            }

            if (finalCountdownTask != null)
                finalCountdownTask.cancel();
            finalCountdownTask = null;
            if (getGameStageEnum() != GameStageEnum.COUNTDOWN)
                return;

            changeLevelForAllGamePlayers(0);
            setGameStageEnum(GameStageEnum.PROGRESS);
            onStart.run();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                announceGameStart(startTitle, startSubtitle);
                playCountdownBit(BIT_C5);
            }
        }, 0L, 20L);
    }

    /**
     * A live remaining-time clock with an exact endpoint: duration is rendered at T0, the first decrement
     * occurs at T0+20 ticks, and zero/onEnd occur at T0+duration*20 ticks.
     */
    protected BukkitTask startRemainingTimer(int durationSeconds, @NotNull IntConsumer onTick,
                                             @NotNull Runnable onEnd) {
        final int[] remaining = {Math.max(0, durationSeconds)};
        onTick.accept(remaining[0]);
        if (remaining[0] == 0) {
            onEnd.run();
            return null;
        }

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = scheduler.runTaskTimer(plugin, () -> {
            remaining[0]--;
            onTick.accept(remaining[0]);
            if (remaining[0] == 0) {
                taskHolder[0].cancel();
                onEnd.run();
            }
        }, 20L, 20L);
        return taskHolder[0];
    }

    public void cancelFinalCountdown() {
        if (finalCountdownTask != null) {
            finalCountdownTask.cancel();
            finalCountdownTask = null;
        }
    }

    protected String getFinalCountdownSubtitle(String gameTitle) {
        return MessageConfig.GAME_START_COUNT_DOWN_SUBTITLE.replace("%game%", gameTitle);
    }

    private void playCountdownBit(Note note) {
        playNoteToAllGamePlayers(Instrument.BIT, note);
        for (Player spectator : getOnlineSpectators()) {
            spectator.playNote(spectator.getLocation(), Instrument.BIT, note);
        }
    }

    protected void announceGameStart(String title, String subtitle) {
        sendActionBarToAllGamePlayers(MessageConfig.GAME_START_ACTION_BAR
                .replace("%game%", gameTypeEnum.toString()));
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "游戏开始");
    }

    protected void announceGameEnd(String title, String subtitle) {
        clearBossBars();
        sendActionBarToAllGamePlayers(MessageConfig.GAME_END_ACTION_BAR
                .replace("%game%", gameTypeEnum.toString()));
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "游戏结束，开始结算");
    }

    /** Cancels a running rule-introduction phase (task + flag); safe to call at any time. */
    public void cancelIntroduction() {
        if (introductionTask != null) {
            introductionTask.cancel();
            introductionTask = null;
        }
        introductionPhase = false;
    }

    /**
     * Where a player (re)joining or being pulled back during PREPARATION should land: the introduction
     * spawn point while the introduction phase runs, otherwise the given normal-preparation fallback.
     */
    public Location getPreparationTeleportLocation(@NotNull Location fallback) {
        Location introductionSpawnPoint = gameConfig.getIntroductionSpawnPoint();
        if (introductionPhase && introductionSpawnPoint != null)
            return introductionSpawnPoint;
        return fallback;
    }

    public boolean isSpectator(@NotNull Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public void handleSpectatorDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isSpectator(player)) {
            event.setDroppedExp(0);
            event.getDrops().clear();
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                removeSpectator(player);
            });
        }
    }

    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            player.teleport(getSpectatorSpawnLocation());
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.SPECTATOR));
        }
    }

    public void teleportAllSpectators(@NotNull Location location) {
        for (Player player : getOnlineSpectators()) {
            player.teleport(location);
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.SPECTATOR));
        }
    }

    public void addSpectator(@NotNull Player player) {
        spectators.add(player.getUniqueId());
        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    public void removeAllSpectator() {
        for (Player player : getOnlineSpectators()) {
            removeSpectator(player);
        }
        spectators.clear();
    }

    /**
     * Whether spectators of this area survive a disconnect and are restored on reconnect (teleported
     * back to the spectator spawn by {@link #handleSpectatorJoin}). Default {@code false}: a spectator
     * who quits is dropped, because {@code GameManagerHandler.onPlayerQuit} calls {@code leaveSpectating}.
     * Areas that opt in must release their spectators on game end via {@link #releaseAllSpectators()},
     * otherwise a reconnecting spectator would land in a finished game.
     */
    public boolean keepSpectatorAcrossReconnect() {
        return false;
    }

    /**
     * Releases every spectator - online ones are teleported to the lobby and set to ADVENTURE, offline
     * ones are just dropped - and clears both this area's spectator set and the GameManager's
     * spectator-status map for them. Used on game end by areas that keep spectators across reconnect.
     */
    public void releaseAllSpectators() {
        Set<UUID> ids = new HashSet<>(spectators);
        for (UUID uuid : ids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);                 // teleport to lobby + ADVENTURE; drop from set
            } else {
                onlyRemoveSpectatorFromList(uuid);       // drop from set
            }
        }
        // Clear the GameManager's spectator-status entries for the released UUIDs (covers offline
        // spectators that leaveSpectating-on-quit would otherwise have cleared).
        for (UUID uuid : ids) {
            plugin.getGameManager().removeSpectator(uuid);
        }
    }

    public void endGameFinally() {
        cancelIntroduction();
        cancelFinalCountdown();
        clearBossBars();
        removeAllSpectator();
        removeAllPlayers();
        endGame();
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (spectators.contains(uuid)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);
            } else {
                onlyRemoveSpectatorFromList(uuid);
            }
        }
    }

    public void removeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (spectators.contains(uuid)) {
            spectators.remove(player.getUniqueId());
            removePlayerFromBossBars(player);
            player.teleport(getLobbyLocation());
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.ADVENTURE));
            player.setLevel(0);
        }
    }

    public void onlyRemoveSpectatorFromList(@NotNull UUID uuid) {
        spectators.remove(uuid);
    }

    public List<Player> getOnlineSpectators() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }

        return list;
    }

    public List<ChampionshipPlayer> getOnlineCCSpectators() {
        List<ChampionshipPlayer> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
            if (championshipPlayer != null) {
                list.add(championshipPlayer);
            }
        }

        return list;
    }

    public void sendMessageToAllSpectators(String message) {
        for (Player player : getOnlineSpectators()) {
            player.sendMessage(message);
        }
    }

    public void sendActionBarToAllSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendActionBar(message);
        }
    }

    public void changeLevelToAllSpectators(int level) {
        for (Player player : getOnlineSpectators()) {
            player.setLevel(Math.abs(level));
        }
    }

    public void sendTitleToAllSpectators(String title, String subTitle) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendTitle(title, subTitle);
        }
    }

    public void cleanDroppedItems() {
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world != null) {
            world.getNearbyEntities(new BoundingBox(
                            pos1.getX(),
                            pos1.getY(),
                            pos1.getZ(),
                            pos2.getX(),
                            pos2.getY(),
                            pos2.getZ()))
                    .forEach(entity -> {
                        if (entity instanceof Item) {
                            entity.remove();
                        }
                    });
        }
    }

    public boolean notInArea(Location location) {
        if (location.getWorld() != null && getSpectatorSpawnLocation().getWorld() != null && location.getWorld().getName().equals(getSpectatorSpawnLocation().getWorld().getName())) {
            return !location.toVector().isInAABB(getGameConfig().getAreaPos1(), getGameConfig().getAreaPos2());
        }

        return true;
    }

    /**
     * The space an external spectator may explore. Usually this is the instance boundary, while a
     * shared map can override it to include all of its permanently allocated instance copies.
     */
    public boolean isSpectatorLocationAllowed(@NotNull Location location) {
        return !notInArea(location);
    }

    public abstract Location getSpectatorSpawnLocation();

    public abstract int getTimer();

    public abstract void endGame();

    public abstract void resetBaseArea();

    public abstract void resetArea();

    public abstract BaseGameConfig getGameConfig();

    public abstract BaseListener getGameHandler();

    public abstract String getWorldName();

    public abstract void removeAllPlayers();

    public abstract void startGamePreparation();

    public abstract void sendMessageToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGamePlayers(String message);

    protected abstract Collection<Player> getOnlineParticipantSpectators();

    public abstract void sendTitleToAllGamePlayers(String title, String subTitle);

    public abstract void changeLevelForAllGamePlayers(int level);

    public abstract void changeGameModelForAllGamePlayers(GameMode gameMode);

    public abstract void setHealthForAllGamePlayers(double health);

    public abstract void revokeAllGamePlayersAdvancements();

    public abstract void setFoodLevelForAllGamePlayers(int level);

    public abstract void teleportAllPlayers(Location location);

    public abstract void clearEffectsForAllGamePlayers();

    public abstract void cleanInventoryForAllGamePlayers();

    public abstract void playSoundToAllGamePlayers(Sound sound, float volume, float pitch);

    public abstract void playNoteToAllGamePlayers(Instrument instrument, Note note);

    public abstract boolean notAreaPlayer(@NotNull Player player);

    public abstract void handlePlayerDeath(@NotNull PlayerDeathEvent event);

    public abstract void handlePlayerQuit(@NotNull PlayerQuitEvent event);

    public abstract void handlePlayerJoin(@NotNull PlayerJoinEvent event);
}
