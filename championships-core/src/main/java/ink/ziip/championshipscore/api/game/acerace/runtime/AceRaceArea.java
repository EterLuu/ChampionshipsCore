package ink.ziip.championshipscore.api.game.acerace.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.acerace.config.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.config.AceRaceCourseLoader;
import ink.ziip.championshipscore.api.game.acerace.editor.AceRaceEditorPreview;
import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceLaunchPads;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceCourse;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceLine;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgress;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgress.PlacementRange;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgressPoint;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.AceRaceMessages;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScoreMessages;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.NativeTeamService;
import ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles.Profile;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.shared.presentation.DurationText;
import ink.ziip.championshipscore.util.Enchants;

import io.papermc.paper.registry.keys.EnchantmentKeys;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A lap race with ordered progress gates and course-bound proximity respawn points. */
public class AceRaceArea extends BaseMultiTeamGameInstance {
    private static final int JUMP_BOOST_DURATION_TICKS = 14;
    private static final int SPEED_BOOST_DURATION_TICKS = 80;
    private static final int RED_SPEED_DURATION_TICKS = 6;
    private static final int ENVIRONMENTAL_EFFECT_DURATION_TICKS = 40;
    private static final int BASE_SPEED_AMPLIFIER = 0;
    private static final int YELLOW_SPEED_AMPLIFIER = 2;

    /** Potion amplifier 9 is Speed X (the Bukkit amplifier is one below the displayed level). */
    private static final int RED_SPEED_AMPLIFIER = 9;

    private static final int DEPTH_STRIDER_LEVEL = 3;
    private static final int DOLPHINS_GRACE_DEPTH_STRIDER_LEVEL = 1;
    private static final double RIPTIDE_EXTRA_MULTIPLIER = 0.01D;
    private static final int SPEED_STATION_RADIUS = 2;
    private static final int WATER_SPEED_STATION_RADIUS = 4;
    private static final double SPEED_STATION_RADIUS_SQUARED =
            SPEED_STATION_RADIUS * SPEED_STATION_RADIUS;
    private static final double WATER_SPEED_STATION_RADIUS_SQUARED =
            WATER_SPEED_STATION_RADIUS * WATER_SPEED_STATION_RADIUS;
    private static final long RACER_VISIBILITY_HIDDEN_AFTER_START_TICKS = 60L * 20L;
    private static final double RACER_VISIBILITY_DISTANCE_SQUARED = 8D * 8D;
    private static final long RACER_VISIBILITY_UPDATE_TICKS = 1L;
    private static final String COLLISION_TEAM_PREFIX = "cc_ar_";
    private static final String TIMER_BOSS_BAR_PREFIX = "acerace-game-timer:";

    private AceRaceCourse course = AceRaceCourse.EMPTY;

    private final AceRaceEditorPreview mapEditPreview;
    private final AceRaceLaunchPads launchPads;
    private final AceRaceProgress raceProgress = new AceRaceProgress();
    private final Map<UUID, Long> lapStartedNanos = new HashMap<>();

    /** Completed lap durations in milliseconds, retained for the entire match for the sidebar. */
    private final Map<UUID, List<Long>> lapDurationsMillis = new HashMap<>();

    private final Map<UUID, Long> raceStartedNanos = new HashMap<>();
    private final Map<UUID, Location> latestRespawnLocations = new HashMap<>();
    private final Map<UUID, Set<Integer>> capturedRespawnPoints = new HashMap<>();
    private final Map<UUID, Integer> activeFallHeights = new HashMap<>();
    private final Map<UUID, Location> lastMoveLocations = new HashMap<>();

    /** A player must leave the start line before a finish-line crossing can count for the race. */
    private final Set<UUID> startLineArmed = new HashSet<>();

    private final Map<UUID, TrackFeatureContact> featureContacts = new HashMap<>();
    private final Set<RacerPair> visibleRacerPairs = new HashSet<>();
    private final String visibilityOwner = "game:acerace:" + UUID.randomUUID();
    private final Set<RacerView> riptideHiddenViews = new HashSet<>();
    private final Map<UUID, Integer> riptideViewerGraceTicks = new HashMap<>();
    private final Map<UUID, TextDisplay> racerNameDisplays = new HashMap<>();
    private final Map<UUID, String> originalScoreboardTeams = new HashMap<>();
    private final int copyIndex;
    @Getter private int timer;
    private BukkitTask progressTask;
    private BukkitTask racerVisibilityTask;
    private BukkitTask racerVisibilityUnlockTask;
    private boolean racerVisibilityUnlocked;

    public AceRaceArea(ChampionshipsCore plugin, AceRaceConfig config) {
        this(plugin, config, 0, true);
    }

    public AceRaceArea(
            ChampionshipsCore plugin,
            AceRaceConfig config,
            int copyIndex,
            boolean initializeConfig) {
        super(plugin, GameTypeEnum.AceRace, new AceRaceHandler(plugin), config);
        if (initializeConfig) getGameConfig().initializeConfiguration(plugin.getFolder());
        this.copyIndex = copyIndex;
        this.launchPads =
                new AceRaceLaunchPads(
                        plugin,
                        player ->
                                !isDisposed()
                                        && getGameStageEnum() == GameStageEnum.PROGRESS
                                        && player.getWorld() == Bukkit.getWorld(getWorldName())
                                        && isGameplayParticipant(player)
                                        && !raceProgress.isFinished(player.getUniqueId()),
                        featureContacts::remove);
        this.mapEditPreview = new AceRaceEditorPreview(plugin, this, this::previewCourse);
        getGameHandler().setAceRaceArea(this);
        getGameHandler().register();
        loadCoursePoints();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public int getCopyIndex() {
        return copyIndex;
    }

    public void loadCoursePoints() {
        course =
                AceRaceCourseLoader.load(
                        getGameConfig(),
                        getWorldName(),
                        plugin.getServer(),
                        (action, detail) ->
                                logGame(java.util.logging.Level.WARNING, action, detail));
        mapEditPreview.refresh();
    }

    public List<AceRaceProgressPoint> getProgressPoints() {
        return course.progressPoints();
    }

    private AceRaceLine getStartLine() {
        AceRaceConfig config = getGameConfig();
        return config.hasStartLine()
                ? new AceRaceLine(config.getStartLinePos1(), config.getStartLinePos2())
                : null;
    }

    private AceRaceLine getFinishLine() {
        AceRaceConfig config = getGameConfig();
        return config.hasFinishLine()
                ? new AceRaceLine(config.getFinishLinePos1(), config.getFinishLinePos2())
                : null;
    }

    public int getRespawnPointBinding(int configIndex) {
        return course.bindingForConfig(configIndex);
    }

    public int getRespawnPointIndexForConfig(int configIndex) {
        return course.loadedIndexForConfig(configIndex);
    }

    public boolean setRespawnPointBinding(int index, int binding) {
        int loadedIndex = getRespawnPointIndexForConfig(index);
        if (loadedIndex < 0
                || index < 0
                || index >= getGameConfig().ensureRespawnPoints().size()
                || binding < -1
                || binding >= course.progressPoints().size()) return false;
        getGameConfig().setRespawnProgressPointBinding(index, binding);
        loadCoursePoints();
        return true;
    }

    public boolean toggleMapEditPreview(@NotNull Player viewer) {
        return mapEditPreview.toggle(viewer);
    }

    public boolean isMapEditPreviewEnabled(PrepareSession session) {
        return mapEditPreview.enabled(session);
    }

    public void disableMapEditPreview(PrepareSession session) {
        mapEditPreview.stopSession(session);
    }

    public int mapEditPreviewRespawnIndex(@NotNull Entity entity) {
        return mapEditPreview.markerIndex(entity);
    }

    public int mapEditPreviewRespawnIndex(Player viewer, Entity entity) {
        return mapEditPreview.markerIndex(viewer, entity);
    }

    private AceRaceEditorPreview.Course previewCourse() {
        var markers = new ArrayList<AceRaceEditorPreview.Marker>();
        for (AceRaceCourse.RespawnMarker marker : course.respawns()) {
            markers.add(
                    new AceRaceEditorPreview.Marker(
                            marker.point().destination(), marker.configIndex(), marker.binding()));
        }
        return new AceRaceEditorPreview.Course(
                Bukkit.getWorld(getWorldName()),
                getGameConfig().getAreaName(),
                markers,
                course.progressPoints());
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        if (getGameConfig().getStartSpawnPoint() != null)
            locations.add(getGameConfig().getStartSpawnPoint());
        if (getGameConfig().getSpectatorSpawnPoint() != null)
            locations.add(getGameConfig().getSpectatorSpawnPoint());
        for (AceRaceCourse.RespawnMarker marker : course.respawns())
            locations.add(marker.point().destination());
        return locations;
    }

    @Override
    public void resetArea() {
        mapEditPreview.stop();
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        launchPads.stop();
        raceProgress.reset();
        lapStartedNanos.clear();
        lapDurationsMillis.clear();
        raceStartedNanos.clear();
        latestRespawnLocations.clear();
        capturedRespawnPoints.clear();
        activeFallHeights.clear();
        lastMoveLocations.clear();
        startLineArmed.clear();
        featureContacts.clear();
        progressTask = null;
    }

    @Override
    public void startGamePreparation() {
        mapEditPreview.stop();
        if (course.progressPoints().isEmpty()
                || course.respawns().isEmpty()
                || getGameConfig().getStartSpawnPoint() == null
                || !getGameConfig().hasStartLine()
                || !getGameConfig().hasFinishLine()) {
            logGame(
                    java.util.logging.Level.WARNING,
                    MessageConfig.codeText("ace-race-area.start-game-preparation-1"),
                    MessageConfig.codeText("ace-race-area.start-game-preparation-2"));
            endGameFinally();
            return;
        }
        setGameStageEnum(GameStageEnum.PREPARATION);
        hideAllRacersForPreparation();
        startGameIntroduction(this::startFormalPreparation);
    }

    private void startFormalPreparation() {
        Location start = getGameConfig().getStartSpawnPoint();
        teleportAllPlayers(start);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        announceGamePreparation(
                AceRaceMessages.ACE_RACE_START_PREPARATION,
                AceRaceMessages.ACE_RACE_START_PREPARATION_TITLE,
                AceRaceMessages.ACE_RACE_START_PREPARATION_SUBTITLE);
        startGameProgress();
    }

    private void startGameProgress() {
        Location start = getGameConfig().getStartSpawnPoint();
        raceProgress.reset();
        for (UUID uuid : gamePlayers) {
            lapStartedNanos.remove(uuid);
            lapDurationsMillis.put(uuid, new ArrayList<>());
            raceStartedNanos.remove(uuid);
            latestRespawnLocations.put(uuid, start.clone());
            capturedRespawnPoints.put(uuid, new HashSet<>());
            activeFallHeights.put(uuid, getGameConfig().getStartFallY());
            lastMoveLocations.put(uuid, start.clone());
        }
        startFinalCountdown(
                GameTypeEnum.AceRace.toString(),
                AceRaceMessages.ACE_RACE_GAME_START_TITLE,
                AceRaceMessages.ACE_RACE_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        scheduleRacerVisibilityUnlock();
        launchPads.start();
        giveTeamArmor();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) handleEnvironmentalEffects(player);
        }
        // The preparation countdown owns the shared timer bar. Ace Race needs a personal lap
        // stopwatch in the same bar, so replace it with one bar per viewer before the live timer
        // starts.
        clearGameTimerBossBar();
        progressTask =
                startRemainingTimer(
                        getGameConfig().getTimer(),
                        seconds -> {
                            refreshEnvironmentalEffects();
                            timer = seconds;
                            updateAceRaceTimerBossBars(seconds);
                        },
                        this::endGame);
    }

    /** Updates a countdown + personal current-lap stopwatch for every online instance viewer. */
    private void updateAceRaceTimerBossBars(int remainingSeconds) {
        Set<UUID> viewerIds = new HashSet<>(getParticipantUniqueIds());
        viewerIds.addAll(getSpectatorUniqueIds());
        for (String key : new ArrayList<>(bossBars.keySet())) {
            if (!key.startsWith(TIMER_BOSS_BAR_PREFIX)) continue;
            String uuidText = key.substring(TIMER_BOSS_BAR_PREFIX.length());
            try {
                if (!viewerIds.contains(UUID.fromString(uuidText))) removeBossBar(key);
            } catch (IllegalArgumentException ignored) {
                removeBossBar(key);
            }
        }

        double progress =
                getGameConfig().getTimer() <= 0
                        ? 0D
                        : remainingSeconds / (double) getGameConfig().getTimer();
        long now = System.nanoTime();
        for (UUID uuid : viewerIds) {
            String key = TIMER_BOSS_BAR_PREFIX + uuid;
            BossBar bossBar =
                    bossBars.computeIfAbsent(
                            key,
                            ignored -> Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID));
            bossBar.setTitle(
                    LegacyText.translateColorCodes(
                            AceRaceMessages.ACE_RACE_ACTION_BAR_COUNT_DOWN
                                    .replace("%time%", CoreMessages.bossBarTime(remainingSeconds))
                                    .replace(
                                            "%lap-time%",
                                            CoreMessages.bossBarTime(
                                                    currentLapElapsedSeconds(uuid, now)))
                                    .replace("%lap%", String.valueOf(currentLapNumber(uuid)))));
            bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
            } else {
                for (Player current : new ArrayList<>(bossBar.getPlayers()))
                    bossBar.removePlayer(current);
            }
        }
    }

    private int currentLapNumber(@NotNull UUID uuid) {
        return Math.min(getGameConfig().getLaps(), raceProgress.completedLaps(uuid) + 1);
    }

    private long currentLapElapsedSeconds(@NotNull UUID uuid, long nowNanos) {
        List<Long> completed = lapDurationsMillis.get(uuid);
        if (raceProgress.isFinished(uuid) && completed != null && !completed.isEmpty())
            return completed.getLast() / 1_000L;
        Long started = lapStartedNanos.get(uuid);
        return started == null ? 0L : Math.max(0L, (nowNanos - started) / 1_000_000_000L);
    }

    public void handlePlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isGameplayParticipant(player)) return;
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || raceProgress.isFinished(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        Location current = event.getTo();
        if (current == null) return;
        Location previous = lastMoveLocations.put(uuid, current.clone());
        if (previous == null) previous = current;
        handleEnvironmentalEffects(player);
        // Apply stations before fall recovery as well: a fast jump into a water ring may cross the
        // station before the next movement event that would otherwise refresh its short effect.
        handleRedSpeedStation(player, previous, current);
        if (hasReachedActiveFallHeight(player) || notInArea(current)) {
            returnToLatestRespawnPoint(player);
            return;
        }
        handleTrackBlockFeature(player);

        handleProgressPoint(player, previous, current);
        handleRespawnPoints(player, previous, current);
        handleStartAndFinishLines(player, previous, current);
    }

    /**
     * A normal jump during the arm window wins over the delayed pad impulse; it is never cancelled
     * itself.
     */
    public void handlePlayerJump(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || raceProgress.isFinished(uuid)
                || !launchPads.hasPending(uuid)) return;
        launchPads.cancelPending(uuid);
        featureContacts.remove(uuid);
    }

    private void handleProgressPoint(
            @NotNull Player player, @NotNull Location previous, @NotNull Location current) {
        int expected = raceProgress.nextPoint(player.getUniqueId());
        // A fast riptide/elytra movement event can cross several gates before Bukkit emits the next
        // movement event. Consume every consecutive gate intersected by this same trajectory
        // instead
        // of leaving the later gates behind the player's new position forever.
        while (expected < course.progressPoints().size()
                && course.progressPoints().get(expected).crossed(previous, current)) {
            advanceProgressPoint(player, expected);
            expected++;
        }
    }

    private void advanceProgressPoint(@NotNull Player player, int progressIndex) {
        AceRaceProgressPoint progressPoint = course.progressPoints().get(progressIndex);
        activeFallHeights.put(player.getUniqueId(), progressPoint.fallY());
        raceProgress.reachPoint(player.getUniqueId(), progressIndex + 1);
        applyProgressPointEquipment(player, progressPoint.equipment());
        announceProgressPointEquipment(player, progressPoint.equipment());
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7F, 1F);
    }

    private void handleRespawnPoints(
            @NotNull Player player, @NotNull Location previous, @NotNull Location current) {
        UUID uuid = player.getUniqueId();
        Set<Integer> captured =
                capturedRespawnPoints.computeIfAbsent(uuid, ignored -> new HashSet<>());
        for (int index : course.crossedRespawns(previous, current, captured)) {
            AceRaceCourse.RespawnMarker marker = course.respawns().get(index);
            int boundProgressPoint = marker.binding();
            int expected = raceProgress.nextPoint(uuid);
            // A marker may repair exactly one missed gate. Later-course markers cannot be used to
            // shortcut several gates, and markers from an older segment cannot move respawn
            // backward.
            if (boundProgressPoint == expected && expected < course.progressPoints().size()) {
                advanceProgressPoint(player, expected);
                expected++;
            }
            if (boundProgressPoint != expected - 1) continue;

            captured.add(index);
            latestRespawnLocations.put(uuid, marker.point().destination());
        }
    }

    private void handleStartAndFinishLines(
            @NotNull Player player, @NotNull Location previous, @NotNull Location current) {
        AceRaceLine startLine = getStartLine();
        AceRaceLine finishLine = getFinishLine();
        if (startLine == null || finishLine == null) return;
        UUID uuid = player.getUniqueId();
        if (!startLineArmed.contains(uuid)) {
            if (startLine.crossedAtOrAbove(previous, current)) {
                startLineArmed.add(uuid);
                long startedAt = System.nanoTime();
                lapStartedNanos.put(uuid, startedAt);
                raceStartedNanos.putIfAbsent(uuid, startedAt);
            }
            return;
        }
        if (raceProgress.nextPoint(uuid) < course.progressPoints().size()
                || !crossedFinishForward(finishLine, previous, current, startLine)) return;

        int lap = raceProgress.completeLap(uuid);
        long completedAt = System.nanoTime();
        // The next lap starts only when the racer crosses the start line again. Do not carry the
        // just-completed timestamp through the reset, otherwise time spent turning around at the
        // line
        // would be incorrectly added to the next lap.
        Long lapStarted = lapStartedNanos.remove(uuid);
        if (lapStarted != null) {
            long durationMillis = Math.max(0L, (completedAt - lapStarted) / 1_000_000L);
            lapDurationsMillis
                    .computeIfAbsent(uuid, ignored -> new ArrayList<>())
                    .add(durationMillis);
            if (getRunMode() == ink.ziip.championshipscore.api.game.model.GameRunMode.DAILY) {
                plugin.getDailyManager()
                        .statsManager()
                        .recordPlayerTime(
                                this, uuid, DailyRecordType.ACERACE_FASTEST_LAP, durationMillis);
            }
        }
        Long raceStarted = raceStartedNanos.get(uuid);
        if (lap == 3
                && getGameConfig().getLaps() == 3
                && raceStarted != null
                && getRunMode() == ink.ziip.championshipscore.api.game.model.GameRunMode.DAILY) {
            long durationMillis = Math.max(0L, (completedAt - raceStarted) / 1_000_000L);
            plugin.getDailyManager()
                    .statsManager()
                    .recordPlayerTime(
                            this, uuid, DailyRecordType.ACERACE_FASTEST_THREE_LAPS, durationMillis);
        }
        if (lap < getGameConfig().getLaps()) {
            resetLapProgress(player, current);
            updateAceRaceTimerBossBars(timer);
            String message =
                    AceRaceMessages.ACE_RACE_LAP_COMPLETED
                            .replace("%player%", CoreMessages.formatPlayerName(player))
                            .replace("%lap%", String.valueOf(lap))
                            .replace("%total%", String.valueOf(getGameConfig().getLaps()));
            sendMessageToAllGamePlayers(message);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
            return;
        }
        finishPlayer(player);
    }

    /**
     * Starts a new lap without carrying any ordered gate or respawn marker state across the line.
     */
    private void resetLapProgress(@NotNull Player player, @NotNull Location movementBaseline) {
        UUID uuid = player.getUniqueId();
        raceProgress.reachPoint(uuid, 0);
        launchPads.cancelPlayer(uuid);
        player.removePotionEffect(PotionEffectType.SPEED);
        handleEnvironmentalEffects(player);
        activeFallHeights.put(uuid, getGameConfig().getStartFallY());
        applyProgressPointEquipment(player, AceRaceEquipment.NONE);
        Location start = getGameConfig().getStartSpawnPoint();
        if (start != null) latestRespawnLocations.put(uuid, start.clone());
        capturedRespawnPoints.computeIfAbsent(uuid, ignored -> new HashSet<>()).clear();
        startLineArmed.remove(uuid);
        lastMoveLocations.put(uuid, movementBaseline.clone());
    }

    private boolean crossedFinishForward(
            @NotNull AceRaceLine finishLine,
            @NotNull Location previous,
            @NotNull Location current,
            @NotNull AceRaceLine startLine) {
        if (!finishLine.crossedAtOrAbove(previous, current)) return false;
        World world = current.getWorld();
        if (world == null) return false;
        Location startCenter = startLine.center(world);
        if (finishLine.sameGeometry(startLine)) {
            Location startSpawn = getGameConfig().getStartSpawnPoint();
            return startSpawn != null
                    && finishLine.crossedTowardReferenceSide(previous, current, startSpawn);
        }
        if (finishLine.side(startCenter) != 0)
            return finishLine.crossedTowardReferenceSide(previous, current, startCenter);
        Location finishCenter = finishLine.center(world);
        Vector towardStart = startCenter.toVector().subtract(finishCenter.toVector());
        towardStart.setY(0D);
        Vector movement = current.toVector().subtract(previous.toVector());
        movement.setY(0D);
        return towardStart.lengthSquared() > 0.0001D && movement.dot(towardStart) > 0D;
    }

    private void handleTrackBlockFeature(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Block block = findTrackFeatureBlock(player.getLocation());
        if (block == null) {
            featureContacts.remove(uuid);
            launchPads.cancelPending(uuid);
            return;
        }
        Material material = block.getType();
        TrackFeatureContact featureContact = TrackFeatureContact.from(block);
        if (featureContact.equals(featureContacts.put(player.getUniqueId(), featureContact)))
            return;
        if (!AceRaceLaunchPads.isLaunchPad(material)) launchPads.cancelPending(uuid);

        switch (material) {
            case YELLOW_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SPEED,
                                SPEED_BOOST_DURATION_TICKS,
                                YELLOW_SPEED_AMPLIFIER,
                                true,
                                false,
                                false));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.5F);
                CoreMessages.sendActionBar(player, AceRaceMessages.ACE_RACE_SPEED_BOOST);
            }
            case LIME_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.JUMP_BOOST,
                                JUMP_BOOST_DURATION_TICKS,
                                7,
                                true,
                                false,
                                false));
                player.playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 0.8F, 1.2F);
                CoreMessages.sendActionBar(player, AceRaceMessages.ACE_RACE_JUMP_PAD);
            }
            case RED_WOOL, ORANGE_WOOL -> launchPads.arm(player, material);
            default -> {}
        }
    }

    private static boolean isTrackFeature(@NotNull Material material) {
        return material == Material.YELLOW_GLAZED_TERRACOTTA
                || material == Material.LIME_GLAZED_TERRACOTTA
                || material == Material.RED_WOOL
                || material == Material.ORANGE_WOOL;
    }

    private static @Nullable Block findTrackFeatureBlock(@NotNull Location location) {
        Block playerBlock = location.getBlock();
        Block directlyBelow = playerBlock.getRelative(0, -1, 0);
        if (isTrackFeature(directlyBelow.getType())) return directlyBelow;
        Block twoBlocksBelow = playerBlock.getRelative(0, -2, 0);
        if (twoBlocksBelow.getType() == Material.YELLOW_GLAZED_TERRACOTTA) return twoBlocksBelow;
        return null;
    }

    private void refreshEnvironmentalEffects() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !isManagedSpectator(player)) handleEnvironmentalEffects(player);
        }
    }

    private void handleEnvironmentalEffects(@NotNull Player player) {
        if (player.isInWater()) {
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.CONDUIT_POWER,
                            ENVIRONMENTAL_EFFECT_DURATION_TICKS,
                            0,
                            true,
                            false,
                            false));
            applyWaterEnvironmentSpeed(player);
            return;
        }
        applyBaseSpeed(player, false);
    }

    private void applyWaterEnvironmentSpeed(@NotNull Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current != null && current.getAmplifier() == YELLOW_SPEED_AMPLIFIER) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
        applyBaseSpeed(player, false);
    }

    private void handleRedSpeedStation(
            @NotNull Player player, @NotNull Location previous, @NotNull Location current) {
        // The first movement packet after a pad launch can arrive while the client is still
        // transitioning
        // into the pool, before Player#isInWater() reflects the new feet block. Include both
        // endpoints so
        // the wider water-ring radius is used on that transition tick as well.
        boolean inWater = player.isInWater() || isWaterAt(previous) || isWaterAt(current);
        int radius = inWater ? WATER_SPEED_STATION_RADIUS : SPEED_STATION_RADIUS;
        double radiusSquared =
                inWater ? WATER_SPEED_STATION_RADIUS_SQUARED : SPEED_STATION_RADIUS_SQUARED;
        boolean currentlyNear =
                isNearStation(current, Material.RED_GLAZED_TERRACOTTA, radius, radiusSquared);
        boolean crossedStation =
                currentlyNear
                        || isNearStationAlongPath(
                                previous,
                                current,
                                Material.RED_GLAZED_TERRACOTTA,
                                radius,
                                radiusSquared);
        // Refresh while the player remains inside the station, and still grant one full pulse when
        // a
        // high-speed movement crosses the station between two movement packets.
        if (crossedStation) {
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SPEED,
                            RED_SPEED_DURATION_TICKS,
                            RED_SPEED_AMPLIFIER,
                            true,
                            false,
                            false));
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.DOLPHINS_GRACE,
                            RED_SPEED_DURATION_TICKS,
                            0,
                            true,
                            false,
                            false));
            // Red terracotta is an intentional speed source. Do not let the launch-flight budget
            // immediately rescale away the first accelerated movement after a pad sends the racer
            // here.
            launchPads.releaseFlight(player.getUniqueId());
        }
    }

    private static boolean isWaterAt(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        Block block = location.getBlock();
        return block.getType() == Material.WATER
                || block.getRelative(0, -1, 0).getType() == Material.WATER;
    }

    private void applyBaseSpeed(@NotNull Player player, boolean replaceCurrent) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (!replaceCurrent && current != null) return;
        if (replaceCurrent) player.removePotionEffect(PotionEffectType.SPEED);
        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.SPEED,
                        PotionEffect.INFINITE_DURATION,
                        BASE_SPEED_AMPLIFIER,
                        true,
                        false,
                        false));
    }

    /** Each progress segment owns the threshold at which fall recovery becomes active. */
    private boolean hasReachedActiveFallHeight(@NotNull Player player) {
        return player.getLocation().getY()
                <= activeFallHeights.getOrDefault(
                        player.getUniqueId(), getGameConfig().getStartFallY());
    }

    private void giveTeamArmor() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) giveTeamArmor(player);
        }
    }

    private void giveTeamArmor(@NotNull Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (team == null) return;
        player.getInventory().setHelmet(unbreakable(team.getHelmet()));
        player.getInventory().setLeggings(unbreakableSwiftSneakLeggings(team.getLeggings()));
        player.getInventory().setBoots(unbreakableDepthStriderBoots(team.getBoots()));
    }

    private static @NotNull ItemStack unbreakable(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull ItemStack unbreakableSwiftSneakLeggings(@NotNull ItemStack leggings) {
        ItemMeta meta = leggings.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.SWIFT_SNEAK), 3, true);
            leggings.setItemMeta(meta);
        }
        return leggings;
    }

    private static @NotNull ItemStack unbreakableDepthStriderBoots(@NotNull ItemStack boots) {
        ItemMeta meta = boots.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER), DEPTH_STRIDER_LEVEL, true);
            boots.setItemMeta(meta);
        }
        return boots;
    }

    private static void setDepthStriderLevel(@NotNull PlayerInventory inventory, int level) {
        ItemStack boots = inventory.getBoots();
        if (boots == null) return;
        ItemMeta meta = boots.getItemMeta();
        if (meta == null) return;
        meta.removeEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER));
        meta.addEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER), level, true);
        boots.setItemMeta(meta);
        inventory.setBoots(boots);
    }

    /** Returns whether at least one trident was removed from any player inventory slot. */
    private boolean removeAllTridents(@NotNull Player player) {
        boolean removed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.TRIDENT) {
                inventory.setItem(slot, null);
                removed = true;
            }
        }
        return removed;
    }

    /** Removes race-issued elytras that a player may have moved out of the chestplate slot. */
    private static void removeAllElytrasOutsideChestplate(@NotNull PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (item != null && item.getType() == Material.ELYTRA) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType() == Material.ELYTRA) {
            inventory.setItemInOffHand(null);
        }
    }

    private static @NotNull ItemStack createRiptideTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        trident.addEnchantment(Enchants.get(EnchantmentKeys.RIPTIDE), 1);
        ItemMeta meta = trident.getItemMeta();
        meta.setUnbreakable(true);
        trident.setItemMeta(meta);
        return trident;
    }

    public void applyIntermediateRiptideBoost(@NotNull PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || notAreaPlayer(player)
                || raceProgress.isFinished(player.getUniqueId())) return;
        if (event.getItem().getEnchantmentLevel(Enchants.get(EnchantmentKeys.RIPTIDE)) != 1) return;

        // Paper fires this event immediately before adding the vanilla riptide vector. Keep only a
        // small course-specific lift above vanilla Riptide I so the boost does not overshoot rings.
        Vector extraVelocity = event.getVelocity().multiply(RIPTIDE_EXTRA_MULTIPLIER);
        player.setVelocity(player.getVelocity().add(extraVelocity));
    }

    private static @NotNull ItemStack createCourseElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.setUnbreakable(true);
        elytra.setItemMeta(meta);
        return elytra;
    }

    private void applyProgressPointEquipment(
            @NotNull Player player, @NotNull AceRaceEquipment equipment) {
        PlayerInventory inventory = player.getInventory();
        setDepthStriderLevel(
                inventory,
                equipment == AceRaceEquipment.DOLPHINS_GRACE
                        ? DOLPHINS_GRACE_DEPTH_STRIDER_LEVEL
                        : DEPTH_STRIDER_LEVEL);
        // Elytra can be manually unequipped into storage/off-hand. Clear those copies whenever
        // the stage changes so an old elytra cannot be carried into a different equipment stage.
        removeAllElytrasOutsideChestplate(inventory);
        ItemStack chestplate = inventory.getChestplate();
        if (equipment != AceRaceEquipment.ELYTRA
                && chestplate != null
                && chestplate.getType() == Material.ELYTRA) {
            inventory.setChestplate(null);
        }
        if (equipment != AceRaceEquipment.TRIDENT) removeAllTridents(player);
        if (equipment != AceRaceEquipment.DOLPHINS_GRACE) {
            player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        }

        if (equipment == AceRaceEquipment.ELYTRA) {
            if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
                inventory.setChestplate(createCourseElytra());
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1F, 1F);
            }
        } else if (equipment == AceRaceEquipment.TRIDENT && !hasTrident(player)) {
            inventory.addItem(createRiptideTrident());
        } else if (equipment == AceRaceEquipment.DOLPHINS_GRACE) {
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.DOLPHINS_GRACE,
                            PotionEffect.INFINITE_DURATION,
                            0,
                            true,
                            false,
                            false));
            if (player.isInWater()) applyWaterEnvironmentSpeed(player);
        }
    }

    /** Rebuilds the deterministic race loadout for players who were offline when the race began. */
    private void restoreRacerEquipment(@NotNull Player player) {
        player.getInventory().clear();
        giveTeamArmor(player);
        int reached = raceProgress.nextPoint(player.getUniqueId()) - 1;
        applyProgressPointEquipment(
                player,
                reached >= 0 && reached < course.progressPoints().size()
                        ? course.progressPoints().get(reached).equipment()
                        : AceRaceEquipment.NONE);
    }

    private void announceProgressPointEquipment(
            @NotNull Player player, @NotNull AceRaceEquipment equipment) {
        if (equipment == AceRaceEquipment.ELYTRA) {
            player.sendMessage(AceRaceMessages.ACE_RACE_RECEIVED_ELYTRA);
        } else if (equipment == AceRaceEquipment.TRIDENT) {
            player.sendMessage(AceRaceMessages.ACE_RACE_RECEIVED_TRIDENT);
        } else if (equipment == AceRaceEquipment.DOLPHINS_GRACE) {
            player.sendMessage(AceRaceMessages.ACE_RACE_RECEIVED_DOLPHINS_GRACE);
        }
    }

    private static boolean hasTrident(@NotNull Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TRIDENT) return true;
        }
        return false;
    }

    private boolean isNearStationAlongPath(
            @NotNull Location previous,
            @NotNull Location current,
            @NotNull Material stationMaterial,
            int radius,
            double radiusSquared) {
        if (previous.getWorld() == null || !previous.getWorld().equals(current.getWorld()))
            return false;
        Vector movement = current.toVector().subtract(previous.toVector());
        if (movement.lengthSquared() <= 1D) return false;
        int samples = Math.min(128, Math.max(1, (int) Math.ceil(movement.length())));
        // The current endpoint was already checked above. Only long movement packets need interior
        // samples; ordinary sub-block movement therefore keeps the same single station lookup cost.
        for (int sample = 1; sample < samples; sample++) {
            Location location =
                    previous.clone().add(movement.clone().multiply(sample / (double) samples));
            if (isNearStation(location, stationMaterial, radius, radiusSquared)) return true;
        }
        return false;
    }

    private boolean isNearStation(
            @NotNull Location location,
            @NotNull Material stationMaterial,
            int radius,
            double radiusSquared) {
        if (location.getWorld() == null) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int blockX = x - radius; blockX <= x + radius; blockX++) {
            for (int blockY = y - radius; blockY <= y + radius; blockY++) {
                for (int blockZ = z - radius; blockZ <= z + radius; blockZ++) {
                    if (location.getWorld().getBlockAt(blockX, blockY, blockZ).getType()
                            != stationMaterial) continue;
                    double dx = location.getX() - (blockX + 0.5D);
                    double dy = location.getY() - (blockY + 0.5D);
                    double dz = location.getZ() - (blockZ + 0.5D);
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) return true;
                }
            }
        }
        return false;
    }

    private record TrackFeatureContact(
            @NotNull UUID worldId, @NotNull Material material, int x, int y, int z) {
        private static @NotNull TrackFeatureContact from(@NotNull Block block) {
            Material material = block.getType();
            if (AceRaceLaunchPads.isLaunchPad(material)) {
                // Adjacent wool blocks form one pad. Keep it active until the player leaves the pad
                // so crossing a block boundary during takeoff cannot launch the player twice.
                return new TrackFeatureContact(block.getWorld().getUID(), material, 0, 0, 0);
            }
            return new TrackFeatureContact(
                    block.getWorld().getUID(), material, block.getX(), block.getY(), block.getZ());
        }
    }

    private void finishPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (raceProgress.isFinished(uuid)) return;
        launchPads.cancelPlayer(uuid);
        int place = raceProgress.finish(uuid);
        int points =
                Math.max(
                                getGameConfig().getMinimumFinishPoints(),
                                getGameConfig().getFirstPlacePoints()
                                        - (place - 1) * getGameConfig().getPlacementDecrement())
                        + getGameConfig().getPlacementBonus(place);
        addPlayerPoints(uuid, points);
        sendMessageToAllGamePlayers(
                AceRaceMessages.ACE_RACE_FINISHED
                        .replace("%player%", CoreMessages.formatPlayerName(player))
                        .replace("%place%", String.valueOf(place)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.4F);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        applyProgressPointEquipment(player, AceRaceEquipment.NONE);
        restoreRacerVisibility(player);
        plugin.getGameManager()
                .getSpectatorManager()
                .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
        // Formal matches retain the complete roster. DAILY runs treat a disconnected racer as
        // unable
        // to finish, so that UUID must not keep the remaining racers in PROGRESS.
        Collection<UUID> requiredParticipants =
                getRunMode() == ink.ziip.championshipscore.api.game.model.GameRunMode.DAILY
                        ? gamePlayers.stream()
                                .filter(participantId -> Bukkit.getPlayer(participantId) != null)
                                .toList()
                        : gamePlayers;
        if (allParticipantsFinished(requiredParticipants, raceProgress.finishedPlayers()))
            endGame();
    }

    /**
     * Uses roster membership rather than equal list sizes so duplicate or already-removed entries
     * cannot delay the end.
     */
    static boolean allParticipantsFinished(
            @NotNull Collection<UUID> participants, @NotNull Collection<UUID> finished) {
        return !participants.isEmpty() && finished.containsAll(participants);
    }

    public void returnToLatestRespawnPoint(@NotNull Player player) {
        if (!isGameplayParticipant(player)) return;
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || raceProgress.isFinished(player.getUniqueId())) return;
        launchPads.cancelPlayer(player.getUniqueId());
        Location destination =
                latestRespawnLocations.getOrDefault(
                        player.getUniqueId(), getGameConfig().getStartSpawnPoint());
        if (destination == null) return;
        player.teleport(destination);
        player.setFallDistance(0F);
        lastMoveLocations.put(player.getUniqueId(), destination.clone());
        CoreMessages.sendActionBar(player, AceRaceMessages.ACE_RACE_RETURNED_TO_RESPAWN_POINT);
    }

    public int getPlayerPosition(@NotNull UUID uuid) {
        return getPlayerPlacementRange(uuid).first();
    }

    /**
     * Displays a shared placement interval when multiple unfinished racers have reached the same
     * lap and ordered progress point. Finished racers always retain their exact finish position.
     */
    public @NotNull String getPlayerPositionDisplay(@NotNull UUID uuid) {
        PlacementRange range = getPlayerPlacementRange(uuid);
        return range.first() == range.last()
                ? String.valueOf(range.first())
                : range.first() + "-" + range.last();
    }

    private @NotNull PlacementRange getPlayerPlacementRange(@NotNull UUID uuid) {
        return raceProgress.placement(uuid, gamePlayers, course.progressPoints().size());
    }

    public List<UUID> getFinishedPlayers() {
        return raceProgress.finishedPlayers();
    }

    public int getCurrentLap(@NotNull UUID uuid) {
        if (!gamePlayers.contains(uuid)) return 0;
        if (raceProgress.isFinished(uuid)) return getGameConfig().getLaps();
        return Math.min(getGameConfig().getLaps(), raceProgress.completedLaps(uuid) + 1);
    }

    /** Returns a completed lap duration in the same mm:ss format used by the live clocks. */
    public @NotNull String getLapDurationDisplay(@NotNull UUID uuid, int lap) {
        if (lap < 1) return ScoreMessages.PLACEHOLDER_NONE;
        List<Long> durations = lapDurationsMillis.get(uuid);
        if (durations == null || lap > durations.size()) return ScoreMessages.PLACEHOLDER_NONE;
        return DurationText.minutesSeconds(durations.get(lap - 1) / 1_000L);
    }

    /** Returns the three fixed sidebar slots, preserving a dash until each lap is completed. */
    public @NotNull String getLapDurationsDisplay(@NotNull UUID uuid) {
        List<Long> durations = lapDurationsMillis.get(uuid);
        List<String> slots = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            slots.add(
                    durations != null && index < durations.size()
                            ? DurationText.minutesSeconds(durations.get(index) / 1_000L)
                            : "-");
        }
        return String.join(" / ", slots);
    }

    public int getReachedProgressPoint(@NotNull UUID uuid) {
        return gamePlayers.contains(uuid) ? raceProgress.nextPoint(uuid) : 0;
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;
        if (progressTask != null) progressTask.cancel();
        launchPads.stop();
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        cleanInventoryForAllGamePlayers();
        announceGameEnd(
                AceRaceMessages.ACE_RACE_GAME_END_TITLE,
                AceRaceMessages.ACE_RACE_GAME_END_SUBTITLE);
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        if (isSettlementAllowed()) sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        launchPads.cancelPlayer(player.getUniqueId());
        event.setKeepInventory(true);
        event.getDrops().clear();
        scheduleParticipantRespawn(
                player,
                () -> {
                    if (getGameStageEnum() == GameStageEnum.PROGRESS
                            && isGameplayParticipant(player)) {
                        player.setGameMode(GameMode.ADVENTURE);
                        returnToLatestRespawnPoint(player);
                        restoreRacerEquipment(player);
                        applyBaseSpeed(player, true);
                    }
                });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        launchPads.cancelPlayer(player.getUniqueId());
        if (!notAreaPlayer(player)) {
            visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
            releaseRacerVisibility(player);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        if (!raceProgress.isFinished(player.getUniqueId())) {
            visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
            hideRacer(player);
            refreshRacerVisibility();
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(getGameConfig().getStartSpawnPoint()));
            player.setGameMode(GameMode.ADVENTURE);
        } else if (getGameStageEnum() == GameStageEnum.COUNTDOWN
                || getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (raceProgress.isFinished(player.getUniqueId())) {
                plugin.getGameManager()
                        .getSpectatorManager()
                        .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
            } else {
                player.setGameMode(GameMode.ADVENTURE);
                returnToLatestRespawnPoint(player);
                restoreRacerEquipment(player);
                applyBaseSpeed(player, true);
            }
        } else {
            releaseRacerVisibility(player);
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void stopRacerVisibilityUpdates() {
        if (racerVisibilityUnlockTask != null) {
            racerVisibilityUnlockTask.cancel();
            racerVisibilityUnlockTask = null;
        }
        if (racerVisibilityTask != null) {
            racerVisibilityTask.cancel();
            racerVisibilityTask = null;
        }
        racerVisibilityUnlocked = false;
        removeAllRacerNameDisplays();
        visibleRacerPairs.clear();
        riptideHiddenViews.clear();
        riptideViewerGraceTicks.clear();
    }

    /** Preparation keeps all racers fully hidden, regardless of distance. */
    private void hideAllRacersForPreparation() {
        stopRacerVisibilityUpdates();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) hideRacer(player);
        }
        racerVisibilityTask =
                scheduler.runTaskTimer(
                        plugin,
                        this::refreshRacerVisibility,
                        RACER_VISIBILITY_UPDATE_TICKS,
                        RACER_VISIBILITY_UPDATE_TICKS);
    }

    /**
     * Keeps active racers hidden until the first minute has elapsed, then enables proximity
     * visibility.
     */
    private void scheduleRacerVisibilityUnlock() {
        if (racerVisibilityUnlockTask != null) racerVisibilityUnlockTask.cancel();
        racerVisibilityUnlocked = false;
        racerVisibilityUnlockTask =
                scheduler.runTaskLater(
                        plugin,
                        () -> {
                            racerVisibilityUnlockTask = null;
                            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
                            racerVisibilityUnlocked = true;
                            refreshRacerVisibility();
                        },
                        RACER_VISIBILITY_HIDDEN_AFTER_START_TICKS);
    }

    /**
     * Keeps active racers mutually visible only while they are within eight blocks after the grace
     * period.
     */
    private void refreshRacerVisibility() {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.WAITING || stage == GameStageEnum.END) return;
        List<Player> racers = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            if (raceProgress.isFinished(uuid)) continue;
            Player racer = Bukkit.getPlayer(uuid);
            if (racer == null || !isGameplayParticipant(racer)) continue;
            disableRacerCollisions(racer);
            racers.add(racer);
        }
        if (stage != GameStageEnum.PROGRESS || !racerVisibilityUnlocked) return;
        for (Player racer : racers) {
            TextDisplay nameDisplay = ensureRacerNameDisplay(racer);
            nameDisplay.teleport(racerNameDisplayLocation(racer));
        }

        Set<UUID> activeRacers = new HashSet<>();
        Map<UUID, Location> racerLocations = new HashMap<>();
        for (Player racer : racers) activeRacers.add(racer.getUniqueId());
        for (Player racer : racers) racerLocations.put(racer.getUniqueId(), racer.getLocation());
        visibleRacerPairs.removeIf(pair -> !pair.bothIn(activeRacers));
        riptideHiddenViews.removeIf(view -> !view.bothIn(activeRacers));
        updateRiptideViewerState(racers, activeRacers);

        for (int firstIndex = 0; firstIndex < racers.size(); firstIndex++) {
            Player first = racers.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < racers.size(); secondIndex++) {
                Player second = racers.get(secondIndex);
                RacerPair pair = RacerPair.of(first.getUniqueId(), second.getUniqueId());
                Location firstLocation = racerLocations.get(first.getUniqueId());
                Location secondLocation = racerLocations.get(second.getUniqueId());
                boolean nearby =
                        firstLocation.getWorld().equals(secondLocation.getWorld())
                                && firstLocation.distanceSquared(secondLocation)
                                        <= RACER_VISIBILITY_DISTANCE_SQUARED;
                boolean visible = visibleRacerPairs.contains(pair);
                if (nearby) {
                    if (!visible) {
                        showRacerNameDisplay(second, first);
                        showRacerNameDisplay(first, second);
                        visibleRacerPairs.add(pair);
                    }
                    updateDirectionalRacerView(second, first, !visible);
                    updateDirectionalRacerView(first, second, !visible);
                } else if (visible) {
                    clearDirectionalRacerView(second, first);
                    clearDirectionalRacerView(first, second);
                    hideRacerNameDisplay(second, first);
                    hideRacerNameDisplay(first, second);
                    visibleRacerPairs.remove(pair);
                }
            }
        }
    }

    private void hideRacer(@NotNull Player player) {
        disableRacerCollisions(player);
        plugin.getVisibilityManager()
                .seeSelf(
                        player.getUniqueId(),
                        visibilityOwner,
                        MessageConfig.codeText("ace-race-area.hide-racer-1"));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (gamePlayers.contains(other.getUniqueId())
                    && !raceProgress.isFinished(other.getUniqueId())) {
                setRacerVisible(other, player, false);
                disableRacerCollisions(other);
            }
        }
    }

    private void restoreRacerVisibility(@NotNull Player player) {
        clearRacerOutlines(player);
        removeRacerNameDisplay(player.getUniqueId());
        visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
        riptideHiddenViews.removeIf(view -> view.contains(player.getUniqueId()));
        riptideViewerGraceTicks.remove(player.getUniqueId());
        restoreRacerCollisions(player);
        plugin.getVisibilityManager().release(player.getUniqueId(), visibilityOwner);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (gamePlayers.contains(other.getUniqueId())
                    && !raceProgress.isFinished(other.getUniqueId()))
                setRacerVisible(other, player, false);
        }
    }

    private void restoreAllRacerVisibility() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) clearRacerOutlines(player);
        }
        visibleRacerPairs.clear();
        riptideHiddenViews.clear();
        riptideViewerGraceTicks.clear();
        plugin.getVisibilityManager().releaseAll(gamePlayers, visibilityOwner);
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            restoreRacerCollisions(player);
        }
    }

    /**
     * Cleans transient visuals on disconnect; the UUID policy intentionally survives reconnects.
     */
    private void releaseRacerVisibility(@NotNull Player player) {
        clearRacerOutlines(player);
        removeRacerNameDisplay(player.getUniqueId());
        riptideHiddenViews.removeIf(view -> view.contains(player.getUniqueId()));
        riptideViewerGraceTicks.remove(player.getUniqueId());
        restoreRacerCollisions(player);
    }

    /**
     * Keeps a newly joined observer hidden from active racers without hiding racers from observers.
     */
    public void handleVisibilityJoin(@NotNull Player joining) {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;
        // GameManagerHandler separately restores reconnecting participants. Do not overwrite its
        // distance-based result if this MONITOR join hook happens to run afterwards.
        if (gamePlayers.contains(joining.getUniqueId())
                && !raceProgress.isFinished(joining.getUniqueId())) return;
        for (UUID uuid : gamePlayers) {
            Player racer = Bukkit.getPlayer(uuid);
            if (racer != null && !racer.equals(joining) && !raceProgress.isFinished(uuid))
                setRacerVisible(racer, joining, false);
        }
    }

    /** Removes both directions of every Ace Race-only invisible glow involving this racer. */
    private void clearRacerOutlines(@NotNull Player player) {
        for (UUID uuid : gamePlayers) {
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || other.equals(player)) continue;
            plugin.getGlowingEntities().unsetInvisibleGlowing(other, player);
            plugin.getGlowingEntities().unsetInvisibleGlowing(player, other);
        }
    }

    /**
     * Removes real racer entities from a riptiding player's client before client-side spin contact
     * can occur.
     */
    public void handleRiptideStart(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || !isGameplayParticipant(player)) return;
        // A riptide is a sanctioned way to exceed the pad's own flight curve.
        launchPads.releaseFlight(player.getUniqueId());
        riptideViewerGraceTicks.put(player.getUniqueId(), 3);
        for (UUID uuid : gamePlayers) {
            if (uuid.equals(player.getUniqueId()) || raceProgress.isFinished(uuid)) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            RacerView view = new RacerView(player.getUniqueId(), target.getUniqueId());
            if (riptideHiddenViews.add(view)) {
                plugin.getGlowingEntities().unsetInvisibleGlowing(target, player);
                setRacerVisible(player, target, false);
            }
        }
        scheduleGameplayAction(player, () -> restoreAuthoritativeRiptideState(player), 1L);
    }

    private void restoreAuthoritativeRiptideState(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !player.isOnline()
                || !player.isRiptiding()) return;
        Vector velocity = player.getVelocity();
        // Mark the riptide metadata dirty again and resend the unchanged authoritative velocity.
        // This repairs
        // the local player's state if it began the spin while already overlapping another client
        // entity.
        player.setRiptiding(false);
        player.setRiptiding(true);
        player.setVelocity(velocity);
    }

    private void updateRiptideViewerState(
            @NotNull List<Player> racers, @NotNull Set<UUID> activeRacers) {
        riptideViewerGraceTicks.keySet().removeIf(uuid -> !activeRacers.contains(uuid));
        for (Player racer : racers) {
            UUID uuid = racer.getUniqueId();
            if (racer.isRiptiding()) {
                riptideViewerGraceTicks.put(uuid, 2);
                continue;
            }
            Integer grace = riptideViewerGraceTicks.get(uuid);
            if (grace == null) continue;
            if (grace <= 1) riptideViewerGraceTicks.remove(uuid);
            else riptideViewerGraceTicks.put(uuid, grace - 1);
        }
    }

    private void updateDirectionalRacerView(
            @NotNull Player target, @NotNull Player viewer, boolean newlyNearby) {
        RacerView view = new RacerView(viewer.getUniqueId(), target.getUniqueId());
        boolean suppressForRiptide = riptideViewerGraceTicks.containsKey(viewer.getUniqueId());
        if (suppressForRiptide) {
            if (riptideHiddenViews.add(view)) {
                plugin.getGlowingEntities().unsetInvisibleGlowing(target, viewer);
                setRacerVisible(viewer, target, false);
            }
            return;
        }
        if (riptideHiddenViews.remove(view) || newlyNearby) {
            setRacerVisible(viewer, target, true);
            plugin.getGlowingEntities().setInvisibleGlowing(target, viewer);
        }
    }

    private void clearDirectionalRacerView(@NotNull Player target, @NotNull Player viewer) {
        RacerView view = new RacerView(viewer.getUniqueId(), target.getUniqueId());
        riptideHiddenViews.remove(view);
        plugin.getGlowingEntities().unsetInvisibleGlowing(target, viewer);
        setRacerVisible(viewer, target, false);
    }

    /** Delegates the directional decision to the UUID-backed global lifecycle manager. */
    private void setRacerVisible(@NotNull Player viewer, @NotNull Player target, boolean visible) {
        plugin.getVisibilityManager()
                .setPlayerVisible(
                        viewer.getUniqueId(),
                        target.getUniqueId(),
                        visible,
                        visibilityOwner,
                        visible
                                ? MessageConfig.codeText("ace-race-area.set-racer-visible-2")
                                : MessageConfig.codeText("ace-race-area.set-racer-visible-1"));
    }

    private @NotNull TextDisplay ensureRacerNameDisplay(@NotNull Player racer) {
        TextDisplay existing = racerNameDisplays.get(racer.getUniqueId());
        if (existing != null && existing.isValid()) return existing;

        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(racer.getUniqueId());
        TextDisplay display =
                racer.getWorld()
                        .spawn(
                                racerNameDisplayLocation(racer),
                                TextDisplay.class,
                                spawned -> {
                                    spawned.text(
                                            ComponentTemplates.render(
                                                    MessageConfig.codeText(
                                                            "ace-race-area.racer-name"),
                                                    Map.of(
                                                            "%player%",
                                                            net.kyori.adventure.text.Component.text(
                                                                    racer.getName(),
                                                                    team == null
                                                                            ? null
                                                                            : team.getTeam()
                                                                                    .color()))));
                                    MessageConfig.worldTextStyle(Profile.ACE_RACE_NAME)
                                            .apply(spawned);
                                    spawned.setTeleportDuration(1);
                                    spawned.setInterpolationDuration(1);
                                    spawned.setVisibleByDefault(false);
                                    spawned.setPersistent(false);
                                    spawned.setInvulnerable(true);
                                    spawned.setGravity(false);
                                });
        racerNameDisplays.put(racer.getUniqueId(), display);
        return display;
    }

    private @NotNull Location racerNameDisplayLocation(@NotNull Player racer) {
        return racer.getLocation().add(0D, racer.getBoundingBox().getHeight() + 0.35D, 0D);
    }

    private void showRacerNameDisplay(@NotNull Player target, @NotNull Player viewer) {
        viewer.showEntity(plugin, ensureRacerNameDisplay(target));
    }

    private void hideRacerNameDisplay(@NotNull Player target, @NotNull Player viewer) {
        TextDisplay display = racerNameDisplays.get(target.getUniqueId());
        if (display != null) viewer.hideEntity(plugin, display);
    }

    private void removeRacerNameDisplay(@NotNull UUID uuid) {
        TextDisplay display = racerNameDisplays.remove(uuid);
        if (display != null) display.remove();
    }

    private void removeAllRacerNameDisplays() {
        for (TextDisplay display : racerNameDisplays.values()) display.remove();
        racerNameDisplays.clear();
    }

    /**
     * Disables both server-side entity pushing and the client-predicted collision driven by
     * scoreboard teams. A separate temporary team is kept for each original colour so glowing
     * outlines retain their team colour.
     */
    private void disableRacerCollisions(@NotNull Player player) {
        player.setCollidable(false);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null && !current.getName().startsWith(COLLISION_TEAM_PREFIX))
            originalScoreboardTeams.putIfAbsent(player.getUniqueId(), current.getName());

        ChampionshipTeam championshipTeam =
                plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        String colorName =
                championshipTeam == null ? "white" : championshipTeam.getColorName().toLowerCase();
        String collisionTeamName = COLLISION_TEAM_PREFIX + colorName;
        Team collisionTeam = scoreboard.getTeam(collisionTeamName);
        if (collisionTeam == null) collisionTeam = scoreboard.registerNewTeam(collisionTeamName);
        var collisionColor = NativeTeamService.resolveNamedColor(colorName);
        if (!collisionTeam.hasColor() || !collisionColor.equals(collisionTeam.color()))
            collisionTeam.color(collisionColor);
        if (collisionTeam.getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER)
            collisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        if (!collisionTeam.hasEntry(player.getName())) collisionTeam.addEntry(player.getName());
    }

    /**
     * Restores the exact scoreboard team occupied before Ace Race and removes empty temporary
     * teams.
     */
    private void restoreRacerCollisions(@NotNull Player player) {
        player.setCollidable(true);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String originalName = originalScoreboardTeams.remove(player.getUniqueId());
        Team original = originalName == null ? null : scoreboard.getTeam(originalName);
        if (original == null) {
            ChampionshipTeam championshipTeam =
                    plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
            if (championshipTeam != null) original = championshipTeam.getTeam();
        }
        if (original != null) {
            original.addEntry(player.getName());
        } else {
            Team current = scoreboard.getEntryTeam(player.getName());
            if (current != null && current.getName().startsWith(COLLISION_TEAM_PREFIX))
                current.removeEntry(player.getName());
        }
        removeEmptyCollisionTeams(scoreboard);
    }

    private void removeEmptyCollisionTeams(@NotNull Scoreboard scoreboard) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(COLLISION_TEAM_PREFIX) && team.getEntries().isEmpty())
                team.unregister();
        }
    }

    @Override
    public void dispose() {
        mapEditPreview.close();
        launchPads.close();
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        super.dispose();
    }

    private record RacerPair(@NotNull UUID first, @NotNull UUID second) {
        private static @NotNull RacerPair of(@NotNull UUID first, @NotNull UUID second) {
            return first.compareTo(second) <= 0
                    ? new RacerPair(first, second)
                    : new RacerPair(second, first);
        }

        private boolean contains(@NotNull UUID uuid) {
            return first.equals(uuid) || second.equals(uuid);
        }

        private boolean bothIn(@NotNull Set<UUID> uuids) {
            return uuids.contains(first) && uuids.contains(second);
        }
    }

    /**
     * Directional view state: {@code viewer} may temporarily hide {@code target} while riptiding.
     */
    private record RacerView(@NotNull UUID viewer, @NotNull UUID target) {
        private boolean contains(@NotNull UUID uuid) {
            return viewer.equals(uuid) || target.equals(uuid);
        }

        private boolean bothIn(@NotNull Set<UUID> uuids) {
            return uuids.contains(viewer) && uuids.contains(target);
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    /** Keeps racers and spectators inside this course's region in the shared Ace Race world. */
    @Override
    public boolean notInArea(Location location) {
        return location == null
                || getGameConfig().getAreaPos1() == null
                || getGameConfig().getAreaPos2() == null
                || super.notInArea(location);
    }

    @Override
    public AceRaceConfig getGameConfig() {
        return (AceRaceConfig) gameConfig;
    }

    @Override
    public AceRaceHandler getGameHandler() {
        return (AceRaceHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
