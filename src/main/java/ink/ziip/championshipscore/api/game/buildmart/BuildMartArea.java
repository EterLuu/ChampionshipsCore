package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;
import ink.ziip.championshipscore.api.game.buildmart.reference.ReferenceBuilder;
import ink.ziip.championshipscore.api.game.buildmart.state.BuildSlot;
import ink.ziip.championshipscore.api.game.buildmart.state.TeamBuildState;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Build Mart game instance: every team builds in its own base inside a prepared static world. Players
 * gather materials from the central resource market, receive random blueprints on their plots, and
 * replicate them for time-scaled points.
 */
public class BuildMartArea extends BaseMultiTeamGameInstance {
    @Getter
    private int timer;

    private GoldenBlueprintScheduler goldenBlueprintScheduler;

    /** The golden order currently live in the hub display (and assigned to every team's golden slot). */
    @Getter
    private BuildMartBlueprint currentGolden;

    /** Round id, bumped each progress start so stale delayed auto-refresh tasks bail out. */
    private int roundId;

    /** Seconds after a normal build completes before a fresh blueprint is auto-assigned to its plot. */
    private static final int AUTO_REFRESH_SECONDS = 5;

    /** Per-player timestamp of the first golden submit click, for the two-click confirmation. */
    private final Map<UUID, Long> goldenArmedAt = new HashMap<>();
    /** Window within which a second golden click confirms the submit. */
    private static final long GOLDEN_CONFIRM_WINDOW_MILLIS = 5000L;

    /** Live per-team build state, keyed by team. Populated at progress start, cleared on reset. */
    private final Map<ChampionshipTeam, TeamBuildState> teamStates = new HashMap<>();
    /** Seat index (0-based grid position) assigned to each participating team for the round. */
    private final Map<ChampionshipTeam, Integer> seatByTeam = new HashMap<>();
    /** Parsed base geometry cached by seat, so the move handler doesn't re-derive it per step. */
    private final Map<Integer, BuildMartBase> baseCache = new HashMap<>();

    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;

    public BuildMartArea(ChampionshipsCore plugin, BuildMartConfig buildMartConfig) {
        super(plugin, GameTypeEnum.BuildMart, new BuildMartHandler(plugin), buildMartConfig);

        getGameHandler().setBuildMartArea(this);
    }

    /** Preloads a clean arena at startup and immediately after each completed game. */
    public void preloadMap() {
        loadMap(World.Environment.NORMAL);
    }

    /** Makes a newly created, not-yet-templated map editable by prepare without deleting its world. */
    public void initializeForSetup() {
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams) {
        return canStartConfiguredMap(teams.size()) && super.tryStartGame(teams);
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams, List<UUID> players) {
        return canStartConfiguredMap(teams.size()) && super.tryStartGame(teams, players);
    }

    private boolean canStartConfiguredMap(int teamCount) {
        BuildMartMapGeometry geometry = getGameConfig().resolveMapGeometry();
        BuildMartBase base = getGameConfig().getBaseTemplate();
        boolean configured = getGameStageEnum() == GameStageEnum.WAITING
                && teamCount > 0 && teamCount <= getGameConfig().getBaseCount()
                && getGameConfig().getTimer() > 0 && getGameConfig().getPrepareTime() >= 0
                && geometry.getBoundary() != null && geometry.getHub() != null
                && geometry.getHubReturn() != null && geometry.getHubSpawn() != null
                && geometry.getGoldenDisplay() != null
                && base != null && base.isComplete();
        if (!configured)
            logGame(Level.WARNING, "启动", "地图配置尚未完成或队伍数量超出 base-count，无法开始游戏");
        return configured;
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        locations.add(getSpectatorSpawnLocation());
        if (getGameConfig().getHubSpawnPoint() != null)
            locations.add(getGameConfig().getHubSpawnPoint());
        int count = Math.min(gameTeams.size(), getGameConfig().getBaseCount());
        for (int seat = 0; seat < count; seat++) {
            BuildMartBase base = getGameConfig().getSeatBase(seat);
            if (base != null && base.getSpawn() != null) locations.add(base.getSpawn());
        }
        return locations;
    }

    @Override
    public void resetArea() {
        startGamePreparationTask = null;
        startGameProgressTask = null;
        teamStates.clear();
        seatByTeam.clear();
        baseCache.clear();
        currentGolden = null;
        goldenArmedAt.clear();

        // Rebuild the arena from the template for the next round (also wipes dropped items / placed blocks).
        preloadMap();
    }

    /** Live build state for a team, or {@code null} outside a round / for non-participants. */
    @org.jetbrains.annotations.Nullable
    public TeamBuildState teamStateOf(ChampionshipTeam team) {
        return teamStates.get(team);
    }

    /** Seat index assigned to {@code team} for this round, or {@code null} for non-participants. */
    @org.jetbrains.annotations.Nullable
    public Integer seatOf(ChampionshipTeam team) {
        return seatByTeam.get(team);
    }

    /** Cached base geometry for a seat (derived once at round start), or {@code null} if unconfigured. */
    @org.jetbrains.annotations.Nullable
    public BuildMartBase cachedBaseForSeat(int seat) {
        return baseCache.get(seat);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.BUILD_MART_START_PREPARATION,
                MessageConfig.BUILD_MART_START_PREPARATION_TITLE, MessageConfig.BUILD_MART_START_PREPARATION_SUBTITLE);

        timer = getGameConfig().getPrepareTime();
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            showPreparationCountdown(timer);

            if (timer == 0) {
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
                startGameProgress();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) {
            logGame(Level.WARNING, "世界", "世界=" + getWorldName() + " 不存在，无法开始");
            endGame();
            return;
        }

        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        roundId++;

        // Assign each participating team a seat (grid position) and derive its base from the template.
        teamStates.clear();
        seatByTeam.clear();
        baseCache.clear();
        int seat = 0;
        for (ChampionshipTeam team : gameTeams) {
            seatByTeam.put(team, seat);
            BuildMartBase base = getGameConfig().getSeatBase(seat);
            if (base != null) baseCache.put(seat, base);
            teamStates.put(team, new TeamBuildState(team, base));
            seat++;
        }

        // Send every team to its own base; teams without a configured base fall back to the hub spawn.
        teleportTeamsToBases();

        // Auto-assign a random normal blueprint to each team's three plots and paste its reference build.
        assignInitialNormalBlueprints();
        rotateGoldenBlueprint(false);

        startFinalCountdown(MessageConfig.BUILD_MART_START_PREPARATION_TITLE,
                MessageConfig.BUILD_MART_GAME_START_TITLE, MessageConfig.BUILD_MART_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        goldenBlueprintScheduler = new GoldenBlueprintScheduler(plugin,
                getGameConfig().getGoldenRefreshSeconds(), this::rotateGoldenBlueprint);
        goldenBlueprintScheduler.start();
        createTimerBossBar();

        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            updateSpectatorTimerBossBar(MessageConfig.BUILD_MART_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer)), timer, getGameConfig().getTimer());
            updateTimerBossBar();
        }, this::endGame);
    }

    private static final String TIMER_BOSS_BAR = "buildmart-timer";

    /** Creates the detailed Build Mart timer for participants; spectators use the shared timer bar. */
    private void createTimerBossBar() {
        createBossBar(TIMER_BOSS_BAR, bossBarTitle(), BarColor.YELLOW, BarStyle.SOLID);
        for (UUID uuid : gamePlayers) {
            addBossBarPlayer(TIMER_BOSS_BAR, Bukkit.getPlayer(uuid));
        }
    }

    private void updateTimerBossBar() {
        setBossBar(TIMER_BOSS_BAR, bossBarTitle());
        int full = Math.max(1, getGameConfig().getTimer());
        setBossBarProgress(TIMER_BOSS_BAR, Math.max(0d, Math.min(1d, (double) timer / full)));
    }

    /** Title showing the round time left and the live golden-window countdown. */
    private String bossBarTitle() {
        String golden = currentGolden == null
                ? "&7黄金: &f无"
                : "&6黄金: &e" + currentGolden.getDisplayName() + " &7(" + goldenSecondsRemaining() + "s)";
        return "&e建材集市 &7| &f剩余 &e" + timer + "s &7| " + golden;
    }

    /** Seconds left in the current golden window, derived from elapsed time and the rotation period. */
    private int goldenSecondsRemaining() {
        int period = Math.max(1, getGameConfig().getGoldenRefreshSeconds());
        int elapsed = Math.max(0, getGameConfig().getTimer() - timer);
        return period - (elapsed % period);
    }

    /**
     * Resolves the submit slot ({@code N0/N1/N2/G}) whose physical submit button sits at {@code clicked},
     * for {@code team}'s base, or {@code null} if the clicked block isn't one of this team's submit buttons.
     */
    @org.jetbrains.annotations.Nullable
    public String submitSlotIdAt(ChampionshipTeam team, Location clicked) {
        if (clicked == null || clicked.getWorld() == null) return null;
        Integer seat = seatOf(team);
        if (seat == null) return null;
        BuildMartBase base = baseCache.get(seat);
        if (base == null) return null;
        List<Location> submits = base.getNormalSubmitAnchors();
        for (int i = 0; i < submits.size(); i++) {
            if (sameBlock(submits.get(i), clicked)) return "N" + i;
        }
        if (sameBlock(base.getGoldenSubmitAnchor(), clicked)) return "G";
        return null;
    }

    /** True when the block at {@code worldX/Y/Z} is any team's submit button (protected from breaking). */
    public boolean isSubmitButtonBlock(World world, int worldX, int worldY, int worldZ) {
        for (TeamBuildState state : teamStates.values()) {
            Integer seat = seatOf(state.getTeam());
            BuildMartBase base = seat == null ? null : baseCache.get(seat);
            if (base == null) continue;
            for (Location loc : base.getNormalSubmitAnchors()) {
                if (sameBlock(loc, world, worldX, worldY, worldZ)) return true;
            }
            if (sameBlock(base.getGoldenSubmitAnchor(), world, worldX, worldY, worldZ)) return true;
        }
        return false;
    }

    /**
     * Handles a submit-button click routed by the handler: normal plots submit on the first click; the
     * golden plot needs a second confirming click within {@link #GOLDEN_CONFIRM_WINDOW_MILLIS} (the first
     * click just arms and prompts).
     */
    public void handleSubmitClick(Player player, String slotId) {
        if (slotId.equals("G")) {
            UUID id = player.getUniqueId();
            Long armedAt = goldenArmedAt.get(id);
            long now = System.currentTimeMillis();
            if (armedAt != null && now - armedAt < GOLDEN_CONFIRM_WINDOW_MILLIS) {
                goldenArmedAt.remove(id);
                submitSlot(player, "G");
            } else {
                goldenArmedAt.put(id, now);
                playerManager.getPlayer(id).sendMessage(MessageConfig.BUILD_MART_GOLDEN_SUBMIT_CONFIRM);
            }
        } else {
            submitSlot(player, slotId);
        }
    }

    /** Whether {@code a} and {@code b} are the same block (same world + block coords). */
    private static boolean sameBlock(Location a, Location b) {
        if (a == null || a.getWorld() == null || b == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    /** Whether {@code a} is the block at {@code worldX/Y/Z} in {@code world}. */
    private static boolean sameBlock(Location a, World world, int worldX, int worldY, int worldZ) {
        if (a == null || a.getWorld() == null || world == null) return false;
        if (!a.getWorld().equals(world)) return false;
        return a.getBlockX() == worldX && a.getBlockY() == worldY && a.getBlockZ() == worldZ;
    }

    /**
     * Auto-assigns a distinct random normal blueprint to each of every team's three plots and pastes its
     * reference build. Called once at round start so every blueprint area shows a build from the off.
     */
    private void assignInitialNormalBlueprints() {
        BuildMartOrderPool pool = plugin.getGameManager().getBuildMartManager().getOrderPool();
        if (pool == null) return;
        for (TeamBuildState state : teamStates.values()) {
            ChampionshipTeam team = state.getTeam();
            List<BuildMartBlueprint> drawn = pool.drawNormal(state.getNormalSlots().size());
            for (int i = 0; i < drawn.size() && i < state.getNormalSlots().size(); i++) {
                BuildSlot slot = state.getNormalSlots().get(i);
                if (slot.getReferenceAnchor() == null) continue;
                BuildMartBlueprint blueprint = drawn.get(i);
                slot.setBlueprint(blueprint);
                ReferenceBuilder.paste(blueprint, slot.getReferenceAnchor());
                team.sendMessageToAll(MessageConfig.BUILD_MART_BLUEPRINT_AUTO_REFRESHED
                        .replace("%blueprint%", blueprint.getDisplayName())
                        .replace("%stars%", String.valueOf(blueprint.getStars())));
            }
        }
    }

    /**
     * Schedules a fresh random normal blueprint onto {@code slot} {@link #AUTO_REFRESH_SECONDS} after a
     * completion, pasting its reference. Bails silently if the round ended, the slot was reassigned, or the
     * slot has since been filled.
     */
    private void scheduleAutoRefresh(ChampionshipTeam team, BuildSlot slot) {
        final int scheduledRound = roundId;
        scheduler.runTaskLater(plugin, () -> {
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
            if (roundId != scheduledRound) return;
            TeamBuildState state = teamStates.get(team);
            if (state == null || !state.getNormalSlots().contains(slot)) return;
            if (!slot.isEmpty()) return;
            BuildMartBlueprint next = drawRandomNormal();
            if (next == null) return;
            slot.setBlueprint(next);
            if (slot.getReferenceAnchor() != null) ReferenceBuilder.paste(next, slot.getReferenceAnchor());
            team.sendMessageToAll(MessageConfig.BUILD_MART_BLUEPRINT_AUTO_REFRESHED
                    .replace("%blueprint%", next.getDisplayName())
                    .replace("%stars%", String.valueOf(next.getStars())));
        }, AUTO_REFRESH_SECONDS * 20L);
    }

    /** Draws a single random normal blueprint from the shared pool, or {@code null} when empty. */
    private BuildMartBlueprint drawRandomNormal() {
        BuildMartOrderPool pool = plugin.getGameManager().getBuildMartManager().getOrderPool();
        if (pool == null) return null;
        List<BuildMartBlueprint> drawn = pool.drawNormal(1);
        return drawn.isEmpty() ? null : drawn.get(0);
    }

    /**
     * Submits one of the caller's team's build plots for validation (from a physical submit button). The
     * plot is settled and scored only when it fully matches the blueprint; otherwise the player is told how
     * many blocks still differ. {@code slotId} is {@code N0/N1/N2} for a normal plot or {@code G} for golden.
     */
    public void submitSlot(Player player, String slotId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || player == null || slotId == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        TeamBuildState state = teamStates.get(team);
        if (state == null) return;

        // Last 10 seconds: no submissions accepted.
        if (timer <= 10) {
            playerManager.getPlayer(player.getUniqueId()).sendMessage(MessageConfig.BUILD_MART_SUBMIT_LOCKED);
            return;
        }

        boolean golden = slotId.equals("G");
        BuildSlot slot;
        if (golden) {
            slot = state.getGoldenSlot();
        } else if (slotId.startsWith("N")) {
            int index;
            try {
                index = Integer.parseInt(slotId.substring(1));
            } catch (NumberFormatException e) {
                return;
            }
            List<BuildSlot> normals = state.getNormalSlots();
            if (index < 0 || index >= normals.size()) return;
            slot = normals.get(index);
        } else {
            return;
        }

        BuildMartBlueprint blueprint = slot.getBlueprint();
        if (blueprint == null || slot.getBuildAnchor() == null) return;

        int matched = blueprint.countMatching(slot.getBuildAnchor());
        if (matched >= blueprint.blockCount()) {
            if (golden) {
                completeGoldenBuild(team, state, slot, blueprint);
            } else {
                completeNormalBuild(team, state, slot, blueprint);
            }
        } else if (golden) {
            // Golden incomplete submit: clear the build zone (no material return), must rebuild from scratch.
            ReferenceBuilder.clear(blueprint, slot.getBuildAnchor());
            playerManager.getPlayer(player.getUniqueId()).sendMessage(MessageConfig.BUILD_MART_GOLDEN_SUBMIT_FAILED
                    .replace("%blueprint%", blueprint.getDisplayName()));
        } else {
            playerManager.getPlayer(player.getUniqueId()).sendMessage(MessageConfig.BUILD_MART_SUBMIT_INCOMPLETE
                    .replace("%blueprint%", blueprint.getDisplayName())
                    .replace("%matched%", String.valueOf(matched))
                    .replace("%total%", String.valueOf(blueprint.blockCount())));
        }
    }

    private void completeNormalBuild(ChampionshipTeam team, TeamBuildState state, BuildSlot slot, BuildMartBlueprint blueprint) {
        int points = pointsForCompletion(blueprint.getStars());
        addPlayerPointsToAllTeamMembers(team, points);
        state.recordCompletion(blueprint.getStars());

        // Clear the player's copy and the reference; a fresh blueprint auto-appears shortly.
        if (slot.getBuildAnchor() != null) ReferenceBuilder.clear(blueprint, slot.getBuildAnchor());
        if (slot.getReferenceAnchor() != null) ReferenceBuilder.clear(blueprint, slot.getReferenceAnchor());
        slot.clear();
        scheduleAutoRefresh(team, slot);

        sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_BUILD_COMPLETED
                .replace("%team%", team.getColoredName())
                .replace("%blueprint%", blueprint.getDisplayName())
                .replace("%stars%", String.valueOf(blueprint.getStars()))
                .replace("%points%", String.valueOf(points)));
        for (Player player : team.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.5F);
        }
    }

    /**
     * Expires the current golden order (clearing any unfinished golden zones as a penalty) and surfaces a
     * fresh one in the hub display, assigning it to every team's golden slot. A no-op pick when the golden
     * pool is empty.
     */
    private void rotateGoldenBlueprint() {
        rotateGoldenBlueprint(true);
    }

    private void rotateGoldenBlueprint(boolean announce) {
        if (announce && getGameStageEnum() != GameStageEnum.PROGRESS) return;
        expireCurrentGolden();

        BuildMartBlueprint next = plugin.getGameManager().getBuildMartManager().getOrderPool().randomGolden();
        if (next == null) return;
        currentGolden = next;

        for (TeamBuildState state : teamStates.values()) {
            state.getGoldenSlot().setBlueprint(next);
        }
        Location display = getGameConfig().getGoldenDisplayPoint();
        if (display != null) {
            ReferenceBuilder.paste(next, display);
        }
        if (announce) {
            sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_REFRESHED);
            playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1F, 1F);
        }
    }

    /** Penalises unfinished golden builds: clears their zones, the team slots, and the hub display. */
    private void expireCurrentGolden() {
        if (currentGolden == null) return;
        boolean anyUnfinished = false;
        for (TeamBuildState state : teamStates.values()) {
            BuildSlot golden = state.getGoldenSlot();
            if (golden.getBlueprint() != null) {
                if (golden.getBuildAnchor() != null) {
                    ReferenceBuilder.clear(golden.getBlueprint(), golden.getBuildAnchor());
                }
                golden.clear();
                anyUnfinished = true;
            }
        }
        Location display = getGameConfig().getGoldenDisplayPoint();
        if (display != null) {
            ReferenceBuilder.clear(currentGolden, display);
        }
        if (anyUnfinished) {
            sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_EXPIRED);
        }
        currentGolden = null;
    }

    private void completeGoldenBuild(ChampionshipTeam team, TeamBuildState state, BuildSlot slot, BuildMartBlueprint blueprint) {
        int points = pointsForCompletion(blueprint.getStars());
        addPlayerPointsToAllTeamMembers(team, points);
        state.recordCompletion(blueprint.getStars());

        if (slot.getBuildAnchor() != null) ReferenceBuilder.clear(blueprint, slot.getBuildAnchor());
        // Clear only this team's golden slot so they can't re-score; other teams keep building it.
        slot.clear();

        sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_BUILD_COMPLETED
                .replace("%team%", team.getColoredName())
                .replace("%blueprint%", blueprint.getDisplayName())
                .replace("%points%", String.valueOf(points)));
        for (Player player : team.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
        }
    }

    /** Whole points a completed build is worth: {@code stars × per-star rate} for the current minute. */
    public int pointsForCompletion(int stars) {
        return stars * pointsPerStar(elapsedMinutes());
    }

    /** Dynamic per-star rate: 10 in the first third, 15 in the second, 20 in the final third. */
    private static int pointsPerStar(int minutes) {
        if (minutes < 4) return 10;
        if (minutes < 8) return 15;
        return 20;
    }

    /** Minutes elapsed since the round began, derived from the countdown timer. */
    public int elapsedMinutes() {
        int elapsedSeconds = Math.max(0, getGameConfig().getTimer() - timer);
        return elapsedSeconds / 60;
    }

    /**
     * True when the block at {@code worldX/Y/Z} belongs to any active reference build, so the handler can
     * cancel breaks that would damage a reference.
     */
    public boolean isProtectedReferenceBlock(World world, int worldX, int worldY, int worldZ) {
        // Normal-plot reference builds.
        for (TeamBuildState state : teamStates.values()) {
            for (BuildSlot slot : state.getNormalSlots()) {
                if (matchesFootprint(slot.getBlueprint(), slot.getReferenceAnchor(), world, worldX, worldY, worldZ)) {
                    return true;
                }
            }
        }
        // The shared golden display build (golden has no per-base reference, only the hub display).
        return matchesFootprint(currentGolden, getGameConfig().getGoldenDisplayPoint(), world, worldX, worldY, worldZ);
    }

    private static boolean matchesFootprint(BuildMartBlueprint blueprint, Location anchor, World world, int x, int y, int z) {
        if (blueprint == null || anchor == null || anchor.getWorld() == null || !anchor.getWorld().equals(world)) return false;
        return ReferenceBuilder.isFootprintBlock(blueprint, anchor, x, y, z);
    }

    /**
     * Final settlement: awards proportional points for every unfinished build (normal + golden), then
     * hands out the three end-of-game awards (entrepreneur / chef / quality assurance) to the top three
     * teams on each metric, +25/+15/+5 per member.
     */
    private void settleEndGame() {
        for (TeamBuildState state : teamStates.values()) {
            ChampionshipTeam team = state.getTeam();
            for (BuildSlot slot : state.getNormalSlots()) {
                scoreIncomplete(team, slot);
            }
            scoreIncomplete(team, state.getGoldenSlot());
        }

        awardAndAnnounce(BuildMartScorer.rankByEntrepreneur(teamStates.values()), MessageConfig.BUILD_MART_AWARD_ENTREPRENEUR);
        awardAndAnnounce(BuildMartScorer.rankByChef(teamStates.values()), MessageConfig.BUILD_MART_AWARD_CHEF);
        awardAndAnnounce(BuildMartScorer.rankByQuality(teamStates.values()), MessageConfig.BUILD_MART_AWARD_QUALITY);
    }

    /** Awards a fraction of a build's points for an unfinished slot, scaled by completion. */
    private void scoreIncomplete(ChampionshipTeam team, BuildSlot slot) {
        BuildMartBlueprint blueprint = slot.getBlueprint();
        if (blueprint == null || slot.getBuildAnchor() == null) return;
        double ratio = blueprint.completionRatio(slot.getBuildAnchor());
        if (ratio <= 0) return;
        int points = (int) Math.round(pointsForCompletion(blueprint.getStars()) * ratio);
        if (points > 0) addPlayerPointsToAllTeamMembers(team, points);
    }

    /** Gives the {@code +25/+15/+5} award bonus to the top three teams of a ranking and announces #1. */
    private void awardAndAnnounce(List<TeamBuildState> ranking, String awardMessage) {
        for (int i = 0; i < ranking.size() && i < BuildMartScorer.AWARD_POINTS.length; i++) {
            addPlayerPointsToAllTeamMembers(ranking.get(i).getTeam(), BuildMartScorer.AWARD_POINTS[i]);
        }
        if (!ranking.isEmpty()) {
            sendMessageToAllGamePlayers(awardMessage.replace("%team%", ranking.get(0).getTeam().getColoredName()));
        }
    }

    /** Teleports each participating team to its seat's base spawn (hub spawn if unconfigured). */
    private void teleportTeamsToBases() {
        Location hub = getGameConfig().getHubSpawnPoint();
        for (ChampionshipTeam team : gameTeams) {
            Integer seat = seatByTeam.get(team);
            BuildMartBase base = seat == null ? null : baseCache.get(seat);
            Location target = base != null && base.getSpawn() != null ? base.getSpawn() : hub;
            if (target == null) target = getSpectatorSpawnLocation();
            for (Player player : team.getOnlinePlayers()) {
                if (gamePlayers.contains(player.getUniqueId())) {
                    player.teleport(target);
                }
            }
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location set = getGameConfig().getSpectatorSpawnPoint();
        if (set != null) return set;
        Location hub = getGameConfig().getHubSpawnPoint();
        if (hub != null) return hub;
        World world = Bukkit.getWorld(getWorldName());
        return world != null ? world.getSpawnLocation() : CCConfig.LOBBY_LOCATION;
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName()))
            return true;
        org.bukkit.util.BoundingBox boundary = getGameConfig().resolveMapGeometry().getBoundary();
        return boundary != null && !boundary.contains(location.toVector());
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (goldenBlueprintScheduler != null)
            goldenBlueprintScheduler.stop();

        getGameHandler().clearCooldowns();
        disableFlightForAllGamePlayers();

        cleanInventoryForAllGamePlayers();

        announceGameEnd(MessageConfig.BUILD_MART_GAME_END_TITLE, MessageConfig.BUILD_MART_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        settleEndGame();

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        resetGame();
    }

    /** Clears any build-zone flight permission so players don't keep flying back in the lobby. */
    private void disableFlightForAllGamePlayers() {
        for (java.util.UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        // No drops in Build Mart; respawn at the hub with a fresh inventory.
        event.setDroppedExp(0);
        event.getDrops().clear();
        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            Location hub = getGameConfig().getHubSpawnPoint();
            if (hub != null) player.teleport(hub);
            player.getInventory().clear();
        });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
    }

    @Override
    public BuildMartConfig getGameConfig() {
        return (BuildMartConfig) gameConfig;
    }

    @Override
    public BuildMartHandler getGameHandler() {
        return (BuildMartHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return getGameConfig().resolveWorldName();
    }
}
