package ink.ziip.championshipscore.api.game.buildmart.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;
import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.geometry.BuildMartMapGeometry;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartJumpPads;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartMaterialDisplays;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartMaterialRefillScheduler;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartScorer;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartSelfMapRenderer;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.GoldenBlueprintScheduler;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.GoldenSubmitConfirmation;
import ink.ziip.championshipscore.api.game.buildmart.model.BuildMartMaterialZone;
import ink.ziip.championshipscore.api.game.buildmart.reference.ReferenceBuilder;
import ink.ziip.championshipscore.api.game.buildmart.state.BuildSlot;
import ink.ziip.championshipscore.api.game.buildmart.state.TeamBuildState;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.spatial.TeleportPositions;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.util.Enchants;

import io.papermc.paper.registry.keys.EnchantmentKeys;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Build Mart game instance: every team builds in its own base inside a prepared static world.
 * Players gather materials from the central resource market, receive random blueprints on their
 * plots, and replicate them for time-scaled points.
 */
public class BuildMartArea extends BaseMultiTeamGameInstance {
    @Getter private int timer;

    private GoldenBlueprintScheduler goldenBlueprintScheduler;

    /**
     * The golden order currently live in the hub display (and assigned to every team's golden
     * slot).
     */
    @Getter private BuildMartBlueprint currentGolden;

    /** Round id, bumped each progress start so stale delayed auto-refresh tasks bail out. */
    private int roundId;

    /**
     * Seconds after a normal build completes before a fresh blueprint is auto-assigned to its plot.
     */
    private static final int AUTO_REFRESH_SECONDS = 5;

    private static final int FIREWORK_REFILL_INTERVAL_SECONDS = 10;

    private final GoldenSubmitConfirmation goldenConfirmation = new GoldenSubmitConfirmation();
    private long goldenGeneration;

    /** Live per-team build state, keyed by team. Populated at progress start, cleared on reset. */
    private final Map<ChampionshipTeam, TeamBuildState> teamStates = new HashMap<>();

    /** One pre-shuffled normal-order sequence shared by every team for the current round. */
    private List<BuildMartBlueprint> normalBlueprintSequence = List.of();

    /** Per-team cursors into the shared normal-order sequence. */
    private final Map<ChampionshipTeam, Integer> normalSequenceCursors = new HashMap<>();

    /** Seat index (0-based grid position) assigned to each participating team for the round. */
    private final Map<ChampionshipTeam, Integer> seatByTeam = new HashMap<>();

    /** Parsed base geometry cached by seat, so the move handler doesn't re-derive it per step. */
    private final Map<Integer, BuildMartBase> baseCache = new HashMap<>();

    private final BuildMartMaterialDisplays materialDisplays = new BuildMartMaterialDisplays();
    private MapView equipmentMap;

    private BukkitTask startGameProgressTask;
    private BuildMartMaterialRefillScheduler materialRefillScheduler;
    private BukkitTask jumpPadTask;
    private BuildMartJumpPads jumpPads;

    public BuildMartArea(ChampionshipsCore plugin, BuildMartConfig buildMartConfig) {
        super(plugin, GameTypeEnum.BuildMart, new BuildMartHandler(plugin), buildMartConfig);

        getGameHandler().setBuildMartArea(this);
    }

    /**
     * Makes a newly created, not-yet-templated map editable by prepare without deleting its world.
     */
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
        boolean configured =
                getGameStageEnum() == GameStageEnum.WAITING
                        && teamCount > 0
                        && teamCount <= getGameConfig().getBaseCount()
                        && getGameConfig().getTimer() > 0
                        && geometry.getHub() != null
                        && getGameConfig().getHubPortalPoint() != null
                        && geometry.getHub()
                                .contains(getGameConfig().getHubPortalPoint().toVector())
                        && !getGameConfig().getJumpPads().isEmpty()
                        && geometry.getGoldenDisplay() != null
                        && base != null
                        && base.isComplete()
                        && base.getPortalPoint() != null
                        && getGameConfig().isInBaseTemplate(base.getPortalPoint());
        if (!configured) logGame(Level.WARNING, "启动", "地图配置尚未完成或队伍数量超出 base-count，无法开始游戏");
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
        materialDisplays.clear();
        clearEquipmentMapRenderer();
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        startGameProgressTask = null;
        clearMaterialRefills();
        clearJumpPads();
        restoreMapRegion();
        teamStates.clear();
        normalBlueprintSequence = List.of();
        normalSequenceCursors.clear();
        seatByTeam.clear();
        baseCache.clear();
        currentGolden = null;
        goldenConfirmation.clear();
        goldenBlueprintScheduler = null;
    }

    /**
     * Restores only this Build Mart map, preserving other map regions in the shared physical world.
     */
    private void restoreMapRegion() {
        World world = Bukkit.getWorld(getWorldName());
        File schematic =
                new File(
                        new File(
                                new File(plugin.getDataFolder(), "buildmart/schematics"),
                                getGameConfig().getConfigName()),
                        "base.schem");
        if (world == null || !schematic.isFile() || getGameConfig().getBaseCount() < 1)
            throw new IllegalStateException("Build Mart 局部重置缺少世界、base.schem 或基地数量");
        try {
            ArenaPreparer.restoreCopies(
                    plugin,
                    world,
                    schematic,
                    getGameConfig().getBaseGrid(),
                    getGameConfig().getBaseCount() + 1);
            Location goldenDisplay = getGameConfig().getGoldenDisplayPoint();
            if (currentGolden != null && goldenDisplay != null)
                ReferenceBuilder.clear(currentGolden, goldenDisplay);
            restoreMaterialZones(world);
            org.bukkit.util.BoundingBox hub = getGameConfig().resolveMapGeometry().getHub();
            if (hub != null) {
                world.getNearbyEntities(
                                hub,
                                entity ->
                                        entity instanceof Item
                                                || entity instanceof ExperienceOrb
                                                || entity instanceof Projectile
                                                || entity instanceof Firework)
                        .forEach(entity -> entity.remove());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Build Mart 地图区域重置失败", exception);
        }
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

    /**
     * Cached base geometry for a seat (derived once at round start), or {@code null} if
     * unconfigured.
     */
    @org.jetbrains.annotations.Nullable
    public BuildMartBase cachedBaseForSeat(int seat) {
        return baseCache.get(seat);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point
        // and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: seat assignment, runs after the rule-introduction phase. */
    private void startFormalPreparation() {
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        // Assign seats before the countdown so every participant starts, waits, and begins from
        // their
        // own team's base rather than from the spectator spawn.
        assignTeamSeats();
        teleportTeamsToBases();

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(
                MessageConfig.BUILD_MART_START_PREPARATION,
                MessageConfig.BUILD_MART_START_PREPARATION_TITLE,
                MessageConfig.BUILD_MART_START_PREPARATION_SUBTITLE);

        startGameProgress();
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
        materialDisplays.clear();
        teamStates.clear();
        for (ChampionshipTeam team : gameTeams) {
            Integer seat = seatByTeam.get(team);
            BuildMartBase base = seat == null ? null : baseCache.get(seat);
            teamStates.put(team, new TeamBuildState(team, base));
        }

        // Send every team to its own base; incomplete geometry falls back to the hub portal landing
        // point.
        teleportTeamsToBases();

        // Surface the golden blueprint first so normal assignment can exclude it from every team's
        // plots.
        rotateGoldenBlueprint(false);
        prepareNormalBlueprintSequence();
        // Auto-assign the same pre-shuffled sequence to each team's three plots and paste its
        // reference build.
        assignInitialNormalBlueprints();

        // Ten seconds so every team can look around its base and the reference builds while frozen.
        startFinalCountdown(
                10,
                MessageConfig.BUILD_MART_START_PREPARATION_TITLE,
                MessageConfig.BUILD_MART_GAME_START_TITLE,
                MessageConfig.BUILD_MART_GAME_START_SUBTITLE,
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
        goldenBlueprintScheduler =
                new GoldenBlueprintScheduler(
                        getGameConfig().getTimer(),
                        getGameConfig().getGoldenRefreshSeconds(),
                        this::rotateGoldenBlueprint);
        clearMaterialRefills();
        refillMaterialZones();
        int scheduledRound = roundId;
        materialRefillScheduler =
                new BuildMartMaterialRefillScheduler(
                        scheduler,
                        plugin,
                        zone -> {
                            if (getGameStageEnum() != GameStageEnum.PROGRESS
                                    || roundId != scheduledRound) return;
                            World world = Bukkit.getWorld(getWorldName());
                            if (world != null) restoreMaterialZone(world, zone);
                        });
        clearJumpPads();
        World world = Bukkit.getWorld(getWorldName());
        if (world != null && !getGameConfig().getJumpPads().isEmpty()) {
            jumpPads = new BuildMartJumpPads(world, getGameConfig().getJumpPads());
            jumpPadTask = scheduler.runTaskTimer(plugin, this::applyJumpPads, 1L, 1L);
        }
        startGameProgressTask =
                startRemainingTimer(
                        getGameConfig().getTimer(),
                        seconds -> {
                            timer = seconds;
                            goldenBlueprintScheduler.tick(seconds);
                            int elapsedSeconds = getGameConfig().getTimer() - seconds;
                            if (seconds > 0
                                    && elapsedSeconds > 0
                                    && elapsedSeconds % FIREWORK_REFILL_INTERVAL_SECONDS == 0) {
                                refillFireworks();
                            }
                            updateGameTimerBossBar(
                                    bossBarTitle(), timer, getGameConfig().getTimer());
                        },
                        this::endGame);
    }

    /** Restores every configured resource cuboid from its saved WorldEdit block snapshot. */
    private void refillMaterialZones() {
        if (getGameStageEnum() != GameStageEnum.COUNTDOWN
                && getGameStageEnum() != GameStageEnum.PROGRESS) return;
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return;
        restoreMaterialZones(world);
    }

    private void restoreMaterialZones(@NotNull World world) {
        for (BuildMartMaterialZone zone : getGameConfig().getMaterialZones()) {
            restoreMaterialZone(world, zone);
        }
    }

    private void restoreMaterialZone(@NotNull World world, @NotNull BuildMartMaterialZone zone) {
        try {
            plugin.getWorldEditManager()
                    .pasteSchematic(
                            world,
                            getGameConfig().getMaterialZoneSnapshotFile(zone),
                            zone.minX(),
                            zone.minY(),
                            zone.minZ());
        } catch (Exception exception) {
            logGame(
                    Level.WARNING,
                    "材料区",
                    "无法恢复快照=" + zone.snapshotId() + " | " + exception.getMessage());
        }
    }

    /** Records a successful player harvest only for the resource cuboids containing that block. */
    public void onMaterialHarvest(@NotNull org.bukkit.block.Block block) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || materialRefillScheduler == null
                || !block.getWorld().getName().equals(getWorldName())) return;
        for (BuildMartMaterialZone zone : getGameConfig().getMaterialZones()) {
            if (inMaterialVolume(zone, block)) materialRefillScheduler.onHarvest(zone);
        }
    }

    private void clearMaterialRefills() {
        if (materialRefillScheduler != null) materialRefillScheduler.clear();
        materialRefillScheduler = null;
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
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));
        inventory.setItemInOffHand(map(183));
        inventory.setItem(0, efficientFortunePickaxe());
        inventory.setItem(1, silkTouchPickaxe());
        inventory.setItem(2, efficientUnbreakable(new ItemStack(Material.DIAMOND_SHOVEL)));
        inventory.setItem(3, efficientUnbreakable(new ItemStack(Material.DIAMOND_AXE)));
        inventory.setItem(4, efficientShears());
        inventory.setHeldItemSlot(0);
        player.updateInventory();
    }

    /** Grants one flight-duration-3 rocket to each active participant on the round clock. */
    private void refillFireworks() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null
                    || !player.isOnline()
                    || player.isDead()
                    || player.getGameMode() == GameMode.SPECTATOR
                    || isManagedSpectator(player)) continue;
            ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET, 1);
            FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();
            if (meta != null) {
                meta.setPower(3);
                rocket.setItemMeta(meta);
            }
            for (ItemStack overflow : player.getInventory().addItem(rocket).values()) {
                player.getWorld().dropItem(player.getLocation(), overflow);
            }
        }
    }

    private ItemStack map(int mapId) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = item.getItemMeta() instanceof MapMeta mapMeta ? mapMeta : null;
        if (meta != null) {
            MapView view = Bukkit.getMap(mapId);
            if (view != null) {
                World world = Bukkit.getWorld(getWorldName());
                if (world != null) {
                    BuildMartSelfMapRenderer.attach(view, world);
                    equipmentMap = view;
                }
                meta.setMapView(view);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void clearEquipmentMapRenderer() {
        if (equipmentMap != null) {
            BuildMartSelfMapRenderer.detach(equipmentMap);
            equipmentMap = null;
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

    private static ItemStack efficientShears() {
        ItemStack item = unbreakable(new ItemStack(Material.SHEARS));
        item.addUnsafeEnchantment(Enchants.get(EnchantmentKeys.EFFICIENCY), 3);
        return item;
    }

    /** Confirms grounded contacts before applying each pad's one-time upward/forward impulse. */
    private void applyJumpPads() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || jumpPads == null) return;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || isManagedSpectator(player)) jumpPads.forget(uuid);
            else if (jumpPads.sample(player))
                player.playSound(
                        player.getLocation(),
                        org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                        .8F,
                        1.35F);
        }
    }

    private void clearJumpPads() {
        if (jumpPadTask != null) jumpPadTask.cancel();
        jumpPadTask = null;
        if (jumpPads != null) jumpPads.clear();
        jumpPads = null;
    }

    /** Timer-bar title showing the round time left and the live golden-window countdown. */
    private String bossBarTitle() {
        String golden =
                currentGolden == null
                        ? MessageConfig.BUILD_MART_BOSSBAR_GOLDEN_NONE
                        : MessageConfig.BUILD_MART_BOSSBAR_GOLDEN_ACTIVE
                                .replace("%blueprint%", currentGolden.getDisplayName())
                                .replace("%seconds%", Integer.toString(goldenSecondsRemaining()));
        return MessageConfig.BUILD_MART_BOSSBAR_TITLE
                .replace("%timer%", Integer.toString(timer))
                .replace("%golden%", golden);
    }

    /**
     * Seconds left in the current golden window, derived from elapsed time and the rotation period.
     */
    public int goldenSecondsRemaining() {
        int period = Math.max(1, getGameConfig().getGoldenRefreshSeconds());
        int elapsed = Math.max(0, getGameConfig().getTimer() - timer);
        return Math.max(0, Math.min(timer, period - (elapsed % period)));
    }

    /**
     * Resolves the submit slot ({@code N0/N1/N2/G}) whose physical submit button sits at {@code
     * clicked}, for {@code team}'s base, or {@code null} if the clicked block isn't one of this
     * team's submit buttons.
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

    /**
     * True when the block at {@code worldX/Y/Z} is any team's submit button (protected from
     * breaking).
     */
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

    /** Normal plots submit immediately; golden confirmation is tied to the current order. */
    public void handleSubmitClick(Player player, String slotId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || player == null
                || slotId == null
                || !isGameplayParticipant(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        TeamBuildState state = teamStates.get(team);
        if (state == null) return;
        if (timer <= 10) {
            goldenConfirmation.remove(player.getUniqueId());
            playerManager
                    .getPlayer(player.getUniqueId())
                    .sendMessage(MessageConfig.BUILD_MART_SUBMIT_LOCKED);
            return;
        }
        if (slotId.equals("G")) {
            BuildSlot slot = state.getGoldenSlot();
            if (slot.getBlueprint() == null || slot.getBuildAnchor() == null) return;
            if (!goldenConfirmation.confirm(
                    player.getUniqueId(),
                    team,
                    slot,
                    goldenGeneration,
                    System.nanoTime() / 1_000_000)) {
                playerManager
                        .getPlayer(player.getUniqueId())
                        .sendMessage(MessageConfig.BUILD_MART_GOLDEN_SUBMIT_CONFIRM);
                return;
            }
        }
        submitSlot(player, slotId);
    }

    /** Whether {@code a} and {@code b} are the same block (same world + block coords). */
    private static boolean sameBlock(Location a, Location b) {
        if (a == null || a.getWorld() == null || b == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** Whether {@code a} is the block at {@code worldX/Y/Z} in {@code world}. */
    private static boolean sameBlock(Location a, World world, int worldX, int worldY, int worldZ) {
        if (a == null || a.getWorld() == null || world == null) return false;
        if (!a.getWorld().equals(world)) return false;
        return a.getBlockX() == worldX && a.getBlockY() == worldY && a.getBlockZ() == worldZ;
    }

    /** Builds the round's shared, pre-shuffled normal blueprint sequence. */
    private void prepareNormalBlueprintSequence() {
        BuildMartOrderPool pool = plugin.getGameManager().getBuildMartManager().getOrderPool();
        if (pool == null) {
            normalBlueprintSequence = List.of();
        } else {
            Set<String> excluded = currentGolden == null ? Set.of() : Set.of(currentGolden.getId());
            normalBlueprintSequence =
                    List.copyOf(pool.drawNormal(pool.getNormal().size(), excluded));
        }
        normalSequenceCursors.clear();
        for (ChampionshipTeam team : teamStates.keySet()) {
            normalSequenceCursors.put(team, 0);
        }
    }

    /**
     * Assigns the same pre-shuffled sequence to each team's three plots and pastes its reference
     * build. Called once at round start so every team sees the same initial orders.
     */
    private void assignInitialNormalBlueprints() {
        for (TeamBuildState state : teamStates.values()) {
            ChampionshipTeam team = state.getTeam();
            for (BuildSlot slot : state.getNormalSlots()) {
                if (slot.getReferenceAnchor() == null) continue;
                BuildMartBlueprint blueprint = nextNormalBlueprint(state, slot);
                if (blueprint == null) continue;
                slot.setBlueprint(blueprint);
                ReferenceBuilder.paste(blueprint, slot.getReferenceAnchor());
                refreshMaterialDisplay(team, slot);
                team.sendMessageToAll(
                        MessageConfig.BUILD_MART_BLUEPRINT_AUTO_REFRESHED
                                .replace("%blueprint%", blueprint.getDisplayName())
                                .replace("%stars%", String.valueOf(blueprint.getStars())));
            }
        }
    }

    /**
     * Schedules the next shared-sequence normal blueprint onto {@code slot} {@link
     * #AUTO_REFRESH_SECONDS} after a completion, pasting its reference. Bails silently if the round
     * ended, the slot was reassigned, or the slot has since been filled.
     */
    private void scheduleAutoRefresh(ChampionshipTeam team, BuildSlot slot) {
        final int scheduledRound = roundId;
        scheduler.runTaskLater(
                plugin,
                () -> {
                    if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
                    if (roundId != scheduledRound) return;
                    TeamBuildState state = teamStates.get(team);
                    if (state == null || !state.getNormalSlots().contains(slot)) return;
                    if (!slot.isEmpty()) return;
                    BuildMartBlueprint next = nextNormalBlueprint(state, slot);
                    if (next == null) return;
                    slot.setBlueprint(next);
                    if (slot.getReferenceAnchor() != null)
                        ReferenceBuilder.paste(next, slot.getReferenceAnchor());
                    refreshMaterialDisplay(team, slot);
                    team.sendMessageToAll(
                            MessageConfig.BUILD_MART_BLUEPRINT_AUTO_REFRESHED
                                    .replace("%blueprint%", next.getDisplayName())
                                    .replace("%stars%", String.valueOf(next.getStars())));
                },
                AUTO_REFRESH_SECONDS * 20L);
    }

    private void refreshMaterialDisplay(ChampionshipTeam team, BuildSlot slot) {
        Integer seat = seatByTeam.get(team);
        BuildMartBase base = seat == null ? null : baseCache.get(seat);
        Location button =
                base == null || slot.getIndex() >= base.getNormalSubmitAnchors().size()
                        ? null
                        : base.getNormalSubmitAnchors().get(slot.getIndex());
        materialDisplays.update(slot, button);
    }

    /**
     * Takes the next blueprint for a team's slot from the shared sequence. Each team advances
     * through the same sequence independently, so completion speed can differ without changing the
     * order of its assignments.
     */
    private BuildMartBlueprint nextNormalBlueprint(TeamBuildState state, BuildSlot target) {
        if (normalBlueprintSequence.isEmpty()) return null;
        int cursor = normalSequenceCursors.getOrDefault(state.getTeam(), 0);

        Set<String> excluded = new HashSet<>();
        if (currentGolden != null) excluded.add(currentGolden.getId());
        for (BuildSlot slot : state.getNormalSlots()) {
            if (slot == target) continue;
            BuildMartBlueprint blueprint = slot.getBlueprint();
            if (blueprint != null) excluded.add(blueprint.getId());
        }

        int size = normalBlueprintSequence.size();
        cursor = Math.floorMod(cursor, size);
        for (int offset = 0; offset < size; offset++) {
            int index = (cursor + offset) % size;
            BuildMartBlueprint candidate = normalBlueprintSequence.get(index);
            if (excluded.contains(candidate.getId())) continue;
            normalSequenceCursors.put(state.getTeam(), (index + 1) % size);
            return candidate;
        }
        return null;
    }

    /**
     * IDs currently assigned to normal plots, so a new golden order does not duplicate an active
     * normal order.
     */
    private Collection<String> activeNormalBlueprintIds() {
        Set<String> excluded = new HashSet<>();
        for (TeamBuildState state : teamStates.values()) {
            for (BuildSlot slot : state.getNormalSlots()) {
                BuildMartBlueprint blueprint = slot.getBlueprint();
                if (blueprint != null) excluded.add(blueprint.getId());
            }
        }
        return excluded;
    }

    /**
     * Submits one of the caller's team's build plots for validation (from a physical submit
     * button). The plot is settled and scored only when it fully matches the blueprint; otherwise
     * the player is told how many blocks still differ. {@code slotId} is {@code N0/N1/N2} for a
     * normal plot or {@code G} for golden.
     */
    public void submitSlot(Player player, String slotId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || player == null || slotId == null)
            return;
        if (notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        TeamBuildState state = teamStates.get(team);
        if (state == null) return;

        // Last 10 seconds: no submissions accepted.
        if (timer <= 10) {
            playerManager
                    .getPlayer(player.getUniqueId())
                    .sendMessage(MessageConfig.BUILD_MART_SUBMIT_LOCKED);
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

        BuildMartBlueprint.Comparison comparison =
                blueprint.compare(ReferenceBuilder.buildOrigin(slot.getBuildAnchor()));
        int matched = comparison.matched();
        if (matched >= blueprint.blockCount()) {
            if (golden) {
                completeGoldenBuild(team, state, slot, blueprint);
            } else {
                completeNormalBuild(team, state, slot, blueprint);
            }
        } else if (golden) {
            // Golden incomplete submit: clear the build zone (no material return), must rebuild
            // from scratch.
            ReferenceBuilder.clearBuildArea(slot.getBuildAnchor());
            playerManager
                    .getPlayer(player.getUniqueId())
                    .sendMessage(
                            MessageConfig.BUILD_MART_GOLDEN_SUBMIT_FAILED.replace(
                                    "%blueprint%", blueprint.getDisplayName()));
        } else {
            playerManager
                    .getPlayer(player.getUniqueId())
                    .sendMessage(
                            MessageConfig.BUILD_MART_SUBMIT_INCOMPLETE
                                    .replace("%blueprint%", blueprint.getDisplayName())
                                    .replace("%matched%", String.valueOf(matched))
                                    .replace("%total%", String.valueOf(blueprint.blockCount())));
            playerManager
                    .getPlayer(player.getUniqueId())
                    .sendMessage(
                            MessageConfig.BUILD_MART_SUBMIT_DIFFERENCES
                                    .replace("%missing%", String.valueOf(comparison.missing()))
                                    .replace(
                                            "%material%",
                                            String.valueOf(comparison.wrongMaterial()))
                                    .replace("%state%", String.valueOf(comparison.wrongState())));
        }
    }

    private void completeNormalBuild(
            ChampionshipTeam team,
            TeamBuildState state,
            BuildSlot slot,
            BuildMartBlueprint blueprint) {
        int points = pointsForCompletion(blueprint.getStars());
        addPlayerPointsToAllTeamMembers(team, points);
        state.recordCompletion(blueprint.getStars());

        // Clear the player's copy and the reference; a fresh blueprint auto-appears shortly.
        if (slot.getBuildAnchor() != null) ReferenceBuilder.clearBuildArea(slot.getBuildAnchor());
        if (slot.getReferenceAnchor() != null)
            ReferenceBuilder.clear(blueprint, slot.getReferenceAnchor());
        materialDisplays.remove(slot);
        slot.clear();
        scheduleAutoRefresh(team, slot);

        String completion =
                MessageConfig.BUILD_MART_BUILD_COMPLETED
                        .replace("%team%", team.getColoredName())
                        .replace("%blueprint%", blueprint.getDisplayName())
                        .replace("%stars%", String.valueOf(blueprint.getStars()))
                        .replace("%points%", String.valueOf(points));
        sendMessageToAllGamePlayers(completion);
        for (Player player : team.getOnlinePlayers()) {
            CoreMessages.sendActionBar(player, completion);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.5F);
        }
    }

    /**
     * Expires the current golden order (clearing any unfinished golden zones as a penalty) and
     * surfaces a fresh one in the hub display, assigning it to every team's golden slot. A no-op
     * pick when the golden pool is empty.
     */
    private void rotateGoldenBlueprint() {
        rotateGoldenBlueprint(true);
    }

    private void rotateGoldenBlueprint(boolean announce) {
        if (announce && getGameStageEnum() != GameStageEnum.PROGRESS) return;
        goldenConfirmation.clear();
        goldenGeneration++;
        expireCurrentGolden();

        BuildMartBlueprint next =
                plugin.getGameManager()
                        .getBuildMartManager()
                        .getOrderPool()
                        .randomGolden(activeNormalBlueprintIds());
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

    /**
     * Penalises unfinished golden builds: clears their zones, the team slots, and the hub display.
     */
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

    private void completeGoldenBuild(
            ChampionshipTeam team,
            TeamBuildState state,
            BuildSlot slot,
            BuildMartBlueprint blueprint) {
        int points = pointsForCompletion(BuildMartOrderPool.GOLDEN_SCORE_STARS);
        addPlayerPointsToAllTeamMembers(team, points);
        state.recordCompletion(BuildMartOrderPool.GOLDEN_SCORE_STARS);

        if (slot.getBuildAnchor() != null) ReferenceBuilder.clearBuildArea(slot.getBuildAnchor());
        // Clear only this team's golden slot so they can't re-score; other teams keep building it.
        slot.clear();

        String completion =
                MessageConfig.BUILD_MART_GOLDEN_BUILD_COMPLETED
                        .replace("%team%", team.getColoredName())
                        .replace("%blueprint%", blueprint.getDisplayName())
                        .replace("%points%", String.valueOf(points));
        sendMessageToAllGamePlayers(completion);
        for (Player player : team.getOnlinePlayers()) {
            CoreMessages.sendActionBar(player, completion);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
        }
    }

    /**
     * Whole points a completed build is worth: {@code stars × per-star rate} for the current
     * minute.
     */
    public int pointsForCompletion(int stars) {
        return stars * pointsPerStar(elapsedMinutes());
    }

    /** Per-star rate: 10 before minute 5, 15 from minute 5, and 20 from minute 10. */
    private static int pointsPerStar(int minutes) {
        if (minutes < 5) return 10;
        if (minutes < 10) return 15;
        return 20;
    }

    /** Minutes elapsed since the round began, derived from the countdown timer. */
    public int elapsedMinutes() {
        int elapsedSeconds = Math.max(0, getGameConfig().getTimer() - timer);
        return elapsedSeconds / 60;
    }

    /**
     * True when the block at {@code worldX/Y/Z} belongs to any active reference build, so the
     * handler can cancel breaks that would damage a reference.
     */
    public boolean isProtectedReferenceBlock(World world, int worldX, int worldY, int worldZ) {
        // Normal-plot reference builds.
        for (TeamBuildState state : teamStates.values()) {
            for (BuildSlot slot : state.getNormalSlots()) {
                if (matchesReferenceArea(
                        slot.getReferenceAnchor(), world, worldX, worldY, worldZ)) {
                    return true;
                }
            }
        }
        // The shared golden display build (golden has no per-base reference, only the hub display).
        return matchesReferenceArea(
                getGameConfig().getGoldenDisplayPoint(), world, worldX, worldY, worldZ);
    }

    /**
     * True when the block lies inside one of the specified team's four fixed 7x7x7 build volumes.
     */
    public boolean isBuildZoneBlock(
            ChampionshipTeam team, World world, int worldX, int worldY, int worldZ) {
        TeamBuildState state = teamStates.get(team);
        if (state == null) return false;
        for (BuildSlot slot : state.getNormalSlots()) {
            if (matchesBuildArea(slot.getBuildAnchor(), world, worldX, worldY, worldZ)) return true;
        }
        return matchesBuildArea(
                state.getGoldenSlot().getBuildAnchor(), world, worldX, worldY, worldZ);
    }

    /** True when the block lies inside any configured material refill cuboid. */
    public boolean isMaterialZoneBlock(World world, int worldX, int worldY, int worldZ) {
        if (world == null || !world.getName().equals(getWorldName())) return false;
        for (BuildMartMaterialZone zone : getGameConfig().getMaterialZones()) {
            if (worldX >= zone.minX()
                    && worldX <= zone.maxX()
                    && worldY >= zone.minY()
                    && worldY <= zone.maxY()
                    && worldZ >= zone.minZ()
                    && worldZ <= zone.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesReferenceArea(Location anchor, World world, int x, int y, int z) {
        return anchor != null
                && (ReferenceBuilder.isBuildAreaBlock(anchor, world, x, y, z)
                        || y == anchor.getBlockY()
                                && ReferenceBuilder.isBuildAreaBlock(anchor, world, x, y + 1, z));
    }

    /** Natural movement may stay within one plot/material cuboid, never cross into another zone. */
    public boolean allowsBlockTransfer(org.bukkit.block.Block from, org.bukkit.block.Block to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        for (TeamBuildState state : teamStates.values()) {
            for (BuildSlot slot : state.getNormalSlots()) {
                if (sameBuildVolume(slot.getBuildAnchor(), from, to)) return true;
            }
            if (sameBuildVolume(state.getGoldenSlot().getBuildAnchor(), from, to)) return true;
        }
        if (!from.getWorld().getName().equals(getWorldName())) return false;
        for (BuildMartMaterialZone zone : getGameConfig().getMaterialZones()) {
            if (inMaterialVolume(zone, from) && inMaterialVolume(zone, to)) return true;
        }
        return false;
    }

    private static boolean sameBuildVolume(
            Location anchor, org.bukkit.block.Block from, org.bukkit.block.Block to) {
        return matchesBuildArea(anchor, from.getWorld(), from.getX(), from.getY(), from.getZ())
                && matchesBuildArea(anchor, to.getWorld(), to.getX(), to.getY(), to.getZ());
    }

    private static boolean inMaterialVolume(
            BuildMartMaterialZone zone, org.bukkit.block.Block block) {
        return block.getX() >= zone.minX()
                && block.getX() <= zone.maxX()
                && block.getY() >= zone.minY()
                && block.getY() <= zone.maxY()
                && block.getZ() >= zone.minZ()
                && block.getZ() <= zone.maxZ();
    }

    private static boolean matchesBuildArea(
            Location anchor, World world, int worldX, int worldY, int worldZ) {
        return anchor != null
                && ReferenceBuilder.isBuildAreaBlock(anchor, world, worldX, worldY, worldZ);
    }

    /**
     * Final settlement: awards proportional points for every unfinished build (normal + golden),
     * then hands out the three end-of-game awards (entrepreneur / chef / quality assurance) to the
     * top three teams on each metric, +25/+15/+5 per member.
     */
    private void settleEndGame() {
        for (TeamBuildState state : teamStates.values()) {
            ChampionshipTeam team = state.getTeam();
            for (BuildSlot slot : state.getNormalSlots()) {
                scoreIncomplete(team, slot);
            }
            scoreIncomplete(team, state.getGoldenSlot());
        }

        awardAndAnnounce(
                BuildMartScorer.rankByEntrepreneur(teamStates.values()),
                MessageConfig.BUILD_MART_AWARD_ENTREPRENEUR);
        awardAndAnnounce(
                BuildMartScorer.rankByChef(teamStates.values()),
                MessageConfig.BUILD_MART_AWARD_CHEF);
        awardAndAnnounce(
                BuildMartScorer.rankByQuality(teamStates.values()),
                MessageConfig.BUILD_MART_AWARD_QUALITY);
    }

    /** Awards a fraction of a build's points for an unfinished slot, scaled by completion. */
    private void scoreIncomplete(ChampionshipTeam team, BuildSlot slot) {
        BuildMartBlueprint blueprint = slot.getBlueprint();
        if (blueprint == null || slot.getBuildAnchor() == null) return;
        double ratio =
                blueprint.completionRatio(ReferenceBuilder.buildOrigin(slot.getBuildAnchor()));
        if (ratio <= 0) return;
        int scoringStars =
                slot.isGolden() ? BuildMartOrderPool.GOLDEN_SCORE_STARS : blueprint.getStars();
        int points = (int) Math.round(pointsForCompletion(scoringStars) * ratio);
        if (points > 0) addPlayerPointsToAllTeamMembers(team, points);
    }

    /**
     * Gives the {@code +25/+15/+5} award bonus to the top three teams of a ranking and announces
     * #1.
     */
    private void awardAndAnnounce(List<TeamBuildState> ranking, String awardMessage) {
        for (int i = 0; i < ranking.size() && i < BuildMartScorer.AWARD_POINTS.length; i++) {
            addPlayerPointsToAllTeamMembers(
                    ranking.get(i).getTeam(), BuildMartScorer.AWARD_POINTS[i]);
        }
        if (!ranking.isEmpty()) {
            sendMessageToAllGamePlayers(
                    awardMessage.replace("%team%", ranking.get(0).getTeam().getColoredName()));
        }
    }

    /** Teleports each participating team to its seat's configured portal landing point. */
    private void teleportTeamsToBases() {
        Location hub = getGameConfig().getHubPortalPoint();
        for (ChampionshipTeam team : gameTeams) {
            Integer seat = seatByTeam.get(team);
            BuildMartBase base = seat == null ? null : baseCache.get(seat);
            Location target =
                    base != null && base.getPortalPoint() != null ? base.getPortalPoint() : hub;
            if (target == null) target = getSpectatorSpawnLocation();
            int playerIndex = 0;
            for (Player player : team.getOnlinePlayers()) {
                if (gamePlayers.contains(player.getUniqueId())) {
                    player.teleport(
                            TeleportPositions.getCollisionSafeTeleportLocation(
                                    target, playerIndex++));
                }
            }
        }
    }

    private Location teamBaseSpawn(ChampionshipTeam team) {
        Integer seat = team == null ? null : seatByTeam.get(team);
        BuildMartBase base = seat == null ? null : baseCache.get(seat);
        Location target =
                base != null && base.getPortalPoint() != null
                        ? base.getPortalPoint()
                        : getGameConfig().getHubPortalPoint();
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
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        materialDisplays.clear();
        clearEquipmentMapRenderer();

        if (startGameProgressTask != null) startGameProgressTask.cancel();
        clearMaterialRefills();
        clearJumpPads();
        goldenBlueprintScheduler = null;
        goldenConfirmation.clear();

        getGameHandler().clearCooldowns();
        disableFlightForAllGamePlayers();

        cleanInventoryForAllGamePlayers();

        announceGameEnd(
                MessageConfig.BUILD_MART_GAME_END_TITLE,
                MessageConfig.BUILD_MART_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        if (isSettlementAllowed()) {
            settleEndGame();
            sendMessageToAllGamePlayers(getTeamPointsRank());
        }
        addPlayerPointsToDatabase();

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
    }

    /** Clears any build-zone flight permission so players don't keep flying back in the lobby. */
    private void disableFlightForAllGamePlayers() {
        for (java.util.UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null
                    && player.getGameMode() != GameMode.CREATIVE
                    && !isManagedSpectator(player)) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        // No drops in Build Mart. During formal preparation/countdown, keep the player at their own
        // base;
        // a death during the live round returns them to the shared resource hub.
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        scheduler.runTask(
                plugin,
                () -> {
                    player.spigot().respawn();
                    Location target =
                            getGameStageEnum() == GameStageEnum.PREPARATION
                                            || getGameStageEnum() == GameStageEnum.COUNTDOWN
                                    ? teamBaseSpawn(plugin.getTeamManager().getTeamByPlayer(player))
                                    : getGameConfig().getHubPortalPoint();
                    if (target != null) player.teleport(target);
                });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        goldenConfirmation.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.PREPARATION) {
            Location target =
                    isIntroductionPhase()
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
