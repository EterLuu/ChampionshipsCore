package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class RiptideRushArea extends BaseMultiTeamGameInstance {
    private final Map<UUID, Location> playerSpawnLocations = new ConcurrentHashMap<>();
    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private final RiptideFloorProtection protectedFloorPlayers = new RiptideFloorProtection();
    private int speedShieldNoticeUntil;
    private final Map<UUID, Integer> shieldNoticeUntil = new ConcurrentHashMap<>();
    private final Set<UUID> raftShields = ConcurrentHashMap.newKeySet();
    private final RiptideRoundLedger roundLedger = new RiptideRoundLedger();
    private final Random random = new Random();
    private final String visibilityOwner = "riptiderush:" + UUID.randomUUID();
    private List<RiptideMathRun.Gate> mathStages = List.of();
    private final Map<UUID, RiptideMathRun> mathRuns = new ConcurrentHashMap<>();
    private final Map<UUID, List<RiptideMathRun.Answer>> pendingMathAnswers = new ConcurrentHashMap<>();
    private final Map<UUID, RiptideMathRun.Gate> mathTitles = new ConcurrentHashMap<>();
    private final Map<UUID, RiptideFallCheck> fallChecks = new ConcurrentHashMap<>();
    private final RiptideDeparture departure = new RiptideDeparture();
    private RiptideMathRun spectatorMathRun;
    private List<Integer> colorFloorSteps = List.of();
    private RiptideCourseGeometry geometry;
    private BukkitTask timerTask;
    private BukkitTask movementTask;
    private double movementBudget;
    private int completedSteps;
    private int nextColorFloorStage;
    private int colorFloorTicksRemaining;
    private RiptideColorFloorRun colorFloorRun;
    private RiptideColorFloorPlatform colorFloorPlatform;
    private final Map<UUID, RiptideColorFloorInventory> colorFloorInventories = new java.util.HashMap<>();
    private List<RiptideCoursePlan.Level> floorStages = List.of();
    private List<RiptideCoursePlan.Level> dodgeStages = List.of();
    private int nextDodgeStage;
    private int dodgeTicksRemaining;
    private RiptideDodgeRun dodgeRun;
    private RiptideDodgeEntities dodgeEntities;
    private RiptideCoursePlan coursePlan;
    private long generationEpoch;
    private int onlinePlayersAtStart;
    private int presentationTicks;
    private boolean boostAnnounced;
    private int announcedSpeedStage;
    private boolean finalSprintAnnounced;
    private boolean ending;
    @Getter
    private int timer;

    public RiptideRushArea(ChampionshipsCore plugin, RiptideRushConfig config,
                            boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.RiptideRush, new RiptideRushHandler(plugin), config);
        getGameHandler().setArea(this);
        config.setAreaName(areaName);
        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    public void preloadMap() {
        geometry = null;
        loadPublishedMapOrDraft(World.Environment.NORMAL);
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        addIfPresent(locations, getGameConfig().getStartPoint());
        addIfPresent(locations, getGameConfig().getFinishPoint());
        addIfPresent(locations, getGameConfig().getSpectatorSpawnPoint());
        return locations;
    }

    private static void addIfPresent(List<Location> locations, Location location) {
        if (location != null) locations.add(location);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        startGameIntroduction(this::startFormalPreparation);
    }

    private void startFormalPreparation() {
        try {
            geometry = getGameConfig().resolveGeometry();
            coursePlan = RiptideCoursePlanner.plan(getGameConfig(), getGameConfig().nextCourseSeed());
            resolveGeneratedLevels();
        } catch (RuntimeException exception) {
            logGame(Level.SEVERE, "配置", "木筏赛道无效，已终止本场 | " + exception.getMessage());
            abortAndReset();
            return;
        }

        long epoch = ++generationEpoch;
        logGame(Level.INFO, "随机赛道", "seed=" + coursePlan.seed() + " generator=" + coursePlan.algorithmVersion()
                + " ticks=" + coursePlan.estimatedTicks() + " mathMin=" + getGameConfig().getMinimumOperand()
                + " mathMax=" + getGameConfig().getMaximumOperand() + " plan=" + new com.google.gson.Gson().toJson(coursePlan.logLevels()));
        RiptideCourseGenerator.generateAsync(plugin, getGameConfig(), coursePlan,
                () -> generationEpoch == epoch && getGameStageEnum() == GameStageEnum.PREPARATION)
                .whenComplete((built, error) -> {
                    if (generationEpoch != epoch) return;
                    if (error != null || !Boolean.TRUE.equals(built)) {
                        logGame(Level.SEVERE, "随机赛道", "赛道生成失败，已终止本场 | " + error);
                        abortAndReset();
                    } else {
                        try { finishFormalPreparation(); }
                        catch (RuntimeException failure) {
                            logGame(Level.SEVERE, "随机赛道", "赛道准备失败，已终止本场 | " + failure);
                            abortAndReset();
                        }
                    }
                });
    }

    private void finishFormalPreparation() {
        courseReveal = new RiptideCourseReveal(getGameConfig(), geometry, coursePlan.levels());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        for (UUID uuid : gamePlayers)
            plugin.getVisibilityManager().seeTeammates(uuid, visibilityOwner, "激流勇进参赛者显示同队队友");
        assignAndTeleportSpawns();
        announceGamePreparation(MessageConfig.RIPTIDE_RUSH_START_PREPARATION,
                MessageConfig.RIPTIDE_RUSH_START_PREPARATION_TITLE,
                MessageConfig.RIPTIDE_RUSH_START_PREPARATION_SUBTITLE);
        startFinalCountdown(MessageConfig.GAME_RIPTIDE_RUSH,
                MessageConfig.RIPTIDE_RUSH_GAME_START_TITLE,
                MessageConfig.RIPTIDE_RUSH_GAME_START_SUBTITLE, this::beginGameProgress);
    }

    private void beginGameProgress() {
        passRun = new RiptidePassRun(geometry, coursePlan.levels(), getGameConfig());
        rhythmRun = new RiptideRhythmRun(geometry, coursePlan.levels());
        // The persisted roster owns the round.  Do not derive starters from the currently online
        // subset: a disconnected member must never make the game start with a reduced roster or
        // trigger an online-count failure.  Entity lookups below are only for applying live Bukkit
        // state to players who are present.
        List<UUID> starters = List.copyOf(gamePlayers);
        roundLedger.start(starters);
        onlinePlayersAtStart = starters.size();
        protectedFloorPlayers.clear();
        shieldNoticeUntil.clear();
        raftShields.clear();
        raftShields.addAll(starters);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !eliminatedPlayers.contains(uuid))
                mathRuns.put(uuid, new RiptideMathRun(geometry, mathStages, player.getLocation()));
        }
        spectatorMathRun = new RiptideMathRun(geometry, mathStages, geometry.centerAt(0));

        timerTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            refreshGameBar();
        }, this::endGame);
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        movementTask = scheduler.runTaskTimer(plugin, this::tickCourse, 0L, 1L);
    }

    private void checkFallenPlayers() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || geometry == null) return;
        for (UUID uuid : List.copyOf(gamePlayers)) {
            if (eliminatedPlayers.contains(uuid)) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                eliminateBatch(List.of(uuid), MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED, false);
                continue;
            }
            Location current = player.getLocation();
            if (fallenFromRaft(player, current))
                eliminateBatch(List.of(uuid), MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND, true);
        }
    }

    private boolean fallenFromRaft(Player player, Location location) {
        return fallChecks.computeIfAbsent(player.getUniqueId(), key -> new RiptideFallCheck())
                .sample(geometry, location, completedSteps, getGameConfig().getHorizontalPadding(),
                        getGameConfig().getFallDistance(), player.isInWater() || player.isInLava(),
                        RiptideFallCheck.deckPresent(geometry, location, completedSteps), Bukkit.getCurrentTick());
    }

    private void tickCourse() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        try {
            checkFallenPlayers();
            tickCourseStep();
        } finally {
            if (getGameStageEnum() == GameStageEnum.PROGRESS
                    && shouldEndAfterElimination(onlinePlayersAtStart, getSurvivedPlayerNums())) {
                // endGame performs a final position/math sample before flushing this same batch.
                endGame();
            } else {
                flushEliminations();
            }
        }
    }

    private void flushEliminations() {
        roundLedger.flush().forEach(this::addPlayerPoints);
    }

    private void tickCourseStep() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || geometry == null) return;
        if (courseReveal != null) courseReveal.tick(completedSteps);
        // Drain accepted movement segments, with server-position sampling as a fallback.
        // Evaluate everyone before any elimination can end the round.
        resolvePlayerMathCrossings();
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        boolean waitingToDepart = departure.tick();
        if (waitingToDepart) {
            // Continue player checks and presentation while the raft remains stationary.
        } else if (passRun != null && passRun.active()) {
            var players = gamePlayers.stream().filter(id -> !eliminatedPlayers.contains(id))
                    .map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
            var answers = passRun.tick(players);
            var wrong = new ArrayList<UUID>();
            for (var answer : answers) {
                plugin.getLogger().info("Riptide side math: player=" + answer.player() + ", level=" + answer.level()
                        + ", beat=" + answer.beat() + ", question=" + answer.question().expression()
                        + ", red=" + answer.question().leftAnswer() + ", blue=" + answer.question().rightAnswer()
                        + ", correct=" + answer.correct());
                if (!answer.correct()) wrong.add(answer.player());
                else {
                    var player = Bukkit.getPlayer(answer.player());
                    if (player != null) Utils.sendActionBar(player, MessageConfig.RIPTIDE_RUSH_MATH_CORRECT);
                }
            }
            eliminateBatch(wrong, MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER, true);
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
            if (!passRun.active()) departure.begin();
        } else if (colorFloorTicksRemaining > 0) {
            tickColorFloor();
        } else if (dodgeTicksRemaining > 0) {
            tickDodge();
        } else {
            double speed = currentSpeed();
            movementBudget += speed / 20D;
            while (movementBudget >= 1D && completedSteps < geometry.totalSteps()
                    && getGameStageEnum() == GameStageEnum.PROGRESS) {
                shiftRaftOneBlock();
                completedSteps++;
                if (courseReveal != null) courseReveal.tick(completedSteps);
                movementBudget -= 1D;
                announceSpeedMilestones();
                if (beginReachedColorFloor()) break;
                if (beginReachedDodge()) break;
                if (passRun != null && passRun.beginReached(completedSteps)) break;
            }
        }
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        if (dodgeTicksRemaining == 0 && rhythmRun != null) rhythmRun.tick(completedSteps, currentSpeed(), gamePlayers.stream()
                .filter(id -> !eliminatedPlayers.contains(id)).map(Bukkit::getPlayer).filter(Objects::nonNull).toList());
        for (UUID uuid : List.copyOf(gamePlayers)) {
            if (eliminatedPlayers.contains(uuid)) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                eliminateBatch(List.of(uuid), MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED, false);
            } else {
                if (completedSteps / (double) geometry.totalSteps() >= 0.80D)
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, true));
            }
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        }
        presentationTicks++;
        if ((passRun != null && passRun.active()) || colorFloorTicksRemaining > 0 || dodgeTicksRemaining > 0 || departure.active() || waitingToDepart) {
            mathRuns.values().forEach(RiptideMathRun::suspendPreview);
            if (spectatorMathRun != null) spectatorMathRun.suspendPreview();
        }
        if ((passRun == null || !passRun.active()) && colorFloorTicksRemaining == 0 && dodgeTicksRemaining == 0 && !departure.active() && !waitingToDepart)
            refreshMathTitle();
        if (colorFloorRun == null && speedShieldNoticeUntil > presentationTicks)
            sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK);
        if (colorFloorRun == null && speedShieldNoticeUntil <= presentationTicks) shieldNoticeUntil.forEach((uuid, until) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (until > presentationTicks && player != null) Utils.sendActionBar(player, MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN);
        });
        if (dodgeTicksRemaining > 0) refreshDodgePresentation();
        if (departure.active() || waitingToDepart)
            sendActionBarToAllGamePlayers(departure.active() ? MessageConfig.RIPTIDE_RUSH_DEPARTURE_ACTIONBAR : "");
        if (!departure.active() && !waitingToDepart && completedSteps >= geometry.totalSteps() && colorFloorRun == null && dodgeTicksRemaining == 0 && (passRun == null || !passRun.active())) endGame();
    }

    private double currentSpeed() {
        double progress = geometry == null || geometry.totalSteps() == 0
                ? 0D : completedSteps / (double) geometry.totalSteps();
        return getGameConfig().speedAtProgress(progress);
    }

    private void announceSpeedMilestones() {
        double progress = completedSteps / (double) geometry.totalSteps();
        int speedStage = Math.min(4, (int) Math.floor(progress * 5 + 1e-9));
        if (speedStage > announcedSpeedStage) {
            announcedSpeedStage = speedStage;
            if (speedStage == 2) {
                raftShields.clear();
                speedShieldNoticeUntil = presentationTicks + 40;
                for (UUID uuid : gamePlayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && !eliminatedPlayers.contains(uuid)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
                    }
                }
                sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK);
            } else sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_SPEED_BOOST_SUBTITLE);
        }
        boolean showingQuestion = !mathTitles.isEmpty() || colorFloorRun != null;
        if (!boostAnnounced && progress >= 0.20D) {
            boostAnnounced = true;
            if (!showingQuestion)
                sendTitleToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_SPEED_BOOST_TITLE,
                        MessageConfig.RIPTIDE_RUSH_SPEED_BOOST_SUBTITLE);
            playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1.4F);
        }
        if (!finalSprintAnnounced && progress >= 0.80D) {
            finalSprintAnnounced = true;
            if (!showingQuestion)
                sendTitleToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_FINAL_SPRINT_TITLE,
                        MessageConfig.RIPTIDE_RUSH_FINAL_SPRINT_SUBTITLE);
            playSoundToAllGamePlayers(Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.2F);
        }
    }

    private void shiftRaftOneBlock() {
        RiptideRaftBlocks.move(geometry, completedSteps, completedSteps + 1, trailMaterial());
    }

    private Material trailMaterial() {
        Material material = Material.matchMaterial(getGameConfig().getTrailMaterial());
        return material != null && material.isBlock() ? material : Material.AIR;
    }

    private void resolvePlayerMathCrossings() {
        List<UUID> wrong = new ArrayList<>();
        List<UUID> outside = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            if (eliminatedPlayers.contains(uuid)) continue;
            Player player = Bukkit.getPlayer(uuid);
            RiptideMathRun run = mathRuns.get(uuid);
            if (player == null || run == null) continue;
            List<RiptideMathRun.Answer> answers = pendingMathAnswers.remove(uuid);
            if (answers == null) answers = new ArrayList<>();
            answers.addAll(run.sample(player.getLocation()));
            for (RiptideMathRun.Answer answer : answers) {
                clearMathTitle(uuid);
                var question = answer.gate().question();
                logGame(Level.INFO, "数学过门", "player=" + player.getName()
                        + " level=" + answer.gate().level() + " step=" + answer.gate().step()
                        + " question=" + question.expression() + " left=" + question.leftAnswer()
                        + " right=" + question.rightAnswer() + " lateral=" + answer.lateral()
                        + " y=" + answer.y() + " result=" + answer.result());
                switch (answer.result()) {
                    case CORRECT -> {
                        Utils.sendActionBar(player, MessageConfig.RIPTIDE_RUSH_MATH_CORRECT);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 1.6F);
                    }
                    case WRONG -> wrong.add(uuid);
                    case OUTSIDE_GATE -> outside.add(uuid);
                }
            }
        }
        eliminateBatch(wrong, MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER, true);
        eliminateBatch(outside, MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE, true);
    }

    private boolean beginReachedColorFloor() {
        if (nextColorFloorStage >= colorFloorSteps.size()
                || completedSteps < geometry.stoppedStep(colorFloorSteps.get(nextColorFloorStage))) return false;
        protectedFloorPlayers.clear();
        nextColorFloorStage++;
        clearMathTitle();
        colorFloorTicksRemaining = RiptideColorFloorRun.INTRO_TICKS + RiptideDifficulty.floorTicks(completedSteps, geometry.totalSteps());
        colorFloorPlatform = new RiptideColorFloorPlatform(geometry, completedSteps);
        var stage = floorStages.get(nextColorFloorStage - 1);
        List<Material> authored = stage.template().blueprint() == null ? List.of() : stage.template().blueprint().floorMaterials();
        colorFloorRun = new RiptideColorFloorRun(geometry.raftWidth(), geometry.raftLength(),
                RiptideDifficulty.floorRoundTicks(completedSteps, geometry.totalSteps()), new Random(stage.contentSeed()),
                RiptideColorFloorRun.Theme.valueOf(stage.variant()), authored,
                RiptideDifficulty.floorMaterials(completedSteps, geometry.totalSteps()));
        showColorFloorRound();
        return true;
    }

    private void showColorFloorRound() {
        // The raft stays at the stage entry position for all seven rounds.
        colorFloorPlatform.paint(colorFloorRun.floor());
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || eliminatedPlayers.contains(uuid)) continue;
            player.closeInventory();
            colorFloorInventories.computeIfAbsent(uuid,
                    key -> RiptideColorFloorInventory.capture(player.getInventory()));
            RiptideColorFloorInventory.show(player.getInventory(), new ItemStack(colorFloorRun.target(), 64));
        }
        logGame(Level.INFO, "踩色", "stage=" + nextColorFloorStage + " round=" + colorFloorRun.roundNumber()
                + " theme=" + colorFloorRun.theme() + " pattern=" + colorFloorRun.pattern()
                + " mode=" + (colorFloorRun.presetPattern() ? "preset" : "random")
                + " target=" + colorFloorRun.target() + " ticks=" + colorFloorRun.remainingTicks());
        refreshColorFloorPresentation();
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.4F);
    }

    private void tickColorFloor() {
        colorFloorTicksRemaining--;
        boolean preparing = colorFloorRun.preparing();
        boolean deadline = colorFloorRun.tick();
        if (preparing) {
            if (!colorFloorRun.preparing()) refreshColorFloorPresentation();
            return;
        }
        if (deadline) {
            // Sample the same deadline for all players before elimination can finish the game.
            List<UUID> wrong = new ArrayList<>();
            for (UUID uuid : gamePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || eliminatedPlayers.contains(uuid)) continue;
                Location feet = player.getLocation();
                boolean correct = colorFloorRun.matches(feet, geometry, completedSteps);
                logGame(Level.INFO, "地板判定", "player=" + player.getName() + " stage=" + nextColorFloorStage
                        + " round=" + colorFloorRun.roundNumber() + " target=" + colorFloorRun.target()
                        + " x=" + feet.getX() + " y=" + feet.getY() + " z=" + feet.getZ()
                        + " result=" + (correct ? "CORRECT" : "WRONG"));
                if (!correct) wrong.add(uuid);
            }
            eliminateBatch(wrong, MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR, true);
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
            if (colorFloorRun.advance()) showColorFloorRound();
            else {
                finishColorFloor();
                departure.begin();
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.4F);
            }
        } else if (colorFloorRun.remainingTicks() % 5 == 0) {
            refreshColorFloorPresentation();
            if (colorFloorRun.remainingTicks() <= 60 && colorFloorRun.remainingTicks() % 20 == 0)
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 1.2F);
        }
    }

    private boolean beginReachedDodge() {
        if (nextDodgeStage >= dodgeStages.size()
                || completedSteps < geometry.stoppedStep(dodgeStages.get(nextDodgeStage).step())) return false;
        var stage = dodgeStages.get(nextDodgeStage++);
        clearMathTitle();
        int phase = RiptideDifficulty.stage(completedSteps, geometry.totalSteps());
        dodgeRun = new RiptideDodgeRun(stage.variant(), stage.contentSeed(), geometry.raftWidth(), geometry.raftLength(), phase);
        dodgeEntities = new RiptideDodgeEntities(geometry, completedSteps);
        logGame(Level.INFO, "躲避", "phase=" + phase + " variant=" + stage.variant() + " seed=" + stage.contentSeed()
                + " count=" + dodgeRun.spawns().size() + " speed=" + dodgeRun.spawns().getFirst().speed());
        dodgeTicksRemaining = RiptideDodgeRun.DURATION_TICKS;
        sendTitleToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_DODGE_TITLE.replace("%direction%", dodgeRun.direction().arrivalSide()), "");
        refreshDodgePresentation();
        return true;
    }

    private void tickDodge() {
        if (dodgeRun == null) { dodgeTicksRemaining = 0; return; }
        Set<UUID> hit = new java.util.HashSet<>();
        for (BoundingBox collision : dodgeEntities.tick(dodgeRun)) {
            for (UUID uuid : List.copyOf(gamePlayers)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && !eliminatedPlayers.contains(uuid)
                        && collision.overlaps(player.getBoundingBox())) hit.add(uuid);
            }
        }
        if (!hit.isEmpty()) eliminateBatch(new ArrayList<>(hit), MessageConfig.RIPTIDE_RUSH_REASON_DODGE, true);
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        dodgeTicksRemaining = RiptideDodgeRun.DURATION_TICKS - dodgeRun.tickNumber();
        if (dodgeRun.complete()) {
            clearDodgeRun();
            departure.begin();
        }
    }

    private void refreshDodgePresentation() {
        if (dodgeTicksRemaining <= 0) return;
        String seconds = String.format(java.util.Locale.ROOT, "%.1f", dodgeTicksRemaining / 20D);
        sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_DODGE_ACTIONBAR.replace("%seconds%", seconds));
    }

    private void clearDodgeRun() {
        if (dodgeEntities != null) dodgeEntities.clear();
        dodgeEntities = null;
        dodgeRun = null;
        dodgeTicksRemaining = 0;
    }

    private void refreshColorFloorPresentation() {
        String seconds = String.format(java.util.Locale.ROOT, "%.1f", colorFloorRun.remainingTicks() / 20D);
        Component title = LegacyText.component(colorFloorRun.preparing() ? MessageConfig.RIPTIDE_RUSH_FLOOR_INTRO_TITLE
                : MessageConfig.RIPTIDE_RUSH_FLOOR_TITLE.replace("%seconds%", seconds));
        Component actionbar = LegacyText.component(MessageConfig.RIPTIDE_RUSH_FLOOR_ACTIONBAR
                .replace("%round%", String.valueOf(colorFloorRun.roundNumber()))
                .replace("%rounds%", String.valueOf(colorFloorRun.roundCount())))
                .replaceText(builder -> builder.matchLiteral("%block%")
                        .replacement(Component.translatable(colorFloorRun.target().translationKey())));
        Title display = Title.title(title, Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(colorFloorRun.preparing() ? 1500 : 350), Duration.ZERO));
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !eliminatedPlayers.contains(uuid)) {
                player.showTitle(display);
                player.sendActionBar(speedShieldNoticeUntil > presentationTicks
                        ? LegacyText.component(MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK + " §7| ").append(actionbar)
                        : shieldNoticeUntil.getOrDefault(uuid, 0) > presentationTicks
                        ? LegacyText.component(MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN + " §7| ").append(actionbar) : actionbar);
            }
        }
        for (var spectator : getOnlineCCSpectators()) {
            Player player = spectator.getPlayer();
            if (player != null) { player.showTitle(display); player.sendActionBar(actionbar); }
        }
    }

    public boolean isColorFloorParticipant(Player player) {
        return colorFloorRun != null && gamePlayers.contains(player.getUniqueId())
                && !eliminatedPlayers.contains(player.getUniqueId());
    }

    private void restoreColorFloorInventory(Player player) {
        var saved = colorFloorInventories.remove(player.getUniqueId());
        if (saved != null) {
            player.closeInventory();
            saved.restore(player.getInventory());
            player.resetTitle();
            player.sendActionBar(Component.empty());
        }
    }

    private void finishColorFloor() {
        protectedFloorPlayers.clear();
        boolean active = colorFloorRun != null;
        colorFloorRun = null;
        colorFloorTicksRemaining = 0;
        if (colorFloorPlatform != null) {
            if (Bukkit.getWorld(getWorldName()) != null) colorFloorPlatform.restore();
            colorFloorPlatform = null;
        }
        for (UUID uuid : List.copyOf(colorFloorInventories.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) restoreColorFloorInventory(player);
        }
        colorFloorInventories.clear();
        if (active) for (var spectator : getOnlineCCSpectators()) {
            Player player = spectator.getPlayer();
            if (player != null) { player.resetTitle(); player.sendActionBar(Component.empty()); }
        }
    }

    private void refreshGameBar() {
        if (geometry == null || getGameStageEnum() != GameStageEnum.PROGRESS) return;
        String title = MessageConfig.RIPTIDE_RUSH_BOSS_BAR
                .replace("%alive%", String.valueOf(getSurvivedPlayerNums()))
                .replace("%total%", String.valueOf(gamePlayers.size()))
                .replace("%time%", Utils.formatMinutesSeconds(timer))
                .replace("%speed%", String.format(java.util.Locale.ROOT, "%.1f", currentSpeed()));
        updateGameTimerBossBar(title, timer, getGameConfig().getTimer());
    }

    private void refreshMathTitle() {
        boolean sideSweepImminent = passRun != null && passRun.startsWithin(completedSteps, currentSpeed());
        if (sideSweepImminent) clearMathTitle();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            RiptideMathRun run = mathRuns.get(uuid);
            if (player == null || run == null || eliminatedPlayers.contains(uuid)) continue;
            if (sideSweepImminent) continue;
            updateMathTitle(player, run.preview(player.getLocation(), currentSpeed(),
                    getGameConfig().getMathPreviewBlocks(), presentationTicks, colorFloorTicksRemaining > 0));
        }
        if (spectatorMathRun == null) return;
        Location center = geometry.centerAt(completedSteps);
        spectatorMathRun.followCourse(center);
        var preview = spectatorMathRun.preview(center, currentSpeed(), getGameConfig().getMathPreviewBlocks(),
                presentationTicks, colorFloorTicksRemaining > 0);
        Set<UUID> recipients = new java.util.HashSet<>();
        for (var spectator : getOnlineCCSpectators()) {
            Player player = spectator.getPlayer();
            if (player != null) { recipients.add(player.getUniqueId()); updateMathTitle(player, preview); }
        }
        for (UUID uuid : List.copyOf(mathTitles.keySet())) {
            if ((!gamePlayers.contains(uuid) || eliminatedPlayers.contains(uuid)) && !recipients.contains(uuid))
                clearMathTitle(uuid);
        }
    }

    private void updateMathTitle(Player player, RiptideMathRun.Gate preview) {
        UUID uuid = player.getUniqueId();
        if (preview == null) { clearMathTitle(uuid); return; }
        if (preview.equals(mathTitles.get(uuid)) && presentationTicks % 10 != 0) return;
        mathTitles.put(uuid, preview);
        var display = RiptideQuestionDisplay.of(preview.question(), preview.level());
        player.sendTitle(display.title(), display.subtitle(), 0, 11, 0);
    }

    private void clearMathTitle() {
        for (UUID uuid : List.copyOf(mathTitles.keySet())) clearMathTitle(uuid);
    }

    private void clearMathTitle(UUID uuid) {
        if (mathTitles.remove(uuid) == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) player.resetTitle();
    }

    /** MONITOR only records; all feedback and elimination happen in the following course tick. */
    public void recordMathMovement(Player player, Location from, Location to) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || eliminatedPlayers.contains(player.getUniqueId())) return;
        RiptideMathRun run = mathRuns.get(player.getUniqueId());
        if (run == null) return;
        List<RiptideMathRun.Answer> answers = new ArrayList<>(run.sample(from));
        answers.addAll(run.sample(to));
        if (!answers.isEmpty()) pendingMathAnswers.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>())
                .addAll(answers);
    }

    private void announceShieldBroken(Player player) {
        shieldNoticeUntil.put(player.getUniqueId(), presentationTicks + 40);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1F, 1F);
        Utils.sendActionBar(player, MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN);
    }

    private synchronized void eliminateBatch(@NotNull Collection<UUID> candidates,
                                             @NotNull String reason, boolean announce) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        List<UUID> eligible = candidates.stream()
                .filter(gamePlayers::contains).filter(uuid -> !eliminatedPlayers.contains(uuid))
                .filter(uuid -> !Objects.equals(reason, MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR) || !protectedFloorPlayers.protects(uuid))
                .distinct().toList();
        if (geometry != null && completedSteps / (double) geometry.totalSteps() < 0.40D
                && !Objects.equals(reason, MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED)
                && !Objects.equals(reason, MessageConfig.RIPTIDE_RUSH_REASON_FELL)) {
            List<UUID> rescued = new ArrayList<>();
            for (UUID uuid : eligible) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline() && raftShields.remove(uuid)) {
                    boolean fallen = Objects.equals(reason, MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND);
                    if (fallen && !player.teleport(geometry.centerAt(completedSteps).add(0D, 1D, 0D))) {
                        raftShields.add(uuid);
                        continue;
                    }
                    rescued.add(uuid);
                    if (Objects.equals(reason, MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR)) protectedFloorPlayers.grant(uuid);
                    if (fallen) {
                        player.setFallDistance(0F);
                        player.setVelocity(new Vector());
                    }
                    var run = mathRuns.get(uuid);
                    if (run != null) run.recoverAt(player.getLocation());
                    pendingMathAnswers.remove(uuid);
                    fallChecks.remove(uuid);
                    clearMathTitle(uuid);
                    announceShieldBroken(player);
                }
            }
            eligible = eligible.stream().filter(uuid -> !rescued.contains(uuid)).toList();
        }
        List<UUID> newlyEliminated = eligible;
        if (newlyEliminated.isEmpty()) return;
        raftShields.removeAll(newlyEliminated);
        eliminatedPlayers.addAll(newlyEliminated);
        roundLedger.eliminate(newlyEliminated);
        for (UUID uuid : newlyEliminated) {
            clearMathTitle(uuid);
            mathRuns.remove(uuid);
            pendingMathAnswers.remove(uuid);
            fallChecks.remove(uuid);
            plugin.getVisibilityManager().release(uuid, visibilityOwner);
            Player player = Bukkit.getPlayer(uuid);
            if (announce) {
                String playerName = player == null
                        ? Objects.toString(playerManager.getPlayerName(uuid), uuid.toString().substring(0, 8))
                        : Utils.formatPlayerName(player);
                sendMessageToAllGamePlayers(RiptideEliminationMessages.choose(reason, random)
                        .replace("%player%", playerName).replace("%reason%", reason));
            }
            if (player != null && !player.isDead()) {
                restoreColorFloorInventory(player);
                player.setVelocity(new Vector());
                player.teleport(getSpectatorSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
                plugin.getVisibilityManager().reconcilePlayer(uuid);
            }
        }
    }

    static boolean shouldEndAfterElimination(int onlinePlayersAtStart, int alive) {
        // A round is scored independently.  The last survivor must keep
        // racing until they finish or are actually eliminated; ending when
        // alive == 1 would award a premature placement and cut off the
        // survivor's round score.
        return alive == 0;
    }

    @Override
    public synchronized void endGame() {
        if (ending || getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        ending = true;
        // Finish accepted movement and offline/fall checks before awarding a timed-out round.
        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            checkFallenPlayers();
            resolvePlayerMathCrossings();
        }
        flushEliminations();
        cancelTasks();
        if (isSettlementAllowed()) calculatePoints();
        setGameStageEnum(GameStageEnum.END);
        announceGameEnd(MessageConfig.RIPTIDE_RUSH_GAME_END_TITLE,
                MessageConfig.RIPTIDE_RUSH_GAME_END_SUBTITLE);
        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        publishGameEndEvent(new SingleGameEndEvent(this, List.copyOf(gameTeams)));
        finishPostGameAfterEndEvent();
    }

    private void calculatePoints() {
        roundLedger.placementBonuses().forEach(this::addPlayerPoints);
        Collection<UUID> winners = roundLedger.winners();
        for (UUID winner : winners) {
            sendMessageToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_WINNER
                    .replace("%player%", Objects.toString(playerManager.getPlayerName(winner),
                            winner.toString().substring(0, 8))));
        }
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
    }

    @Override
    public void resetArea() {
        cancelTasks();
        playerSpawnLocations.clear();
        eliminatedPlayers.clear();
        protectedFloorPlayers.clear();
        shieldNoticeUntil.clear();
        raftShields.clear();
        roundLedger.clear();
        fallChecks.clear();
        departure.clear();
        mathStages = List.of();
        colorFloorSteps = List.of();
        floorStages = List.of();
        dodgeStages = List.of();
        coursePlan = null;
        geometry = null;
        movementBudget = 0D;
        completedSteps = 0;
        nextColorFloorStage = 0;
        nextDodgeStage = 0;
        colorFloorTicksRemaining = 0;
        clearDodgeRun();
        onlinePlayersAtStart = 0;
        presentationTicks = 0;
        speedShieldNoticeUntil = 0;
        boostAnnounced = false;
        announcedSpeedStage = 0;
        finalSprintAnnounced = false;
        ending = false;
        preloadMap();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> abortAndReset() {
        if (!Bukkit.isPrimaryThread()) return super.abortAndReset();
        if (isEventRun()) plugin.getScheduleManager().endGameSchedule(GameTypeEnum.RiptideRush);
        return super.abortAndReset();
    }

    @Override
    public void resetGame() {
        cancelTasks();
        cancelIntroduction();
        cancelFinalCountdown();
        playerPoints.clear();
        resetBaseArea();
    }

    private RiptidePassRun passRun;
    private RiptideRhythmRun rhythmRun;
    private RiptideCourseReveal courseReveal;

    private void cancelTasks() {
        if (passRun != null) { passRun.close(); passRun = null; }
        if (rhythmRun != null) { rhythmRun.close(); rhythmRun = null; }
        if (courseReveal != null) { courseReveal.close(); courseReveal = null; }
        generationEpoch++;
        RiptideCourseGenerator.cancel(Bukkit.getWorld(getWorldName()));
        finishColorFloor();
        clearDodgeRun();
        clearMathTitle();
        mathRuns.clear();
        pendingMathAnswers.clear();
        fallChecks.clear();
        departure.clear();
        spectatorMathRun = null;
        plugin.getVisibilityManager().releaseAll(gamePlayers, visibilityOwner);
        for (UUID uuid : new ArrayList<>(spawnCollisionStates.keySet())) restoreSpawnCollision(uuid);
        if (timerTask != null) timerTask.cancel();
        if (movementTask != null) movementTask.cancel();
        timerTask = null;
        movementTask = null;
    }

    @Override
    public void dispose() {
        cancelTasks();
        super.dispose();
    }

    private void resolveGeneratedLevels() {
        List<RiptideMathRun.Gate> resolvedMath = new ArrayList<>();
        List<Integer> resolvedFloors = new ArrayList<>();
        for (var level : coursePlan.levels()) {
            if (level.type() == RiptideLevelType.MATH && !level.isSideSweep()) {
                resolvedMath.add(new RiptideMathRun.Gate(level.number(), level.step(), level.question(
                        getGameConfig().getMinimumOperand(), getGameConfig().getMaximumOperand(),
                        RiptideDifficulty.stage(level.step(), geometry.totalSteps()))));
            } else if (level.colorFloor()) {
                resolvedFloors.add(level.step());
            }
        }
        floorStages = coursePlan.levels().stream().filter(RiptideCoursePlan.Level::colorFloor).toList();
        dodgeStages = coursePlan.levels().stream().filter(level -> level.type() == RiptideLevelType.DODGE).toList();
        mathStages = List.copyOf(resolvedMath);
        colorFloorSteps = List.copyOf(resolvedFloors);
    }

    private final Map<UUID, Boolean> spawnCollisionStates = new java.util.HashMap<>();

    private void disableSpawnCollision(Player player) {
        spawnCollisionStates.putIfAbsent(player.getUniqueId(), player.isCollidable());
        player.setCollidable(false);
    }

    private void restoreSpawnCollision(UUID uuid) {
        Boolean previous = spawnCollisionStates.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (previous != null && player != null) player.setCollidable(previous);
    }

    @Override
    public void sanitizeParticipantForLobby(@NotNull Player player, boolean teleport) {
        super.sanitizeParticipantForLobby(player, teleport);
        restoreSpawnCollision(player.getUniqueId());
    }

    private void assignAndTeleportSpawns() {
        for (UUID uuid : gamePlayers) {
            Location location = geometry.centerAt(0);
            playerSpawnLocations.put(uuid, location);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                disableSpawnCollision(player);
                player.setVelocity(new Vector());
                player.setFallDistance(0F);
                player.teleport(location);
            }
        }
    }

    public void teleportPlayerToSpawnPoint(@NotNull Player player) {
        Location target = isIntroductionPhase()
                ? getPreparationTeleportLocation(getSpectatorSpawnLocation())
                : playerSpawnLocations.getOrDefault(player.getUniqueId(), getGameConfig().getStartPoint());
        if (target != null) {
            if (!isIntroductionPhase()) disableSpawnCollision(player);
            player.teleport(target);
        }
        player.setFallDistance(0F);
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        GameStageEnum stage = getGameStageEnum();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        if (stage == GameStageEnum.PROGRESS)
            eliminateBatch(List.of(player.getUniqueId()), MessageConfig.RIPTIDE_RUSH_REASON_FELL, true);
        long epoch = generationEpoch;
        scheduler.runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.spigot().respawn();
            if (generationEpoch != epoch) return;
            GameStageEnum current = getGameStageEnum();
            if (current == GameStageEnum.PREPARATION || current == GameStageEnum.COUNTDOWN) {
                teleportPlayerToSpawnPoint(player);
                player.setGameMode(GameMode.ADVENTURE);
            } else if (current == GameStageEnum.PROGRESS) {
                player.teleport(getSpectatorSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
            } else {
                sanitizeParticipantForLobby(player, true);
            }
        });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        restoreColorFloorInventory(event.getPlayer());
        restoreSpawnCollision(event.getPlayer().getUniqueId());
        plugin.getVisibilityManager().release(event.getPlayer().getUniqueId(), visibilityOwner);
        if (getGameStageEnum() == GameStageEnum.PROGRESS && !notAreaPlayer(event.getPlayer()))
            eliminateBatch(List.of(event.getPlayer().getUniqueId()), MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED, true);
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.PROGRESS || eliminatedPlayers.contains(player.getUniqueId())) {
            // Reconnect is never an admission path, even if a quit callback was missed.
            if (stage == GameStageEnum.PROGRESS && !eliminatedPlayers.contains(player.getUniqueId()))
                eliminateBatch(List.of(player.getUniqueId()), MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED, false);
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR);
        } else if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN) {
            teleportPlayerToSpawnPoint(player);
            player.setGameMode(GameMode.ADVENTURE);
        } else {
            player.teleport(CCConfig.LOBBY_LOCATION);
            player.setGameMode(GameMode.ADVENTURE);
        }
        if ((stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN)
                && !eliminatedPlayers.contains(player.getUniqueId()))
            plugin.getVisibilityManager().seeTeammates(player.getUniqueId(), visibilityOwner, "激流勇进参赛者显示同队队友");
        else
            plugin.getVisibilityManager().release(player.getUniqueId(), visibilityOwner);
        plugin.getVisibilityManager().reconcilePlayer(player.getUniqueId());
    }

    public boolean isEliminated(@NotNull UUID uuid) {
        return eliminatedPlayers.contains(uuid);
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - eliminatedPlayers.size();
    }

    public int getCourseProgressPercent() {
        return geometry == null ? 0 : (int) Math.clamp(100D * completedSteps / geometry.totalSteps(), 0D, 100D);
    }

    public String getColorFloorRoundProgress() {
        return colorFloorRun == null ? "-" : colorFloorRun.roundNumber() + "/" + colorFloorRun.roundCount();
    }

    /** Reads the existing runtime state; sidebar refreshes must never regenerate or validate a course. */
    public String getCurrentChallengeKey() {
        if (getGameStageEnum() == GameStageEnum.END) return "ended";
        if (getGameStageEnum() != GameStageEnum.PROGRESS || geometry == null) return "waiting";
        if (departure.active()) return "departing";
        if (passRun != null && passRun.active()) return passRun.sideMathActive() ? "side-math" : "side-sweep";
        if (colorFloorRun != null) return "color-floor";
        if (dodgeTicksRemaining > 0) return "dodge";
        if (!mathTitles.isEmpty()) return "math";
        if (coursePlan != null) {
            for (var level : coursePlan.levels()) {
                if (!level.stopsRaft() && completedSteps + geometry.halfLength() >= level.step()
                        && completedSteps <= level.step() + level.extent() + geometry.halfLength()) {
                    return level.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                }
            }
        }
        return "moving";
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location location = getGameConfig().getSpectatorSpawnPoint();
        if (location != null) return location;
        World world = Bukkit.getWorld(getWorldName());
        if (world != null) return world.getSpawnLocation();
        return CCConfig.LOBBY_LOCATION;
    }

    @Override
    public Location getAdminTeleportLocation() {
        Location start = getGameConfig().getStartPoint();
        return start == null ? getSpectatorSpawnLocation() : start;
    }

    @Override
    public RiptideRushConfig getGameConfig() {
        return (RiptideRushConfig) gameConfig;
    }

    @Override
    public RiptideRushHandler getGameHandler() {
        return (RiptideRushHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }


}
