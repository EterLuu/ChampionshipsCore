package ink.ziip.championshipscore.api.game.acerace;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
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
import org.bukkit.entity.Player;
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
    private static final long WALK_ON_LAUNCH_DELAY_TICKS = 2L;
    private static final long AIRBORNE_LAUNCH_DELAY_TICKS = 4L;
    private static final double INTERMEDIATE_RIPTIDE_EXTRA_MULTIPLIER = 0.25D;
    private static final int SPEED_STATION_RADIUS = 2;
    private static final int WATER_SPEED_STATION_RADIUS = 4;
    private static final double SPEED_STATION_RADIUS_SQUARED = SPEED_STATION_RADIUS * SPEED_STATION_RADIUS;
    private static final double WATER_SPEED_STATION_RADIUS_SQUARED =
            WATER_SPEED_STATION_RADIUS * WATER_SPEED_STATION_RADIUS;

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
    private final Map<UUID, Boolean> lastGroundedStates = new HashMap<>();
    /** A player must leave the start line before a finish-line crossing can count for the race. */
    private final Set<UUID> startLineArmed = new HashSet<>();
    private final Map<UUID, TrackFeatureContact> featureContacts = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingLaunchPadTasks = new HashMap<>();
    @Getter
    private int timer;
    private BukkitTask progressTask;

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
        restoreAllRacerVisibility();
        cancelAllPendingLaunchPads();
        finishedPlayers.clear();
        nextProgressPoint.clear();
        completedLaps.clear();
        latestRespawnLocations.clear();
        capturedRespawnPoints.clear();
        activeFallHeights.clear();
        lastMoveLocations.clear();
        lastGroundedStates.clear();
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
        hideAllRacers();
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
            lastGroundedStates.put(uuid, true);
        }
        startFinalCountdown(GameTypeEnum.AceRace.toString(), MessageConfig.ACE_RACE_GAME_START_TITLE,
                MessageConfig.ACE_RACE_GAME_START_SUBTITLE, this::beginGameProgress);
    }

    private void beginGameProgress() {
        giveTeamArmor();
        progressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            refreshEnvironmentalEffects();
            timer = seconds;
            changeLevelForAllGamePlayers(seconds);
            updateSpectatorTimerBossBar(MessageConfig.ACE_RACE_ACTION_BAR_COUNT_DOWN
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
        boolean enteredFromAir = !lastGroundedStates.getOrDefault(uuid, player.isOnGround())
                || !player.isOnGround();
        lastGroundedStates.put(uuid, player.isOnGround());
        // Apply stations before fall recovery as well: a fast jump into a water ring may cross the
        // station before the next movement event that would otherwise refresh its short effect.
        handleEnvironmentalEffects(player);
        if (hasReachedActiveFallHeight(player) || notInArea(current)) {
            returnToLatestRespawnPoint(player);
            return;
        }
        handleTrackBlockFeature(player, enteredFromAir);

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
            return finishLine.crossedFromReferenceSide(previous, current, startCenter);
        Location finishCenter = finishLine.center(world);
        Vector forward = finishCenter.toVector().subtract(startCenter.toVector());
        forward.setY(0D);
        Vector movement = current.toVector().subtract(previous.toVector());
        movement.setY(0D);
        return forward.lengthSquared() > 0.0001D && movement.dot(forward) > 0D;
    }

    private void handleTrackBlockFeature(@NotNull Player player, boolean enteredFromAir) {
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
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.5F);
                Utils.sendActionBar(player, MessageConfig.ACE_RACE_SPEED_BOOST);
            }
            case LIME_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20, 6, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 0.8F, 1.2F);
                Utils.sendActionBar(player, MessageConfig.ACE_RACE_JUMP_PAD);
            }
            case RED_WOOL -> scheduleLaunchPlayer(player, material, 2D, 0.8D, 1.1F,
                    launchDelayTicks(enteredFromAir));
            case ORANGE_WOOL -> scheduleLaunchPlayer(player, material, 4D, 1.5D, 1.35F,
                    launchDelayTicks(enteredFromAir));
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

    public boolean shouldSuppressLaunchPadJump(@NotNull Player player, @NotNull Location jumpOrigin) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || notAreaPlayer(player)
                || finishedPlayers.contains(player.getUniqueId())) return false;
        return isLaunchPad(jumpOrigin.getBlock().getRelative(BlockFace.DOWN).getType());
    }

    private static long launchDelayTicks(boolean enteredFromAir) {
        return enteredFromAir ? AIRBORNE_LAUNCH_DELAY_TICKS : WALK_ON_LAUNCH_DELAY_TICKS;
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 7, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 20, 0, true, false, false));
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
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (player == null || team == null) continue;
            player.getInventory().setHelmet(unbreakable(team.getHelmet()));
            player.getInventory().setLeggings(unbreakableSwiftSneakLeggings(team.getLeggings()));
            player.getInventory().setBoots(unbreakableDepthStriderBoots(team.getBoots()));
        }
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

        // Paper fires this event immediately before adding the vanilla riptide vector. Adding 25%
        // here makes Riptide I travel halfway between the vanilla level I and level II impulses.
        Vector extraVelocity = event.getVelocity().multiply(INTERMEDIATE_RIPTIDE_EXTRA_MULTIPLIER);
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
        direction.setY(0D);
        if (direction.lengthSquared() < 0.0001D) direction = new Vector(0D, 0D, 1D);
        else direction.normalize();
        // setVelocity replaces the old motion with this fixed launch vector; existing momentum is not
        // carried into the pad's horizontal or vertical impulse.
        player.setVelocity(new Vector(direction.getX() * horizontalVelocity, verticalVelocity,
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
        lastGroundedStates.put(player.getUniqueId(), true);
        Utils.sendActionBar(player, MessageConfig.ACE_RACE_RETURNED_TO_RESPAWN_POINT);
    }

    public int getPlayerPosition(@NotNull UUID uuid) {
        if (!gamePlayers.contains(uuid)) return 0;
        int finishedIndex = finishedPlayers.indexOf(uuid);
        if (finishedIndex >= 0) return finishedIndex + 1;

        int progress = playerProgress(uuid);
        int ahead = finishedPlayers.size();
        for (UUID other : gamePlayers) {
            if (!finishedPlayers.contains(other) && playerProgress(other) > progress) ahead++;
        }
        return ahead + 1;
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
        cancelPendingLaunchPad(event.getPlayer().getUniqueId());
        if (!notAreaPlayer(event.getPlayer())) releaseRacerVisibility(event.getPlayer());
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        if (!finishedPlayers.contains(player.getUniqueId())) hideRacer(player);
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
                int reached = nextProgressPoint.getOrDefault(player.getUniqueId(), 0) - 1;
                applyProgressPointEquipment(player, reached >= 0 && reached < progressPoints.size()
                        ? progressPoints.get(reached).equipment() : AceRaceEquipment.NONE);
            }
        } else {
            releaseRacerVisibility(player);
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void hideAllRacers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) hideRacer(player);
        }
    }

    private void hideRacer(@NotNull Player player) {
        player.setCollidable(false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.hidePlayer(plugin, other);
            if (gamePlayers.contains(other.getUniqueId()) && !finishedPlayers.contains(other.getUniqueId())) {
                other.hidePlayer(plugin, player);
                other.setCollidable(false);
            }
        }
    }

    private void restoreRacerVisibility(@NotNull Player player) {
        player.setCollidable(true);
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
            if (player == null) continue;
            player.setCollidable(true);
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
        player.setCollidable(true);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.showPlayer(plugin, other);
            other.showPlayer(plugin, player);
        }
    }

    /** Keeps a newly joined observer hidden from active racers without hiding racers from observers. */
    public void handleVisibilityJoin(@NotNull Player joining) {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        for (UUID uuid : gamePlayers) {
            Player racer = Bukkit.getPlayer(uuid);
            if (racer != null && !racer.equals(joining) && !finishedPlayers.contains(uuid))
                racer.hidePlayer(plugin, joining);
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
        return "acerace";
    }
}
