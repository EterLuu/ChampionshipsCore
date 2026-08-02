package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;
import ink.ziip.championshipscore.api.game.buildmart.gui.BuildMartMenu;
import ink.ziip.championshipscore.api.game.buildmart.library.BlueprintLibrary;
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
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build Mart arena: every team builds in its own base inside a pre-built static world (copied from
 * {@code plugin/maps/buildmart}). Players gather materials from the central resource market (the hub),
 * pick blueprints from the hub library, and replicate them on their base build plots for dynamic,
 * time-scaled points.
 *
 * <p>Phase 1 implements only the area lifecycle (waiting → preparation → progress → end) so the area can
 * be created and a round can be started/ended. Blueprints, portals, flight, scoring and the end awards
 * are layered in by the later phases.
 */
public class BuildMartArea extends BaseSingleTeamArea {
    @Getter
    private volatile int timer;

    @Getter
    private volatile BlueprintLibrary blueprintLibrary;
    private BlueprintRefreshScheduler blueprintRefreshScheduler;
    private GoldenBlueprintScheduler goldenBlueprintScheduler;

    /** The golden order currently live in the hub display (and assigned to every team's golden slot). */
    @Getter
    private volatile BuildMartBlueprint currentGolden;

    /** Live per-team build state, keyed by team. Populated at progress start, cleared on reset. */
    private final Map<ChampionshipTeam, TeamBuildState> teamStates = new ConcurrentHashMap<>();
    /** Seat index (0-based grid position) assigned to each participating team for the round. */
    private final Map<ChampionshipTeam, Integer> seatByTeam = new ConcurrentHashMap<>();
    /** Parsed base geometry cached by seat, so the move handler doesn't re-derive it per step. */
    private final Map<Integer, BuildMartBase> baseCache = new ConcurrentHashMap<>();
    /** Cross-chunk reads/writes which must finish before the arena world is reset. */
    private final java.util.Set<CompletableFuture<?>> pendingWorldOperations = ConcurrentHashMap.newKeySet();

    private volatile ScheduledTask startGamePreparationTask;
    private volatile ScheduledTask startGameProgressTask;

    public BuildMartArea(ChampionshipsCore plugin, BuildMartConfig buildMartConfig) {
        super(plugin, GameTypeEnum.BuildMart, new BuildMartHandler(plugin), buildMartConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setBuildMartArea(this);
        // loadMap copies the static world from plugin/maps/buildmart, (re)registers the handler and
        // leaves the area in WAITING; falls back to an empty void world if no template is present yet.
        loadMap(World.Environment.NORMAL);
    }

    @Override
    public void resetArea() {
        startGamePreparationTask = null;
        startGameProgressTask = null;
        teamStates.clear();
        seatByTeam.clear();
        baseCache.clear();
        currentGolden = null;

        // Rebuild the arena from the template for the next round (also wipes dropped items / placed blocks).
        loadMap(World.Environment.NORMAL);
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

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BUILD_MART_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.BUILD_MART_START_PREPARATION_TITLE, MessageConfig.BUILD_MART_START_PREPARATION_SUBTITLE);

        timer = getGameConfig().getPrepareTime();
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
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) {
            plugin.getLogger().warning("[BuildMart] 世界 " + getWorldName() + " 不存在，无法开始游戏。");
            endGame();
            return;
        }

        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        // Enter PROGRESS up front so the scheduled callbacks (golden rotation) run on the first spawn.
        setGameStageEnum(GameStageEnum.PROGRESS);
        timer = getGameConfig().getTimer();

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
        giveBlueprintBookToAll();

        // Seed the hub blueprint library and start its periodic re-roll.
        BuildMartOrderPool pool = plugin.getGameManager().getBuildMartManager().getOrderPool();
        blueprintLibrary = new BlueprintLibrary(pool);
        blueprintLibrary.refresh();
        blueprintRefreshScheduler = new BlueprintRefreshScheduler(plugin,
                getGameConfig().getLibraryRefreshSeconds(), this::onLibraryRefresh, this::getSpectatorSpawnLocation);
        blueprintRefreshScheduler.start();

        // Spawn the first golden order, then rotate it on the golden window.
        rotateGoldenBlueprint();
        goldenBlueprintScheduler = new GoldenBlueprintScheduler(plugin,
                getGameConfig().getGoldenRefreshSeconds(), this::rotateGoldenBlueprint, this::getSpectatorSpawnLocation);
        goldenBlueprintScheduler.start();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BUILD_MART_GAME_START);
        sendTitleToAllGamePlayers(MessageConfig.BUILD_MART_GAME_START_TITLE, MessageConfig.BUILD_MART_GAME_START_SUBTITLE);

        createTimerBossBar();

        startGameProgressTask = scheduler.runTaskTimer(() -> {
            sendActionBarToAllGameSpectators(MessageConfig.BUILD_MART_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));
            updateTimerBossBar();

            if (timer <= 0) {
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    private static final String TIMER_BOSS_BAR = "buildmart-timer";

    /** Creates the shared timer boss bar and shows it to all participants and spectators. */
    private void createTimerBossBar() {
        createBossBar(TIMER_BOSS_BAR, bossBarTitle(), BarColor.YELLOW, BarStyle.SOLID);
        for (UUID uuid : gamePlayers) {
            addBossBarPlayer(TIMER_BOSS_BAR, Bukkit.getPlayer(uuid));
        }
        for (Player spectator : getOnlineSpectators()) {
            addBossBarPlayer(TIMER_BOSS_BAR, spectator);
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

    /** Gives every participant the bound library book (used to open the hub blueprint menu). */
    private void giveBlueprintBookToAll() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scheduler.runEntity(player, () -> player.getInventory().addItem(BuildMartMenu.createBook()));
            }
        }
    }

    /** Opens the hub library menu for a player (called from the handler on book right-click). */
    public void openBlueprintMenu(Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || blueprintLibrary == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        BuildMartMenu.open(player, this, team);
    }

    /**
     * Assigns the chosen library blueprint to the player's team's first free normal slot and pastes its
     * reference build. No-ops (with feedback) when nothing is free or the order has rolled off the board.
     */
    public synchronized void selectBlueprint(Player player, String blueprintId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || blueprintLibrary == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        TeamBuildState state = teamStates.get(team);
        if (state == null) return;

        BuildMartBlueprint blueprint = blueprintLibrary.currentById(blueprintId);
        if (blueprint == null) return;

        BuildSlot slot = state.firstFreeNormalSlot();
        if (slot == null) {
            playerManager.getPlayer(player.getUniqueId()).sendMessage(MessageConfig.BUILD_MART_NO_FREE_SLOT);
            return;
        }
        slot.setBlueprint(blueprint);
        if (slot.getReferenceAnchor() != null) {
            Location reference = slot.getReferenceAnchor();
            pasteBlueprint(blueprint, reference);
        }
        player.closeInventory();
        team.sendMessageToAll(MessageConfig.BUILD_MART_BLUEPRINT_SELECTED
                .replace("%blueprint%", blueprint.getDisplayName())
                .replace("%stars%", String.valueOf(blueprint.getStars())));
    }

    /**
     * Submits one of the caller's team's build plots for validation (from the blueprint menu). The plot is
     * settled and scored only when it fully matches the blueprint; otherwise the player is told how many
     * blocks still differ. {@code slotId} is {@code N0/N1/N2} for a normal plot or {@code G} for golden.
     */
    public synchronized void submitSlot(Player player, String slotId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || player == null || slotId == null) return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        TeamBuildState state = teamStates.get(team);
        if (state == null) return;

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

        Location buildAnchor = slot.getBuildAnchor();
        track(ReferenceBuilder.countMatchingAsync(plugin, blueprint, buildAnchor)).thenAccept(matched -> {
            slot.setLastMatched(matched);
            if (matched >= blueprint.blockCount()) {
                scheduler.runEntity(player, () -> player.closeInventory());
                synchronized (this) {
                    if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
                    if (slot.getBlueprint() != blueprint) return;
                    if (golden) completeGoldenBuild(team, state, slot, blueprint);
                    else completeNormalBuild(team, state, slot, blueprint);
                }
            } else {
                playerManager.getPlayer(player.getUniqueId()).sendMessage(MessageConfig.BUILD_MART_SUBMIT_INCOMPLETE
                        .replace("%blueprint%", blueprint.getDisplayName())
                        .replace("%matched%", String.valueOf(matched))
                        .replace("%total%", String.valueOf(blueprint.blockCount())));
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to validate Build Mart slot", throwable);
            return null;
        });
    }

    /**
     * Refreshes one normal build slot's blueprint (menu "刷新" button). Each slot may be refreshed once per
     * game: the old reference build and any partial player build in the zone are wiped, then a fresh normal
     * order is drawn and its reference pasted. No-op for golden/empty slots or once the single refresh has
     * been spent (the player is told so). {@code slotId} is {@code N0/N1/N2}.
     */
    public synchronized void refreshSlot(Player player, String slotId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || player == null || slotId == null) return;
        if (notAreaPlayer(player)) return;
        if (!slotId.startsWith("N")) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        TeamBuildState state = teamStates.get(team);
        if (state == null) return;

        int index;
        try {
            index = Integer.parseInt(slotId.substring(1));
        } catch (NumberFormatException e) {
            return;
        }
        List<BuildSlot> normals = state.getNormalSlots();
        if (index < 0 || index >= normals.size()) return;
        BuildSlot slot = normals.get(index);

        BuildMartBlueprint old = slot.getBlueprint();
        if (old == null) return; // nothing assigned to refresh
        if (slot.isRefreshUsed()) {
            playerManager.getPlayer(player.getUniqueId()).sendMessage(MessageConfig.BUILD_MART_REFRESH_USED);
            return;
        }

        BuildMartBlueprint next = drawDifferentNormal(old);
        if (next == null) return;

        // Wipe the old reference and any partial build in the zone, then assign + paste the new order.
        if (slot.getBuildAnchor() != null) {
            Location build = slot.getBuildAnchor();
            clearBlueprint(old, build);
        }
        if (slot.getReferenceAnchor() != null) {
            Location reference = slot.getReferenceAnchor();
            clearBlueprint(old, reference);
        }
        slot.setBlueprint(next);
        slot.setRefreshUsed(true);
        slot.setLastMatched(0);
        if (slot.getReferenceAnchor() != null) {
            Location reference = slot.getReferenceAnchor();
            pasteBlueprint(next, reference);
        }

        team.sendMessageToAll(MessageConfig.BUILD_MART_BLUEPRINT_REFRESHED
                .replace("%blueprint%", next.getDisplayName())
                .replace("%stars%", String.valueOf(next.getStars())));
    }

    /** Draws a normal order different from {@code exclude} where possible (retries a few times). */
    private BuildMartBlueprint drawDifferentNormal(BuildMartBlueprint exclude) {
        BuildMartOrderPool pool = plugin.getGameManager().getBuildMartManager().getOrderPool();
        if (pool == null) return null;
        BuildMartBlueprint pick = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            List<BuildMartBlueprint> drawn = pool.drawNormal(1);
            if (drawn.isEmpty()) return null;
            pick = drawn.get(0);
            if (!pick.getId().equals(exclude.getId())) return pick;
        }
        return pick; // pool has effectively one order; hand back what we drew
    }

    private void completeNormalBuild(ChampionshipTeam team, TeamBuildState state, BuildSlot slot, BuildMartBlueprint blueprint) {
        int points = pointsForCompletion(blueprint.getStars());
        addPlayerPointsToAllTeamMembers(team, points);
        state.recordCompletion(blueprint.getStars());

        // Clear the player's copy and the reference build; the slot is now free to re-select.
        if (slot.getBuildAnchor() != null) {
            Location build = slot.getBuildAnchor();
            clearBlueprint(blueprint, build);
        }
        if (slot.getReferenceAnchor() != null) {
            Location reference = slot.getReferenceAnchor();
            clearBlueprint(blueprint, reference);
        }
        slot.clear();

        sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_BUILD_COMPLETED
                .replace("%team%", team.getColoredName())
                .replace("%blueprint%", blueprint.getDisplayName())
                .replace("%stars%", String.valueOf(blueprint.getStars()))
                .replace("%points%", String.valueOf(points)));
        team.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.5F);
    }

    /**
     * Expires the current golden order (clearing any unfinished golden zones as a penalty) and surfaces a
     * fresh one in the hub display, assigning it to every team's golden slot. A no-op pick when the golden
     * pool is empty.
     */
    private void rotateGoldenBlueprint() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        expireCurrentGolden();

        BuildMartBlueprint next = plugin.getGameManager().getBuildMartManager().getOrderPool().randomGolden();
        if (next == null) return;
        currentGolden = next;

        for (TeamBuildState state : teamStates.values()) {
            state.getGoldenSlot().setBlueprint(next);
        }
        Location display = getGameConfig().getGoldenDisplayPoint();
        if (display != null) {
            pasteBlueprint(next, display);
        }
        sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_REFRESHED);
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1F, 1F);
    }

    /** Penalises unfinished golden builds: clears their zones, the team slots, and the hub display. */
    private void expireCurrentGolden() {
        if (currentGolden == null) return;
        boolean anyUnfinished = false;
        for (TeamBuildState state : teamStates.values()) {
            BuildSlot golden = state.getGoldenSlot();
            if (golden.getBlueprint() != null) {
                if (golden.getBuildAnchor() != null) {
                    BuildMartBlueprint blueprint = golden.getBlueprint();
                    Location anchor = golden.getBuildAnchor();
                    clearBlueprint(blueprint, anchor);
                }
                golden.clear();
                anyUnfinished = true;
            }
        }
        Location display = getGameConfig().getGoldenDisplayPoint();
        if (display != null) {
            BuildMartBlueprint golden = currentGolden;
            clearBlueprint(golden, display);
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

        if (slot.getBuildAnchor() != null) {
            Location build = slot.getBuildAnchor();
            clearBlueprint(blueprint, build);
        }
        // Clear only this team's golden slot so they can't re-score; other teams keep building it.
        slot.clear();

        sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_BUILD_COMPLETED
                .replace("%team%", team.getColoredName())
                .replace("%blueprint%", blueprint.getDisplayName())
                .replace("%points%", String.valueOf(points)));
        team.playSoundToAllPlayers(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
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
     * teams on each metric, +100/+50/+25 per member.
     */
    private CompletableFuture<Void> settleIncompleteBuildsAsync() {
        List<CompletableFuture<Void>> scores = new ArrayList<>();
        for (TeamBuildState state : teamStates.values()) {
            ChampionshipTeam team = state.getTeam();
            for (BuildSlot slot : state.getNormalSlots()) {
                scores.add(scoreIncompleteAsync(team, slot));
            }
            scores.add(scoreIncompleteAsync(team, state.getGoldenSlot()));
        }
        return CompletableFuture.allOf(scores.toArray(CompletableFuture[]::new));
    }

    /** Awards a fraction of a build's points for an unfinished slot, scaled by completion. */
    private CompletableFuture<Void> scoreIncompleteAsync(ChampionshipTeam team, BuildSlot slot) {
        BuildMartBlueprint blueprint = slot.getBlueprint();
        Location anchor = slot.getBuildAnchor();
        if (blueprint == null || anchor == null) return CompletableFuture.completedFuture(null);
        return ReferenceBuilder.countMatchingAsync(plugin, blueprint, anchor).thenAccept(matched -> {
            slot.setLastMatched(matched);
            double ratio = blueprint.blockCount() == 0 ? 1.0 : (double) matched / blueprint.blockCount();
            if (ratio <= 0) return;
            int points = (int) Math.round(pointsForCompletion(blueprint.getStars()) * ratio);
            if (points > 0) addPlayerPointsToAllTeamMembers(team, points);
        });
    }

    /** Gives the {@code +100/+50/+25} award bonus to the top three teams of a ranking and announces #1. */
    private void awardAndAnnounce(List<TeamBuildState> ranking, String awardMessage) {
        for (int i = 0; i < ranking.size() && i < BuildMartScorer.AWARD_POINTS.length; i++) {
            addPlayerPointsToAllTeamMembers(ranking.get(i).getTeam(), BuildMartScorer.AWARD_POINTS[i]);
        }
        if (!ranking.isEmpty()) {
            sendMessageToAllGamePlayers(awardMessage.replace("%team%", ranking.get(0).getTeam().getColoredName()));
        }
    }

    /** Re-rolls the hub library and announces it; selected builds are untouched. */
    private void onLibraryRefresh() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || blueprintLibrary == null) return;
        blueprintLibrary.refresh();
        sendMessageToAllGamePlayers(MessageConfig.BUILD_MART_LIBRARY_REFRESHED);
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
                    player.teleportAsync(target);
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
        Location spawn = getSpectatorSpawnLocation();
        if (location == null || location.getWorld() == null || spawn == null || spawn.getWorld() == null)
            return false;
        return !location.getWorld().getName().equals(spawn.getWorld().getName());
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
        if (blueprintRefreshScheduler != null)
            blueprintRefreshScheduler.stop();
        if (goldenBlueprintScheduler != null)
            goldenBlueprintScheduler.stop();

        getGameHandler().clearCooldowns();
        disableFlightForAllGamePlayers();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BUILD_MART_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BUILD_MART_GAME_END_TITLE, MessageConfig.BUILD_MART_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        waitForPendingWorldOperations()
                .thenCompose(ignored -> settleIncompleteBuildsAsync())
                .whenComplete((ignored, throwable) -> scheduler.runTask(() -> finishEndGame(throwable)));
    }

    private synchronized void finishEndGame(Throwable throwable) {
        if (throwable != null) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to settle Build Mart arena " + getGameConfig().getAreaName(), throwable);
        }

        awardAndAnnounce(BuildMartScorer.rankByEntrepreneur(teamStates.values()), MessageConfig.BUILD_MART_AWARD_ENTREPRENEUR);
        awardAndAnnounce(BuildMartScorer.rankByChef(teamStates.values()), MessageConfig.BUILD_MART_AWARD_CHEF);
        awardAndAnnounce(BuildMartScorer.rankByQuality(teamStates.values()), MessageConfig.BUILD_MART_AWARD_QUALITY);

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        resetGame();
    }

    private CompletableFuture<Void> pasteBlueprint(BuildMartBlueprint blueprint, Location anchor) {
        return track(ReferenceBuilder.pasteAsync(plugin, blueprint, anchor));
    }

    private CompletableFuture<Void> clearBlueprint(BuildMartBlueprint blueprint, Location anchor) {
        return track(ReferenceBuilder.clearAsync(plugin, blueprint, anchor));
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        pendingWorldOperations.add(future);
        future.whenComplete((ignored, throwable) -> pendingWorldOperations.remove(future));
        return future;
    }

    private CompletableFuture<Void> waitForPendingWorldOperations() {
        CompletableFuture<?>[] snapshot = pendingWorldOperations.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(snapshot);
    }

    /** Clears any build-zone flight permission so players don't keep flying back in the lobby. */
    private void disableFlightForAllGamePlayers() {
        for (java.util.UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scheduler.runEntity(player, () -> {
                    if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                });
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        // No drops in Build Mart; respawn at the hub with a fresh inventory + library book.
        event.setDroppedExp(0);
        event.getDrops().clear();
        scheduler.runEntity(player, () -> {
            player.spigot().respawn();
            Location hub = getGameConfig().getHubSpawnPoint();
            if (hub != null) player.teleportAsync(hub);
            player.getInventory().clear();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                player.getInventory().addItem(BuildMartMenu.createBook());
            }
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
        return "buildmart";
    }
}
