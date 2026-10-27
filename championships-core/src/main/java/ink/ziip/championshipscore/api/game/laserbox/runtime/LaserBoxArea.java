package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.laser.mechanics.LaserGunMechanics;
import ink.ziip.championshipscore.api.game.laser.mechanics.LaserShotEngine;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserBeam;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserCombatMessages;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserEquipmentItems;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserGunItems;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserGunMenu;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserImpact;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserKillFeedback;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserNameTags;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserZoom;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponSelection;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponState;
import ink.ziip.championshipscore.api.game.laserbox.config.LaserBoxConfig;
import ink.ziip.championshipscore.api.game.laserbox.geometry.LaserBoxGeometry;
import ink.ziip.championshipscore.api.game.laserbox.mechanics.SmokeExpansion;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.TeamColors;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * One paired run. All combat, particles and cleanup are owned by this instance on the server
 * thread.
 */
public final class LaserBoxArea extends BasePairedGameInstance {
    private static final int REVEAL_DURATION_TICKS = 70;
    private static final int SHIELD_HITS = 1;

    /** Passive right-click repeats every four client ticks; expire promptly after key release. */
    private static final int AIM_INPUT_GRACE_TICKS = LaserGunMechanics.AIM_INPUT_GRACE_TICKS;

    private static final int GUN_SELECTOR_SLOT = 8;
    private static final int AMMO_SLOT = 4;
    private static final String GUN_SELECTOR_TYPE = "gun-selector";
    private final NamespacedKey itemKey;
    private final Map<UUID, Integer> protection = new HashMap<>();
    private final Map<UUID, Grenade> grenades = new HashMap<>();
    private final Map<UUID, SmokeCloud> smokeClouds = new HashMap<>();
    private final Map<Block, BlockData> smokeOriginals = new HashMap<>();
    private final Map<Block, Set<UUID>> smokeOwners = new HashMap<>();
    private final List<Strike> strikes = new ArrayList<>();
    private final Map<UUID, Item> drops = new HashMap<>();
    private final Map<UUID, LaserGun> selectedGuns = new HashMap<>();

    /** Players currently receiving LaserBox's movement speed effect. */
    private final Set<UUID> movementSpeed = new HashSet<>();

    private final Set<UUID> movementSlow = new HashSet<>();
    private final Map<UUID, Integer> bulletSlowUntil = new HashMap<>();
    private final Map<UUID, Integer> aimingUntil = new HashMap<>();
    private LaserZoom zoom;

    private final Map<Integer, Item> supplies = new HashMap<>();
    private final ArrayDeque<String> supplyPool = new ArrayDeque<>();
    private LaserBoxRound round;
    private LaserBoxIntroductionRun introductionRun;
    private ChampionshipTeam matchWinner;
    private BukkitTask tickTask;
    private boolean firstRoundGunSelectionWindow;
    private int timer;
    private int strikeCooldown;
    private List<Location> supplyLocations = List.of();
    private final int copyIndex;
    private LaserBoxGeometry geometry;
    private LaserNameTags nameTags;

    private record Grenade(Snowball entity, UUID owner, boolean right, int expires) {}

    private record SmokeCloud(
            UUID id,
            Location location,
            UUID owner,
            boolean right,
            int start,
            int expires,
            SmokeExpansion expansion) {}

    private record Strike(Location location, UUID owner, boolean right, int expires) {}

    public LaserBoxArea(ChampionshipsCore plugin, LaserBoxConfig config) {
        this(plugin, config, 0, true);
    }

    public LaserBoxArea(
            ChampionshipsCore plugin,
            LaserBoxConfig config,
            int copyIndex,
            boolean initializeConfig) {
        super(plugin, GameTypeEnum.LaserBox, new LaserBoxHandler(plugin), config);
        if (initializeConfig) config.initializeConfiguration(plugin.getFolder());
        this.copyIndex = copyIndex;
        if (config.getAreaPos1() != null
                && config.getAreaPos2() != null
                && config.getRightSpawnPoint() != null
                && config.getLeftSpawnPoint() != null
                && config.getSpectatorSpawnPoint() != null) {
            try {
                this.geometry =
                        new ReplicatedSpatialLayout<>(
                                        LaserBoxGeometry.from(config),
                                        config.getCopyGrid(),
                                        Math.max(1, config.getCopyCount()))
                                .geometry(copyIndex);
            } catch (RuntimeException ignored) {
                // Validation during start reports incomplete map geometry to the administrator.
            }
        }
        itemKey = new NamespacedKey(plugin, "laserbox-item");
        getGameHandler().setArea(this);
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public LaserBoxConfig getGameConfig() {
        return (LaserBoxConfig) gameConfig;
    }

    @Override
    public int getCopyIndex() {
        return copyIndex;
    }

    @Override
    public LaserBoxHandler getGameHandler() {
        return (LaserBoxHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return getGameConfig().getConfiguredWorld();
    }

    @Override
    public int getTimer() {
        return timer;
    }

    public int getRightProgress() {
        return round == null ? LaserBoxRound.INITIAL_SCORE : round.right();
    }

    public int getLeftProgress() {
        return round == null ? LaserBoxRound.INITIAL_SCORE : round.left();
    }

    public int getTransfer() {
        return round == null ? LaserBoxRound.INITIAL_TRANSFER : round.transfer();
    }

    public int getPlayerKills(UUID player) {
        return round == null ? 0 : round.kills(player);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return geometry == null
                ? getGameConfig().bind(getGameConfig().getSpectatorSpawnPoint())
                : geometry.spectatorSpawn();
    }

    private LaserBoxIntroductionArena introductionArena() {
        var manager = plugin.getGameManager().getLaserBoxManager();
        return manager == null ? null : manager.getIntroductionArena();
    }

    boolean isIntroductionCombat() {
        return isIntroductionPhase() && introductionRun != null && round != null;
    }

    boolean isIntroductionParticipant(Player player) {
        return introductionRun != null && introductionRun.admitted(player);
    }

    boolean isIntroductionRosterParticipant(Player player) {
        if (player == null
                || notAreaPlayer(player)
                || plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) != this)
            return false;
        return player.getGameMode() == GameMode.ADVENTURE
                || round != null && round.respawning(player.getUniqueId());
    }

    Location introductionRespawn(Player player) {
        return introductionRun == null
                ? getSpectatorSpawnLocation()
                : introductionRun.respawn(player.getUniqueId());
    }

    @Override
    protected Location resolveIntroductionSpawnPoint() {
        if (introductionRun != null) {
            Location spawn =
                    introductionRun.respawn(
                            getParticipantUniqueIds().stream().findFirst().orElse(null));
            if (spawn != null) return spawn;
        }
        LaserBoxIntroductionArena arena = introductionArena();
        if (isEventRun() && arena != null && arena.availableTo(this)) return arena.spectatorSpawn();
        return super.resolveIntroductionSpawnPoint();
    }

    @Override
    protected boolean isInteractiveIntroduction() {
        LaserBoxIntroductionArena arena = introductionArena();
        return arena != null && arena.availableTo(this);
    }

    @Override
    protected void onIntroductionStarted() {
        LaserBoxIntroductionArena arena = introductionArena();
        introductionRun = arena == null ? null : arena.begin(this);
        if (introductionRun == null) return;
        round = new LaserBoxRound();
        timer = INTRODUCTION_DURATION;
        tickTask = scheduler.runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    @Override
    protected void onIntroductionStopped() {
        for (Player player : players()) player.getInventory().clear();
        if (introductionRun != null) introductionRun.close();
        introductionRun = null;
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        for (Player player : players()) clearMovementSpeed(player);
        protection.clear();
        movementSpeed.clear();
        movementSlow.clear();
        bulletSlowUntil.clear();
        aimingUntil.clear();
        if (zoom != null) zoom.close();
        zoom = null;
        if (round != null) round.finish();
        round = null;
        timer = 0;
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null
                || geometry == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())) return true;
        return !location.toVector()
                .isInAABB(
                        geometry.boundaryMin(),
                        geometry.boundaryMax().clone().add(new Vector(1, 1, 1)));
    }

    @Override
    public boolean tryStartGame(ChampionshipTeam right, ChampionshipTeam left) {
        try {
            getGameConfig().validate();
            geometry =
                    new ReplicatedSpatialLayout<>(
                                    LaserBoxGeometry.from(getGameConfig()),
                                    getGameConfig().getCopyGrid(),
                                    getGameConfig().getCopyCount())
                            .geometry(copyIndex);
        } catch (RuntimeException failure) {
            logGame(
                    Level.WARNING,
                    MessageConfig.codeText("laser-box-area.try-start-game-1"),
                    failure.getMessage());
            return false;
        }
        return super.tryStartGame(right, left);
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        locations.add(spawn(true));
        locations.add(spawn(false));
        locations.add(getSpectatorSpawnLocation());
        LaserBoxIntroductionArena arena = introductionArena();
        if (arena != null) locations.addAll(arena.preloadLocations());
        return locations;
    }

    private Location spawn(boolean right) {
        if (geometry == null)
            return getGameConfig()
                    .bind(
                            right
                                    ? getGameConfig().getRightSpawnPoint()
                                    : getGameConfig().getLeftSpawnPoint());
        return right ? geometry.rightSpawn() : geometry.leftSpawn();
    }

    private boolean right(UUID id) {
        return rightChampionshipTeam != null && rightChampionshipTeam.getMembers().contains(id);
    }

    private List<Player> players() {
        return getParticipantUniqueIds().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean live() {
        return getGameStageEnum() == GameStageEnum.PROGRESS && round != null && !round.finished();
    }

    private boolean combatLive() {
        return live() || isIntroductionCombat();
    }

    boolean active(Player player) {
        if (isIntroductionCombat())
            return introductionRun.admitted(player)
                    && player.getGameMode() == GameMode.ADVENTURE
                    && round.ready(player.getUniqueId());
        return live()
                && isGameplayParticipant(player)
                && !notInArea(player.getLocation())
                && player.getGameMode() == GameMode.ADVENTURE
                && round.ready(player.getUniqueId());
    }

    boolean isGunSelectionStage() {
        GameStageEnum stage = getGameStageEnum();
        return !isIntroductionPhase()
                && (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN);
    }

    void openGunMenu(Player player) {
        long generation = captureGameGeneration();
        if (!isCurrentParticipantConnection(generation, player)
                || !isGameplayParticipant(player)
                || isIntroductionPhase()
                || LaserGunMenu.isOpen(this, player)) return;
        LaserBoxRound selectionRound = round;
        LaserWeaponSelection life = round == null ? null : round.selection(player.getUniqueId());
        if (!isGunSelectionStage()) {
            if (!active(player) || life == null || !life.openMenu(round.tick())) {
                syncGunSelection(player);
                return;
            }
        }
        LaserGunMenu.open(
                plugin,
                itemKey,
                this,
                player,
                selectedGuns.get(player.getUniqueId()),
                () -> canSelectGun(player, generation, selectionRound, life),
                gun -> equipGun(player, gun, null),
                () -> closeGunMenu(player, generation, selectionRound, life));
    }

    private boolean canSelectGun(
            Player player,
            long generation,
            LaserBoxRound selectionRound,
            LaserWeaponSelection life) {
        if (!isCurrentParticipantConnection(generation, player)
                || !isGameplayParticipant(player)
                || isIntroductionPhase()
                || round != selectionRound
                || (round != null && life != round.selection(player.getUniqueId()))) return false;
        return isGunSelectionStage()
                || active(player) && life != null && life.canSelect(round.tick());
    }

    private void closeGunMenu(
            Player player,
            long generation,
            LaserBoxRound selectionRound,
            LaserWeaponSelection life) {
        if (!isCurrentParticipantConnection(generation, player)
                || round != selectionRound
                || life == null
                || life != round.selection(player.getUniqueId())) return;
        life.closeMenu(round.tick());
        if (isGameplayParticipant(player)) syncGunSelection(player);
    }

    private void syncGunSelection(Player player) {
        LaserWeaponSelection life = round == null ? null : round.selection(player.getUniqueId());
        if (life == null) return;
        life.expire(round.tick());
        if (!life.locked()) return;
        if (GUN_SELECTOR_TYPE.equals(type(player.getInventory().getItem(GUN_SELECTOR_SLOT))))
            player.getInventory().setItem(GUN_SELECTOR_SLOT, null);
        LaserGunMenu.closeOpen(this, player);
    }

    private LaserGun selectedGun(UUID player) {
        return selectedGuns.computeIfAbsent(player, ignored -> LaserGun.RIFLE);
    }

    private LaserGun gun(String type) {
        if (type == null) return null;
        if (type.equalsIgnoreCase("laser")) return LaserGun.RIFLE;
        return LaserGun.fromId(type);
    }

    private LaserGun gun(ItemStack stack) {
        return gun(type(stack));
    }

    boolean holdsGun(Player player) {
        return gun(player.getInventory().getItemInMainHand()) != null;
    }

    boolean holdsGunSelector(Player player) {
        return GUN_SELECTOR_TYPE.equals(type(player.getInventory().getItemInMainHand()));
    }

    boolean holdsSniper(Player player) {
        return gun(player.getInventory().getItemInMainHand()) == LaserGun.SNIPER;
    }

    private boolean aiming(Player player) {
        return aimingUntil.getOrDefault(player.getUniqueId(), 0) > Bukkit.getCurrentTick();
    }

    private void syncAiming(Player player) {
        if (!active(player)
                || !holdsSniper(player)
                || round.reloading(player.getUniqueId())
                || !aiming(player)) endAiming(player);
    }

    void beginAiming(Player player) {
        if (!active(player) || !holdsSniper(player) || round.reloading(player.getUniqueId())) {
            endAiming(player);
            return;
        }
        aimingUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + AIM_INPUT_GRACE_TICKS);
        if (zoom == null) zoom = new LaserZoom(plugin);
        zoom.setAiming(player, true);
    }

    private void endAiming(Player player) {
        if (aimingUntil.remove(player.getUniqueId()) != null && zoom != null)
            zoom.setAiming(player, false);
    }

    void fireFromLeftClick(Player player) {
        if (!active(player)) return;
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == LaserGun.SNIPER) fireGun(player, gun);
    }

    boolean holdsRightClickAutomaticGun(Player player) {
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        return gun == LaserGun.SMG || gun == LaserGun.MACHINE;
    }

    void fireFromRightClick(Player player) {
        if (!active(player)) return;
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == null || gun == LaserGun.SNIPER) return;
        fireGun(player, gun);
    }

    /** Handles the F key fallback for manual reloads (R is not exposed by Bukkit). */
    void reloadFromSwap(Player player) {
        if (!active(player)) return;
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == null) return;
        if (round.reloading(player.getUniqueId())) {
            startReload(player, gun);
            return;
        }
        if (round.requestReload(player.getUniqueId(), gun)) {
            playReloadStartSound(player);
            startReload(player, gun);
        }
    }

    private void fireHeldRightClick(Player player) {
        if (!active(player)
                || !holdsRightClickAutomaticGun(player)
                || !player.isHandRaised()
                || player.getActiveItemHand() != EquipmentSlot.HAND
                || round.reloading(player.getUniqueId())) return;
        fireGun(player, gun(player.getInventory().getItemInMainHand()));
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        firstRoundGunSelectionWindow = selectedGuns.isEmpty();
        startGameIntroduction(
                () -> {
                    resetPlayerHealthFoodEffectLevelInventory();
                    for (Player player : players()) {
                        spawnPlayer(player, false);
                        if (firstRoundGunSelectionWindow) openGunMenu(player);
                    }
                    announceGamePreparation(
                            MessageConfig.LASER_BOX_START_PREPARATION,
                            MessageConfig.LASER_BOX_START_PREPARATION_TITLE,
                            MessageConfig.LASER_BOX_START_PREPARATION_SUBTITLE);
                    if (firstRoundGunSelectionWindow)
                        startFinalCountdown(
                                15,
                                MessageConfig.GAME_LASER_BOX,
                                MessageConfig.LASER_BOX_GAME_START_TITLE,
                                MessageConfig.LASER_BOX_GAME_START_SUBTITLE,
                                false,
                                false,
                                this::finishGunSelectionCountdown);
                    else startFormalGameCountdown();
                });
    }

    /** Closes the initial selection view before the normal five-second game countdown. */
    private void finishGunSelectionCountdown() {
        firstRoundGunSelectionWindow = false;
        for (Player player : players()) player.closeInventory();
        startFormalGameCountdown();
    }

    private void startFormalGameCountdown() {
        startFinalCountdown(
                MessageConfig.GAME_LASER_BOX,
                MessageConfig.LASER_BOX_GAME_START_TITLE,
                MessageConfig.LASER_BOX_GAME_START_SUBTITLE,
                this::begin);
    }

    private void begin() {
        firstRoundGunSelectionWindow = false;
        if (geometry == null) {
            geometry =
                    new ReplicatedSpatialLayout<>(
                                    LaserBoxGeometry.from(getGameConfig()),
                                    getGameConfig().getCopyGrid(),
                                    getGameConfig().getCopyCount())
                            .geometry(copyIndex);
        }
        round = new LaserBoxRound();
        matchWinner = null;
        hideNameTags();
        timer = getGameConfig().getTimer();
        supplyLocations =
                geometry.supplyPoints().stream()
                        .map(vector -> vector.toLocation(Bukkit.getWorld(getWorldName())))
                        .toList();
        for (Player player : players()) {
            player.closeInventory();
            spawnPlayer(player, false);
        }
        updateHud();
        tickTask = scheduler.runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void spawnPlayer(Player player, boolean protect) {
        UUID id = player.getUniqueId();
        player.getInventory().clear();
        player.getActivePotionEffects()
                .forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setVelocity(new Vector());
        Location destination =
                isIntroductionCombat() ? introductionRun.respawn(id) : spawn(right(id));
        if (destination != null) player.teleport(destination);
        player.getInventory().setHeldItemSlot(0);
        LaserGun gun = selectedGun(id);
        equipGun(player, gun, round);
        if (!isIntroductionCombat()) {
            if (live()) round.beginLife(id);
            player.getInventory()
                    .setItem(
                            GUN_SELECTOR_SLOT, LaserEquipmentItems.box(GUN_SELECTOR_TYPE, itemKey));
            ChampionshipTeam team = right(id) ? rightChampionshipTeam : leftChampionshipTeam;
            player.getInventory().setHelmet(team.getHelmet().clone());
            player.getInventory().setChestplate(null);
            player.getInventory().setBoots(null);
        } else {
            player.getInventory().setItem(GUN_SELECTOR_SLOT, null);
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
        }
        player.setLevel(100);
        player.setExp(0);
        syncMovementSpeed(player);
        if (protect && round != null) {
            protection.put(id, round.tick() + 80);
            round.protect(id);
            if (!isIntroductionCombat()) syncChestplate(player);
            plugin.getGameManager().getSpectatorManager().resumeParticipant(player, this);
        }
    }

    private void equipGun(Player player, LaserGun gun, LaserBoxRound state) {
        if (gun == null) gun = LaserGun.RIFLE;
        player.clearActiveItem();
        endAiming(player);
        selectedGuns.put(player.getUniqueId(), gun);
        player.getInventory().setItem(0, LaserGunItems.gunItem(plugin, itemKey, gun, null));
        player.getInventory().setItemInOffHand(null);
        syncAmmoItem(player, gun);
        if (state != null) {
            state.completeReload(player.getUniqueId(), gun);
            syncAmmoItem(player, gun);
            player.setCooldown(gun.material(), 0);
        }
    }

    /** Mirrors this round's authoritative magazine in the shared ammo presentation. */
    private void syncAmmoItem(Player player, LaserGun gun) {
        if (gun == null) return;
        int ammo = round == null ? gun.magazine() : round.ammo(player.getUniqueId(), gun);
        LaserGunItems.syncAmmo(player, itemKey, AMMO_SLOT, ammo);
    }

    /** Gun-specific movement trade-offs keep the Transport sight lines meaningful. */
    private void syncMovementSpeed(Player player) {
        UUID id = player.getUniqueId();
        if (!active(player)) {
            clearMovementSpeed(player);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        LaserGun gun = gun(held);
        boolean ammo = "ammo".equals(type(held));
        boolean empty =
                (ammo || held == null || held.getType().isAir())
                        && (offHand == null || offHand.getType().isAir());
        int amplifier = LaserGunMechanics.speedAmplifier(gun, empty);
        boolean bulletSlow = bulletSlowUntil.getOrDefault(id, 0) > round.tick();
        if (!bulletSlow) bulletSlowUntil.remove(id);
        if (bulletSlow) amplifier = LaserGunMechanics.hitSpeedAmplifier(amplifier);
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (amplifier < 0) {
            if (current != null) player.removePotionEffect(PotionEffectType.SPEED);
        } else if (!movementSpeed.contains(id)
                || current == null
                || current.getAmplifier() != amplifier
                || current.getDuration() < 20) {
            // Remove the previous level so a weaker effect takes over immediately too.
            if (current != null && current.getAmplifier() != amplifier)
                player.removePotionEffect(PotionEffectType.SPEED);
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, 40, amplifier, true, false, false));
        }
        int slowAmplifier = LaserGunMechanics.slownessAmplifier(gun, aiming(player));
        if (bulletSlow) slowAmplifier = LaserGunMechanics.hitSlownessAmplifier(slowAmplifier);
        if (slowAmplifier >= 0) {
            PotionEffect currentSlow = player.getPotionEffect(PotionEffectType.SLOWNESS);
            if (currentSlow == null
                    || currentSlow.getAmplifier() != slowAmplifier
                    || currentSlow.getDuration() < 20) {
                if (currentSlow != null && currentSlow.getAmplifier() != slowAmplifier)
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SLOWNESS, 40, slowAmplifier, true, false, false));
            }
            movementSlow.add(id);
        } else if (movementSlow.remove(id)) player.removePotionEffect(PotionEffectType.SLOWNESS);
        movementSpeed.add(id);
    }

    private void clearMovementSpeed(Player player) {
        UUID id = player.getUniqueId();
        if (movementSpeed.remove(id)) player.removePotionEffect(PotionEffectType.SPEED);
        if (movementSlow.remove(id)) player.removePotionEffect(PotionEffectType.SLOWNESS);
        bulletSlowUntil.remove(id);
    }

    String type(ItemStack item) {
        return LaserGunItems.type(item, itemKey);
    }

    void use(Player player) {
        if (!active(player) || isIntroductionCombat()) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        String type = type(held);
        if (type == null) return;
        UUID id = player.getUniqueId();
        if (GUN_SELECTOR_TYPE.equals(type)) {
            openGunMenu(player);
            return;
        }
        if (gun(type) != null) return;
        if (type.equals("strike") && strikeCooldown > round.tick()) return;
        if (!Set.of("grenade", "strike", "reveal").contains(type)) return;
        held.setAmount(held.getAmount() - 1);
        switch (type) {
            case "grenade" -> {
                Snowball projectile =
                        player.launchProjectile(
                                Snowball.class,
                                player.getEyeLocation().getDirection().multiply(1.1));
                grenades.put(
                        projectile.getUniqueId(),
                        new Grenade(projectile, id, right(id), round.tick() + 400));
            }
            case "strike" -> {
                Location origin = player.getLocation();
                List<Player> targets =
                        players().stream()
                                .filter(
                                        enemy ->
                                                active(enemy)
                                                        && right(enemy.getUniqueId()) != right(id))
                                .sorted(
                                        Comparator.comparingDouble(
                                                enemy ->
                                                        enemy.getLocation()
                                                                .distanceSquared(origin)))
                                .limit(2)
                                .toList();
                for (Player enemy : targets) {
                    strikes.add(
                            new Strike(
                                    enemy.getLocation().clone(), id, right(id), round.tick() + 15));
                    enemy.playSound(enemy.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
                }
                strikeCooldown = round.tick() + 15;
            }
            case "reveal" -> {
                for (Player enemy : players())
                    if (active(enemy) && right(enemy.getUniqueId()) != right(id))
                        enemy.addPotionEffect(
                                new PotionEffect(
                                        PotionEffectType.GLOWING,
                                        REVEAL_DURATION_TICKS,
                                        0,
                                        false,
                                        false));
            }
        }
    }

    private void fireGun(Player player, LaserGun gun) {
        if (round == null || gun == null || !active(player)) return;
        UUID id = player.getUniqueId();
        cancelReloadIfNotHolding(player);
        boolean wasReloading = round.reloading(id);
        LaserWeaponState.FireResult result = round.shoot(id, gun);
        if (result == LaserWeaponState.FireResult.FIRED) {
            fire(player, gun, id, right(id), true);
            syncAmmoItem(player, gun);
            syncGunSelection(player);
            showAmmo(player, gun);
            if (!wasReloading && round.reloading(id)) playReloadStartSound(player);
        } else if (result == LaserWeaponState.FireResult.RELOADING) {
            if (!wasReloading) playReloadStartSound(player);
            startReload(player, gun);
        }
    }

    private void playReloadStartSound(Player player) {
        endAiming(player);
        World world = player.getWorld();
        if (world != null)
            world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.85F, 0.9F);
    }

    private void playReloadCompleteSound(Player player) {
        World world = player.getWorld();
        if (world != null)
            world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.9F, 1.05F);
    }

    private void startReload(Player player, LaserGun gun) {
        endAiming(player);
        int remaining = round.reloadRemaining(player.getUniqueId());
        player.setCooldown(gun.material(), remaining);
        LaserGunItems.showCombatStatus(
                player,
                gun,
                round.ammo(player.getUniqueId(), gun),
                remaining,
                round.protectionRemaining(player.getUniqueId()));
    }

    /** Reloads are interrupted as soon as the reloading gun leaves the player's main hand. */
    private void cancelReloadIfNotHolding(Player player) {
        if (round == null) return;
        UUID id = player.getUniqueId();
        LaserGun reloadingGun = round.reloadGun(id);
        if (reloadingGun == null || reloadingGun == gun(player.getInventory().getItemInMainHand()))
            return;
        round.cancelReload(id);
        player.setCooldown(reloadingGun.material(), 0);
    }

    /** Called from the hotbar event before Bukkit applies the new selected slot. */
    void heldItemChanged(Player player, int newSlot) {
        if (round == null) return;
        UUID id = player.getUniqueId();
        LaserGun reloadingGun = round.reloadGun(id);
        if (reloadingGun == null) return;
        ItemStack next = player.getInventory().getItem(newSlot);
        if (reloadingGun != gun(next)) {
            round.cancelReload(id);
            player.setCooldown(reloadingGun.material(), 0);
        }
    }

    private void showAmmo(Player player, LaserGun gun) {
        if (round.reloading(player.getUniqueId())) {
            startReload(player, gun);
            return;
        }
        LaserGunItems.showCombatStatus(
                player,
                gun,
                round.ammo(player.getUniqueId(), gun),
                0,
                round.protectionRemaining(player.getUniqueId()));
    }

    private void fire(
            Player shooter, LaserGun gun, UUID owner, boolean onRight, boolean intercept) {
        Set<UUID> ids = new LinkedHashSet<>(getParticipantUniqueIds());
        ids.addAll(spectators);
        Location shotEye = shooter.getEyeLocation();
        List<Player> viewers =
                LaserBeam.viewers(
                        shotEye.getWorld(),
                        shotEye,
                        shotEye.getDirection(),
                        gun.range(),
                        ids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList());
        LaserBoxRound shootingRound = round;
        long generation = captureGameGeneration();
        LaserShotEngine.fire(
                shooter,
                gun,
                round.burstShot(shooter.getUniqueId()),
                aiming(shooter),
                new LaserShotEngine.Context() {
                    @Override
                    public boolean current() {
                        return round == shootingRound
                                && captureGameGeneration() == generation
                                && combatLive()
                                && active(shooter);
                    }

                    @Override
                    public boolean collidable(Entity entity) {
                        return entity instanceof Player player && active(player)
                                || intercept && grenades.containsKey(entity.getUniqueId());
                    }

                    @Override
                    public boolean enemy(Player player) {
                        return active(player) && right(player.getUniqueId()) != onRight;
                    }

                    @Override
                    public void beam(World world, Location eye, Vector direction, double distance) {
                        LaserBeam.draw(world, eye, direction, distance, viewers);
                    }

                    @Override
                    public void intercept(Entity entity, Vector point) {
                        Grenade grenade = grenades.remove(entity.getUniqueId());
                        if (grenade == null) return;
                        Location location = entity.getLocation();
                        entity.remove();
                        players().stream()
                                .filter(p -> active(p) && right(p.getUniqueId()) != onRight)
                                .min(
                                        Comparator.comparingDouble(
                                                p -> p.getLocation().distanceSquared(location)))
                                .ifPresent(
                                        p ->
                                                hit(
                                                        p,
                                                        owner,
                                                        onRight,
                                                        false,
                                                        false,
                                                        LaserGun.RIFLE.damage(
                                                                LaserGun.HitRegion.BODY,
                                                                location.distance(
                                                                        p.getEyeLocation())),
                                                        gun,
                                                        LaserGun.HitRegion.BODY,
                                                        false));
                    }

                    @Override
                    public void damage(
                            Player player, double amount, LaserGun.HitRegion region, Vector point) {
                        if (hit(player, owner, onRight, false, false, amount, gun, region, true))
                            LaserImpact.draw(shotEye.getWorld(), point, viewers);
                    }
                });
    }

    private void hit(Player victim, UUID owner, boolean onRight, boolean gas, boolean strike) {
        hit(victim, owner, onRight, gas, strike, 100.0D, null, LaserGun.HitRegion.BODY, false);
    }

    private boolean hit(
            Player victim,
            UUID owner,
            boolean onRight,
            boolean gas,
            boolean strike,
            double amount,
            LaserGun weapon,
            LaserGun.HitRegion hitRegion,
            boolean bullet) {
        boolean introduction = isIntroductionCombat();
        if (!combatLive()
                || !isGameplayParticipant(victim)
                || victim.getGameMode() != GameMode.ADVENTURE
                || !round.ready(victim.getUniqueId())
                || !introduction && !gas && notInArea(victim.getLocation())
                || right(victim.getUniqueId()) == onRight
                || owner != null && !isGameplayParticipant(owner)) return false;
        UUID id = victim.getUniqueId();
        if (round.protectedFromHits(id)) return false;
        if (gas && inSmoke(victim)) return false;
        if (bullet) {
            bulletSlowUntil.put(id, round.tick() + 20);
            syncMovementSpeed(victim);
        }
        if (strike && round.shield(id) > 0) {
            round.destroyShield(id);
            syncChestplate(victim);
            victim.setLevel((int) Math.ceil(round.health(id)));
            return false;
        }
        UUID killer = owner != null && getParticipantUniqueIds().contains(owner) ? owner : null;
        LaserBoxRound.DamageResult result =
                round.damage(id, right(id), gas || introduction, killer, amount, !introduction);
        if (result == LaserBoxRound.DamageResult.IGNORED) return false;
        syncGunSelection(victim);
        if (result == LaserBoxRound.DamageResult.SHIELD) {
            syncChestplate(victim);
            victim.setLevel((int) Math.ceil(round.health(id)));
            victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1, 1);
            return false;
        }
        if (result == LaserBoxRound.DamageResult.DAMAGED) {
            syncChestplate(victim);
            victim.setLevel((int) Math.ceil(round.health(id)));
            return true;
        }
        if (killer != null && !introduction) {
            ChampionshipTeam killerTeam =
                    right(killer) ? rightChampionshipTeam : leftChampionshipTeam;
            LaserKillFeedback.burst(
                    victim,
                    TeamColors.fromHex(killerTeam.getColorCode()),
                    itemKey,
                    hitRegion == LaserGun.HitRegion.HEAD);
        }
        if (!introduction) {
            for (ItemStack stack : victim.getInventory().getContents()) {
                String kind = type(stack);
                if (kind != null
                        && !kind.equals("laser")
                        && !kind.equals("ammo")
                        && stack.getType() != Material.SPECTRAL_ARROW
                        && gun(stack) == null)
                    trackDrop(victim.getWorld().dropItem(victim.getLocation(), stack.clone()));
            }
        }
        victim.getInventory().clear();
        endAiming(victim);
        victim.setLevel(0);
        victim.removePotionEffect(PotionEffectType.GLOWING);
        plugin.getGameManager().getSpectatorManager().enterParticipantSpectator(victim, this);
        clearMovementSpeed(victim);
        if (killer != null && !introduction) {
            addPlayerPoints(killer, getGameConfig().getKillPoints());
            Player player = Bukkit.getPlayer(killer);
            LaserBoxRound feedbackRound = round;
            long feedbackGeneration = captureGameGeneration();
            if (player != null)
                LaserKillFeedback.play(
                        player,
                        feedbackRound.kills(killer),
                        plugin,
                        () ->
                                captureGameGeneration() == feedbackGeneration
                                        && !isDisposed()
                                        && round == feedbackRound
                                        && !notAreaPlayer(player));
        }
        String weaponName =
                weapon == null
                        ? strike
                                ? LaserEquipmentItems.name("strike")
                                : owner == null
                                        ? MessageConfig.codeText("laser-box-area.hit-1")
                                        : MessageConfig.LASER_BOX_SPECIAL_ATTACK_NAME
                        : LaserGunItems.gunName(weapon);
        String attackerName =
                owner == null
                        ? MessageConfig.codeText("laser-box-area.hit-1")
                        : CoreMessages.formatPlayerName(owner);
        sendMessageToAllGamePlayers(
                LaserCombatMessages.elimination(
                        hitRegion,
                        attackerName,
                        CoreMessages.formatPlayerName(victim),
                        weaponName));
        if (!introduction && (round.right() == 0 || round.left() == 0)) endGame();
        return true;
    }

    void landed(Entity projectile) {
        Grenade grenade = grenades.remove(projectile.getUniqueId());
        if (grenade == null) return;
        Location point = projectile.getLocation().clone();
        projectile.remove();
        if (live() && !notInArea(point)) createSmoke(point, grenade.owner(), grenade.right());
    }

    private void createSmoke(Location center, UUID owner, boolean right) {
        List<Block> cells = new ArrayList<>();
        Block origin = center.getBlock();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin);
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty() && cells.size() < 180) {
            Block block = queue.remove();
            int dx = block.getX() - origin.getX(),
                    dy = block.getY() - origin.getY(),
                    dz = block.getZ() - origin.getZ();
            if (dx * dx / 9D + dy * dy / 4D + dz * dz / 9D > 1.0D) continue;
            if (!block.isPassable() || block.isLiquid()) continue;
            cells.add(block);
            for (int[] direction : directions) {
                Block next = block.getRelative(direction[0], direction[1], direction[2]);
                if (seen.add(next)) queue.add(next);
            }
        }
        UUID id = UUID.randomUUID();
        smokeClouds.put(
                id,
                new SmokeCloud(
                        id,
                        center.clone(),
                        owner,
                        right,
                        round.tick(),
                        round.tick() + 400,
                        new SmokeExpansion(cells)));
    }

    private boolean inSmoke(Player player) {
        Location location = player.getLocation();
        for (Block block : smokeOwners.keySet()) {
            if (block.getWorld().equals(location.getWorld())
                    && block.getLocation().distanceSquared(location) <= 6.25D) return true;
        }
        return false;
    }

    private void tickSmoke(int tick) {
        for (SmokeCloud cloud : List.copyOf(smokeClouds.values())) {
            int age = tick - cloud.start();
            if (age >= cloud.expires() - cloud.start()) {
                removeSmoke(cloud);
                continue;
            }
            int count = Math.min(cloud.expansion().cells().size(), Math.max(1, age * 3));
            cloud.expansion()
                    .expand(
                            count,
                            block -> {
                                if ((!block.isPassable() && !smokeOwners.containsKey(block))
                                        || block.isLiquid()) return false;
                                smokeOwners
                                        .computeIfAbsent(
                                                block,
                                                ignored -> {
                                                    smokeOriginals.put(
                                                            block, block.getBlockData().clone());
                                                    block.setType(Material.POWDER_SNOW, false);
                                                    return new HashSet<>();
                                                })
                                        .add(cloud.id());
                                return true;
                            });
            if (tick % 5 == 0)
                cloud.location()
                        .getWorld()
                        .spawnParticle(
                                Particle.SNOWFLAKE,
                                cloud.location().clone().add(0, 1, 0),
                                12,
                                1.5,
                                1,
                                1.5,
                                .01);
        }
    }

    private void removeSmoke(SmokeCloud cloud) {
        smokeClouds.remove(cloud.id());
        for (Block block : cloud.expansion().cells()) {
            Set<UUID> owners = smokeOwners.get(block);
            if (owners == null) continue;
            owners.remove(cloud.id());
            if (!owners.isEmpty()) continue;
            if (block.getType() == Material.POWDER_SNOW) {
                BlockData original = smokeOriginals.remove(block);
                if (original != null) block.setBlockData(original, false);
            } else smokeOriginals.remove(block);
            smokeOwners.remove(block);
        }
    }

    private void clearSmoke() {
        for (SmokeCloud cloud : List.copyOf(smokeClouds.values())) removeSmoke(cloud);
        smokeClouds.clear();
        smokeOwners.clear();
        smokeOriginals.clear();
    }

    boolean owns(Entity entity) {
        return grenades.containsKey(entity.getUniqueId())
                || drops.containsKey(entity.getUniqueId())
                || LaserKillFeedback.isKillFirework(entity, itemKey);
    }

    private void trackDrop(Item item) {
        item.setGlowing(true);
        item.setInvulnerable(true);
        item.setPickupDelay(0);
        drops.put(item.getUniqueId(), item);
    }

    void pickup(Player player, Item drop) {
        if (!active(player) || !drops.containsKey(drop.getUniqueId())) return;
        String type = type(drop.getItemStack());
        if ("shield".equals(type)) {
            if (round.shield(player.getUniqueId()) > 0) return;
            round.shield(player.getUniqueId(), SHIELD_HITS);
            player.setLevel((int) Math.ceil(round.health(player.getUniqueId())));
        } else if (!player.getInventory().addItem(drop.getItemStack().clone()).isEmpty()) return;
        drops.remove(drop.getUniqueId());
        supplies.values().removeIf(i -> i.getUniqueId().equals(drop.getUniqueId()));
        drop.remove();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1, 1);
        syncChestplate(player);
        player.sendActionBar(LaserCombatMessages.itemHint(type));
    }

    private void syncChestplate(Player player) {
        UUID id = player.getUniqueId();
        Material material =
                round != null && round.protectedFromHits(id)
                        ? Material.DIAMOND_CHESTPLATE
                        : round != null && round.shield(id) > 0 ? Material.IRON_CHESTPLATE : null;
        ItemStack chestplate = player.getInventory().getChestplate();
        if (material != null) {
            if (chestplate == null || chestplate.getType() != material)
                player.getInventory().setChestplate(new ItemStack(material));
        } else if (chestplate != null
                && (chestplate.getType() == Material.IRON_CHESTPLATE
                        || chestplate.getType() == Material.DIAMOND_CHESTPLATE)) {
            player.getInventory().setChestplate(null);
        }
    }

    private void tick() {
        if (isIntroductionCombat()) {
            tickIntroduction();
            return;
        }
        if (!live()) return;
        round.advance();
        if (round.tick() % 5 == 0) enforceNameTagsHidden();
        for (Player player : players()) {
            UUID id = player.getUniqueId();
            if (round.respawn(id)) spawnPlayer(player, true);
            else if (round.respawning(id) && round.tick() % 20 == 0)
                LaserCombatMessages.showRespawn(player, round.respawnRemaining(id));
            syncGunSelection(player);
            cancelReloadIfNotHolding(player);
            syncAiming(player);
            fireHeldRightClick(player);
            if (!live()) return;
            if (protection.containsKey(id) && protection.get(id) <= round.tick())
                protection.remove(id);
            LaserGun gun = gun(player.getInventory().getItemInMainHand());
            LaserGun reloadingGun = round.reloadGun(id);
            if (reloadingGun != null && reloadingGun == gun && round.completeReload(id, gun)) {
                player.setCooldown(reloadingGun.material(), 0);
                syncAmmoItem(player, reloadingGun);
                playReloadCompleteSound(player);
                LaserCombatMessages.showReloadComplete(player, reloadingGun);
            } else if (reloadingGun != null && reloadingGun == gun && round.tick() % 10 == 0)
                startReload(player, reloadingGun);
            syncChestplate(player);
            syncMovementSpeed(player);
            if (active(player) && round.tick() % 10 == 0)
                LaserGunItems.showCombatStatus(
                        player,
                        gun,
                        gun == null ? 0 : round.ammo(id, gun),
                        round.reloadRemaining(id),
                        round.protectionRemaining(id));
        }
        for (Grenade g : List.copyOf(grenades.values()))
            if (g.expires() <= round.tick() || !g.entity().isValid()) {
                grenades.remove(g.entity().getUniqueId());
                g.entity().remove();
            }
        for (Strike strike : List.copyOf(strikes)) {
            strike.location()
                    .getWorld()
                    .spawnParticle(Particle.ELECTRIC_SPARK, strike.location(), 8, 1, 0.1, 1, 0);
            if (strike.expires() > round.tick()) continue;
            strikes.remove(strike);
            for (Player player : players())
                if (player.getWorld().equals(strike.location().getWorld())
                        && player.getLocation().distanceSquared(strike.location()) <= 2.2 * 2.2)
                    hit(player, strike.owner(), strike.right(), false, true);
            if (!live()) return;
        }
        tickSmoke(round.tick());
        drops.values().removeIf(item -> !item.isValid());
        supplies.values().removeIf(item -> !item.isValid());
        if (round.tick() % 400 == 0) {
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < supplyLocations.size(); i++)
                if (!supplies.containsKey(i)) free.add(i);
            if (!free.isEmpty()) {
                var random = ThreadLocalRandom.current();
                int slot = free.get(random.nextInt(free.size()));
                if (supplyPool.isEmpty()) {
                    List<String> kinds =
                            new ArrayList<>(List.of("shield", "grenade", "strike", "reveal"));
                    Collections.shuffle(kinds, random);
                    supplyPool.addAll(kinds);
                }
                String kind = supplyPool.removeFirst();
                Location location = supplyLocations.get(slot);
                Item item =
                        location.getWorld()
                                .dropItem(location, LaserEquipmentItems.box(kind, itemKey));
                item.setVelocity(new Vector());
                item.setGravity(false);
                trackDrop(item);
                supplies.put(slot, item);
            }
        }
        if (round.tick() % 20 == 0) {
            timer = Math.max(0, getGameConfig().getTimer() - round.tick() / 20);
            updateHud();
            boolean rightOnline = players().stream().anyMatch(p -> right(p.getUniqueId()));
            boolean leftOnline = players().stream().anyMatch(p -> !right(p.getUniqueId()));
            if (!rightOnline || !leftOnline) {
                if (rightOnline != leftOnline) round.forfeit(!rightOnline);
                endGame();
            } else if (timer == 0) endGame();
        }
    }

    private void tickIntroduction() {
        if (introductionRun == null || !introductionRun.current() || round == null) return;
        round.advance();
        for (Player player : introductionRun.participants()) {
            UUID id = player.getUniqueId();
            if (round.respawn(id)) spawnPlayer(player, true);
            else if (introductionRun.needsSpawn(player)) {
                spawnPlayer(player, false);
                introductionRun.markSpawned(player);
            } else if (round.respawning(id) && round.tick() % 20 == 0)
                LaserCombatMessages.showRespawn(player, round.respawnRemaining(id));
            syncGunSelection(player);
            cancelReloadIfNotHolding(player);
            syncAiming(player);
            fireHeldRightClick(player);
            if (!isIntroductionCombat()) return;
            if (protection.containsKey(id) && protection.get(id) <= round.tick())
                protection.remove(id);
            LaserGun gun = gun(player.getInventory().getItemInMainHand());
            LaserGun reloadingGun = round.reloadGun(id);
            if (reloadingGun != null && reloadingGun == gun && round.completeReload(id, gun)) {
                player.setCooldown(reloadingGun.material(), 0);
                syncAmmoItem(player, reloadingGun);
                playReloadCompleteSound(player);
                LaserCombatMessages.showReloadComplete(player, reloadingGun);
            } else if (reloadingGun != null && reloadingGun == gun && round.tick() % 10 == 0)
                startReload(player, reloadingGun);
            syncMovementSpeed(player);
            if (!introductionRun.contains(player) && !round.respawning(id))
                hit(player, null, !right(id), true, false);
        }
    }

    private void updateHud() {
        String title =
                MessageConfig.LASER_BOX_BOSS_BAR
                        .replace("%right_team%", rightChampionshipTeam.getColoredName())
                        .replace("%left_team%", leftChampionshipTeam.getColoredName())
                        .replace("%right%", Integer.toString(getRightProgress()))
                        .replace("%left%", Integer.toString(getLeftProgress()))
                        .replace("%time%", CoreMessages.bossBarTime(timer));
        updateGameTimerBossBar(title, timer, getGameConfig().getTimer());
    }

    @Override
    public void endGame() {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.WAITING || stage == GameStageEnum.END) return;
        // Stop commands can arrive before the live round exists. Finish the lifecycle here so
        // a preparation/countdown task cannot leave the instance half-started.
        if (stage != GameStageEnum.PROGRESS || round == null) {
            cleanup();
            setGameStageEnum(GameStageEnum.END);
            beginPostGameSettlement();
            completePostGame(false);
            return;
        }
        if (!round.finish()) return;
        cleanup();
        if (isSettlementAllowed()) {
            ChampionshipTeam winner =
                    round.right() > round.left()
                            ? rightChampionshipTeam
                            : round.left() > round.right() ? leftChampionshipTeam : null;
            matchWinner = winner;
            if (winner != null)
                addPlayerPointsToAllTeamMembers(winner, getGameConfig().getWinPoints());
            else {
                addPlayerPointsToAllTeamMembers(
                        rightChampionshipTeam, getGameConfig().getDrawPoints());
                addPlayerPointsToAllTeamMembers(
                        leftChampionshipTeam, getGameConfig().getDrawPoints());
            }
            addPlayerPointsToDatabase();
            sendMessageToAllGamePlayers(
                    winner == null
                            ? MessageConfig.LASER_BOX_DRAW
                            : MessageConfig.LASER_BOX_WINNER.replace(
                                    "%team%", winner.getColoredName()));
        }
        announceGameEnd(
                MessageConfig.LASER_BOX_END_TITLE,
                MessageConfig.LASER_BOX_END_SUBTITLE
                        .replace("%right%", Integer.toString(getRightProgress()))
                        .replace("%left%", Integer.toString(getLeftProgress())));
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        publishGameEndEvent(
                new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));
        finishPostGameAfterEndEvent();
    }

    /** Winner of the most recently settled match; {@code null} means a draw or no result. */
    public ChampionshipTeam getMatchWinner() {
        return matchWinner;
    }

    private void cleanup() {
        if (round != null) round.finish();
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        if (introductionRun != null) introductionRun.close();
        introductionRun = null;
        grenades.values().forEach(g -> g.entity().remove());
        grenades.clear();
        clearSmoke();
        strikes.clear();
        drops.values().forEach(Entity::remove);
        drops.clear();
        supplies.clear();
        supplyPool.clear();
        protection.clear();
        strikeCooldown = 0;
        for (Player player : players()) {
            LaserGunMenu.closeOpen(this, player);
            clearMovementSpeed(player);
            for (LaserGun gun : LaserGun.values()) player.setCooldown(gun.material(), 0);
            ItemStack chestplate = player.getInventory().getChestplate();
            if (chestplate != null
                    && (chestplate.getType() == Material.IRON_CHESTPLATE
                            || chestplate.getType() == Material.DIAMOND_CHESTPLATE))
                player.getInventory().setChestplate(null);
        }
        movementSpeed.clear();
        movementSlow.clear();
        bulletSlowUntil.clear();
        aimingUntil.clear();
        if (zoom != null) zoom.close();
        zoom = null;
        firstRoundGunSelectionWindow = false;
        restoreNameTags();
    }

    private void hideNameTags() {
        restoreNameTags();
        nameTags = new LaserNameTags(plugin);
        nameTags.refresh(getParticipantUniqueIds());
    }

    private void enforceNameTagsHidden() {
        if (nameTags != null) nameTags.refresh(getParticipantUniqueIds());
    }

    private void restoreNameTags() {
        if (nameTags != null) nameTags.close();
        nameTags = null;
    }

    @Override
    public void resetArea() {
        cleanup();
        if (!isRoundTransitionPending()) selectedGuns.clear();
        round = null;
        timer = 0;
    }

    @Override
    public void dispose() {
        cleanup();
        super.dispose();
    }

    @Override
    public void handlePlayerQuit(PlayerQuitEvent event) {
        if (notAreaPlayer(event.getPlayer()) || round == null) return;
        clearMovementSpeed(event.getPlayer());
        UUID id = event.getPlayer().getUniqueId();
        if (nameTags != null) nameTags.remove(id);
        if (introductionRun != null) introductionRun.disconnected(id);
        endAiming(event.getPlayer());
        round.disconnect(id);
        LaserGunMenu.closeOpen(this, event.getPlayer());
        protection.remove(id);
        grenades.values()
                .removeIf(
                        g -> {
                            if (!g.owner().equals(id)) return false;
                            g.entity().remove();
                            return true;
                        });
        for (SmokeCloud cloud : List.copyOf(smokeClouds.values()))
            if (cloud.owner().equals(id)) removeSmoke(cloud);
        strikes.removeIf(s -> s.owner().equals(id));
    }

    @Override
    public void handlePlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (notAreaPlayer(p)) return;
        clearMovementSpeed(p);
        if (isIntroductionPhase()) {
            if (introductionRun != null) {
                introductionRun.disconnected(p.getUniqueId());
                p.teleport(introductionRun.respawn(p.getUniqueId()));
            } else p.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            return;
        }
        if (live()) {
            round.disconnect(p.getUniqueId());
            p.getInventory().clear();
            plugin.getGameManager()
                    .getSpectatorManager()
                    .enterParticipantSpectator(p, this, getSpectatorSpawnLocation());
        } else if (getGameStageEnum() == GameStageEnum.COUNTDOWN
                || getGameStageEnum() == GameStageEnum.PREPARATION) spawnPlayer(p, false);
    }

    @Override
    public void handlePlayerDeath(PlayerDeathEvent event) {
        if (notAreaPlayer(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        Player player = event.getEntity();
        if (live()) {
            round.disconnect(player.getUniqueId());
            LaserGunMenu.closeOpen(this, player);
        }
        clearMovementSpeed(player);
        player.getInventory().setChestplate(null);
        if (isIntroductionCombat()) {
            round.disconnect(player.getUniqueId());
            introductionRun.disconnected(player.getUniqueId());
            player.setRespawnLocation(introductionRespawn(player), true);
            long generation = captureGameGeneration();
            scheduler.runTask(
                    plugin,
                    () -> {
                        if (!isCurrentParticipantConnection(generation, player)
                                || !isIntroductionCombat()) return;
                        player.spigot().respawn();
                    });
        } else player.setRespawnLocation(getSpectatorSpawnLocation(), true);
    }

    void boundary(Player player) {
        if (isIntroductionCombat()) {
            if (!isIntroductionParticipant(player) || introductionRun.contains(player)) return;
            if (round.respawning(player.getUniqueId())) {
                Location spawn = introductionRespawn(player);
                if (spawn != null) player.teleport(spawn);
            } else hit(player, null, !right(player.getUniqueId()), true, false);
            return;
        }
        if (!live() || notAreaPlayer(player) || !notInArea(player.getLocation())) return;
        if (round.respawning(player.getUniqueId())) {
            player.teleport(getSpectatorSpawnLocation());
            return;
        }
        hit(player, null, !right(player.getUniqueId()), true, false);
        player.teleport(spawn(right(player.getUniqueId())));
    }
}
