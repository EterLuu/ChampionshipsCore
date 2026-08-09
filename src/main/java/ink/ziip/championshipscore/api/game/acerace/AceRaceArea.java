package ink.ziip.championshipscore.api.game.acerace;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Enchants;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A lap race with ordered progress gates and independent proximity-based respawn points. */
public class AceRaceArea extends BaseMultiTeamGameInstance {
    private static final long LAUNCH_PAD_DELAY_TICKS = 2L;
    private static final int JUMP_BOOST_DURATION_TICKS = 14;
    private static final int SPEED_BOOST_DURATION_TICKS = 100;
    private static final int RED_SPEED_DURATION_TICKS = 16;
    private static final double RIPTIDE_EXTRA_MULTIPLIER = 0.03D;
    private static final int SPEED_STATION_RADIUS = 2;
    private static final int WATER_SPEED_STATION_RADIUS = 4;
    private static final double SPEED_STATION_RADIUS_SQUARED = SPEED_STATION_RADIUS * SPEED_STATION_RADIUS;
    private static final double WATER_SPEED_STATION_RADIUS_SQUARED =
            WATER_SPEED_STATION_RADIUS * WATER_SPEED_STATION_RADIUS;
    private static final long RACER_VISIBILITY_HIDDEN_AFTER_START_TICKS = 60L * 20L;
    private static final double RACER_VISIBILITY_DISTANCE_SQUARED = 8D * 8D;
    private static final long RACER_VISIBILITY_UPDATE_TICKS = 1L;
    private static final String COLLISION_TEAM_PREFIX = "cc_ar_";

    @Getter
    private final List<AceRaceProgressPoint> progressPoints = new ArrayList<>();
    private final List<AceRaceRespawnPoint> respawnPoints = new ArrayList<>();
    @Getter
    private final List<UUID> finishedPlayers = new ArrayList<>();
    private final Map<UUID, Integer> nextProgressPoint = new HashMap<>();
    private final Map<UUID, Integer> completedLaps = new HashMap<>();
    private final Map<UUID, Location> latestRespawnLocations = new HashMap<>();
    private final Map<UUID, Set<Integer>> capturedRespawnPoints = new HashMap<>();
    private final Map<UUID, Integer> activeFallHeights = new HashMap<>();
    private final Map<UUID, Location> lastMoveLocations = new HashMap<>();
    /** A player must leave the start line before a finish-line crossing can count for the race. */
    private final Set<UUID> startLineArmed = new HashSet<>();
    private final Map<UUID, TrackFeatureContact> featureContacts = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingLaunchPadTasks = new HashMap<>();
    private final Set<RacerPair> visibleRacerPairs = new HashSet<>();
    private final Set<RacerView> riptideHiddenViews = new HashSet<>();
    private final Map<UUID, Integer> riptideViewerGraceTicks = new HashMap<>();
    private final Map<UUID, TextDisplay> racerNameDisplays = new HashMap<>();
    private final Map<UUID, String> originalScoreboardTeams = new HashMap<>();
    @Getter
    private int timer;
    private BukkitTask progressTask;
    private BukkitTask racerVisibilityTask;
    private BukkitTask racerVisibilityUnlockTask;
    private boolean racerVisibilityUnlocked;

    public AceRaceArea(ChampionshipsCore plugin, AceRaceConfig config) {
        super(plugin, GameTypeEnum.AceRace, new AceRaceHandler(plugin), config);
        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setAceRaceArea(this);
        getGameHandler().register();
        loadCoursePoints();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    public void loadCoursePoints() {
        progressPoints.clear();
        ConfigurationSection root = getGameConfig().getProgressPoints();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) continue;
                Vector pos1 = section.getVector("pos1");
                Vector pos2 = section.getVector("pos2");
                if (pos1 == null || pos2 == null) {
                    logGame(java.util.logging.Level.WARNING, "进度点", "跳过不完整进度点=" + key);
                    continue;
                }
                progressPoints.add(new AceRaceProgressPoint(
                        section.getInt("order", progressPoints.size() + 1), pos1, pos2,
                        section.getInt("fall-y", getWorldFallHeight()),
                        AceRaceEquipment.fromConfig(section.getString("equipment"))));
            }
            progressPoints.sort(Comparator.comparingInt(AceRaceProgressPoint::order));
        }

        respawnPoints.clear();
        for (String serialized : getGameConfig().ensureRespawnPoints()) {
            try {
                Location location = Utils.getLocation(serialized);
                if (location.getWorld() == null || !getWorldName().equals(location.getWorld().getName())) {
                    logGame(java.util.logging.Level.WARNING, "重生点", "跳过世界无效的重生点=" + serialized);
                    continue;
                }
                respawnPoints.add(new AceRaceRespawnPoint(location));
            } catch (Exception exception) {
                logGame(java.util.logging.Level.WARNING, "重生点", "跳过格式无效的重生点=" + serialized);
            }
        }
    }

    private int getWorldFallHeight() {
        World world = Bukkit.getWorld(getWorldName());
        return world == null ? -64 : world.getMinHeight();
    }

    private AceRaceLine getStartLine() {
        AceRaceConfig config = getGameConfig();
        return config.hasStartLine() ? new AceRaceLine(config.getStartLinePos1(), config.getStartLinePos2()) : null;
    }

    private AceRaceLine getFinishLine() {
        AceRaceConfig config = getGameConfig();
        return config.hasFinishLine() ? new AceRaceLine(config.getFinishLinePos1(), config.getFinishLinePos2()) : null;
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        if (getGameConfig().getStartSpawnPoint() != null) locations.add(getGameConfig().getStartSpawnPoint());
        if (getGameConfig().getSpectatorSpawnPoint() != null) locations.add(getGameConfig().getSpectatorSpawnPoint());
        for (AceRaceRespawnPoint respawnPoint : respawnPoints) locations.add(respawnPoint.destination());
        return locations;
    }

    @Override
    public void resetArea() {
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        cancelAllPendingLaunchPads();
        finishedPlayers.clear();
        nextProgressPoint.clear();
        completedLaps.clear();
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
        if (progressPoints.isEmpty() || respawnPoints.isEmpty() || getGameConfig().getStartSpawnPoint() == null
                || !getGameConfig().hasStartLine() || !getGameConfig().hasFinishLine()) {
            logGame(java.util.logging.Level.WARNING, "启动",
                    "赛道缺少进度点、重生点、起点出生点、起点线或终点线，已取消本局");
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
        announceGamePreparation(MessageConfig.ACE_RACE_START_PREPARATION,
                MessageConfig.ACE_RACE_START_PREPARATION_TITLE, MessageConfig.ACE_RACE_START_PREPARATION_SUBTITLE);
        startGameProgress();
    }

    private void startGameProgress() {
        Location start = getGameConfig().getStartSpawnPoint();
        for (UUID uuid : gamePlayers) {
            nextProgressPoint.put(uuid, 0);
            completedLaps.put(uuid, 0);
            latestRespawnLocations.put(uuid, start.clone());
            capturedRespawnPoints.put(uuid, new HashSet<>());
            activeFallHeights.put(uuid, getGameConfig().getStartFallY());
            lastMoveLocations.put(uuid, start.clone());
        }
        startFinalCountdown(GameTypeEnum.AceRace.toString(), MessageConfig.ACE_RACE_GAME_START_TITLE,
                MessageConfig.ACE_RACE_GAME_START_SUBTITLE, this::beginGameProgress);
    }

    private void beginGameProgress() {
        scheduleRacerVisibilityUnlock();
        giveTeamArmor();
        progressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            refreshEnvironmentalEffects();
            timer = seconds;
            updateGameTimerBossBar(MessageConfig.ACE_RACE_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(seconds)), seconds, getGameConfig().getTimer());
        }, this::endGame);
    }

    public void handlePlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (getGameStageEnum() != GameStageEnum.PROGRESS || finishedPlayers.contains(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        Location current = player.getLocation();
        Location previous = lastMoveLocations.put(uuid, current.clone());
        if (previous == null) previous = current;
        // Apply stations before fall recovery as well: a fast jump into a water ring may cross the
        // station before the next movement event that would otherwise refresh its short effect.
        handleEnvironmentalEffects(player);
        if (hasReachedActiveFallHeight(player) || notInArea(current)) {
            returnToLatestRespawnPoint(player);
            return;
        }
        handleTrackBlockFeature(player);

        handleProgressPoint(player, previous, current);
        handleRespawnPoints(player, previous, current);
        handleStartAndFinishLines(player, previous, current);
    }

    private void handleProgressPoint(@NotNull Player player, @NotNull Location previous,
                                     @NotNull Location current) {
        int expected = nextProgressPoint.getOrDefault(player.getUniqueId(), 0);
        if (expected >= progressPoints.size()) return;
        AceRaceProgressPoint progressPoint = progressPoints.get(expected);
        if (!progressPoint.crossed(previous, current)) return;

        activeFallHeights.put(player.getUniqueId(), progressPoint.fallY());
        nextProgressPoint.put(player.getUniqueId(), expected + 1);
        applyProgressPointEquipment(player, progressPoint.equipment());
        announceProgressPointEquipment(player, progressPoint.equipment());
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7F, 1F);
    }

    private void handleRespawnPoints(@NotNull Player player, @NotNull Location previous,
                                     @NotNull Location current) {
        UUID uuid = player.getUniqueId();
        Set<Integer> captured = capturedRespawnPoints.computeIfAbsent(uuid, ignored -> new HashSet<>());
        for (int index = 0; index < respawnPoints.size(); index++) {
            if (captured.contains(index)) continue;
            AceRaceRespawnPoint respawnPoint = respawnPoints.get(index);
            if (!respawnPoint.reached(previous, current)) continue;
            captured.add(index);
            latestRespawnLocations.put(uuid, respawnPoint.destination());
        }
    }

    private void handleStartAndFinishLines(@NotNull Player player, @NotNull Location previous,
                                           @NotNull Location current) {
        AceRaceLine startLine = getStartLine();
        AceRaceLine finishLine = getFinishLine();
        if (startLine == null || finishLine == null) return;
        UUID uuid = player.getUniqueId();
        if (!startLineArmed.contains(uuid)) {
            if (startLine.crossedAtOrAbove(previous, current)) startLineArmed.add(uuid);
            return;
        }
        if (nextProgressPoint.getOrDefault(uuid, 0) < progressPoints.size()
                || !crossedFinishForward(finishLine, previous, current, startLine)) return;

        int lap = completedLaps.getOrDefault(player.getUniqueId(), 0) + 1;
        completedLaps.put(player.getUniqueId(), lap);
        if (lap < getGameConfig().getLaps()) {
            resetLapProgress(player, current);
            String message = MessageConfig.ACE_RACE_LAP_COMPLETED
                    .replace("%player%", Utils.formatPlayerName(player))
                    .replace("%lap%", String.valueOf(lap))
                    .replace("%total%", String.valueOf(getGameConfig().getLaps()));
            sendMessageToAllGamePlayers(message);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
            return;
        }
        finishPlayer(player);
    }

    /** Starts a new lap without carrying any ordered gate or respawn marker state across the line. */
    private void resetLapProgress(@NotNull Player player, @NotNull Location movementBaseline) {
        UUID uuid = player.getUniqueId();
        nextProgressPoint.put(uuid, 0);
        activeFallHeights.put(uuid, getGameConfig().getStartFallY());
        applyProgressPointEquipment(player, AceRaceEquipment.NONE);
        Location start = getGameConfig().getStartSpawnPoint();
        if (start != null) latestRespawnLocations.put(uuid, start.clone());
        capturedRespawnPoints.computeIfAbsent(uuid, ignored -> new HashSet<>()).clear();
        startLineArmed.remove(uuid);
        lastMoveLocations.put(uuid, movementBaseline.clone());
    }

    private boolean crossedFinishForward(@NotNull AceRaceLine finishLine, @NotNull Location previous,
                                         @NotNull Location current, @NotNull AceRaceLine startLine) {
        if (!finishLine.crossedAtOrAbove(previous, current)) return false;
        World world = current.getWorld();
        if (world == null) return false;
        Location startCenter = startLine.center(world);
        if (finishLine.sameGeometry(startLine)) {
            Location startSpawn = getGameConfig().getStartSpawnPoint();
            return startSpawn != null && finishLine.crossedTowardReferenceSide(previous, current, startSpawn);
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
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        Material material = block.getType();
        if (!isTrackFeature(material)) {
            featureContacts.remove(player.getUniqueId());
            cancelPendingLaunchPad(player.getUniqueId());
            return;
        }
        if (!isLaunchPad(material)) cancelPendingLaunchPad(player.getUniqueId());
        TrackFeatureContact featureContact = TrackFeatureContact.from(block);
        if (featureContact.equals(featureContacts.put(player.getUniqueId(), featureContact))) return;

        switch (material) {
            case YELLOW_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        SPEED_BOOST_DURATION_TICKS, 2, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.5F);
                Utils.sendActionBar(player, MessageConfig.ACE_RACE_SPEED_BOOST);
            }
            case LIME_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                        JUMP_BOOST_DURATION_TICKS, 6, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 0.8F, 1.2F);
                Utils.sendActionBar(player, MessageConfig.ACE_RACE_JUMP_PAD);
            }
            case RED_WOOL -> scheduleLaunchPlayer(player, material, 2.05D, 0.75D, 1.1F,
                    LAUNCH_PAD_DELAY_TICKS);
            case ORANGE_WOOL -> scheduleLaunchPlayer(player, material, 4D, 1.5D, 1.35F,
                    LAUNCH_PAD_DELAY_TICKS);
            default -> {
            }
        }
    }

    private static boolean isTrackFeature(@NotNull Material material) {
        return material == Material.YELLOW_GLAZED_TERRACOTTA
                || material == Material.LIME_GLAZED_TERRACOTTA
                || material == Material.RED_WOOL
                || material == Material.ORANGE_WOOL;
    }

    private static boolean isLaunchPad(@NotNull Material material) {
        return material == Material.RED_WOOL || material == Material.ORANGE_WOOL;
    }

    public void suppressLaunchPadJump(@NotNull Player player, @NotNull Location jumpOrigin) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || notAreaPlayer(player)
                || finishedPlayers.contains(player.getUniqueId())
                || !isLaunchPad(jumpOrigin.getBlock().getRelative(BlockFace.DOWN).getType())) return;

        // Cancelling PlayerJumpEvent makes Paper move the player back to the jump origin. Around a
        // delayed pad launch that correction looks like the pad has pulled the player backwards.
        // Remove only vanilla's upward jump motion; the scheduled pad task will replace the whole
        // velocity with its fixed launch vector, so ordinary jump momentum still cannot stack.
        Vector velocity = player.getVelocity();
        if (velocity.getY() > 0D) {
            velocity.setY(0D);
            player.setVelocity(velocity);
        }
    }

    private void refreshEnvironmentalEffects() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != GameMode.SPECTATOR) handleEnvironmentalEffects(player);
        }
    }

    private void handleEnvironmentalEffects(@NotNull Player player) {
        boolean nearSpeedStation = isNearStation(player, Material.RED_GLAZED_TERRACOTTA,
                SPEED_STATION_RADIUS, SPEED_STATION_RADIUS_SQUARED);
        // The red terracotta commonly forms a ring around a water landing pool. While swimming, use
        // the wider ring radius so a jump landing in the pool cannot skip the intended boost.
        if (!nearSpeedStation && player.isInWater()) {
            nearSpeedStation = isNearStation(player, Material.RED_GLAZED_TERRACOTTA,
                    WATER_SPEED_STATION_RADIUS, WATER_SPEED_STATION_RADIUS_SQUARED);
        }
        if (nearSpeedStation) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    RED_SPEED_DURATION_TICKS, 7, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                    RED_SPEED_DURATION_TICKS, 0, true, false, false));
        }
        if (player.isInWater()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 20, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 2, true, false, false));
        }
    }

    /** Each progress segment owns the threshold at which fall recovery becomes active. */
    private boolean hasReachedActiveFallHeight(@NotNull Player player) {
        return player.getLocation().getY() <= activeFallHeights.getOrDefault(
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
            meta.addEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER), 3, true);
            boots.setItemMeta(meta);
        }
        return boots;
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
        if (getGameStageEnum() != GameStageEnum.PROGRESS || notAreaPlayer(player)
                || finishedPlayers.contains(player.getUniqueId())) return;
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

    private void applyProgressPointEquipment(@NotNull Player player, @NotNull AceRaceEquipment equipment) {
        PlayerInventory inventory = player.getInventory();
        ItemStack chestplate = inventory.getChestplate();
        if (equipment != AceRaceEquipment.ELYTRA
                && chestplate != null && chestplate.getType() == Material.ELYTRA) {
            inventory.setChestplate(null);
        }
        if (equipment != AceRaceEquipment.TRIDENT) removeAllTridents(player);

        if (equipment == AceRaceEquipment.ELYTRA) {
            if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
                inventory.setChestplate(createCourseElytra());
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1F, 1F);
            }
        } else if (equipment == AceRaceEquipment.TRIDENT && !hasTrident(player)) {
            inventory.addItem(createRiptideTrident());
        }
    }

    /** Rebuilds the deterministic race loadout for players who were offline when the race began. */
    private void restoreRacerEquipment(@NotNull Player player) {
        player.getInventory().clear();
        giveTeamArmor(player);
        int reached = nextProgressPoint.getOrDefault(player.getUniqueId(), 0) - 1;
        applyProgressPointEquipment(player, reached >= 0 && reached < progressPoints.size()
                ? progressPoints.get(reached).equipment() : AceRaceEquipment.NONE);
    }

    private void announceProgressPointEquipment(@NotNull Player player, @NotNull AceRaceEquipment equipment) {
        if (equipment == AceRaceEquipment.ELYTRA) {
            player.sendMessage(MessageConfig.ACE_RACE_RECEIVED_ELYTRA);
        } else if (equipment == AceRaceEquipment.TRIDENT) {
            player.sendMessage(MessageConfig.ACE_RACE_RECEIVED_TRIDENT);
        }
    }

    private static boolean hasTrident(@NotNull Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TRIDENT) return true;
        }
        return false;
    }

    private boolean isNearStation(@NotNull Player player, @NotNull Material stationMaterial,
                                  int radius, double radiusSquared) {
        Location location = player.getLocation();
        if (location.getWorld() == null) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int blockX = x - radius; blockX <= x + radius; blockX++) {
            for (int blockY = y - radius; blockY <= y + radius; blockY++) {
                for (int blockZ = z - radius; blockZ <= z + radius; blockZ++) {
                    if (location.getWorld().getBlockAt(blockX, blockY, blockZ).getType() != stationMaterial) continue;
                    double dx = location.getX() - (blockX + 0.5D);
                    double dy = location.getY() - (blockY + 0.5D);
                    double dz = location.getZ() - (blockZ + 0.5D);
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) return true;
                }
            }
        }
        return false;
    }

    private void launchPlayer(@NotNull Player player, double horizontalVelocity, double verticalVelocity, float pitch) {
        Vector direction = player.getLocation().getDirection();
        // Preserve the tuned horizontal range while letting pitch scale the pad's original lift:
        // level aim keeps the old height, looking up approaches double height, and looking down
        // approaches a flat launch without ever firing the racer into the pad.
        double aimedVerticalVelocity = Math.max(0D, verticalVelocity * (1D + direction.getY()));
        direction.setY(0D);
        if (direction.lengthSquared() < 0.0001D) {
            double yawRadians = Math.toRadians(player.getLocation().getYaw());
            direction = new Vector(-Math.sin(yawRadians), 0D, Math.cos(yawRadians));
        }
        else direction.normalize();
        // setVelocity replaces the old motion with this fixed launch vector; existing momentum is not
        // carried into the pad's horizontal or vertical impulse.
        player.setVelocity(new Vector(direction.getX() * horizontalVelocity, aimedVerticalVelocity,
                direction.getZ() * horizontalVelocity));
        player.setFallDistance(0F);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8F, pitch);
        Utils.sendActionBar(player, MessageConfig.ACE_RACE_LAUNCH_PAD);
    }

    private void scheduleLaunchPlayer(@NotNull Player player, @NotNull Material launchPad,
                                      double horizontalVelocity, double verticalVelocity, float pitch,
                                      long delayTicks) {
        UUID uuid = player.getUniqueId();
        cancelPendingLaunchPad(uuid);
        BukkitTask task = scheduler.runTaskLater(plugin, () -> {
            pendingLaunchPadTasks.remove(uuid);
            if (getGameStageEnum() != GameStageEnum.PROGRESS || finishedPlayers.contains(uuid)
                    || !player.isOnline()) return;
            Material currentBlock = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
            if (currentBlock != launchPad) return;
            launchPlayer(player, horizontalVelocity, verticalVelocity, pitch);
        }, delayTicks);
        pendingLaunchPadTasks.put(uuid, task);
    }

    private void cancelPendingLaunchPad(@NotNull UUID uuid) {
        BukkitTask task = pendingLaunchPadTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void cancelAllPendingLaunchPads() {
        for (BukkitTask task : pendingLaunchPadTasks.values()) task.cancel();
        pendingLaunchPadTasks.clear();
    }

    private record TrackFeatureContact(@NotNull UUID worldId, @NotNull Material material, int x, int y, int z) {
        private static @NotNull TrackFeatureContact from(@NotNull Block block) {
            Material material = block.getType();
            if (isLaunchPad(material)) {
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
        if (finishedPlayers.contains(uuid)) return;
        cancelPendingLaunchPad(uuid);
        finishedPlayers.add(uuid);
        int place = finishedPlayers.size();
        int points = Math.max(getGameConfig().getMinimumFinishPoints(),
                getGameConfig().getFirstPlacePoints() - (place - 1) * getGameConfig().getPlacementDecrement())
                + getGameConfig().getPlacementBonus(place);
        addPlayerPoints(uuid, points);
        sendMessageToAllGamePlayers(MessageConfig.ACE_RACE_FINISHED
                .replace("%player%", Utils.formatPlayerName(player))
                .replace("%place%", String.valueOf(place)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.4F);
        applyProgressPointEquipment(player, AceRaceEquipment.NONE);
        restoreRacerVisibility(player);
        player.setGameMode(GameMode.SPECTATOR);
        if (finishedPlayers.size() == gamePlayers.size()) scheduler.runTask(plugin, this::endGame);
    }

    public void returnToLatestRespawnPoint(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || finishedPlayers.contains(player.getUniqueId())) return;
        cancelPendingLaunchPad(player.getUniqueId());
        Location destination = latestRespawnLocations.getOrDefault(
                player.getUniqueId(), getGameConfig().getStartSpawnPoint());
        if (destination == null) return;
        player.teleport(destination);
        player.setFallDistance(0F);
        lastMoveLocations.put(player.getUniqueId(), destination.clone());
        Utils.sendActionBar(player, MessageConfig.ACE_RACE_RETURNED_TO_RESPAWN_POINT);
    }

    public int getPlayerPosition(@NotNull UUID uuid) {
        return getPlayerPlacementRange(uuid).first();
    }

    /**
     * Displays a shared placement interval when multiple unfinished racers have reached the same lap and
     * ordered progress point. Finished racers always retain their exact finish position.
     */
    public @NotNull String getPlayerPositionDisplay(@NotNull UUID uuid) {
        PlacementRange range = getPlayerPlacementRange(uuid);
        return range.first() == range.last()
                ? String.valueOf(range.first())
                : range.first() + "-" + range.last();
    }

    private @NotNull PlacementRange getPlayerPlacementRange(@NotNull UUID uuid) {
        if (!gamePlayers.contains(uuid)) return new PlacementRange(0, 0);
        int finishedIndex = finishedPlayers.indexOf(uuid);
        if (finishedIndex >= 0) {
            int place = finishedIndex + 1;
            return new PlacementRange(place, place);
        }

        int progress = playerProgress(uuid);
        int ahead = finishedPlayers.size();
        int tied = 0;
        for (UUID other : gamePlayers) {
            if (finishedPlayers.contains(other)) continue;
            int otherProgress = playerProgress(other);
            if (otherProgress > progress) ahead++;
            else if (otherProgress == progress) tied++;
        }
        int first = ahead + 1;
        return new PlacementRange(first, first + Math.max(1, tied) - 1);
    }

    public int getCurrentLap(@NotNull UUID uuid) {
        if (!gamePlayers.contains(uuid)) return 0;
        if (finishedPlayers.contains(uuid)) return getGameConfig().getLaps();
        return Math.min(getGameConfig().getLaps(), completedLaps.getOrDefault(uuid, 0) + 1);
    }

    public int getReachedProgressPoint(@NotNull UUID uuid) {
        return gamePlayers.contains(uuid) ? nextProgressPoint.getOrDefault(uuid, 0) : 0;
    }

    private int playerProgress(@NotNull UUID uuid) {
        return completedLaps.getOrDefault(uuid, 0) * progressPoints.size()
                + nextProgressPoint.getOrDefault(uuid, 0);
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        if (progressTask != null) progressTask.cancel();
        cancelAllPendingLaunchPads();
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        cleanInventoryForAllGamePlayers();
        announceGameEnd(MessageConfig.ACE_RACE_GAME_END_TITLE, MessageConfig.ACE_RACE_GAME_END_SUBTITLE);
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                player.setGameMode(GameMode.ADVENTURE);
                returnToLatestRespawnPoint(player);
            }
        });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelPendingLaunchPad(player.getUniqueId());
        if (!notAreaPlayer(player)) {
            visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
            releaseRacerVisibility(player);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        if (!finishedPlayers.contains(player.getUniqueId())) {
            visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
            hideRacer(player);
            refreshRacerVisibility();
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(getGameConfig().getStartSpawnPoint()));
            player.setGameMode(GameMode.ADVENTURE);
        } else if (getGameStageEnum() == GameStageEnum.COUNTDOWN || getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (finishedPlayers.contains(player.getUniqueId())) {
                player.teleport(getSpectatorSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
            } else {
                player.setGameMode(GameMode.ADVENTURE);
                returnToLatestRespawnPoint(player);
                restoreRacerEquipment(player);
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
        racerVisibilityTask = scheduler.runTaskTimer(
                plugin, this::refreshRacerVisibility,
                RACER_VISIBILITY_UPDATE_TICKS, RACER_VISIBILITY_UPDATE_TICKS);
    }

    /** Keeps active racers hidden until the first minute has elapsed, then enables proximity visibility. */
    private void scheduleRacerVisibilityUnlock() {
        if (racerVisibilityUnlockTask != null) racerVisibilityUnlockTask.cancel();
        racerVisibilityUnlocked = false;
        racerVisibilityUnlockTask = scheduler.runTaskLater(plugin, () -> {
            racerVisibilityUnlockTask = null;
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
            racerVisibilityUnlocked = true;
            refreshRacerVisibility();
        }, RACER_VISIBILITY_HIDDEN_AFTER_START_TICKS);
    }

    /** Keeps active racers mutually visible only while they are within eight blocks after the grace period. */
    private void refreshRacerVisibility() {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.WAITING || stage == GameStageEnum.END) return;
        List<Player> racers = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            if (finishedPlayers.contains(uuid)) continue;
            Player racer = Bukkit.getPlayer(uuid);
            if (racer == null) continue;
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
                boolean nearby = firstLocation.getWorld().equals(secondLocation.getWorld())
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
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.hidePlayer(plugin, other);
            if (gamePlayers.contains(other.getUniqueId()) && !finishedPlayers.contains(other.getUniqueId())) {
                other.hidePlayer(plugin, player);
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
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.showPlayer(plugin, other);
            if (gamePlayers.contains(other.getUniqueId()) && !finishedPlayers.contains(other.getUniqueId()))
                other.hidePlayer(plugin, player);
            else
                other.showPlayer(plugin, player);
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
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            restoreRacerCollisions(player);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    player.showPlayer(plugin, other);
                    other.showPlayer(plugin, player);
                }
            }
        }
    }

    /** Clears this plugin's bidirectional hide state before a racer disconnects. */
    private void releaseRacerVisibility(@NotNull Player player) {
        clearRacerOutlines(player);
        removeRacerNameDisplay(player.getUniqueId());
        riptideHiddenViews.removeIf(view -> view.contains(player.getUniqueId()));
        riptideViewerGraceTicks.remove(player.getUniqueId());
        restoreRacerCollisions(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.showPlayer(plugin, other);
            other.showPlayer(plugin, player);
        }
    }

    /** Keeps a newly joined observer hidden from active racers without hiding racers from observers. */
    public void handleVisibilityJoin(@NotNull Player joining) {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        // GameManagerHandler separately restores reconnecting participants. Do not overwrite its
        // distance-based result if this MONITOR join hook happens to run afterwards.
        if (gamePlayers.contains(joining.getUniqueId()) && !finishedPlayers.contains(joining.getUniqueId())) return;
        for (UUID uuid : gamePlayers) {
            Player racer = Bukkit.getPlayer(uuid);
            if (racer != null && !racer.equals(joining) && !finishedPlayers.contains(uuid))
                racer.hidePlayer(plugin, joining);
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

    /** Removes real racer entities from a riptiding player's client before client-side spin contact can occur. */
    public void handleRiptideStart(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || notAreaPlayer(player)) return;
        riptideViewerGraceTicks.put(player.getUniqueId(), 3);
        for (UUID uuid : gamePlayers) {
            if (uuid.equals(player.getUniqueId()) || finishedPlayers.contains(uuid)) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            RacerView view = new RacerView(player.getUniqueId(), target.getUniqueId());
            if (riptideHiddenViews.add(view)) {
                plugin.getGlowingEntities().unsetInvisibleGlowing(target, player);
                player.hidePlayer(plugin, target);
            }
        }
        scheduler.runTask(plugin, () -> restoreAuthoritativeRiptideState(player));
    }

    private void restoreAuthoritativeRiptideState(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || !player.isOnline() || !player.isRiptiding()) return;
        Vector velocity = player.getVelocity();
        // Mark the riptide metadata dirty again and resend the unchanged authoritative velocity. This repairs
        // the local player's state if it began the spin while already overlapping another client entity.
        player.setRiptiding(false);
        player.setRiptiding(true);
        player.setVelocity(velocity);
    }

    private void updateRiptideViewerState(@NotNull List<Player> racers, @NotNull Set<UUID> activeRacers) {
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

    private void updateDirectionalRacerView(@NotNull Player target, @NotNull Player viewer, boolean newlyNearby) {
        RacerView view = new RacerView(viewer.getUniqueId(), target.getUniqueId());
        boolean suppressForRiptide = riptideViewerGraceTicks.containsKey(viewer.getUniqueId());
        if (suppressForRiptide) {
            if (riptideHiddenViews.add(view)) {
                plugin.getGlowingEntities().unsetInvisibleGlowing(target, viewer);
                viewer.hidePlayer(plugin, target);
            }
            return;
        }
        if (riptideHiddenViews.remove(view) || newlyNearby) {
            viewer.showPlayer(plugin, target);
            plugin.getGlowingEntities().setInvisibleGlowing(target, viewer);
        }
    }

    private void clearDirectionalRacerView(@NotNull Player target, @NotNull Player viewer) {
        RacerView view = new RacerView(viewer.getUniqueId(), target.getUniqueId());
        riptideHiddenViews.remove(view);
        plugin.getGlowingEntities().unsetInvisibleGlowing(target, viewer);
        viewer.hidePlayer(plugin, target);
    }

    private @NotNull TextDisplay ensureRacerNameDisplay(@NotNull Player racer) {
        TextDisplay existing = racerNameDisplays.get(racer.getUniqueId());
        if (existing != null && existing.isValid()) return existing;

        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(racer.getUniqueId());
        TextDisplay display = racer.getWorld().spawn(racerNameDisplayLocation(racer), TextDisplay.class, spawned -> {
            spawned.text(net.kyori.adventure.text.Component.text(racer.getName(),
                    team == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : team.getTeam().color()));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
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
     * Disables both server-side entity pushing and the client-predicted collision driven by scoreboard teams.
     * A separate temporary team is kept for each original colour so glowing outlines retain their team colour.
     */
    private void disableRacerCollisions(@NotNull Player player) {
        player.setCollidable(false);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null && !current.getName().startsWith(COLLISION_TEAM_PREFIX))
            originalScoreboardTeams.putIfAbsent(player.getUniqueId(), current.getName());

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        String colorName = championshipTeam == null ? "white" : championshipTeam.getColorName().toLowerCase();
        String collisionTeamName = COLLISION_TEAM_PREFIX + colorName;
        Team collisionTeam = scoreboard.getTeam(collisionTeamName);
        if (collisionTeam == null) collisionTeam = scoreboard.registerNewTeam(collisionTeamName);
        var collisionColor = Utils.toNamedTextColor(colorName);
        if (!collisionTeam.hasColor() || !collisionColor.equals(collisionTeam.color()))
            collisionTeam.color(collisionColor);
        if (collisionTeam.getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER)
            collisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        if (!collisionTeam.hasEntry(player.getName())) collisionTeam.addEntry(player.getName());
    }

    /** Restores the exact scoreboard team occupied before Ace Race and removes empty temporary teams. */
    private void restoreRacerCollisions(@NotNull Player player) {
        player.setCollidable(true);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String originalName = originalScoreboardTeams.remove(player.getUniqueId());
        Team original = originalName == null ? null : scoreboard.getTeam(originalName);
        if (original == null) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
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
            if (team.getName().startsWith(COLLISION_TEAM_PREFIX) && team.getEntries().isEmpty()) team.unregister();
        }
    }

    @Override
    public void dispose() {
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        super.dispose();
    }

    private record PlacementRange(int first, int last) {
    }

    private record RacerPair(@NotNull UUID first, @NotNull UUID second) {
        private static @NotNull RacerPair of(@NotNull UUID first, @NotNull UUID second) {
            return first.compareTo(second) <= 0 ? new RacerPair(first, second) : new RacerPair(second, first);
        }

        private boolean contains(@NotNull UUID uuid) {
            return first.equals(uuid) || second.equals(uuid);
        }

        private boolean bothIn(@NotNull Set<UUID> uuids) {
            return uuids.contains(first) && uuids.contains(second);
        }
    }

    /** Directional view state: {@code viewer} may temporarily hide {@code target} while riptiding. */
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

    /** Ace Race is the sole owner of its world; it has no artificial course boundary. */
    @Override
    public boolean notInArea(Location location) {
        return location == null || location.getWorld() == null
                || !getWorldName().equals(location.getWorld().getName());
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
