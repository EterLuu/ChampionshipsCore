package ink.ziip.championshipscore.api.game.buildmart;

import io.papermc.paper.registry.keys.EnchantmentKeys;
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
import ink.ziip.championshipscore.util.Enchants;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
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
    private BukkitTask materialRefillTask;
    private BukkitTask windVentTask;

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
                && geometry.getHub() != null
                && getGameConfig().getHubPortalPoint() != null
                && geometry.getHub().contains(getGameConfig().getHubPortalPoint().toVector())
                && !getGameConfig().getWindZones().isEmpty()
                && geometry.getGoldenDisplay() != null
                && base != null && base.isComplete()
                && base.getPortalPoint() != null && getGameConfig().isInBaseTemplate(base.getPortalPoint());
        if (!configured)
            logGame(Level.WARNING, "启动", "地图配置尚未完成或队伍数量超出 base-count，无法开始游戏");
        return configured;
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        locations.add(getSpectatorSpawnLocation());
        if (getGameConfig().getHubPortalPoint() != null)
            locations.add(getGameConfig().getHubPortalPoint());
        int count = Math.min(gameTeams.size(), getGameConfig().getBaseCount());
        for (int seat = 0; seat < count; seat++) {
            BuildMartBase base = getGameConfig().getSeatBase(seat);
            if (base != null && base.getPortalPoint() != null) locations.add(base.getPortalPoint());
        }
        return locations;
    }

    @Override
    public void resetArea() {
        startGamePreparationTask = null;
        startGameProgressTask = null;
        if (materialRefillTask != null) materialRefillTask.cancel();
        materialRefillTask = null;
        if (windVentTask != null) windVentTask.cancel();
        windVentTask = null;
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
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        // Assign seats before the countdown so every participant starts, waits, and begins from their
        // own team's base rather than from the spectator spawn.
        assignTeamSeats();
        teleportTeamsToBases();

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
        giveStartingEquipment();
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        roundId++;

        // Build the live per-team state from the seats assigned during formal preparation.
        teamStates.clear();
        for (ChampionshipTeam team : gameTeams) {
            Integer seat = seatByTeam.get(team);
            BuildMartBase base = seat == null ? null : baseCache.get(seat);
            teamStates.put(team, new TeamBuildState(team, base));
        }

        // Send every team to its own base; incomplete geometry falls back to the hub portal landing point.
        teleportTeamsToBases();

        // Auto-assign a random normal blueprint to each team's three plots and paste its reference build.
        assignInitialNormalBlueprints();
        rotateGoldenBlueprint(false);

        startFinalCountdown(MessageConfig.BUILD_MART_START_PREPARATION_TITLE,
                MessageConfig.BUILD_MART_GAME_START_TITLE, MessageConfig.BUILD_MART_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    /** Assigns each participating team a stable base seat for the current round. */
    private void assignTeamSeats() {
        seatByTeam.clear();
        baseCache.clear();
        int seat = 0;
        for (ChampionshipTeam team : gameTeams) {
            seatByTeam.put(team, seat);
            BuildMartBase base = getGameConfig().getSeatBase(seat);
            if (base != null) baseCache.put(seat, base);
            seat++;
        }
    }

    private void beginGameProgress() {
        goldenBlueprintScheduler = new GoldenBlueprintScheduler(plugin,
                getGameConfig().getGoldenRefreshSeconds(), this::rotateGoldenBlueprint);
        goldenBlueprintScheduler.start();
        refillMaterialZones();
        if (materialRefillTask != null) materialRefillTask.cancel();
        materialRefillTask = scheduler.runTaskTimer(plugin, this::refillMaterialZones, 2400L, 2400L);
        if (windVentTask != null) windVentTask.cancel();
        windVentTask = scheduler.runTaskTimer(plugin, this::applyWindVent, 1L, 1L);
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            updateGameTimerBossBar(bossBarTitle(), timer, getGameConfig().getTimer());
        }, this::endGame);
    }

    /** Restores every configured resource cuboid from its saved WorldEdit block snapshot. */
    private void refillMaterialZones() {
        if (getGameStageEnum() != GameStageEnum.COUNTDOWN && getGameStageEnum() != GameStageEnum.PROGRESS)
            return;
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return;
        for (BuildMartMaterialZone zone : getGameConfig().getMaterialZones()) {
            try {
                plugin.getWorldEditManager().pasteSchematic(world,
                        getGameConfig().getMaterialZoneSnapshotFile(zone),
                        zone.minX(), zone.minY(), zone.minZ());
            } catch (Exception exception) {
                logGame(Level.WARNING, "材料区", "无法恢复快照=" + zone.snapshotId()
                        + " | " + exception.getMessage());
            }
        }
    }

    /** Gives each participant the fixed Build Mart kit at the start of the live round. */
    private void giveStartingEquipment() {
        for (UUID uuid : gamePlayers) {
            giveStartingEquipment(Bukkit.getPlayer(uuid));
        }
    }

    /** Gives one player the fixed kit; used after a live-round death clears their inventory. */
    private void giveStartingEquipment(Player player) {
        if (player == null) return;
        ItemStack rockets = new ItemStack(Material.FIREWORK_ROCKET, 64);
        FireworkMeta meta = (FireworkMeta) rockets.getItemMeta();
        if (meta != null) {
            meta.setPower(3);
            rockets.setItemMeta(meta);
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));
        inventory.setItemInOffHand(map(183));
        inventory.setItem(0, efficientFortunePickaxe());
        inventory.setItem(1, silkTouchPickaxe());
        inventory.setItem(2, efficientUnbreakable(new ItemStack(Material.DIAMOND_SHOVEL)));
        inventory.setItem(3, efficientUnbreakable(new ItemStack(Material.DIAMOND_AXE)));
        inventory.setItem(8, rockets);
        inventory.setHeldItemSlot(0);
        player.updateInventory();
    }

    private static ItemStack map(int mapId) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = item.getItemMeta() instanceof MapMeta mapMeta ? mapMeta : null;
        if (meta != null) {
            MapView view = Bukkit.getMap(mapId);
            if (view == null) {
                meta.setMapId(mapId);
            } else {
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                if (view.getRenderers().stream().noneMatch(BuildMartSelfMapRenderer.class::isInstance)) {
                    view.addRenderer(new BuildMartSelfMapRenderer());
                }
                meta.setMapView(view);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Replaces vanilla multiplayer cursors with one contextual cursor for the player viewing the map. */
    private static final class BuildMartSelfMapRenderer extends MapRenderer {
        private BuildMartSelfMapRenderer() {
            super(true);
        }

        @Override
        public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
            MapCursorCollection cursors = new MapCursorCollection();
            if (view.getWorld() != null && view.getWorld().equals(player.getWorld())) {
                int scale = 1 << view.getScale().getValue();
                int cursorX = (int) Math.floor((player.getX() - view.getCenterX()) * 2.0 / scale + 0.5);
                int cursorZ = (int) Math.floor((player.getZ() - view.getCenterZ()) * 2.0 / scale + 0.5);
                int clampedX = Math.clamp(cursorX, -128, 127);
                int clampedZ = Math.clamp(cursorZ, -128, 127);
                int rotation = Math.floorMod((int) Math.floor(player.getYaw() * 16.0F / 360.0F), 16);
                MapCursor.Type type = Math.abs(cursorX) <= 63 && Math.abs(cursorZ) <= 63
                        ? MapCursor.Type.PLAYER : MapCursor.Type.PLAYER_OFF_MAP;
                cursors.addCursor(new MapCursor((byte) clampedX, (byte) clampedZ,
                        (byte) rotation, type, true));
            }
            canvas.setCursors(cursors);
        }
    }

    private static ItemStack efficientFortunePickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.EFFICIENCY), 3, true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.FORTUNE), 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack silkTouchPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.EFFICIENCY), 3, true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.SILK_TOUCH), 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack efficientUnbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.EFFICIENCY), 3, true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.SILK_TOUCH), 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Applies a fast upward force while a participant remains above the configured wind vent. */
    private void applyWindVent() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        BuildMartConfig config = getGameConfig();
        if (config.getWindZones().isEmpty()) return;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.isGliding() || !config.isAboveWindZone(player.getLocation())) continue;
            double remaining = 180.0 - player.getLocation().getY();
            if (remaining <= 0.0) continue;
            Vector current = player.getVelocity();
            double upward = Math.min(2.0, remaining);
            if (current.getY() < upward)
                player.setVelocity(new Vector(current.getX(), upward, current.getZ()));
        }
    }

    /** Timer-bar title showing the round time left and the live golden-window countdown. */
    private String bossBarTitle() {
        String golden = currentGolden == null
                ? "&7黄金: &f无"
                : "&6黄金: &e" + currentGolden.getDisplayName() + " &7(" + goldenSecondsRemaining() + "s)";
        return "&e匹配赛建 &7| &f剩余 &e" + timer + "s &7| " + golden;
    }

    /** Seconds left in the current golden window, derived from elapsed time and the rotation period. */
    public int goldenSecondsRemaining() {
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

        int matched = blueprint.countMatching(ReferenceBuilder.buildOrigin(slot.getBuildAnchor()));
        if (matched >= blueprint.blockCount()) {
            if (golden) {
                completeGoldenBuild(team, state, slot, blueprint);
            } else {
                completeNormalBuild(team, state, slot, blueprint);
            }
        } else if (golden) {
            // Golden incomplete submit: clear the build zone (no material return), must rebuild from scratch.
            ReferenceBuilder.clearBuildArea(slot.getBuildAnchor());
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
        if (slot.getBuildAnchor() != null) ReferenceBuilder.clearBuildArea(slot.getBuildAnchor());
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
            sendActionBarToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_REFRESHED);
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
                    ReferenceBuilder.clearBuildArea(golden.getBuildAnchor());
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
            sendActionBarToAllGamePlayers(MessageConfig.BUILD_MART_GOLDEN_EXPIRED);
        }
        currentGolden = null;
    }

    private void completeGoldenBuild(ChampionshipTeam team, TeamBuildState state, BuildSlot slot, BuildMartBlueprint blueprint) {
        int points = pointsForCompletion(BuildMartOrderPool.GOLDEN_SCORE_STARS);
        addPlayerPointsToAllTeamMembers(team, points);
        state.recordCompletion(BuildMartOrderPool.GOLDEN_SCORE_STARS);

        if (slot.getBuildAnchor() != null) ReferenceBuilder.clearBuildArea(slot.getBuildAnchor());
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

    /** True when the block lies inside one of the specified team's four fixed 7x7x7 build volumes. */
    public boolean isBuildZoneBlock(ChampionshipTeam team, World world, int worldX, int worldY, int worldZ) {
        TeamBuildState state = teamStates.get(team);
        if (state == null) return false;
        for (BuildSlot slot : state.getNormalSlots()) {
            if (matchesBuildArea(slot.getBuildAnchor(), world, worldX, worldY, worldZ)) return true;
        }
        return matchesBuildArea(state.getGoldenSlot().getBuildAnchor(), world, worldX, worldY, worldZ);
    }

    /** True when the block lies inside any configured material refill cuboid. */
    public boolean isMaterialZoneBlock(World world, int worldX, int worldY, int worldZ) {
        if (world == null) return false;
        for (BuildMartMaterialZone zone : getGameConfig().getMaterialZones()) {
            if (worldX >= zone.minX() && worldX <= zone.maxX()
                    && worldY >= zone.minY() && worldY <= zone.maxY()
                    && worldZ >= zone.minZ() && worldZ <= zone.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFootprint(BuildMartBlueprint blueprint, Location anchor, World world, int x, int y, int z) {
        if (blueprint == null || anchor == null || anchor.getWorld() == null || !anchor.getWorld().equals(world)) return false;
        return ReferenceBuilder.isFootprintBlock(blueprint, anchor, x, y, z);
    }

    private static boolean matchesBuildArea(Location anchor, World world, int worldX, int worldY, int worldZ) {
        return anchor != null && ReferenceBuilder.isBuildAreaBlock(anchor, world, worldX, worldY, worldZ);
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
        double ratio = blueprint.completionRatio(ReferenceBuilder.buildOrigin(slot.getBuildAnchor()));
        if (ratio <= 0) return;
        int scoringStars = slot.isGolden() ? BuildMartOrderPool.GOLDEN_SCORE_STARS : blueprint.getStars();
        int points = (int) Math.round(pointsForCompletion(scoringStars) * ratio);
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

    /** Teleports each participating team to its seat's configured portal landing point. */
    private void teleportTeamsToBases() {
        Location hub = getGameConfig().getHubPortalPoint();
        for (ChampionshipTeam team : gameTeams) {
            Integer seat = seatByTeam.get(team);
            BuildMartBase base = seat == null ? null : baseCache.get(seat);
            Location target = base != null && base.getPortalPoint() != null ? base.getPortalPoint() : hub;
            if (target == null) target = getSpectatorSpawnLocation();
            for (Player player : team.getOnlinePlayers()) {
                if (gamePlayers.contains(player.getUniqueId())) {
                    player.teleport(target);
                }
            }
        }
    }

    private Location teamBaseSpawn(ChampionshipTeam team) {
        Integer seat = team == null ? null : seatByTeam.get(team);
        BuildMartBase base = seat == null ? null : baseCache.get(seat);
        Location target = base != null && base.getPortalPoint() != null
                ? base.getPortalPoint() : getGameConfig().getHubPortalPoint();
        return target != null ? target : getSpectatorSpawnLocation();
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location set = getGameConfig().getSpectatorSpawnPoint();
        if (set != null) return set;
        Location hub = getGameConfig().getHubPortalPoint();
        if (hub != null) return hub;
        World world = Bukkit.getWorld(getWorldName());
        return world != null ? world.getSpawnLocation() : CCConfig.LOBBY_LOCATION;
    }

    @Override
    public boolean notInArea(Location location) {
        return !getGameConfig().isInPlayableArea(location);
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (materialRefillTask != null)
            materialRefillTask.cancel();
        materialRefillTask = null;
        if (windVentTask != null)
            windVentTask.cancel();
        windVentTask = null;
        if (goldenBlueprintScheduler != null)
            goldenBlueprintScheduler.stop();

        getGameHandler().clearCooldowns();
        disableFlightForAllGamePlayers();

        cleanInventoryForAllGamePlayers();

        announceGameEnd(MessageConfig.BUILD_MART_GAME_END_TITLE, MessageConfig.BUILD_MART_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        settleEndGame();

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
    }

    /** Clears any build-zone flight permission so players don't keep flying back in the lobby. */
    private void disableFlightForAllGamePlayers() {
        for (java.util.UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != GameMode.CREATIVE && !isManagedSpectator(player)) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        // No drops in Build Mart. During formal preparation/countdown, keep the player at their own base;
        // a death during the live round returns them to the shared resource hub.
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            Location target = getGameStageEnum() == GameStageEnum.PREPARATION
                    || getGameStageEnum() == GameStageEnum.COUNTDOWN
                    ? teamBaseSpawn(plugin.getTeamManager().getTeamByPlayer(player))
                    : getGameConfig().getHubPortalPoint();
            if (target != null) player.teleport(target);
        });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.PREPARATION) {
            Location target = isIntroductionPhase()
                    ? getPreparationTeleportLocation(getSpectatorSpawnLocation())
                    : teamBaseSpawn(plugin.getTeamManager().getTeamByPlayer(player));
            player.teleport(target);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        if (stage == GameStageEnum.COUNTDOWN || stage == GameStageEnum.PROGRESS) {
            Location target = teamBaseSpawn(plugin.getTeamManager().getTeamByPlayer(player));
            player.teleport(target);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(!getGameConfig().isInHub(target));
            player.setFlying(false);
            // A reconnecting participant may have lost the live kit while offline. Reuse the same
            // authoritative kit path as round start/death so the fixed 183 map is restored too.
            giveStartingEquipment(player);
            return;
        }
        player.teleport(CCConfig.LOBBY_LOCATION);
        player.setGameMode(GameMode.ADVENTURE);
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
        return getGameConfig().getConfiguredWorld();
    }
}
