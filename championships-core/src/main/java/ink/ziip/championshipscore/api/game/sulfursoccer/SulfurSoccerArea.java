package ink.ziip.championshipscore.api.game.sulfursoccer;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.SulfurCube;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Level;

/** A timed first-to-N finale, using native cube physics and alternating penalties on a tie. */
public final class SulfurSoccerArea extends BasePairedGameInstance {
    static final int WARMUP_SECONDS = 180;
    static final int PEARL_COOLDOWN_TICKS = 160;
    private final NamespacedKey ballKey;
    private final Map<UUID, Location> spawns = new LinkedHashMap<>();
    private final Map<UUID, Integer> pearlReadyTicks = new HashMap<>();
    private final Map<UUID, EnderPearl> pearls = new HashMap<>();
    private SulfurSoccerMatch match;
    private SulfurSoccerShootout shootout;
    private SulfurSoccerPenaltyLayout rightPenalty;
    private SulfurSoccerPenaltyLayout leftPenalty;
    private final SulfurSoccerPenaltyBarrier penaltyBarrier = new SulfurSoccerPenaltyBarrier();
    private int keeperSlot = 1;
    private SulfurCube ball;
    private Vector previousBallCenter;
    private BoundingBox field;
    private BoundingBox rightGoal;
    private BoundingBox leftGoal;
    private BukkitTask tickTask;
    private BukkitTask kickoffTask;
    private boolean paused;
    private Location pausedBallLocation;
    private Vector pausedBallVelocity;
    private int ticks;
    private ChampionshipTeam champion;
    private boolean openingKickoff;
    private double pitchFloorY;
    private long pearlGeneration;
    /** Original smooth-quartz goal blocks, restored when this match leaves the field. */
    private final Map<Vector, Material> originalGoalBlocks = new LinkedHashMap<>();

    public SulfurSoccerArea(ChampionshipsCore plugin, SulfurSoccerConfig config, boolean firstTime, String name) {
        super(plugin, GameTypeEnum.SulfurSoccer, new SulfurSoccerHandler(plugin), config);
        ballKey = new NamespacedKey(plugin, "sulfursoccer_ball");
        getGameHandler().setArea(this);
        config.setAreaName(name);
        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    public void preloadMap() { loadPublishedMapOrDraft(World.Environment.NORMAL); }
    @Override public SulfurSoccerConfig getGameConfig() { return (SulfurSoccerConfig) gameConfig; }
    @Override public SulfurSoccerHandler getGameHandler() { return (SulfurSoccerHandler) gameHandler; }
    @Override public String getWorldName() { return getGameConfig().getConfiguredWorld(); }
    @Override public Location getSpectatorSpawnLocation() { return getGameConfig().bind(getGameConfig().getSpectatorSpawnPoint()); }
    @Override public int getTimer() {
        return match == null ? 0 : isWarmingUp() ? match.warmupSeconds()
                : isShootout() ? shootout.secondsRemaining() : match.regulationSeconds();
    }
    public int getRightGoals() { return match == null ? 0 : match.goals(SulfurSoccerSide.RIGHT); }
    public int getLeftGoals() { return match == null ? 0 : match.goals(SulfurSoccerSide.LEFT); }
    public boolean isPaused() { return paused; }
    public boolean isWarmingUp() { return match != null && match.warmingUp(); }
    public boolean isShootout() { return shootout != null && getGameStageEnum() == GameStageEnum.PROGRESS; }
    public boolean isPlayPhase() {
        return getGameStageEnum() == GameStageEnum.PROGRESS
                || getGameStageEnum() == GameStageEnum.PREPARATION && isWarmingUp();
    }
    public String getStateText() {
        if (getGameStageEnum() == GameStageEnum.END) return state(MessageConfig.SULFUR_SOCCER_STATE_END, "比赛结束");
        if (paused) return state(MessageConfig.SULFUR_SOCCER_STATE_PAUSED, "等待选手重连");
        if (isWarmingUp()) return scoreText(state(MessageConfig.SULFUR_SOCCER_STATE_WARMUP, "试踢 %time%（不计分）").replace("%time%", warmupTime()));
        if (isShootout()) {
            String phase = shootout.preparing() ? "准备" : shootout.struck() ? "判定" : "射门";
            return penaltyText(state(MessageConfig.SULFUR_SOCCER_STATE_SHOOTOUT, "点球 %right_penalties%:%left_penalties% 第 %round% 轮 %phase% %seconds% 秒")).replace("%phase%", phase);
        }
        return scoreText(state(MessageConfig.SULFUR_SOCCER_STATE_REGULATION, "剩余 %time%"));
    }
    private static String state(String configured, String fallback) { return configured == null ? fallback : configured; }
    public ChampionshipTeam getChampion() { return champion; }

    /** Force a valid finalist to win when a referee must settle the finale. */
    public boolean forceChampion(ChampionshipTeam team) {
        if (team == null || getGameStageEnum() == GameStageEnum.WAITING
                || getGameStageEnum() == GameStageEnum.END
                || (!team.equals(rightChampionshipTeam) && !team.equals(leftChampionshipTeam))) return false;
        champion = team;
        endGame();
        return true;
    }

    /** Administrative controls shared by finale commands. */
    public boolean pauseMatch() {
        if (match == null || getGameStageEnum() != GameStageEnum.PROGRESS || paused) return false;
        setPaused(true);
        return true;
    }

    public boolean resumeMatch() {
        if (match == null || getGameStageEnum() != GameStageEnum.PROGRESS || !paused) return false;
        setPaused(false);
        return true;
    }

    public boolean restartCurrentRound() {
        if (match == null || getGameStageEnum() != GameStageEnum.PROGRESS || isShootout()) return false;
        prepareKickoff(true);
        return true;
    }

    @Override public boolean tryStartGame(ChampionshipTeam right, ChampionshipTeam left) {
        if (getGameStageEnum() != GameStageEnum.WAITING || !readyTeam(right) || !readyTeam(left)) return false;
        try {
            getGameConfig().validate();
            concreteMaterial(right.getColorName());
            concreteMaterial(left.getColorName());
            field = SulfurSoccerGeometry.box(getGameConfig().getAreaPos1(), getGameConfig().getAreaPos2());
            rightGoal = SulfurSoccerGeometry.box(getGameConfig().getRightGoalPos1(), getGameConfig().getRightGoalPos2());
            leftGoal = SulfurSoccerGeometry.box(getGameConfig().getLeftGoalPos1(), getGameConfig().getLeftGoalPos2());
        } catch (IllegalArgumentException failure) {
            logGame(Level.WARNING, "启动", failure.getMessage());
            return false;
        }
        return super.tryStartGame(right, left);
    }

    private static boolean readyTeam(ChampionshipTeam team) {
        // The persisted roster is authoritative. Offline finalists are restored when they rejoin;
        // checking Bukkit's current online subset here can abort an otherwise valid finale start.
        return team != null && !team.getMembers().isEmpty() && team.getMembers().size() <= 4;
    }

    @Override protected Collection<Location> getStartPreloadLocations() {
        List<Location> points = new ArrayList<>();
        points.add(getSpectatorSpawnLocation());
        points.add(getGameConfig().bind(getGameConfig().getBallSpawnPoint()));
        for (String raw : getGameConfig().getRightSpawnPoints()) points.add(getGameConfig().bind(getGameConfig().parseSpawn(raw)));
        for (String raw : getGameConfig().getLeftSpawnPoints()) points.add(getGameConfig().bind(getGameConfig().parseSpawn(raw)));
        Set<Long> colorChunks = new HashSet<>();
        World world = Bukkit.getWorld(getWorldName());
        // Keeper markers may be outside the old four landing chunks. Warm the pitch before scanning it.
        for (int x = ((int) Math.floor(field.getMinX())) >> 4; x <= ((int) Math.ceil(field.getMaxX()) - 1) >> 4; x++)
            for (int z = ((int) Math.floor(field.getMinZ())) >> 4; z <= ((int) Math.ceil(field.getMaxZ()) - 1) >> 4; z++)
                points.add(new Location(world, x * 16 + 8, getGameConfig().getBallSpawnPoint().getY(), z * 16 + 8));
        for (List<String> blocks : List.of(getGameConfig().getRightTeamColorBlocks(), getGameConfig().getLeftTeamColorBlocks())) {
            for (String raw : blocks) {
                Vector block = SulfurSoccerConfig.parseColorBlock(raw);
                int x = block.getBlockX() >> 4, z = block.getBlockZ() >> 4;
                if (colorChunks.add(((long) x << 32) | (z & 0xffffffffL)))
                    points.add(new Location(world, x * 16 + 8, block.getY(), z * 16 + 8));
            }
        }
        return points;
    }

    @Override public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        World world = Bukkit.getWorld(getWorldName());
        SulfurSoccerSpawns.Layout layout;
        try {
            layout = SulfurSoccerSpawns.resolve(world, getGameConfig());
        } catch (IllegalArgumentException failure) {
            logGame(Level.WARNING, "出生点", failure.getMessage());
            sendMessageToAllGamePlayers(MessageConfig.SULFUR_SOCCER_START_FAILED + " " + failure.getMessage());
            abortAndReset();
            return;
        }
        World stadium = Bukkit.getWorld(getWorldName());
        applyTeamColors(stadium, getGameConfig(),
                rightChampionshipTeam.getColorName(), leftChampionshipTeam.getColorName());
        rememberGoalColors(stadium, rightGoal, concreteMaterial(rightChampionshipTeam.getColorName()));
        rememberGoalColors(stadium, leftGoal, concreteMaterial(leftChampionshipTeam.getColorName()));
        champion = null;
        openingKickoff = true;
        pitchFloorY = layout.floorY();
        match = new SulfurSoccerMatch(getGameConfig().getGoalsToWin());
        shootout = null;
        try {
            rightPenalty = SulfurSoccerPenaltyLayout.resolve(field, rightGoal, leftGoal, pitchFloorY);
            leftPenalty = SulfurSoccerPenaltyLayout.resolve(field, leftGoal, rightGoal, pitchFloorY);
            validatePenaltySpace(world, rightPenalty);
            validatePenaltySpace(world, leftPenalty);
        } catch (IllegalArgumentException failure) {
            logGame(Level.WARNING, "点球场地", failure.getMessage());
            sendMessageToAllGamePlayers(MessageConfig.SULFUR_SOCCER_START_FAILED + " " + failure.getMessage());
            abortAndReset();
            return;
        }
        assignSpawns(rightChampionshipTeam, layout.right());
        assignSpawns(leftChampionshipTeam, layout.left());
        startGameIntroduction(() -> {
            if (isEventRun()) beginWarmup();
            else prepareKickoff();
        });
    }

    private static Material concreteMaterial(String colorName) {
        Material material = colorName == null ? null : Material.getMaterial(colorName.toUpperCase(Locale.ROOT) + "_CONCRETE");
        if (material == null) throw new IllegalArgumentException("无法解析参赛队伍的混凝土颜色");
        return material;
    }

    /** Explicit accents remain owned by their side even when both teams share or swap colours. */
    static int applyTeamColors(World world, SulfurSoccerConfig config, String rightColor, String leftColor) {
        Material right = concreteMaterial(rightColor), left = concreteMaterial(leftColor);
        int changed = 0;
        for (SulfurSoccerSide side : SulfurSoccerSide.values()) {
            List<String> blocks = side == SulfurSoccerSide.RIGHT ? config.getRightTeamColorBlocks() : config.getLeftTeamColorBlocks();
            Material material = side == SulfurSoccerSide.RIGHT ? right : left;
            for (String raw : blocks) {
                Vector point = SulfurSoccerConfig.parseColorBlock(raw);
                org.bukkit.block.Block block = world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ());
                if (block.getType().name().endsWith("_CONCRETE") && block.getType() != material) {
                    block.setType(material, false);
                    changed++;
                }
            }
        }
        return changed;
    }

    /**
     * Recolours only the smooth-quartz shell immediately around a goal's configured empty volume.
     * The returned map records the original material so a later match can restore the template.
     */
    static Map<Vector, Material> applyGoalColors(World world, BoundingBox goal, Material concrete) {
        if (world == null || goal == null || concrete == null || !concrete.name().endsWith("_CONCRETE"))
            throw new IllegalArgumentException("球门换色需要有效的世界、球门选区和混凝土颜色");
        Map<Vector, Material> original = new LinkedHashMap<>();
        BoundingBox shell = goal.clone().expand(1.0, 1.0, 1.0);
        int minX = (int) Math.floor(shell.getMinX());
        int minY = (int) Math.floor(shell.getMinY());
        int minZ = (int) Math.floor(shell.getMinZ());
        int maxX = (int) Math.ceil(shell.getMaxX());
        int maxY = (int) Math.ceil(shell.getMaxY());
        int maxZ = (int) Math.ceil(shell.getMaxZ());
        for (int x = minX; x < maxX; x++) for (int y = minY; y < maxY; y++) for (int z = minZ; z < maxZ; z++) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            if (block.getType() != Material.SMOOTH_QUARTZ) continue;
            Vector point = new Vector(x, y, z);
            original.put(point, Material.SMOOTH_QUARTZ);
            block.setType(concrete, false);
        }
        return original;
    }

    static void restoreGoalColors(World world, Map<Vector, Material> original) {
        if (world == null || original == null) return;
        for (Map.Entry<Vector, Material> entry : original.entrySet()) {
            Vector point = entry.getKey();
            world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()).setType(entry.getValue(), false);
        }
    }

    private void rememberGoalColors(World world, BoundingBox goal, Material concrete) {
        originalGoalBlocks.putAll(applyGoalColors(world, goal, concrete));
    }

    private void assignSpawns(ChampionshipTeam team, List<Location> points) {
        int index = 0;
        for (UUID player : team.getMembers())
            spawns.put(player, points.get(index++).clone());
    }

    private List<Player> players() {
        return getParticipantUniqueIds().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private void prepareKickoff() {
        prepareKickoff(false);
    }

    private void prepareKickoff(boolean showGoal) {
        match.stopPlay();
        if (getGameStageEnum() == GameStageEnum.PROGRESS) setGameStageEnum(GameStageEnum.STOPPING);
        removeBall();
        removePearls();
        resetPlayerHealthFoodEffectLevelInventory();
        for (Player player : players()) restorePlayer(player);
        updateScore();
        Runnable countdown = () -> startFinalCountdown(getGameConfig().getKickoffCountdown(), MessageConfig.GAME_SULFUR_SOCCER,
                openingKickoff ? MessageConfig.SULFUR_SOCCER_OPENING_TITLE : MessageConfig.SULFUR_SOCCER_RESTART_TITLE, "",
                this::beginPlay);
        if (showGoal) {
            setGameStageEnum(GameStageEnum.COUNTDOWN);
            kickoffTask = scheduler.runTaskLater(plugin, () -> {
                kickoffTask = null;
                if (getGameStageEnum() == GameStageEnum.COUNTDOWN) countdown.run();
            }, 40L);
        } else countdown.run();
    }

    private void beginPlay() {
        boolean firstKickoff = openingKickoff;
        openingKickoff = false;
        match.kickOff();
        paused = !everyoneOnline();
        if (firstKickoff) startPearlCooldown();
        spawnBall();
        if (tickTask == null) tickTask = scheduler.runTaskTimer(plugin, this::tick, 1L, 1L);
        updateScore();
    }

    @Override protected String getFinalCountdownTitle(int seconds) {
        return openingKickoff ? MessageConfig.SULFUR_SOCCER_UPCOMING_TITLE : super.getFinalCountdownTitle(seconds);
    }

    @Override protected String getFinalCountdownSubtitle(String gameTitle, int seconds) {
        return openingKickoff ? "&e" + seconds : "";
    }

    private void beginWarmup() {
        match.startWarmup(WARMUP_SECONDS);
        paused = !everyoneOnline();
        resetPlayerHealthFoodEffectLevelInventory();
        for (Player player : players()) restorePlayer(player);
        startPearlCooldown();
        spawnBall();
        tickTask = scheduler.runTaskTimer(plugin, this::tick, 1L, 1L);
        sendMessageToAllGamePlayers(MessageConfig.SULFUR_SOCCER_WARMUP_START);
        updateScore();
    }

    private void spawnBall() {
        spawnBall(getGameConfig().bind(getGameConfig().getBallSpawnPoint()));
    }

    private void spawnBall(Location spawn) {
        removeBall();
        ball = spawn.getWorld().spawn(spawn, SulfurCube.class, cube -> {
            cube.setAdult();
            cube.setAgeLock(true);
            cube.setSize(2);
            cube.setWander(false);
            // BODY is the swallowed block slot in the project's Paper 26.2 API.
            // A wood block selects the native bouncy archetype and disables autonomous movement.
            cube.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(Material.BIRCH_PLANKS));
            cube.setCanPickupItems(false);
            cube.setRemoveWhenFarAway(false);
            cube.setPersistent(false);
            cube.setCollidable(true);
            cube.setVelocity(new Vector());
            cube.getPersistentDataContainer().set(ballKey, PersistentDataType.STRING, getGameConfig().getAreaName());
        });
        previousBallCenter = ball.getBoundingBox().getCenter();
        if (paused) freezeBall();
    }

    public boolean isBall(Entity entity) {
        return entity != null && ball != null && entity.getUniqueId().equals(ball.getUniqueId());
    }

    public boolean canKick(Player player) {
        return isPlayPhase() && match != null && !paused
                && (isShootout() ? shootout.canStrike(player.getUniqueId()) : match.playing())
                && !notAreaPlayer(player) && !isManagedSpectator(player)
                && player.getGameMode() == GameMode.ADVENTURE && !notInArea(player.getLocation());
    }

    /** Consume the sole shot at the native attack-push event, after attack eligibility has been checked. */
    public boolean allowAttackPush(Player player) {
        return canKick(player) && (!isShootout() || shootout.strike(player.getUniqueId()));
    }

    public boolean canContactBall(Player player) { return !isShootout() && canKick(player); }

    private void tick() {
        ticks++;
        if (match == null || getGameStageEnum() == GameStageEnum.END || getGameStageEnum() == GameStageEnum.WAITING) return;
        setPaused(!everyoneOnline());
        if (!paused && !isShootout() && match.tickRegulation()) {
            cancelFinalCountdown();
            if (kickoffTask != null) kickoffTask.cancel();
            kickoffTask = null;
            removeBall();
            removePearls();
            if (match.winner() != null) {
                sendMessageToAllGamePlayers(scoreText(MessageConfig.SULFUR_SOCCER_TIME_UP));
                finishWithWinner(match.winner());
            } else beginShootout();
            return;
        }
        if (isShootout()) {
            tickShootout();
            return;
        }
        if (!isPlayPhase() || !match.playing()) {
            if (ticks % 20 == 0) updateScore();
            return;
        }
        if (!paused && isWarmingUp() && match.tickWarmup()) {
            pearlReadyTicks.clear();
            prepareKickoff();
            return;
        }
        if (ball == null || !ball.isValid() || ball.isDead()) {
            resetLostBall();
            return;
        }
        if (paused) {
            holdPausedBall();
            return;
        }
        Vector center = ball.getBoundingBox().getCenter();
        SulfurSoccerSide goal = SulfurSoccerGeometry.crossedGoal(rightGoal, leftGoal, previousBallCenter, center);
        previousBallCenter = center;
        if (goal != null) {
            scoreGoal(goal);
        } else if (notInArea(ball.getLocation())) {
            resetLostBall();
        } else if (ticks % 20 == 0) {
            updateScore();
        }
    }

    private void scoreGoal(SulfurSoccerSide defending) {
        if (isWarmingUp()) {
            spawnBall();
            return;
        }
        if (!match.enterGoal(defending)) return;
        removeBall();
        ChampionshipTeam scoringTeam = defending == SulfurSoccerSide.RIGHT ? leftChampionshipTeam : rightChampionshipTeam;
        sendMessageToAllGamePlayers(scoreText(MessageConfig.SULFUR_SOCCER_GOAL).replace("%team%", scoringTeam.getColoredName()));
        sendTitleToAllGamePlayers(MessageConfig.SULFUR_SOCCER_GOAL_TITLE,
                scoreText(MessageConfig.SULFUR_SOCCER_GOAL_SUBTITLE).replace("%team%", scoringTeam.getColoredName()));
        playSoundToAllGamePlayers(Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
        if (match.winner() != null) {
            finishWithWinner(match.winner());
        } else {
            prepareKickoff(true);
        }
    }

    private void resetLostBall() {
        if (isWarmingUp()) spawnBall();
        else {
            sendMessageToAllGamePlayers(MessageConfig.SULFUR_SOCCER_BALL_RESET);
            prepareKickoff();
        }
    }

    private boolean everyoneOnline() {
        // Starting admits the persisted roster even when somebody is disconnected. Once play is
        // live, keep the existing reconnect pause behavior based on the currently online players.
        return rightChampionshipTeam != null && leftChampionshipTeam != null
                && rightChampionshipTeam.getOnlinePlayers().size() == rightChampionshipTeam.getMembers().size()
                && leftChampionshipTeam.getOnlinePlayers().size() == leftChampionshipTeam.getMembers().size();
    }

    private void finishWithWinner(SulfurSoccerSide winner) {
        champion = winner == SulfurSoccerSide.RIGHT ? rightChampionshipTeam : leftChampionshipTeam;
        endGame();
    }

    private static void validatePenaltySpace(World world, SulfurSoccerPenaltyLayout layout) {
        for (Vector point : List.of(layout.ball(), layout.shooter(), layout.keeper())) {
            for (int y = point.getBlockY(); y <= point.getBlockY() + 1; y++)
                if (!world.getBlockAt(point.getBlockX(), y, point.getBlockZ()).isPassable())
                    throw new IllegalArgumentException("请清空点球足球、射手和球门内守门员位置的上方两格");
        }
        for (int slot = 0; slot < 3; slot++)
            for (Vector point : layout.paneBlocks(slot))
                if (!world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()).getType().isAir())
                    throw new IllegalArgumentException("请清空球门前一格的点球玻璃板位置");
    }

    private SulfurSoccerPenaltyLayout penaltyLayout() {
        return shootout.shootingSide() == SulfurSoccerSide.RIGHT ? leftPenalty : rightPenalty;
    }

    private void beginShootout() {
        // The spawn map preserves the official player order, including the fourth keeper spawn.
        List<UUID> right = spawns.keySet().stream().filter(rightChampionshipTeam.getMembers()::contains).toList();
        List<UUID> left = spawns.keySet().stream().filter(leftChampionshipTeam.getMembers()::contains).toList();
        shootout = new SulfurSoccerShootout(right, left);
        setGameStageEnum(GameStageEnum.PROGRESS);
        sendMessageToAllGamePlayers(scoreText(MessageConfig.SULFUR_SOCCER_SHOOTOUT_START));
        preparePenalty();
    }

    private void preparePenalty() {
        removeBall();
        penaltyBarrier.clear();
        keeperSlot = 1;
        for (Player player : players()) restorePlayer(player);
        SulfurSoccerPenaltyLayout layout = penaltyLayout();
        penaltyBarrier.select(Bukkit.getWorld(getWorldName()), layout, keeperSlot);
        spawnBall(layout.ball().toLocation(Bukkit.getWorld(getWorldName())));
        // An unstruck cube stays still throughout preparation and aiming.
        ball.setGravity(false);
        sendMessageToAllGamePlayers(penaltyText(MessageConfig.SULFUR_SOCCER_PENALTY_TURN));
        updateScore();
    }

    private void tickShootout() {
        if (paused) {
            holdPausedBall();
            return;
        }
        if (ball == null || !ball.isValid() || ball.isDead()) {
            finishPenalty(false);
            return;
        }
        if (!shootout.struck()) {
            ball.teleport(penaltyLayout().ball().toLocation(ball.getWorld()));
            ball.setVelocity(new Vector());
            previousBallCenter = ball.getBoundingBox().getCenter();
        } else {
            ball.setGravity(true);
            BoundingBox bounds = ball.getBoundingBox();
            Vector center = bounds.getCenter();
            BoundingBox target = shootout.shootingSide() == SulfurSoccerSide.RIGHT ? leftGoal : rightGoal;
            double goalEntry = SulfurSoccerGeometry.entry(target, previousBallCenter, center);
            double blockEntry = penaltyBarrier.entry(previousBallCenter, center, bounds);
            previousBallCenter = center;
            if (Double.isFinite(blockEntry) && blockEntry <= goalEntry) {
                finishPenalty(false);
                return;
            }
            if (Double.isFinite(goalEntry)) {
                finishPenalty(true);
                return;
            }
            if (notInArea(ball.getLocation())) {
                finishPenalty(false);
                return;
            }
        }
        if (shootout.tick()) finishPenalty(false);
        else if (ticks % 20 == 0) updateScore();
    }

    private void finishPenalty(boolean scored) {
        String result = penaltyText(scored ? MessageConfig.SULFUR_SOCCER_PENALTY_GOAL : MessageConfig.SULFUR_SOCCER_PENALTY_MISS);
        shootout.finishAttempt(scored);
        sendMessageToAllGamePlayers(result);
        sendMessageToAllGamePlayers(penaltyText(MessageConfig.SULFUR_SOCCER_PENALTY_SCORE));
        if (shootout.winner() != null) finishWithWinner(shootout.winner());
        else preparePenalty();
    }

    public boolean selectPenaltyDirection(Player player, int slot) {
        if (!isShootout() || shootout.winner() != null || paused || slot < 0 || slot > 2
                || !shootout.keeper().equals(player.getUniqueId())) return false;
        if (keeperSlot != slot) {
            keeperSlot = slot;
            penaltyBarrier.select(Bukkit.getWorld(getWorldName()), penaltyLayout(), slot);
        }
        return true;
    }

    public Location penaltyMoveDestination(Player player, Location from, Location to) {
        if (!isShootout()) return to;
        Location anchor;
        SulfurSoccerPenaltyLayout layout = penaltyLayout();
        if (shootout.shooter().equals(player.getUniqueId())) {
            if (to.getWorld() != null && getWorldName().equals(to.getWorld().getName()) && layout.allowsShooter(to)) return to;
            anchor = from.clone();
        } else {
            anchor = shootout.keeper().equals(player.getUniqueId())
                    ? layout.keeperLocation(Bukkit.getWorld(getWorldName())) : getSpectatorSpawnLocation();
        }
        anchor.setYaw(to.getYaw());
        anchor.setPitch(to.getPitch());
        return anchor;
    }

    private void holdPausedBall() {
        if (ball != null && pausedBallLocation != null) {
            ball.teleport(pausedBallLocation);
            ball.setVelocity(new Vector());
        }
    }

    private void freezeBall() {
        if (ball == null) return;
        pausedBallLocation = ball.getLocation();
        pausedBallVelocity = ball.getVelocity();
        ball.setGravity(false);
        ball.setVelocity(new Vector());
    }

    private void setPaused(boolean value) {
        if (paused == value) return;
        paused = value;
        if (paused) {
            freezeBall();
            removePearls();
        } else if (ball != null && pausedBallVelocity != null) {
            ball.setGravity(!isShootout() || shootout.struck());
            ball.setVelocity(pausedBallVelocity);
            previousBallCenter = ball.getBoundingBox().getCenter();
        }
        sendMessageToAllGamePlayers(paused ? MessageConfig.SULFUR_SOCCER_PAUSED : MessageConfig.SULFUR_SOCCER_RESUMED);
        updateScore();
    }

    private String scoreText(String message) {
        return message.replace("%right%", rightChampionshipTeam == null ? "-" : rightChampionshipTeam.getColoredName())
                .replace("%left%", leftChampionshipTeam == null ? "-" : leftChampionshipTeam.getColoredName())
                .replace("%right_goals%", String.valueOf(getRightGoals())).replace("%left_goals%", String.valueOf(getLeftGoals()))
                .replace("%time%", regulationTime())
                .replace("%target%", String.valueOf(getGameConfig().getGoalsToWin()));
    }

    private String penaltyText(String message) {
        Player shooter = Bukkit.getPlayer(shootout.shooter()), keeper = Bukkit.getPlayer(shootout.keeper());
        return scoreText(message).replace("%round%", String.valueOf(shootout.round()))
                .replace("%shooter%", shooter == null ? "-" : shooter.getName())
                .replace("%keeper%", keeper == null ? "-" : keeper.getName())
                .replace("%right_penalties%", String.valueOf(shootout.goals(SulfurSoccerSide.RIGHT)))
                .replace("%left_penalties%", String.valueOf(shootout.goals(SulfurSoccerSide.LEFT)))
                .replace("%seconds%", String.valueOf(shootout.secondsRemaining()));
    }

    private void updateScore() {
        if (isShootout() && !paused) {
            sendActionBarToAllGamePlayers(penaltyText(MessageConfig.SULFUR_SOCCER_PENALTY_SCORE) + " &7| " + getStateText());
            return;
        }
        String text = paused ? MessageConfig.SULFUR_SOCCER_PAUSED
                : isWarmingUp() ? MessageConfig.SULFUR_SOCCER_WARMUP_TIME.replace("%time%", warmupTime())
                : MessageConfig.SULFUR_SOCCER_SCORE;
        sendActionBarToAllGamePlayers(scoreText(text));
    }

    private String warmupTime() {
        int seconds = match.warmupSeconds();
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private String regulationTime() {
        int seconds = match == null ? SulfurSoccerMatch.REGULATION_SECONDS : match.regulationSeconds();
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public boolean canUsePearl(Player player) {
        return !isShootout() && canKick(player) && pearlCooldown(player.getUniqueId()) == 0;
    }

    void startPearlCooldown() {
        for (UUID player : getParticipantUniqueIds()) pearlReadyTicks.put(player, ticks + PEARL_COOLDOWN_TICKS);
        for (Player player : players()) startPearlCooldown(player);
    }

    void startPearlCooldown(Player player) {
        pearlReadyTicks.put(player.getUniqueId(), ticks + PEARL_COOLDOWN_TICKS);
        player.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN_TICKS);
    }

    int pearlCooldown(UUID player) {
        return Math.max(0, pearlReadyTicks.getOrDefault(player, 0) - ticks);
    }

    public void recordPearlLaunch(Player player, EnderPearl pearl) {
        pearlReadyTicks.put(player.getUniqueId(), ticks + PEARL_COOLDOWN_TICKS);
        pearls.values().removeIf(entity -> !entity.isValid());
        pearls.put(pearl.getUniqueId(), pearl);
        player.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN_TICKS);
        long generation = pearlGeneration;
        // Vanilla consumes the item and writes its own cooldown after the launch event finishes.
        scheduler.runTask(plugin, () -> {
            if (generation == pearlGeneration && player.isOnline() && !notAreaPlayer(player)
                    && isPlayPhase() && !isShootout()) givePearl(player);
        });
    }

    public boolean validPearlDestination(Location destination) {
        return !notInArea(destination) && SulfurSoccerGeometry.finite(destination.toVector())
                && destination.getY() <= pitchFloorY + 2;
    }

    private void givePearl(Player player) {
        player.getInventory().remove(Material.ENDER_PEARL);
        player.getInventory().setItem(8, new ItemStack(Material.ENDER_PEARL));
        player.setCooldown(Material.ENDER_PEARL, pearlCooldown(player.getUniqueId()));
    }

    private void removePearls() {
        pearlGeneration++;
        for (EnderPearl pearl : pearls.values()) pearl.remove();
        pearls.clear();
    }

    public void teleportParticipant(Player player) {
        Location spawn = isShootout() ? shootout.shooter().equals(player.getUniqueId())
                ? penaltyLayout().shooterLocation(Bukkit.getWorld(getWorldName()))
                : shootout.keeper().equals(player.getUniqueId())
                ? penaltyLayout().keeperLocation(Bukkit.getWorld(getWorldName())) : getSpectatorSpawnLocation()
                : spawns.get(player.getUniqueId());
        if (spawn != null) player.teleport(spawn);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    private void restorePlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setCollidable(!isShootout());
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        if (isShootout()) {
            player.setCooldown(Material.ENDER_PEARL, 0);
            if (shootout.keeper().equals(player.getUniqueId())) {
                String[] names = {"挡左", "挡中", "挡右"};
                for (int slot = 0; slot < names.length; slot++) {
                    ItemStack item = new ItemStack(Material.GLASS_PANE);
                    var meta = item.getItemMeta();
                    meta.displayName(net.kyori.adventure.text.Component.text(names[slot]));
                    meta.lore(List.of(net.kyori.adventure.text.Component.text("选择此快捷栏位置切换玻璃挡板")));
                    item.setItemMeta(meta);
                    player.getInventory().setItem(slot, item);
                }
                player.getInventory().setHeldItemSlot(keeperSlot);
            }
        } else givePearl(player);
        teleportParticipant(player);
    }

    @Override public boolean notInArea(Location location) {
        return location == null || location.getWorld() == null || !getWorldName().equals(location.getWorld().getName())
                || field == null || !field.contains(location.toVector());
    }

    @Override public void handlePlayerQuit(PlayerQuitEvent event) {
        if (!notAreaPlayer(event.getPlayer()) && isPlayPhase()) setPaused(true);
    }

    @Override public void handlePlayerJoin(PlayerJoinEvent event) {
        if (!notAreaPlayer(event.getPlayer()) && getGameStageEnum() != GameStageEnum.END
                && getGameStageEnum() != GameStageEnum.WAITING) restorePlayer(event.getPlayer());
    }

    @Override public void handlePlayerDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        scheduler.runTask(plugin, () -> {
            Player player = event.getEntity();
            player.spigot().respawn();
            if (!notAreaPlayer(player) && getGameStageEnum() != GameStageEnum.END
                    && getGameStageEnum() != GameStageEnum.WAITING) restorePlayer(player);
        });
    }

    private void removeBall() {
        if (ball != null) ball.remove();
        ball = null;
        previousBallCenter = null;
        pausedBallLocation = null;
        pausedBallVelocity = null;
    }

    private void cleanup() {
        if (tickTask != null) tickTask.cancel();
        if (kickoffTask != null) kickoffTask.cancel();
        tickTask = null;
        kickoffTask = null;
        removeBall();
        removePearls();
        penaltyBarrier.clear();
        restoreGoalColors(Bukkit.getWorld(getWorldName()), originalGoalBlocks);
        originalGoalBlocks.clear();
        pearlReadyTicks.clear();
        for (Player player : players()) {
            player.setCooldown(Material.ENDER_PEARL, 0);
            player.setCollidable(true);
        }
    }

    @Override public void addPlayerPointsToDatabase() { }

    @Override public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        cancelIntroduction();
        cancelFinalCountdown();
        cleanup();
        setGameStageEnum(GameStageEnum.END);
        paused = false;
        if (isSettlementAllowed()) {
            if (champion != null) Utils.sendMessageToAllPlayers(scoreText(MessageConfig.SULFUR_SOCCER_CHAMPION)
                    .replace("%team%", champion.getColoredName())
                    + (shootout != null ? " " + penaltyText(MessageConfig.SULFUR_SOCCER_PENALTY_SCORE) : ""));
            else sendMessageToAllGamePlayers(MessageConfig.SULFUR_SOCCER_STOPPED);
        }
        announceGameEnd(MessageConfig.SULFUR_SOCCER_END_TITLE,
                champion == null ? "" : scoreText(MessageConfig.SULFUR_SOCCER_CHAMPION_SUBTITLE).replace("%team%", champion.getColoredName())
                        + (shootout != null ? " " + penaltyText(MessageConfig.SULFUR_SOCCER_PENALTY_SCORE) : ""));
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        beginPostGameSettlement();
        publishGameEndEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));
        finishPostGameAfterEndEvent();
    }

    @Override public void resetArea() {
        cleanup();
        match = null;
        shootout = null;
        rightPenalty = null;
        leftPenalty = null;
        champion = null;
        paused = false;
        ticks = 0;
        openingKickoff = false;
        spawns.clear();
        preloadMap();
    }
}
