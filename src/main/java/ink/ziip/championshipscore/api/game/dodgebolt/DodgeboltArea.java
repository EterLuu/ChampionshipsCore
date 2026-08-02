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
import org.bukkit.entity.AbstractArrow;
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
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/** One complete, non-scoring best-of-five Dodgebolt final. */
public final class DodgeboltArea extends BasePairedGameInstance {
    private static final int WINS_TO_CHAMPION = 3;
    private static final int PROJECTILE_MAX_TICKS = 240;
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final NamespacedKey tokenKey;
    private final NamespacedKey tokenSideKey;
    private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();
    private final Map<UUID, Item> tokenItems = new ConcurrentHashMap<>();
    private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> pauseLocations = new ConcurrentHashMap<>();
    private final Map<BlockPosition, BlockData> platformSnapshot = new ConcurrentHashMap<>();
    private final Set<Column> activePlatformColumns = ConcurrentHashMap.newKeySet();

    @Getter private volatile int timer;
    @Getter private volatile int roundNumber;
    @Getter private volatile int rightWins;
    @Getter private volatile int leftWins;
    @Getter private volatile int shrinkLevel;
    @Getter private volatile int shotsThisRound;
    @Getter private volatile boolean paused;
    @Getter private volatile @Nullable ChampionshipTeam champion;

    private volatile int shotsSinceShrink;
    private volatile int eliminationsThisRound;
    private volatile int queuedShrinkLayers;
    private volatile ChampionshipTeam firstRoundArrowTeam;
    private volatile ScheduledTask preparationTask;
    private volatile ScheduledTask restartTask;
    private volatile ScheduledTask shrinkTask;

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
        if (rightTeam.equals(leftTeam) || rightTeam.getMembers().isEmpty() || leftTeam.getMembers().isEmpty())
            return false;
        if (rightTeam.getOnlinePlayers().size() != rightTeam.getMembers().size()
                || leftTeam.getOnlinePlayers().size() != leftTeam.getMembers().size())
            return false;
        return super.tryStartGame(rightTeam, leftTeam);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        applyFinalistColorsAsync()
                .thenCompose(ignored -> snapshotPlatformAsync())
                .whenComplete((ignored, throwable) -> scheduler.runTask(() -> {
                    if (throwable != null) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to prepare Dodgebolt platform", throwable);
                        finishMatch();
                        return;
                    }
                    startGameIntroduction(this::startFormalPreparation);
                }));
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

    private synchronized void prepareRound() {
        cleanupRoundEntities();
        restorePlatformAsync().whenComplete((ignored, throwable) -> scheduler.runTask(() -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to restore Dodgebolt platform", throwable);
                finishMatch();
                return;
            }
            finishPrepareRound();
        }));
    }

    private synchronized void finishPrepareRound() {
        shrinkLevel = 0;
        shotsThisRound = 0;
        shotsSinceShrink = 0;
        eliminationsThisRound = 0;
        queuedShrinkLayers = 0;
        alivePlayers.clear();
        eliminatedPlayers.clear();
        if (rightChampionshipTeam != null) alivePlayers.addAll(rightChampionshipTeam.getMembers());
        if (leftChampionshipTeam != null) alivePlayers.addAll(leftChampionshipTeam.getMembers());

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
        return side != null && getGameStageEnum() == GameStageEnum.PROGRESS && !paused
                && isAlive(player) && contains(shootArea(side), player.getLocation())
                && hasTokenArrow(player);
    }

    public synchronized void registerShot(@NotNull Player shooter, @NotNull Entity projectile) {
        if (!(projectile instanceof Arrow arrow)) return;
        DodgeboltSide shooterSide = sideOf(shooter);
        if (shooterSide == null) return;
        Flight flight = new Flight(arrow, shooterSide.opposite());
        flights.put(arrow.getUniqueId(), flight);
        flight.monitorTask = scheduler.runEntityTimer(arrow, task -> {
            if (paused || getGameStageEnum() != GameStageEnum.PROGRESS)
                return;
            flight.ticks++;
            if (!arrow.isValid() || arrow.isDead() || flight.ticks > PROJECTILE_MAX_TICKS
                    || notInArea(arrow.getLocation())) {
                task.cancel();
                resolveProjectile(arrow, null);
            }
        }, 1L, 1L);
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

    public synchronized void resolveProjectile(@NotNull Arrow arrow, @Nullable Player hitPlayer) {
        Flight flight = flights.remove(arrow.getUniqueId());
        if (flight == null) return;
        cancelTask(flight.monitorTask);
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

    public synchronized boolean eliminate(@NotNull Player player, boolean arrowHit) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || paused || !alivePlayers.remove(player.getUniqueId()))
            return false;
        DodgeboltSide side = sideOf(player);
        eliminatedPlayers.add(player.getUniqueId());
        scheduler.runEntity(player, () -> {
            preserveHeldTokens(player, side);
            player.getInventory().clear();
            teleportToSpectatorArea(player);
        });
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
        UUID uuid = disconnected == null ? null : disconnected.getUniqueId();
        Location location = disconnected == null ? null : disconnected.getLocation();
        if (getGameStageEnum() != GameStageEnum.PROGRESS || paused)
            return false;
        scheduler.runTask(() -> pauseMatch(uuid, location));
        return true;
    }

    private synchronized boolean pauseMatch(@Nullable UUID disconnected, @Nullable Location disconnectedLocation) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || paused) return false;
        paused = true;
        pauseLocations.clear();
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                scheduler.runEntity(player, () -> {
                    if (player.isOnline())
                        pauseLocations.put(uuid, player.getLocation().clone());
                });
        }
        if (disconnected != null && disconnectedLocation != null)
            pauseLocations.put(disconnected, disconnectedLocation.clone());
        for (Flight flight : flights.values()) {
            scheduler.runEntity(flight.arrow, () -> {
                flight.savedVelocity = flight.arrow.getVelocity().clone();
                flight.arrow.setVelocity(new Vector());
                flight.arrow.setGravity(false);
            });
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

    public synchronized boolean resumeMatch() {
        if (!paused || getGameStageEnum() != GameStageEnum.PROGRESS) return false;
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return false;
        }
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            Location location = pauseLocations.get(uuid);
            if (player != null && location != null) player.teleportAsync(location);
        }
        for (Flight flight : flights.values()) {
            scheduler.runEntity(flight.arrow, () -> {
                flight.arrow.setGravity(true);
                if (flight.savedVelocity != null)
                    flight.arrow.setVelocity(flight.savedVelocity);
                flight.savedVelocity = null;
            });
        }
        paused = false;
        pauseLocations.clear();
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_RESUMED);
        sendTitleToAllGamePlayers(MessageConfig.DODGEBOLT_RESUMED_TITLE,
                MessageConfig.DODGEBOLT_RESUMED_SUBTITLE);
        updateScoreBar();
        return true;
    }

    public synchronized boolean restartCurrentRound() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return false;
        cancelRoundTasks();
        paused = false;
        setGameStageEnum(GameStageEnum.STOPPING);
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_ROUND_RESTARTED);
        scheduler.runTaskLater(plugin, this::prepareRound, 20L);
        return true;
    }

    public synchronized boolean forceChampion(@NotNull ChampionshipTeam team) {
        DodgeboltSide side = sideOf(team);
        if (side == null || getGameStageEnum() == GameStageEnum.WAITING) return false;
        if (side == DodgeboltSide.RIGHT) rightWins = WINS_TO_CHAMPION;
        else leftWins = WINS_TO_CHAMPION;
        champion = team;
        finishMatch();
        return true;
    }

    public synchronized boolean stopMatch() {
        if (getGameStageEnum() == GameStageEnum.WAITING) return false;
        champion = null;
        finishMatch();
        return true;
    }

    private synchronized void finishRound(DodgeboltSide winner) {
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
            sendActionBarToAllGamePlayers(MessageConfig.DODGEBOLT_NEXT_ROUND
                    .replace("%round%", String.valueOf(roundNumber))
                    .replace("%time%", String.valueOf(timer)));
            changeLevelForAllGamePlayers(timer);
            if (timer == 0) {
                cancelTask(restartTask);
                restartTask = null;
                prepareRound();
                return;
            }
            remaining[0]--;
        }, 0L, 20L);
    }

    private synchronized void finishMatch() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        cancelRoundTasks();
        setGameStageEnum(GameStageEnum.END);
        announceGameEnd(MessageConfig.DODGEBOLT_GAME_END_TITLE, MessageConfig.DODGEBOLT_GAME_END_SUBTITLE);
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
        restoreParticipantCollisions();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        removeAllPlayers();
        releaseAllSpectators();
        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));
        resetGame();
    }

    private synchronized void queueShrink(int layers) {
        int room = Math.max(0, getGameConfig().getMaxShrinkLevels() - shrinkLevel - queuedShrinkLayers);
        queuedShrinkLayers += Math.min(Math.max(0, layers), room);
        if (queuedShrinkLayers == 0 || shrinkTask != null) return;
        shrinkTask = scheduler.runTaskTimer(plugin, () -> {
            if (getGameStageEnum() != GameStageEnum.PROGRESS) {
                cancelTask(shrinkTask);
                shrinkTask = null;
                return;
            }
            if (paused) return;
            if (queuedShrinkLayers <= 0) {
                cancelTask(shrinkTask);
                shrinkTask = null;
                return;
            }
            queuedShrinkLayers--;
            shrinkOneLayer();
        }, 0L, 20L);
    }

    private synchronized void shrinkOneLayer() {
        if (activePlatformColumns.isEmpty()) return;
        Set<Column> boundary = new LinkedHashSet<>();
        for (Column column : activePlatformColumns) {
            for (int[] d : CARDINAL) {
                if (!activePlatformColumns.contains(new Column(column.x + d[0], column.z + d[1]))) {
                    boundary.add(column);
                    break;
                }
            }
        }
        if (boundary.isEmpty()) return;
        activePlatformColumns.removeAll(boundary);
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;
        for (BlockPosition position : platformSnapshot.keySet()) {
            if (boundary.contains(new Column(position.x, position.z)))
                scheduler.runAtLocation(new Location(world, position.x, position.y, position.z),
                        () -> world.getBlockAt(position.x, position.y, position.z)
                                .setType(Material.AIR, false));
        }
        shrinkLevel++;
        sendMessageToAllGamePlayers(MessageConfig.DODGEBOLT_SHRINK
                .replace("%level%", String.valueOf(shrinkLevel))
                .replace("%max%", String.valueOf(getGameConfig().getMaxShrinkLevels())));
        playSoundToAllGamePlayers(Sound.BLOCK_PISTON_EXTEND, 0.8F, 0.8F);
        updateScoreBar();
    }

    private CompletableFuture<Void> snapshotPlatformAsync() {
        platformSnapshot.clear();
        activePlatformColumns.clear();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null) return CompletableFuture.completedFuture(null);
        Vector min = Vector.getMinimum(getGameConfig().getPlatformPos1(), getGameConfig().getPlatformPos2());
        Vector max = Vector.getMaximum(getGameConfig().getPlatformPos1(), getGameConfig().getPlatformPos2());
        return forEachBlockAsync(world, min, max, block -> {
            if (block.getType().isAir()) return;
            platformSnapshot.put(new BlockPosition(block.getX(), block.getY(), block.getZ()),
                    block.getBlockData().clone());
            activePlatformColumns.add(new Column(block.getX(), block.getZ()));
        });
    }

    private CompletableFuture<Void> restorePlatformAsync() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Map.Entry<BlockPosition, BlockData> entry : platformSnapshot.entrySet()) {
            BlockPosition p = entry.getKey();
            Location location = new Location(world, p.x, p.y, p.z);
            writes.add(scheduler.runAtLocationFuture(location,
                    () -> location.getBlock().setBlockData(entry.getValue(), false)));
        }
        activePlatformColumns.clear();
        for (BlockPosition p : platformSnapshot.keySet()) activePlatformColumns.add(new Column(p.x, p.z));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /**
     * The map's red and blue blocks are team-colour placeholders. The right side is authored in red and
     * the left side in blue, so command argument order also determines which team receives each side.
     */
    private CompletableFuture<Void> applyFinalistColorsAsync() {
        World world = getSpectatorSpawnLocation().getWorld();
        Vector areaPos1 = getGameConfig().getAreaPos1();
        Vector areaPos2 = getGameConfig().getAreaPos2();
        if (world == null || areaPos1 == null || areaPos2 == null
                || rightChampionshipTeam == null || leftChampionshipTeam == null) {
            logGame(Level.WARNING, "队伍颜色", "无法替换决赛场地颜色：比赛区、世界或决赛队伍缺失");
            return CompletableFuture.completedFuture(null);
        }

        Material rightConcrete = teamMaterial(rightChampionshipTeam, "_CONCRETE");
        Material rightCarpet = teamMaterial(rightChampionshipTeam, "_CARPET");
        Material leftConcrete = teamMaterial(leftChampionshipTeam, "_CONCRETE");
        Material leftCarpet = teamMaterial(leftChampionshipTeam, "_CARPET");
        if (rightConcrete == null || rightCarpet == null || leftConcrete == null || leftCarpet == null) {
            logGame(Level.WARNING, "队伍颜色", "无法解析决赛队伍的 Minecraft 颜色方块");
            return CompletableFuture.completedFuture(null);
        }

        Vector min = Vector.getMinimum(areaPos1, areaPos2);
        Vector max = Vector.getMaximum(areaPos1, areaPos2);
        AtomicInteger replacements = new AtomicInteger();
        return forEachBlockAsync(world, min, max, block -> {
            Material replacement = switch (block.getType()) {
                case RED_CONCRETE -> rightConcrete;
                case RED_CARPET -> rightCarpet;
                case BLUE_CONCRETE -> leftConcrete;
                case BLUE_CARPET -> leftCarpet;
                default -> null;
            };
            if (replacement != null && replacement != block.getType()) {
                block.setType(replacement, false);
                replacements.incrementAndGet();
            }
        }).thenRun(() -> logGame(Level.INFO, "队伍颜色",
                "右队=" + rightChampionshipTeam.getName() + "(" + rightChampionshipTeam.getColorName()
                        + ") 左队=" + leftChampionshipTeam.getName() + "(" + leftChampionshipTeam.getColorName()
                        + ") 已替换 " + replacements.get() + " 个占位方块"));
    }

    private CompletableFuture<Void> forEachBlockAsync(
            World world, Vector min, Vector max, Consumer<Block> action) {
        Map<ChunkPosition, List<BlockPosition>> byChunk = new HashMap<>();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    byChunk.computeIfAbsent(new ChunkPosition(x >> 4, z >> 4), ignored -> new ArrayList<>())
                            .add(new BlockPosition(x, y, z));
                }
            }
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<ChunkPosition, List<BlockPosition>> entry : byChunk.entrySet()) {
            ChunkPosition chunk = entry.getKey();
            Location owner = new Location(world, (chunk.x << 4) + 8, min.getY(), (chunk.z << 4) + 8);
            futures.add(scheduler.runAtLocationFuture(owner, () -> {
                for (BlockPosition position : entry.getValue())
                    action.accept(world.getBlockAt(position.x, position.y, position.z));
            }));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
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
        meta.setDisplayName(Utils.translateColorCodes("&#fff566躲避箭"));
        stack.setItemMeta(meta);
        scheduler.runAtLocation(location, () -> {
            if (getGameStageEnum() != GameStageEnum.PROGRESS)
                return;
            Item item = location.getWorld().dropItem(location, stack);
            item.setPickupDelay(0);
            item.setUnlimitedLifetime(true);
            item.setVelocity(new Vector());
            tokenItems.put(item.getUniqueId(), item);
        });
    }

    private void preserveHeldTokens(Player player, @Nullable DodgeboltSide side) {
        if (side == null) return;
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTokenArrow(stack)) count += stack.getAmount();
        }
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
            meta.setDisplayName(Utils.translateColorCodes("&#fff566躲避箭之弓"));
            bow.setItemMeta(meta);
            scheduler.runEntity(player, () -> {
                player.getInventory().setItem(0, bow);
                player.getInventory().setHeldItemSlot(0);
            });
        }
    }

    private boolean hasTokenArrow(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTokenArrow(stack)) return true;
        }
        return false;
    }

    private void cleanupRoundEntities() {
        for (Flight flight : flights.values()) {
            cancelTask(flight.monitorTask);
            scheduler.runEntity(flight.arrow, flight.arrow::remove);
        }
        flights.clear();
        for (Item item : tokenItems.values())
            scheduler.runEntity(item, item::remove);
        tokenItems.clear();
        for (Player player : participants())
            scheduler.runEntity(player, () -> player.getInventory().remove(Material.ARROW));
    }

    private void cancelRoundTasks() {
        cancelIntroduction();
        cancelFinalCountdown();
        cancelTask(preparationTask);
        cancelTask(restartTask);
        cancelTask(shrinkTask);
        preparationTask = restartTask = shrinkTask = null;
        cleanupRoundEntities();
    }

    private void updateScoreBar() {
        String state = paused ? MessageConfig.DODGEBOLT_STATE_PAUSED : MessageConfig.DODGEBOLT_STATE_LIVE;
        updateSpectatorTimerBossBar(MessageConfig.DODGEBOLT_SCORE_BAR
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
        Location origin = getSpectatorSpawnLocation();
        if (origin.getWorld() == null) return;
        Color color;
        try {
            color = Utils.hex2rgb(team.getColorCode());
        } catch (Exception ignored) {
            color = Color.YELLOW;
        }
        Color finalColor = color;
        for (int i = 0; i < 5; i++) {
            int delay = i * 8;
            scheduler.runAtLocationLater(origin, () -> {
                if (origin.getWorld() == null) return;
                Firework firework = origin.getWorld().spawn(origin, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().withColor(finalColor).withFade(Color.WHITE)
                        .with(FireworkEffect.Type.BALL_LARGE).trail(true).flicker(true).build());
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            }, delay);
        }
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

    /** A knocked-out finalist remains in adventure mode and may only roam the configured viewing ring. */
    public boolean isEliminatedPlayer(@NotNull Player player) {
        return eliminatedPlayers.contains(player.getUniqueId());
    }

    public void teleportToSpectatorArea(@NotNull Player player) {
        player.teleportAsync(getSpectatorSpawnLocation());
        scheduler.runEntity(player, () -> {
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setCollidable(false);
        });
    }

    @Override
    public boolean isSpectatorLocationAllowed(@NotNull Location location) {
        Vector pos1 = getGameConfig().getSpectatorAreaPos1();
        Vector pos2 = getGameConfig().getSpectatorAreaPos2();
        Location spectatorSpawn = getSpectatorSpawnLocation();
        return pos1 != null && pos2 != null && spectatorSpawn != null
                && location.getWorld() != null && spectatorSpawn.getWorld() != null
                && location.getWorld().getName().equals(spectatorSpawn.getWorld().getName())
                && location.toVector().isInAABB(pos1, pos2)
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
        for (Player player : participants())
            scheduler.runEntity(player, () -> player.setCollidable(true));
    }

    private int aliveCount(DodgeboltSide side) {
        ChampionshipTeam team = teamOf(side);
        if (team == null) return 0;
        int count = 0;
        for (UUID uuid : team.getMembers()) if (alivePlayers.contains(uuid)) count++;
        return count;
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
        if (rightChampionshipTeam != null) players.addAll(rightChampionshipTeam.getOnlinePlayers());
        if (leftChampionshipTeam != null) players.addAll(leftChampionshipTeam.getOnlinePlayers());
        return players;
    }

    private void teleportTeamsToRoundSpawns() {
        teleportTeam(rightChampionshipTeam, getGameConfig().getRightSpawnPoints());
        teleportTeam(leftChampionshipTeam, getGameConfig().getLeftSpawnPoints());
    }

    private void teleportTeam(@Nullable ChampionshipTeam team, List<String> spawns) {
        if (team == null || spawns == null || spawns.isEmpty()) return;
        int index = 0;
        for (Player player : team.getOnlinePlayers()) {
            player.teleportAsync(Utils.getLocation(spawns.get(index++ % spawns.size())));
        }
    }

    public void teleportParticipant(Player player) {
        if (isIntroductionPhase() && getGameConfig().getIntroductionSpawnPoint() != null) {
            player.teleportAsync(getGameConfig().getIntroductionSpawnPoint());
            return;
        }
        DodgeboltSide side = sideOf(player);
        if (side == null) {
            player.teleportAsync(getSpectatorSpawnLocation());
            return;
        }
        List<String> spawns = side == DodgeboltSide.RIGHT ? getGameConfig().getRightSpawnPoints()
                : getGameConfig().getLeftSpawnPoints();
        if (spawns != null && !spawns.isEmpty()) player.teleportAsync(Utils.getLocation(spawns.getFirst()));
    }

    private static void cancelTask(@Nullable ScheduledTask task) {
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
                if (saved != null) player.teleportAsync(saved);
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
        player.teleportAsync(CCConfig.LOBBY_LOCATION);
        player.setGameMode(GameMode.ADVENTURE);
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player player = event.getEntity();
        if (getGameStageEnum() == GameStageEnum.PROGRESS && isAlive(player) && !paused) eliminate(player, false);
        scheduler.runEntity(player, () -> {
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
    @Override public String getWorldName() { return "dodgebolt_" + getGameConfig().getAreaName(); }
    @Override public DodgeboltConfig getGameConfig() { return (DodgeboltConfig) gameConfig; }
    @Override public DodgeboltHandler getGameHandler() { return (DodgeboltHandler) gameHandler; }
    @Override public void addPlayerPointsToDatabase() { }

    @Override
    public synchronized void endGame() {
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
        paused = false;
        champion = null;
        firstRoundArrowTeam = null;
        alivePlayers.clear();
        eliminatedPlayers.clear();
        pauseLocations.clear();
        platformSnapshot.clear();
        activePlatformColumns.clear();
        preloadMap();
    }

    private static final class Flight {
        private final Arrow arrow;
        private final DodgeboltSide destination;
        private int ticks;
        private Vector savedVelocity;
        private volatile ScheduledTask monitorTask;

        private Flight(Arrow arrow, DodgeboltSide destination) {
            this.arrow = arrow;
            this.destination = destination;
        }
    }

    private record BlockPosition(int x, int y, int z) { }
    private record Column(int x, int z) { }
    private record ChunkPosition(int x, int z) { }
}
