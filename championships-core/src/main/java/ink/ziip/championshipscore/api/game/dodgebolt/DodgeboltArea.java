package ink.ziip.championshipscore.api.game.dodgebolt;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.ThreadLocalRandom;

/** One complete, non-scoring best-of-five Dodgebolt final. */
public final class DodgeboltArea extends BasePairedGameInstance {
    private static final int WINS_TO_CHAMPION = 3;
    private static final int PROJECTILE_MAX_TICKS = 240;
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final NamespacedKey tokenKey;
    private final NamespacedKey tokenSideKey;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Set<UUID> tokenItems = new HashSet<>();
    private final Map<UUID, List<ItemStack>> stowedTokens = new HashMap<>();
    private final Set<UUID> alivePlayers = new LinkedHashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> forcedParticipants = new LinkedHashSet<>();
    private final Map<UUID, Location> pauseLocations = new HashMap<>();
    private final Map<BlockPosition, BlockData> platformSnapshot = new LinkedHashMap<>();
    private final Set<Column> activePlatformColumns = new LinkedHashSet<>();
    private final Set<Column> pendingShrinkColumns = new LinkedHashSet<>();
    private final Map<BlockPosition, BlockData> shrinkWarningSnapshot = new LinkedHashMap<>();

    @Getter private int timer;
    @Getter private int roundNumber;
    @Getter private int rightWins;
    @Getter private int leftWins;
    @Getter private int shrinkLevel;
    @Getter private int shotsThisRound;
    @Getter private boolean paused;
    private boolean partialRoster;
    @Getter private @Nullable ChampionshipTeam champion;

    private int shotsSinceShrink;
    private int eliminationsThisRound;
    private int queuedShrinkLayers;
    private int completedShrinkEvents;
    private int shrinkWarningTicks;
    private int shrinkWarningSeconds;
    private boolean shrinkWarningVisible;
    private ChampionshipTeam firstRoundArrowTeam;
    private BukkitTask preparationTask;
    private BukkitTask restartTask;
    private BukkitTask flightMonitorTask;
    private BukkitTask shrinkTask;
    private BukkitTask championFireworksTask;

    public DodgeboltArea(ChampionshipsCore plugin, DodgeboltConfig config, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.Dodgebolt, new DodgeboltHandler(plugin), config);
        tokenKey = new NamespacedKey(plugin, "dodgebolt_arrow_token");
        tokenSideKey = new NamespacedKey(plugin, "dodgebolt_arrow_side");
        getGameHandler().setArea(this);
        config.setAreaName(areaName);
        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    public void preloadMap() {
        loadPublishedMapOrDraft(World.Environment.NORMAL);
    }

    public void setFirstRoundArrowTeam(@Nullable ChampionshipTeam team) {
        firstRoundArrowTeam = team;
    }

    @Override
    public boolean tryStartGame(ChampionshipTeam rightTeam, ChampionshipTeam leftTeam) {
        return tryStartGame(rightTeam, leftTeam, false);
    }

    public boolean tryStartGame(ChampionshipTeam rightTeam, ChampionshipTeam leftTeam, boolean forcePartialRoster) {
        if (rightTeam.equals(leftTeam) || rightTeam.getMembers().isEmpty() || leftTeam.getMembers().isEmpty())
            return false;
        List<Player> rightOnline = rightTeam.getOnlinePlayers();
        List<Player> leftOnline = leftTeam.getOnlinePlayers();
        if (forcePartialRoster) {
            if (rightOnline.isEmpty() || leftOnline.isEmpty()) return false;
        } else if (rightOnline.size() != rightTeam.getMembers().size()
                || leftOnline.size() != leftTeam.getMembers().size()) {
            return false;
        }
        partialRoster = forcePartialRoster;
        forcedParticipants.clear();
        if (forcePartialRoster) {
            rightOnline.forEach(player -> forcedParticipants.add(player.getUniqueId()));
            leftOnline.forEach(player -> forcedParticipants.add(player.getUniqueId()));
        }
        boolean started = super.tryStartGame(rightTeam, leftTeam);
        if (!started) {
            partialRoster = false;
            forcedParticipants.clear();
        }
        return started;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        applyFinalistColors();
        snapshotPlatform();
        startGameIntroduction(this::startFormalPreparation);
    }

    private void startFormalPreparation() {
        roundNumber = 1;
        champion = null;
        paused = false;
        restoreParticipantCollisions();
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        teleportTeamsToRoundSpawns();
        announceGamePreparation(MessageConfig.DODGEBOLT_START_PREPARATION,
                MessageConfig.DODGEBOLT_START_PREPARATION_TITLE,
                MessageConfig.DODGEBOLT_START_PREPARATION_SUBTITLE);

        final int[] remaining = {10};
        preparationTask = scheduler.runTaskTimer(plugin, () -> {
            timer = remaining[0];
            showPreparationCountdown(timer);
            if (timer == 0) {
                cancelTask(preparationTask);
                preparationTask = null;
                prepareRound();
                return;
            }
            remaining[0]--;
        }, 0L, 20L);
    }

    private void prepareRound() {
        cleanupRoundEntities();
        restorePlatform();
        shrinkLevel = 0;
        shotsThisRound = 0;
        shotsSinceShrink = 0;
        eliminationsThisRound = 0;
        queuedShrinkLayers = 0;
        completedShrinkEvents = 0;
        alivePlayers.clear();
        eliminatedPlayers.clear();
        addRoundPlayers(rightChampionshipTeam);
        addRoundPlayers(leftChampionshipTeam);

        restoreParticipantCollisions();
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        teleportTeamsToRoundSpawns();
        giveBowsToAlivePlayers();
        updateScoreBar();
        startFinalCountdown(MessageConfig.DODGEBOLT_GAME_START_SOON_TITLE,
                MessageConfig.DODGEBOLT_GAME_START_TITLE,
                MessageConfig.DODGEBOLT_GAME_START_SUBTITLE,
                this::beginRound);
    }

    private void beginRound() {
        UUID disconnected = firstOfflineAlivePlayer();
        if (disconnected != null) {
            // The shared countdown changes the stage to PROGRESS immediately before this callback.
            // Re-check connectivity in that same tick so a quit during COUNTDOWN/intermission can never
            // expose even one live tick in which the remaining players are allowed to shoot.
            pauseMatch(disconnected, null);
        }
        if (roundNumber == 1) {
            DodgeboltSide side = sideOf(firstRoundArrowTeam);
            if (side == null) side = DodgeboltSide.RIGHT;
            spawnArrowToken(side);
            spawnArrowToken(side);
        } else {
            spawnArrowToken(DodgeboltSide.RIGHT);
            spawnArrowToken(DodgeboltSide.LEFT);
        }
        startFlightMonitor();
        updateScoreBar();
    }

    public boolean isAlive(@NotNull Player player) {
        return alivePlayers.contains(player.getUniqueId());
    }

    public boolean isTokenArrow(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.ARROW || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(tokenKey, PersistentDataType.STRING);
    }

    public @Nullable DodgeboltSide tokenSide(@Nullable ItemStack stack) {
        if (!isTokenArrow(stack)) return null;
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(tokenSideKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return DodgeboltSide.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean canPickUpToken(@NotNull Player player, @NotNull Item item) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || paused || !isAlive(player)) return false;
        DodgeboltSide side = tokenSide(item.getItemStack());
        return side != null && side == sideOf(player) && contains(sideArea(side), player.getLocation());
    }

    public void tokenPickedUp(@NotNull Item item) {
        tokenItems.remove(item.getUniqueId());
    }

    public boolean canShoot(@NotNull Player player) {
        DodgeboltSide side = sideOf(player);
        return canShootFromArea(player, side) && hasTokenArrow(player, side);
    }

    private boolean canShootFromArea(@NotNull Player player, @Nullable DodgeboltSide side) {
        return side != null && getGameStageEnum() == GameStageEnum.PROGRESS && !paused
                && isAlive(player) && contains(shootArea(side), player.getLocation());
    }

    public boolean canShoot(@NotNull Player player, @Nullable ItemStack consumedArrow) {
        DodgeboltSide side = sideOf(player);
        return canShootFromArea(player, side) && tokenSide(consumedArrow) == side;
    }

    public void registerShot(@NotNull Player shooter, @NotNull Entity projectile) {
        if (!(projectile instanceof Arrow arrow)) return;
        DodgeboltSide shooterSide = sideOf(shooter);
        if (shooterSide == null) return;
        flights.put(arrow.getUniqueId(), new Flight(arrow, shooterSide.opposite()));
        shotsThisRound++;
        shotsSinceShrink++;
        int threshold = Math.max(1, getGameConfig().getShotsPerShrink());
        if (shotsSinceShrink >= threshold) {
            shotsSinceShrink -= threshold;
            queueShrink(1);
        }
        updateScoreBar();
    }

    public boolean isTrackedProjectile(@NotNull Entity projectile) {
        return flights.containsKey(projectile.getUniqueId());
    }

    public void resolveProjectile(@NotNull Arrow arrow, @Nullable Player hitPlayer) {
        Flight flight = flights.remove(arrow.getUniqueId());
        if (flight == null) return;
        arrow.remove();
        if (hitPlayer != null && isAlive(hitPlayer) && sideOf(hitPlayer) == flight.destination) {
            eliminate(hitPlayer, true);
        }
        if (getGameStageEnum() == GameStageEnum.PROGRESS && !roundWon()) {
            scheduler.runTask(plugin, () -> {
                if (getGameStageEnum() == GameStageEnum.PROGRESS && !roundWon())
                    spawnArrowToken(flight.destination);
            });
        }
    }

    public boolean eliminate(@NotNull Player player, boolean arrowHit) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || paused || !alivePlayers.remove(player.getUniqueId()))
            return false;
        DodgeboltSide side = sideOf(player);
        preserveHeldTokens(player, side);
        eliminatedPlayers.add(player.getUniqueId());
        player.getInventory().clear();
        teleportToSpectatorArea(player);
        eliminationsThisRound++;
        sendMessageToAllGamePlayers((arrowHit ? MessageConfig.DODGEBOLT_HIT : MessageConfig.DODGEBOLT_ELIMINATED)
                .replace("%player%", Utils.formatPlayerName(player)));
        playSoundToAllGamePlayers(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9F, 1.2F);

        if (roundWon()) {
            finishRound(aliveCount(DodgeboltSide.RIGHT) > 0 ? DodgeboltSide.RIGHT : DodgeboltSide.LEFT);
        } else {
            queueShrink(eliminationsThisRound <= 2 ? 2 : 1);
            updateScoreBar();
        }
        return true;
    }

    public boolean pauseMatch(@Nullable Player disconnected) {
        return pauseMatch(disconnected == null ? null : disconnected.getUniqueId(),
                disconnected == null ? null : disconnected.getLocation());
    }

    private boolean pauseMatch(@Nullable UUID disconnected, @Nullable Location disconnectedLocation) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || paused) return false;
        paused = true;
        pauseLocations.clear();
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) pauseLocations.put(uuid, player.getLocation().clone());
        }
        if (disconnected != null && disconnectedLocation != null)
            pauseLocations.put(disconnected, disconnectedLocation.clone());
        for (Flight flight : flights.values()) {
            flight.savedVelocity = flight.arrow.getVelocity().clone();
            flight.arrow.setVelocity(new Vector());
            flight.arrow.setGravity(false);
        }
        String playerName = disconnected == null ? "管理员" : Utils.formatPlayerName(disconnected);
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_PAUSED.replace("%player%", playerName));
        sendPauseTitle(disconnected);
        updateScoreBar();
        return true;
    }

    private void sendPauseTitle(@Nullable UUID disconnected) {
        String playerName = disconnected == null ? "管理员" : Utils.formatPlayerName(disconnected);
        sendTitleToAllGamePlayers(MessageConfig.DODGEBOLT_PAUSED_TITLE,
                MessageConfig.DODGEBOLT_PAUSED_SUBTITLE.replace("%player%", playerName));
    }

    public boolean resumeMatch() {
        if (!paused || getGameStageEnum() != GameStageEnum.PROGRESS) return false;
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return false;
        }
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            Location location = pauseLocations.get(uuid);
            if (player != null && location != null) player.teleport(location);
        }
        for (Flight flight : flights.values()) {
            flight.arrow.setGravity(true);
            if (flight.savedVelocity != null) flight.arrow.setVelocity(flight.savedVelocity);
            flight.savedVelocity = null;
        }
        paused = false;
        pauseLocations.clear();
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_RESUMED);
        sendTitleToAllGamePlayers(MessageConfig.DODGEBOLT_RESUMED_TITLE,
                MessageConfig.DODGEBOLT_RESUMED_SUBTITLE);
        updateScoreBar();
        return true;
    }

    public boolean restartCurrentRound() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return false;
        cancelRoundTasks();
        paused = false;
        setGameStageEnum(GameStageEnum.STOPPING);
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_ROUND_RESTARTED);
        scheduler.runTaskLater(plugin, this::prepareRound, 20L);
        return true;
    }

    public boolean forceChampion(@NotNull ChampionshipTeam team) {
        DodgeboltSide side = sideOf(team);
        if (side == null || getGameStageEnum() == GameStageEnum.WAITING) return false;
        if (side == DodgeboltSide.RIGHT) rightWins = WINS_TO_CHAMPION;
        else leftWins = WINS_TO_CHAMPION;
        champion = team;
        finishMatch();
        return true;
    }

    public boolean stopMatch() {
        if (getGameStageEnum() == GameStageEnum.WAITING) return false;
        champion = null;
        finishMatch();
        return true;
    }

    private void finishRound(DodgeboltSide winner) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        setGameStageEnum(GameStageEnum.STOPPING);
        cleanupRoundEntities();
        queuedShrinkLayers = 0;
        cancelTask(shrinkTask);
        shrinkTask = null;
        if (winner == DodgeboltSide.RIGHT) rightWins++;
        else leftWins++;
        ChampionshipTeam winningTeam = teamOf(winner);
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_ROUND_WIN
                .replace("%round%", String.valueOf(roundNumber))
                .replace("%team%", winningTeam == null ? "-" : winningTeam.getColoredName())
                .replace("%right_wins%", String.valueOf(rightWins))
                .replace("%left_wins%", String.valueOf(leftWins)));
        sendTitleToAllGamePlayers(MessageConfig.DODGEBOLT_ROUND_WIN_TITLE,
                MessageConfig.DODGEBOLT_ROUND_WIN_SUBTITLE
                        .replace("%team%", winningTeam == null ? "-" : winningTeam.getColoredName())
                        .replace("%right_wins%", String.valueOf(rightWins))
                        .replace("%left_wins%", String.valueOf(leftWins)));

        if (rightWins >= WINS_TO_CHAMPION || leftWins >= WINS_TO_CHAMPION) {
            champion = rightWins >= WINS_TO_CHAMPION ? rightChampionshipTeam : leftChampionshipTeam;
            scheduler.runTaskLater(plugin, this::finishMatch, 60L);
            return;
        }

        roundNumber++;
        final int[] remaining = {Math.max(1, getGameConfig().getRoundRestartDelay())};
        restartTask = scheduler.runTaskTimer(plugin, () -> {
            timer = remaining[0];
            updateGameTimerBossBar(MessageConfig.DODGEBOLT_NEXT_ROUND
                    .replace("%round%", String.valueOf(roundNumber))
                    .replace("%time%", String.valueOf(timer)), timer,
                    Math.max(1, getGameConfig().getRoundRestartDelay()));
            if (timer == 0) {
                cancelTask(restartTask);
                restartTask = null;
                prepareRound();
                return;
            }
            remaining[0]--;
        }, 0L, 20L);
    }

    private void finishMatch() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        cancelRoundTasks();
        setGameStageEnum(GameStageEnum.END);
        if (champion != null) {
            String score = rightWins + " - " + leftWins;
            Utils.sendMessageToAllPlayers(MessageConfig.DODGEBOLT_CHAMPION
                    .replace("%team%", champion.getColoredName()).replace("%score%", score));
            Utils.sendTitleToAllPlayers(MessageConfig.DODGEBOLT_CHAMPION_TITLE,
                    MessageConfig.DODGEBOLT_CHAMPION_SUBTITLE
                            .replace("%team%", champion.getColoredName()).replace("%score%", score), 100);
            launchChampionFireworks(champion);
        } else {
            Utils.sendMessageToAllPlayers(MessageConfig.DODGEBOLT_STOPPED);
        }
        // Keep the unified "game over" title authoritative; champion details remain in chat/fireworks.
        announceGameEnd(MessageConfig.DODGEBOLT_GAME_END_TITLE, MessageConfig.DODGEBOLT_GAME_END_SUBTITLE);
        restoreParticipantCollisions();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        beginPostGameSettlement();
        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));
        finishPostGameAfterEndEvent();
    }

    private void queueShrink(int layers) {
        int room = Math.max(0, getGameConfig().getMaxShrinkLevels() - shrinkLevel - queuedShrinkLayers);
        queuedShrinkLayers += Math.min(Math.max(0, layers), room);
        if (queuedShrinkLayers == 0 || shrinkTask != null) return;
        beginShrinkWarning();
    }

    private void beginShrinkWarning() {
        prepareShrinkWarning();
        if (pendingShrinkColumns.isEmpty()) {
            queuedShrinkLayers = 0;
            return;
        }
        shrinkWarningTicks = 0;
        shrinkWarningSeconds = 3;
        shrinkWarningVisible = false;
        sendShrinkWarningActionBar(shrinkWarningSeconds);
        shrinkTask = scheduler.runTaskTimer(plugin, () -> {
            if (getGameStageEnum() != GameStageEnum.PROGRESS) {
                cancelShrinkWarning();
                return;
            }
            if (paused) return;

            shrinkWarningTicks += 6;
            int remainingSeconds = Math.max(1, (60 - shrinkWarningTicks + 19) / 20);
            if (remainingSeconds != shrinkWarningSeconds) {
                shrinkWarningSeconds = remainingSeconds;
                sendShrinkWarningActionBar(remainingSeconds);
            }
            setShrinkWarningVisible(!shrinkWarningVisible);
            if (shrinkWarningTicks >= 60) completeShrinkWarning();
        }, 6L, 6L);
    }

    private void prepareShrinkWarning() {
        pendingShrinkColumns.clear();
        Set<Column> remaining = new LinkedHashSet<>(activePlatformColumns);
        int availableLayers = Math.max(0, getGameConfig().getMaxShrinkLevels() - shrinkLevel);
        int layers = Math.min(availableLayers, completedShrinkEvents < 2 ? 2 : 1);
        for (int layer = 0; layer < layers; layer++) {
            Set<Column> boundary = findBoundary(remaining);
            if (boundary.isEmpty()) break;
            pendingShrinkColumns.addAll(boundary);
            remaining.removeAll(boundary);
        }
        prepareShrinkWarningBlocks();
    }

    private Set<Column> findBoundary(@NotNull Set<Column> columns) {
        Set<Column> boundary = new LinkedHashSet<>();
        for (Column column : columns) {
            for (int[] direction : CARDINAL) {
                if (!columns.contains(new Column(column.x + direction[0], column.z + direction[1]))) {
                    boundary.add(column);
                    break;
                }
            }
        }
        return boundary;
    }

    private void prepareShrinkWarningBlocks() {
        shrinkWarningSnapshot.clear();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null) return;
        for (BlockPosition position : platformSnapshot.keySet()) {
            if (!pendingShrinkColumns.contains(new Column(position.x, position.z))) continue;
            Block block = world.getBlockAt(position.x, position.y, position.z);
            if (block.getType().name().endsWith("_CARPET")) {
                shrinkWarningSnapshot.put(position, block.getBlockData().clone());
            }
        }
    }

    private void setShrinkWarningVisible(boolean visible) {
        if (shrinkWarningVisible == visible) return;
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null) return;
        if (visible) {
            for (BlockPosition position : shrinkWarningSnapshot.keySet()) {
                world.getBlockAt(position.x, position.y, position.z)
                        .setType(Material.PALE_MOSS_CARPET, false);
            }
        } else {
            for (Map.Entry<BlockPosition, BlockData> entry : shrinkWarningSnapshot.entrySet()) {
                BlockPosition position = entry.getKey();
                world.getBlockAt(position.x, position.y, position.z).setBlockData(entry.getValue(), false);
            }
        }
        shrinkWarningVisible = visible;
    }

    private void sendShrinkWarningActionBar(int seconds) {
        sendActionBarToAllGamePlayers(MessageConfig.DODGEBOLT_SHRINK_WARNING
                .replace("%time%", String.valueOf(seconds)));
        playSoundToAllGamePlayers(Sound.BLOCK_ANVIL_USE, 0.9F, 1.0F);
    }

    private void completeShrinkWarning() {
        setShrinkWarningVisible(false);
        shrinkWarningSnapshot.clear();
        Set<Column> boundary = new LinkedHashSet<>(pendingShrinkColumns);
        pendingShrinkColumns.clear();
        if (boundary.isEmpty()) {
            cancelShrinkWarning();
            return;
        }
        activePlatformColumns.removeAll(boundary);
        World world = getSpectatorSpawnLocation().getWorld();
        if (world != null) {
            for (BlockPosition position : platformSnapshot.keySet()) {
                if (boundary.contains(new Column(position.x, position.z))) {
                    world.getBlockAt(position.x, position.y, position.z).setType(Material.AIR, false);
                }
            }
        }
        int layersRemoved = completedShrinkEvents < 2 ? 2 : 1;
        layersRemoved = Math.min(layersRemoved, getGameConfig().getMaxShrinkLevels() - shrinkLevel);
        shrinkLevel += Math.max(0, layersRemoved);
        completedShrinkEvents++;
        queuedShrinkLayers = Math.max(0, queuedShrinkLayers - 1);
        sendActionBarToAllGamePlayers(MessageConfig.DODGEBOLT_SHRINK
                .replace("%level%", String.valueOf(shrinkLevel))
                .replace("%max%", String.valueOf(getGameConfig().getMaxShrinkLevels())));
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_SHRINK
                .replace("%level%", String.valueOf(shrinkLevel))
                .replace("%max%", String.valueOf(getGameConfig().getMaxShrinkLevels())));
        playSoundToAllGamePlayers(Sound.BLOCK_ANVIL_USE, 1.0F, 0.8F);
        updateScoreBar();
        if (queuedShrinkLayers <= 0 || shrinkLevel >= getGameConfig().getMaxShrinkLevels()) {
            cancelShrinkWarning();
        } else {
            prepareShrinkWarning();
            shrinkWarningTicks = 0;
            shrinkWarningSeconds = 3;
            shrinkWarningVisible = false;
            sendShrinkWarningActionBar(shrinkWarningSeconds);
        }
    }

    private void cancelShrinkWarning() {
        cancelTask(shrinkTask);
        shrinkTask = null;
        setShrinkWarningVisible(false);
        shrinkWarningSnapshot.clear();
        pendingShrinkColumns.clear();
        shrinkWarningTicks = 0;
        shrinkWarningSeconds = 0;
        shrinkWarningVisible = false;
    }

    private void snapshotPlatform() {
        platformSnapshot.clear();
        activePlatformColumns.clear();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null) return;
        Vector min = Vector.getMinimum(getGameConfig().getPlatformPos1(), getGameConfig().getPlatformPos2());
        Vector max = Vector.getMaximum(getGameConfig().getPlatformPos1(), getGameConfig().getPlatformPos2());
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    platformSnapshot.put(new BlockPosition(x, y, z), block.getBlockData().clone());
                    activePlatformColumns.add(new Column(x, z));
                }
            }
        }
    }

    private void restorePlatform() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null) return;
        for (Map.Entry<BlockPosition, BlockData> entry : platformSnapshot.entrySet()) {
            BlockPosition p = entry.getKey();
            world.getBlockAt(p.x, p.y, p.z).setBlockData(entry.getValue(), false);
        }
        activePlatformColumns.clear();
        for (BlockPosition p : platformSnapshot.keySet()) activePlatformColumns.add(new Column(p.x, p.z));
    }

    /**
     * The map's red and blue blocks are team-colour placeholders. The right side is authored in red and
     * the left side in blue, so command argument order also determines which team receives each side.
     */
    private void applyFinalistColors() {
        World world = getSpectatorSpawnLocation().getWorld();
        Vector areaPos1 = getGameConfig().getAreaPos1();
        Vector areaPos2 = getGameConfig().getAreaPos2();
        if (world == null || areaPos1 == null || areaPos2 == null
                || rightChampionshipTeam == null || leftChampionshipTeam == null) {
            logGame(Level.WARNING, "队伍颜色", "无法替换决赛场地颜色：比赛区、世界或决赛队伍缺失");
            return;
        }

        Material rightConcrete = teamMaterial(rightChampionshipTeam, "_CONCRETE");
        Material rightCarpet = teamMaterial(rightChampionshipTeam, "_CARPET");
        Material leftConcrete = teamMaterial(leftChampionshipTeam, "_CONCRETE");
        Material leftCarpet = teamMaterial(leftChampionshipTeam, "_CARPET");
        if (rightConcrete == null || rightCarpet == null || leftConcrete == null || leftCarpet == null) {
            logGame(Level.WARNING, "队伍颜色", "无法解析决赛队伍的 Minecraft 颜色方块");
            return;
        }

        Vector spectatorPos1 = getGameConfig().getSpectatorAreaPos1();
        Vector spectatorPos2 = getGameConfig().getSpectatorAreaPos2();
        Vector scanMin = Vector.getMinimum(areaPos1, areaPos2);
        Vector scanMax = Vector.getMaximum(areaPos1, areaPos2);
        if (spectatorPos1 != null && spectatorPos2 != null) {
            Vector spectatorMin = Vector.getMinimum(spectatorPos1, spectatorPos2);
            Vector spectatorMax = Vector.getMaximum(spectatorPos1, spectatorPos2);
            scanMin = Vector.getMinimum(scanMin, spectatorMin);
            scanMax = Vector.getMaximum(scanMax, spectatorMax);
        }
        int replacements = replaceFinalistColors(world, scanMin, scanMax,
                rightConcrete, rightCarpet, leftConcrete, leftCarpet);
        logGame(Level.INFO, "队伍颜色", "右队=" + rightChampionshipTeam.getName() + "(" + rightChampionshipTeam.getColorName()
                + ") 左队=" + leftChampionshipTeam.getName() + "(" + leftChampionshipTeam.getColorName()
                + ") 已替换比赛区和观赛区内 " + replacements + " 个占位方块");
    }

    private static int replaceFinalistColors(@NotNull World world, @NotNull Vector pos1, @NotNull Vector pos2,
                                              @NotNull Material rightConcrete, @NotNull Material rightCarpet,
                                              @NotNull Material leftConcrete, @NotNull Material leftCarpet) {
        Vector min = Vector.getMinimum(pos1, pos2);
        Vector max = Vector.getMaximum(pos1, pos2);
        int replacements = 0;
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material replacement = switch (block.getType()) {
                        case RED_CONCRETE -> rightConcrete;
                        case RED_CARPET -> rightCarpet;
                        case BLUE_CONCRETE -> leftConcrete;
                        case BLUE_CARPET -> leftCarpet;
                        default -> null;
                    };
                    if (replacement != null && replacement != block.getType()) {
                        block.setType(replacement, false);
                        replacements++;
                    }
                }
            }
        }
        return replacements;
    }

    private static @Nullable Material teamMaterial(@NotNull ChampionshipTeam team, @NotNull String suffix) {
        return Material.getMaterial(team.getColorName().toUpperCase(Locale.ROOT) + suffix);
    }

    private void spawnArrowToken(DodgeboltSide side) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        String configured = arrowSpawn(side);
        if (configured == null || configured.isBlank()) return;
        Location location = Utils.getLocation(configured);
        if (location.getWorld() == null) return;
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.getPersistentDataContainer().set(tokenSideKey, PersistentDataType.STRING, side.name());
        meta.displayName(Utils.toComponent("&#fff566躲避箭"));
        stack.setItemMeta(meta);
        Item item = location.getWorld().dropItem(location, stack);
        item.setPickupDelay(0);
        item.setUnlimitedLifetime(true);
        item.setVelocity(new Vector());
        tokenItems.add(item.getUniqueId());
    }

    private void preserveHeldTokens(Player player, @Nullable DodgeboltSide side) {
        int count = countTokenArrows(player);
        stowedTokens.remove(player.getUniqueId());
        if (side == null) return;
        for (int i = 0; i < count; i++) spawnArrowToken(side);
    }

    private void giveBowsToAlivePlayers() {
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            ItemStack bow = new ItemStack(Material.BOW);
            ItemMeta meta = bow.getItemMeta();
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.displayName(Utils.toComponent("&#fff566躲避箭之弓"));
            bow.setItemMeta(meta);
            player.getInventory().setItem(0, bow);
            player.getInventory().setHeldItemSlot(0);
        }
    }

    private boolean hasTokenArrow(@NotNull Player player, @Nullable DodgeboltSide side) {
        if (side == null) return false;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (tokenSide(stack) == side) return true;
        }
        return false;
    }

    /** Keeps a live arrow unavailable outside the permitted shooting strip without deleting it. */
    public void updateArrowAccess(@NotNull Player player, @NotNull Location location) {
        DodgeboltSide side = sideOf(player);
        if (side == null || getGameStageEnum() != GameStageEnum.PROGRESS || paused || !isAlive(player)) return;
        if (contains(shootArea(side), location)) {
            restoreStowedTokens(player);
        } else {
            stashTokenArrows(player);
        }
    }

    private void stashTokenArrows(@NotNull Player player) {
        List<ItemStack> stored = null;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isTokenArrow(stack)) continue;
            if (stored == null) {
                stored = stowedTokens.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
            }
            stored.add(stack.clone());
            player.getInventory().setItem(slot, null);
        }
    }

    private void restoreStowedTokens(@NotNull Player player) {
        List<ItemStack> stored = stowedTokens.remove(player.getUniqueId());
        if (stored == null || stored.isEmpty()) return;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : stored) {
            remaining.addAll(player.getInventory().addItem(stack.clone()).values());
        }
        if (!remaining.isEmpty()) stowedTokens.put(player.getUniqueId(), remaining);
    }

    private int countTokenArrows(@NotNull Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTokenArrow(stack)) count += stack.getAmount();
        }
        for (ItemStack stack : stowedTokens.getOrDefault(player.getUniqueId(), List.of())) {
            count += stack.getAmount();
        }
        return count;
    }

    private void startFlightMonitor() {
        cancelTask(flightMonitorTask);
        flightMonitorTask = scheduler.runTaskTimer(plugin, () -> {
            if (paused || getGameStageEnum() != GameStageEnum.PROGRESS) return;
            for (Flight flight : new ArrayList<>(flights.values())) {
                flight.ticks++;
                Arrow arrow = flight.arrow;
                if (!arrow.isValid() || arrow.isDead() || flight.ticks > PROJECTILE_MAX_TICKS
                        || notInArea(arrow.getLocation())) {
                    resolveProjectile(arrow, null);
                }
            }
        }, 1L, 1L);
    }

    private void cleanupRoundEntities() {
        cancelShrinkWarning();
        cancelTask(flightMonitorTask);
        flightMonitorTask = null;
        for (Flight flight : flights.values()) flight.arrow.remove();
        flights.clear();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world != null) {
            for (UUID uuid : new HashSet<>(tokenItems)) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) entity.remove();
            }
        }
        tokenItems.clear();
        stowedTokens.clear();
        for (Player player : participants()) {
            player.getInventory().remove(Material.ARROW);
        }
    }

    private void cancelRoundTasks() {
        cancelIntroduction();
        cancelFinalCountdown();
        cancelTask(preparationTask);
        cancelTask(restartTask);
        cancelTask(flightMonitorTask);
        cancelShrinkWarning();
        cancelTask(championFireworksTask);
        preparationTask = restartTask = flightMonitorTask = null;
        championFireworksTask = null;
        cleanupRoundEntities();
    }

    private void updateScoreBar() {
        String state = paused ? MessageConfig.DODGEBOLT_STATE_PAUSED : MessageConfig.DODGEBOLT_STATE_LIVE;
        updateGameTimerBossBar(MessageConfig.DODGEBOLT_SCORE_BAR
                .replace("%round%", String.valueOf(roundNumber))
                .replace("%right%", rightChampionshipTeam == null ? "-" : rightChampionshipTeam.getColoredName())
                .replace("%left%", leftChampionshipTeam == null ? "-" : leftChampionshipTeam.getColoredName())
                .replace("%right_wins%", String.valueOf(rightWins))
                .replace("%left_wins%", String.valueOf(leftWins))
                .replace("%right_alive%", String.valueOf(aliveCount(DodgeboltSide.RIGHT)))
                .replace("%left_alive%", String.valueOf(aliveCount(DodgeboltSide.LEFT)))
                .replace("%state%", state), 1D);
    }

    private void launchChampionFireworks(ChampionshipTeam team) {
        cancelTask(championFireworksTask);
        championFireworksTask = null;
        Location origin = getSpectatorSpawnLocation();
        Vector areaMin = getGameConfig().getSpectatorAreaPos1();
        Vector areaMax = getGameConfig().getSpectatorAreaPos2();
        if (origin == null || origin.getWorld() == null || areaMin == null || areaMax == null) return;
        Color color;
        try {
            color = Utils.hex2rgb(team.getColorCode());
        } catch (Exception ignored) {
            color = Color.YELLOW;
        }
        Color finalColor = color;
        Vector min = Vector.getMinimum(areaMin, areaMax);
        Vector max = Vector.getMaximum(areaMin, areaMax);
        final int[] elapsed = {0};
        championFireworksTask = scheduler.runTaskTimer(plugin, () -> {
            if (elapsed[0] > 200 || origin.getWorld() == null) {
                cancelTask(championFireworksTask);
                championFireworksTask = null;
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location location = new Location(origin.getWorld(),
                    randomCoordinate(random, min.getX(), max.getX() + 1),
                    randomCoordinate(random, min.getY() + 1, max.getY() + 1),
                    randomCoordinate(random, min.getZ(), max.getZ() + 1));
            Firework firework = origin.getWorld().spawn(location, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(finalColor).withFade(Color.WHITE)
                    .with(FireworkEffect.Type.BALL_LARGE).trail(true).flicker(true).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
            elapsed[0] += 10;
        }, 0L, 10L);
    }

    private static double randomCoordinate(@NotNull ThreadLocalRandom random, double min, double max) {
        return min + random.nextDouble() * Math.max(0.01D, max - min);
    }

    public @Nullable DodgeboltSide sideOf(@Nullable Player player) {
        return player == null ? null : sideOf(plugin.getTeamManager().getTeamByPlayer(player));
    }

    public @Nullable DodgeboltSide sideOf(@Nullable ChampionshipTeam team) {
        if (team == null) return null;
        if (team.equals(rightChampionshipTeam)) return DodgeboltSide.RIGHT;
        if (team.equals(leftChampionshipTeam)) return DodgeboltSide.LEFT;
        return null;
    }

    public boolean inOwnArea(@NotNull Player player, @NotNull Location location) {
        DodgeboltSide side = sideOf(player);
        return side != null && contains(sideArea(side), location);
    }

    @Override
    public boolean notInArea(@NotNull Location location) {
        Vector areaPos1 = getGameConfig().getAreaPos1();
        Vector areaPos2 = getGameConfig().getAreaPos2();
        Location spectatorSpawn = getSpectatorSpawnLocation();
        return areaPos1 == null || areaPos2 == null || location.getWorld() == null
                || spectatorSpawn == null || spectatorSpawn.getWorld() == null
                || !location.getWorld().getName().equals(spectatorSpawn.getWorld().getName())
                || !contains(box(areaPos1, areaPos2), location);
    }

    /** A knocked-out finalist remains in adventure mode and may only roam the configured viewing ring. */
    public boolean isEliminatedPlayer(@NotNull Player player) {
        return eliminatedPlayers.contains(player.getUniqueId());
    }

    public void teleportToSpectatorArea(@NotNull Player player) {
        player.teleport(getSpectatorSpawnLocation());
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(false);
    }

    @Override
    public boolean isSpectatorLocationAllowed(@NotNull Location location) {
        Vector pos1 = getGameConfig().getSpectatorAreaPos1();
        Vector pos2 = getGameConfig().getSpectatorAreaPos2();
        Location spectatorSpawn = getSpectatorSpawnLocation();
        return pos1 != null && pos2 != null && spectatorSpawn != null
                && location.getWorld() != null && spectatorSpawn.getWorld() != null
                && location.getWorld().getName().equals(spectatorSpawn.getWorld().getName())
                && contains(box(pos1, pos2), location)
                && notInArea(location);
    }

    private BoundingBox sideArea(DodgeboltSide side) {
        return side == DodgeboltSide.RIGHT
                ? box(getGameConfig().getRightAreaPos1(), getGameConfig().getRightAreaPos2())
                : box(getGameConfig().getLeftAreaPos1(), getGameConfig().getLeftAreaPos2());
    }

    private BoundingBox shootArea(DodgeboltSide side) {
        return side == DodgeboltSide.RIGHT
                ? box(getGameConfig().getRightShootPos1(), getGameConfig().getRightShootPos2())
                : box(getGameConfig().getLeftShootPos1(), getGameConfig().getLeftShootPos2());
    }

    private static BoundingBox box(Vector a, Vector b) {
        return BoundingBox.of(Vector.getMinimum(a, b), Vector.getMaximum(a, b).add(new Vector(1, 1, 1)));
    }

    private static boolean contains(BoundingBox box, Location location) {
        return location.getWorld() != null && box.contains(location.toVector());
    }

    private void restoreParticipantCollisions() {
        for (Player player : participants()) player.setCollidable(true);
    }

    private int aliveCount(DodgeboltSide side) {
        ChampionshipTeam team = teamOf(side);
        if (team == null) return 0;
        int count = 0;
        for (UUID uuid : team.getMembers()) if (alivePlayers.contains(uuid)) count++;
        return count;
    }

    public int getAliveCount(@NotNull DodgeboltSide side) {
        return aliveCount(side);
    }

    private @Nullable UUID firstOfflineAlivePlayer() {
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return uuid;
        }
        return null;
    }

    private boolean roundWon() {
        return aliveCount(DodgeboltSide.RIGHT) == 0 || aliveCount(DodgeboltSide.LEFT) == 0;
    }

    private ChampionshipTeam teamOf(DodgeboltSide side) {
        return side == DodgeboltSide.RIGHT ? rightChampionshipTeam : leftChampionshipTeam;
    }

    private String arrowSpawn(DodgeboltSide side) {
        return side == DodgeboltSide.RIGHT ? getGameConfig().getRightArrowSpawnPoint()
                : getGameConfig().getLeftArrowSpawnPoint();
    }

    private List<Player> participants() {
        List<Player> players = new ArrayList<>();
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.getOnlinePlayers().stream()
                    .filter(this::isSelectedParticipant).forEach(players::add);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.getOnlinePlayers().stream()
                    .filter(this::isSelectedParticipant).forEach(players::add);
        return players;
    }

    private boolean isSelectedParticipant(@NotNull Player player) {
        return !partialRoster || forcedParticipants.contains(player.getUniqueId());
    }

    private void addRoundPlayers(@Nullable ChampionshipTeam team) {
        if (team == null) return;
        if (partialRoster) {
            for (UUID uuid : team.getMembers()) {
                if (forcedParticipants.contains(uuid)) alivePlayers.add(uuid);
            }
            return;
        }
        alivePlayers.addAll(team.getMembers());
    }

    private void teleportTeamsToRoundSpawns() {
        teleportTeam(rightChampionshipTeam, getGameConfig().getRightSpawnPoints());
        teleportTeam(leftChampionshipTeam, getGameConfig().getLeftSpawnPoints());
    }

    private void teleportTeam(@Nullable ChampionshipTeam team, List<String> spawns) {
        if (team == null || spawns == null || spawns.isEmpty()) return;
        int index = 0;
        for (Player player : team.getOnlinePlayers()) {
            if (!isSelectedParticipant(player)) continue;
            player.teleport(Utils.getLocation(spawns.get(index++ % spawns.size())));
        }
    }

    @Override
    public boolean notAreaPlayer(@NotNull Player player) {
        return partialRoster ? !forcedParticipants.contains(player.getUniqueId()) : super.notAreaPlayer(player);
    }

    @Override
    public Collection<UUID> getParticipantUniqueIds() {
        return partialRoster ? List.copyOf(forcedParticipants) : super.getParticipantUniqueIds();
    }

    public void teleportParticipant(Player player) {
        if (isIntroductionPhase() && getGameConfig().getIntroductionSpawnPoint() != null) {
            player.teleport(getGameConfig().getIntroductionSpawnPoint());
            return;
        }
        DodgeboltSide side = sideOf(player);
        if (side == null) {
            player.teleport(getSpectatorSpawnLocation());
            return;
        }
        List<String> spawns = side == DodgeboltSide.RIGHT ? getGameConfig().getRightSpawnPoints()
                : getGameConfig().getLeftSpawnPoints();
        if (spawns != null && !spawns.isEmpty()) player.teleport(Utils.getLocation(spawns.getFirst()));
    }

    private static void cancelTask(@Nullable BukkitTask task) {
        if (task != null) task.cancel();
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        if (isAlive(event.getPlayer()) && getGameStageEnum() == GameStageEnum.PROGRESS)
            pauseMatch(event.getPlayer());
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (isAlive(player)) {
                Location saved = pauseLocations.get(player.getUniqueId());
                if (saved != null) player.teleport(saved);
                else teleportParticipant(player);
                player.setGameMode(GameMode.SURVIVAL);
                player.setCollidable(true);
            } else {
                teleportToSpectatorArea(player);
            }
            return;
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION || getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            teleportParticipant(player);
            player.setGameMode(getGameStageEnum() == GameStageEnum.COUNTDOWN ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            return;
        }
        player.getInventory().clear();
        player.teleport(CCConfig.LOBBY_LOCATION);
        player.setGameMode(GameMode.ADVENTURE);
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player player = event.getEntity();
        if (getGameStageEnum() == GameStageEnum.PROGRESS && isAlive(player) && !paused) eliminate(player, false);
        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            teleportToSpectatorArea(player);
        });
    }

    @Override
    protected void applySpectatorGameMode(@NotNull Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(false);
    }

    @Override
    protected void clearSpectatorGameMode(@NotNull Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
    }

    @Override public Location getSpectatorSpawnLocation() { return getGameConfig().getSpectatorSpawnPoint(); }
    @Override public String getWorldName() { return getGameConfig().getConfiguredWorld(); }
    @Override public DodgeboltConfig getGameConfig() { return (DodgeboltConfig) gameConfig; }
    @Override public DodgeboltHandler getGameHandler() { return (DodgeboltHandler) gameHandler; }
    @Override public void addPlayerPointsToDatabase() { }

    @Override
    public void endGame() {
        stopMatch();
    }

    @Override
    public void resetArea() {
        cancelRoundTasks();
        timer = 0;
        roundNumber = 0;
        rightWins = 0;
        leftWins = 0;
        shrinkLevel = 0;
        shotsThisRound = 0;
        completedShrinkEvents = 0;
        paused = false;
        partialRoster = false;
        champion = null;
        firstRoundArrowTeam = null;
        alivePlayers.clear();
        eliminatedPlayers.clear();
        forcedParticipants.clear();
        pauseLocations.clear();
        stowedTokens.clear();
        platformSnapshot.clear();
        activePlatformColumns.clear();
        preloadMap();
    }

    private static final class Flight {
        private final Arrow arrow;
        private final DodgeboltSide destination;
        private int ticks;
        private Vector savedVelocity;

        private Flight(Arrow arrow, DodgeboltSide destination) {
            this.arrow = arrow;
            this.destination = destination;
        }
    }

    private record BlockPosition(int x, int y, int z) { }
    private record Column(int x, int z) { }
}
