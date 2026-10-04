package ink.ziip.championshipscore.api.game.spectate;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.player.SpectatorStateService;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single lifecycle owner for spectator presentation. The game manager remains the routing facade
 * for compatibility, while this module owns the mode, inventory, effects, controls, reconnect and
 * cleanup contract shared by every game area.
 */
public final class SpectatorManager extends BaseManager implements Listener {
    public static final String OWNER = "spectator:controls";
    private static final int MAIN_SIZE = 9;
    private static final float MIN_SPEED = 0.05F;
    private static final float MAX_SPEED = 1.0F;

    private final GameManager gameManager;
    private final Map<UUID, SpectatorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> participantControlItems = new ConcurrentHashMap<>();

    /** Original helmet for internally eliminated participants (AIR means there was no helmet). */
    private final Map<UUID, ItemStack> participantHelmets = new ConcurrentHashMap<>();

    /** Original physics flag for internally eliminated participants. */
    private final Map<UUID, Boolean> participantNoPhysics = new ConcurrentHashMap<>();

    private volatile boolean loaded;
    private final Map<UUID, UUID> teleportRequests = new ConcurrentHashMap<>();
    private BukkitTask presentationTask;

    public SpectatorManager(@NotNull ChampionshipsCore plugin, @NotNull GameManager gameManager) {
        super(plugin);
        this.gameManager = gameManager;
    }

    @Override
    public void load() {
        if (presentationTask != null) return;
        loaded = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        presentationTask =
                Bukkit.getScheduler().runTaskTimer(plugin, this::updatePresentation, 10L, 10L);
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getGameMode() == GameMode.SPECTATOR
                    || gameManager.getPlayerSpectatorStatus(player.getUniqueId()) != null)
                adoptExisting(player);
    }

    @Override
    public void unload() {
        loaded = false;
        HandlerList.unregisterAll(this);
        if (presentationTask != null) presentationTask.cancel();
        presentationTask = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            SpectatorSession session = sessions.get(player.getUniqueId());
            if (session != null) {
                sessions.remove(player.getUniqueId(), session);
                clearPresentation(player, session.external());
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ControlHolder)
                player.closeInventory();
        }
        for (SpectatorSession session : sessions.values()) {
            if (session.area() != null) session.area().onlyRemoveSpectatorFromList(session.uuid());
        }
        sessions.clear();
        teleportRequests.clear();
        snapshots.clear();
        participantControlItems.clear();
        participantHelmets.clear();
        participantNoPhysics.clear();
        HandlerList.unregisterAll(this);
    }

    public boolean isSpectatorLike(@NotNull UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return sessions.containsKey(uuid)
                || gameManager.getPlayerSpectatorStatus(uuid) != null
                || player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    @org.jetbrains.annotations.Nullable
    public BaseGameInstance areaOf(@NotNull UUID uuid) {
        SpectatorSession session = sessions.get(uuid);
        if (session != null) return session.area();
        BaseGameInstance external = gameManager.getPlayerSpectatorStatus(uuid);
        return external != null
                ? external
                : isSpectatorLike(uuid) ? gameManager.getBasePlayerArea(uuid) : null;
    }

    /**
     * Adopts a genuine spectator when the plugin is enabled after the connection already exists.
     */
    public void adoptExisting(@NotNull Player player) {
        if (!loaded) return;
        UUID uuid = player.getUniqueId();
        BaseGameInstance external = gameManager.getPlayerSpectatorStatus(uuid);
        BaseGameInstance participant = gameManager.getBasePlayerArea(uuid);
        if (external != null) prepareExternal(player);
        else if (participant != null) {
            prepareParticipant(player, participant);
            applyPresentation(player);
        } else {
            snapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
            sessions.computeIfAbsent(uuid, ignored -> new SpectatorSession(uuid, null, true));
            applyPresentation(player);
        }
    }

    public boolean isStandalone(@NotNull UUID uuid) {
        SpectatorSession session = sessions.get(uuid);
        return session != null && session.area() == null;
    }

    private boolean currentSession(UUID uuid, SpectatorSession expected) {
        SpectatorSession current = sessions.get(uuid);
        return expected != null
                && current != null
                && current.token().equals(expected.token())
                && (!expected.external()
                        || expected.area() == null
                        || gameManager.getPlayerSpectatorStatus(uuid) == expected.area());
    }

    /** Called by the routing facade before an external spectator is teleported into an area. */
    public void prepareExternal(@NotNull Player player) {
        if (!loaded) return;
        UUID uuid = player.getUniqueId();
        BaseGameInstance area = gameManager.getPlayerSpectatorStatus(uuid);
        snapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
        sessions.compute(
                uuid,
                (ignored, current) ->
                        current == null
                                ? new SpectatorSession(uuid, area, true)
                                : current.withArea(area).withExternal(true));
        player.closeInventory();
        applyPresentation(player);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    /** Called when a game internally turns a participant into an eliminated spectator. */
    private void prepareParticipant(@NotNull Player player, @NotNull BaseGameInstance area) {
        UUID uuid = player.getUniqueId();
        snapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
        ItemStack controlSlot = player.getInventory().getItem(8);
        if (controlSlot != null) participantControlItems.putIfAbsent(uuid, controlSlot.clone());
        ItemStack helmet = player.getInventory().getHelmet();
        participantHelmets.putIfAbsent(
                uuid, helmet == null ? new ItemStack(Material.AIR) : helmet.clone());
        participantNoPhysics.putIfAbsent(
                uuid, player.getGameMode() != GameMode.SPECTATOR && player.hasNoPhysics());
        sessions.computeIfAbsent(uuid, ignored -> new SpectatorSession(uuid, area, false));
        player.closeInventory();
        plugin.getVisibilityManager().reconcilePlayer(uuid);
    }

    public void onAreaReleased(@NotNull BaseGameInstance area) {
        for (SpectatorSession session : List.copyOf(sessions.values())) {
            // External spectators are released by BaseGameInstance.releaseAllSpectators (or held
            // for
            // the next event round) and must not be stolen by participant cleanup here.
            if (session.area() != area || session.external()) continue;
            Player player = Bukkit.getPlayer(session.uuid());
            sessions.remove(session.uuid(), session);
            if (player != null) clearPresentation(player, session.external());
            snapshots.remove(session.uuid());
            participantControlItems.remove(session.uuid());
            // An offline eliminated participant cannot be restored yet. Keep the original
            // presentation
            // state until the next join, where onJoin() removes the core effects and restores it.
            if (player != null) {
                participantHelmets.remove(session.uuid());
                participantNoPhysics.remove(session.uuid());
            }
            plugin.getVisibilityManager().clearManualOverrides(session.uuid());
        }
    }

    public void beforeParticipantJoin(@NotNull Player player, @NotNull BaseGameInstance area) {
        resumeParticipant(player, area);
        // A reconnect may already have the requested saved mode. Give the game a normal baseline so
        // choosing Spectator again produces a real transition and a fresh managed session.
        if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.ADVENTURE);
    }

    public void openControls(@NotNull Player player) {
        if (!isSpectatorLike(player.getUniqueId())) return;
        openPlayerTeleportMenu(player);
    }

    public void openMainMenu(@NotNull Player player) {
        gameManager.openSpectateMenu(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            BaseGameInstance external = gameManager.getPlayerSpectatorStatus(uuid);
            BaseGameInstance area = gameManager.getBasePlayerArea(uuid);
            if (external != null) {
                snapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
                sessions.computeIfAbsent(
                        uuid, ignored -> new SpectatorSession(uuid, external, true));
            } else if (area != null) {
                prepareParticipant(player, area);
            } else {
                snapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
                sessions.computeIfAbsent(uuid, ignored -> new SpectatorSession(uuid, null, true));
            }
            // The server keeps vanilla Spectator semantics. The shared packet adapter sends
            // Adventure.
            SpectatorSession expected = sessions.get(uuid);
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (player.isOnline() && currentSession(uuid, expected))
                                    applyPresentation(player);
                                plugin.getVisibilityManager().reconcilePlayer(uuid);
                            });
        } else {
            SpectatorSession current = sessions.get(uuid);
            if (current != null && !current.external()) resumeParticipant(player, current.area());
            else if (current != null && current.area() == null) leavePresentation(player);
            // External area spectators can be temporarily changed by arena overlays. Restore their
            // authoritative mode next tick unless the routing facade has already released the
            // session.
            else if (current != null)
                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    if (player.isOnline() && currentSession(uuid, current))
                                        enforcePassiveState(player, current);
                                });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStartSpectating(PlayerStartSpectatingEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        SpectatorSession expected = sessions.get(player.getUniqueId());
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (player.isOnline() && currentSession(player.getUniqueId(), expected))
                                applyPresentation(player);
                            plugin.getVisibilityManager().reconcilePlayer(player.getUniqueId());
                        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (isProtectedSpectator(event.getEntity())
                || event instanceof EntityDamageByEntityEvent damage
                        && isSpectatorSource(damage.getDamager())) event.setCancelled(true);
    }

    /** Cancelling the modern hit event keeps projectiles from affecting protected spectators. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(@NotNull ProjectileHitEvent event) {
        if (isProtectedSpectator(event.getHitEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(@NotNull ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (shooter instanceof Player player && isSpectatorLike(player.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityTarget(@NotNull EntityTargetEvent event) {
        if (!isProtectedSpectator(event.getTarget())) return;
        event.setCancelled(true);
        event.setTarget(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityInteract(@NotNull EntityInteractEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInsideBlock(@NotNull EntityInsideBlockEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    /** Prevents spectator movement from producing sculk/warden vibration game events. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEvent(@NotNull GenericGameEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (!isSpectatorLike(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        SpectatorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        int slot = player.getInventory().getHeldItemSlot();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!session.external()) {
            if (slot == hotbarSlot("participant-controls", 8)) {
                Bukkit.getScheduler().runTask(plugin, () -> openPlayerTeleportMenu(player));
            }
            return;
        }
        if (slot == hotbarSlot("night-vision", 0)) toggleNightVision(player, session);
        else if (slot == hotbarSlot("player-teleport", 1)) {
            if (rightClick)
                Bukkit.getScheduler().runTask(plugin, () -> openPlayerTeleportMenu(player));
        } else if (slot == hotbarSlot("leave", 5)) {
            if (gameManager.leaveSpectating(player))
                feedback(player, MessageConfig.SPECTATOR_LEFT, NamedTextColor.RED, 0.8F);
        } else if (slot == hotbarSlot("flight-speed", 7)) {
            adjustFlySpeed(player, rightClick ? -.05F : .05F);
        } else if (slot == hotbarSlot("venue-selector", 8)) {
            Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(@NotNull PlayerInteractAtEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStandManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(@NotNull PlayerBucketFillEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEntity(@NotNull PlayerBucketEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(@NotNull PlayerSwapHandItemsEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttemptPickup(@NotNull PlayerAttemptPickupItemEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupArrow(@NotNull PlayerPickupArrowEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMount(@NotNull EntityMountEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleEnter(@NotNull VehicleEnterEvent event) {
        if (isProtectedSpectator(event.getEntered())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleCollision(@NotNull VehicleEntityCollisionEvent event) {
        if (isProtectedSpectator(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(@NotNull PlayerLeashEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnleash(@NotNull PlayerUnleashEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShear(@NotNull PlayerShearEntityEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRaidTrigger(@NotNull RaidTriggerEvent event) {
        if (isSpectatorLike(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isSpectatorLike(player.getUniqueId())) return;
        if (!sessions.containsKey(player.getUniqueId())) adoptExisting(player);
        SpectatorSession expected = sessions.get(player.getUniqueId());
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!player.isOnline()
                                    || Bukkit.getPlayer(player.getUniqueId()) != player) return;
                            player.spigot().respawn();
                            if (currentSession(player.getUniqueId(), expected))
                                applyPresentation(player);
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!event.getPlayer().isOnline()) return;
                            if (isSpectatorLike(uuid)) {
                                if (!sessions.containsKey(uuid)) adoptExisting(event.getPlayer());
                                else applyPresentation(event.getPlayer());
                            } else restoreStaleParticipantState(event.getPlayer());
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        teleportRequests.remove(uuid);
        SpectatorSession session = sessions.get(uuid);
        if (session != null) {
            sessions.remove(uuid, session);
            clearPassiveState(event.getPlayer());
            InventorySnapshot snapshot = snapshots.get(uuid);
            if (snapshot != null) {
                if (session.external()) snapshot.restore(event.getPlayer());
                else {
                    snapshot.restoreFlags(event.getPlayer());
                    ItemStack helmet = participantHelmets.get(uuid);
                    event.getPlayer()
                            .getInventory()
                            .setHelmet(
                                    helmet == null || helmet.getType().isAir()
                                            ? null
                                            : helmet.clone());
                    ItemStack control = participantControlItems.get(uuid);
                    event.getPlayer()
                            .getInventory()
                            .setItem(8, control == null ? null : control.clone());
                    event.getPlayer()
                            .setGameMode(
                                    snapshot.mode() == GameMode.SPECTATOR
                                            ? GameMode.ADVENTURE
                                            : snapshot.mode());
                }
            }
            sessions.put(uuid, session);
        }
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ControlHolder holder) {
            event.setCancelled(true);
            if (holder.viewer.equals(player.getUniqueId()) && event.getClickedInventory() == top)
                clickControl(player, holder, event.getRawSlot());
            return;
        }
        if (isSpectatorLike(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getClickedInventory() == player.getInventory()
                    && event.getRawSlot() == 8
                    && player.getInventory().getItem(8) != null) openControls(player);
            // A cancelled click can leave a client-side cursor/hotbar ghost for number-key,
            // double-click and shift-click actions.  Resend the authoritative inventory after the
            // event so protected compass, feather and Bingo-card slots cannot be copied or swapped.
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && isSpectatorLike(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryCreative(@NotNull InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && isSpectatorLike(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    public void leavePresentation(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        teleportRequests.remove(uuid);
        SpectatorSession session = sessions.remove(uuid);
        if (session == null) {
            plugin.getVisibilityManager().clearManualOverrides(uuid);
            return;
        }
        player.closeInventory();
        clearPresentation(player, session.external());
        snapshots.remove(uuid);
        participantControlItems.remove(uuid);
        participantHelmets.remove(uuid);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    /**
     * Returns an internally eliminated participant to live play without touching their game
     * inventory. External spectators must continue to use {@link #leavePresentation(Player)} so
     * their pre-spectate inventory snapshot is restored instead.
     */
    public void resumeParticipant(@NotNull Player player, @NotNull BaseGameInstance area) {
        UUID uuid = player.getUniqueId();
        SpectatorSession session = sessions.get(uuid);
        if (session == null || session.external() || session.area() != area) return;
        if (!sessions.remove(uuid, session)) return;
        teleportRequests.remove(uuid);
        player.closeInventory();
        clearPassiveState(player);
        InventorySnapshot snapshot = snapshots.get(uuid);
        if (snapshot != null) snapshot.restoreFlags(player);
        restoreParticipantHelmet(player);
        Boolean previousNoPhysics = participantNoPhysics.remove(uuid);
        if (previousNoPhysics != null) player.setNoPhysics(previousNoPhysics);
        player.getInventory().setItem(8, participantControlItems.remove(uuid));
        snapshots.remove(uuid);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
        plugin.getVisibilityManager().reconcilePlayer(uuid);
    }

    /** Drops an offline session after its area has removed the UUID from its spectator roster. */
    public void forget(@NotNull UUID uuid) {
        teleportRequests.remove(uuid);
        sessions.remove(uuid);
        snapshots.remove(uuid);
        participantControlItems.remove(uuid);
        participantHelmets.remove(uuid);
        participantNoPhysics.remove(uuid);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    /**
     * Preloads only; the actual teleport occurs on the server thread after session and request
     * checks.
     */
    public void teleportManaged(
            @NotNull Player player,
            BaseGameInstance expectedArea,
            @NotNull org.bukkit.Location destination,
            @NotNull java.util.function.Consumer<Boolean> completion) {
        UUID uuid = player.getUniqueId();
        SpectatorSession expected = sessions.get(uuid);
        if (!loaded || expected == null || expected.area() != expectedArea) return;
        UUID request = UUID.randomUUID();
        teleportRequests.put(uuid, request);
        org.bukkit.Location location = destination.clone();
        if (location.getWorld() == null) {
            teleportRequests.remove(uuid, request);
            completion.accept(false);
            return;
        }
        location.getWorld()
                .getChunkAtAsync(location)
                .whenComplete(
                        (chunk, failure) -> {
                            if (!loaded || !plugin.isEnabled()) return;
                            try {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (!loaded
                                                            || !request.equals(
                                                                    teleportRequests.get(uuid))
                                                            || Bukkit.getPlayer(uuid) != player
                                                            || !currentSession(uuid, expected)) {
                                                        teleportRequests.remove(uuid, request);
                                                        return;
                                                    }
                                                    teleportRequests.remove(uuid, request);
                                                    boolean success =
                                                            failure == null
                                                                    && player.teleport(location);
                                                    if (success) applyPresentation(player);
                                                    completion.accept(success);
                                                });
                            } catch (org.bukkit.plugin.IllegalPluginAccessException stopped) {
                                // Plugin shutdown won the race with completion of the chunk
                                // preload.
                            }
                        });
    }

    public void refreshPresentation(@NotNull Player player) {
        if (isSpectatorLike(player.getUniqueId())) applyPresentation(player);
    }

    private void applyPresentation(@NotNull Player player) {
        if (!loaded) return;
        SpectatorSession session = sessions.get(player.getUniqueId());
        enforcePassiveState(player, session);
        // External spectators have no game-owned inventory. Internal eliminated participants
        // (notably
        // Bingo) retain their read-only card items and only receive the common control compass.
        if (session == null || session.external()) player.getInventory().clear();
        ensureSpectatorHelmet(player);
        if (session != null && session.external()) {
            applyExternalControlItems(player, session);
        } else {
            setHotbarItem(player.getInventory(), "participant-controls", Map.of(), null);
        }
        if (session != null && session.area() != null)
            session.area().applyManagedSpectatorPresentation(player);
        plugin.getVisibilityManager().reconcilePlayer(player.getUniqueId());
    }

    private void clearPresentation(@NotNull Player player, boolean restoreSnapshot) {
        clearPassiveState(player);
        InventorySnapshot snapshot = restoreSnapshot ? snapshots.get(player.getUniqueId()) : null;
        if (snapshot != null) snapshot.restore(player);
        else {
            restoreParticipantHelmet(player);
            player.getInventory().clear();
            player.setGameMode(GameMode.ADVENTURE);
            Boolean previousNoPhysics = participantNoPhysics.remove(player.getUniqueId());
            if (previousNoPhysics != null) player.setNoPhysics(previousNoPhysics);
        }
    }

    private void clearPassiveState(@NotNull Player player) {
        SpectatorStateService.clear(player);
    }

    private void updatePresentation() {
        for (SpectatorSession session : sessions.values()) {
            Player viewer = Bukkit.getPlayer(session.uuid());
            if (viewer == null || !viewer.isOnline()) continue;
            enforcePassiveState(viewer, session);
        }
    }

    private void enforcePassiveState(@NotNull Player player, SpectatorSession session) {
        SpectatorStateService.apply(player, session == null || session.nightVision());
    }

    private static void ensureSpectatorHelmet(@NotNull Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType().isAir())
            player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
    }

    private void restoreParticipantHelmet(@NotNull Player player) {
        ItemStack original = participantHelmets.remove(player.getUniqueId());
        if (original == null) return;
        player.getInventory().setHelmet(original.getType().isAir() ? null : original.clone());
    }

    private void restoreStaleParticipantState(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (!participantHelmets.containsKey(uuid) && !participantNoPhysics.containsKey(uuid))
            return;
        clearPassiveState(player);
        restoreParticipantHelmet(player);
        Boolean previousNoPhysics = participantNoPhysics.remove(uuid);
        if (previousNoPhysics != null) player.setNoPhysics(previousNoPhysics);
        participantControlItems.remove(uuid);
    }

    private void applyExternalControlItems(
            @NotNull Player player, @NotNull SpectatorSession session) {
        setHotbarItem(
                player.getInventory(),
                "night-vision",
                Map.of(),
                session.nightVision() ? "enabled" : "disabled");
        setHotbarItem(player.getInventory(), "player-teleport", Map.of(), null);
        setHotbarItem(player.getInventory(), "leave", Map.of(), null);
        setHotbarItem(
                player.getInventory(), "flight-speed", Map.of("speed", speedText(player)), null);
        setHotbarItem(player.getInventory(), "venue-selector", Map.of(), null);
    }

    private void toggleNightVision(@NotNull Player player, @NotNull SpectatorSession session) {
        boolean enabled = !session.nightVision();
        SpectatorStateService.setNightVision(player, enabled);
        SpectatorSession updated = session.withNightVision(enabled);
        sessions.put(player.getUniqueId(), updated);
        if (updated.external()) applyExternalControlItems(player, updated);
        feedback(
                player,
                enabled
                        ? MessageConfig.SPECTATOR_NIGHT_VISION_ENABLED
                        : MessageConfig.SPECTATOR_NIGHT_VISION_DISABLED,
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED,
                enabled ? 1.2F : 0.8F);
    }

    private void openPlayerTeleportMenu(@NotNull Player player) {
        String menuName = "player-teleport-selector";
        ControlHolder holder = new ControlHolder(player.getUniqueId());
        GuiConfig.MenuSpec menu = controlMenu(menuName);
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    private void adjustFlySpeed(@NotNull Player player, float delta) {
        float speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, player.getFlySpeed() + delta));
        player.setFlySpeed(speed);
        SpectatorSession session = sessions.get(player.getUniqueId());
        if (session != null && session.external()) applyExternalControlItems(player, session);
        feedback(
                player,
                MessageConfig.SPECTATOR_FLIGHT_SPEED.replace("%speed%", speedText(player)),
                NamedTextColor.YELLOW,
                delta > 0 ? 1.25F : 0.8F);
    }

    private static String speedText(@NotNull Player player) {
        return Math.round(player.getFlySpeed() * 100F) + "%";
    }

    private static void feedback(
            @NotNull Player player,
            @NotNull String message,
            @NotNull NamedTextColor color,
            float pitch) {
        player.sendActionBar(LegacyText.component(message, color).decorate(TextDecoration.BOLD));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    private boolean isProtectedSpectator(Entity entity) {
        return entity instanceof Player player && isSpectatorLike(player.getUniqueId());
    }

    private boolean isSpectatorSource(Entity entity) {
        if (isProtectedSpectator(entity)) return true;
        if (!(entity instanceof Projectile projectile)) return false;
        return projectile.getShooter() instanceof Player player
                && isSpectatorLike(player.getUniqueId());
    }

    private void refresh(@NotNull ControlHolder holder) {
        holder.inventory.clear();
        SpectatorSession session = sessions.get(holder.viewer);
        if (session == null) return;
        holder.targets.clear();
        String screen = "player-teleport-selector";
        GuiConfig.MenuSpec menu = controlMenu(screen);
        List<Player> targets = playersInArea(session.area(), holder.viewer);
        int pageSize = menu.contentSlots().size();
        int pageCount = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        holder.page = Math.min(holder.page, pageCount - 1);
        int from = holder.page * pageSize;
        for (int i = from; i < Math.min(from + pageSize, targets.size()); i++) {
            Player target = targets.get(i);
            int slot = menu.contentSlots().get(i - from);
            holder.inventory.setItem(
                    slot,
                    configuredMenuItem(
                            screen, "player", Map.of("player", target.getName()), null, null));
            holder.targets.put(slot, target.getUniqueId());
        }
        setMenuItem(holder.inventory, screen, "close", Map.of(), null);
        if (holder.page > 0) setMenuItem(holder.inventory, screen, "previous", Map.of(), null);
        setMenuItem(
                holder.inventory,
                screen,
                "page",
                Map.of("page", holder.page + 1, "pages", pageCount),
                null);
        if (holder.page + 1 < pageCount)
            setMenuItem(holder.inventory, screen, "next", Map.of(), null);
    }

    private void clickControl(@NotNull Player player, @NotNull ControlHolder holder, int slot) {
        SpectatorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        String screen = "player-teleport-selector";
        if (slot == menuItemSlot(screen, "close", 48)) {
            player.closeInventory();
            return;
        }
        if (slot == menuItemSlot(screen, "previous", 45) && holder.page > 0) {
            holder.page--;
            refresh(holder);
            return;
        }
        if (slot == menuItemSlot(screen, "next", 53)) {
            holder.page++;
            refresh(holder);
            return;
        }
        UUID targetId = holder.targets.get(slot);
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target != null
                && playersInArea(session.area(), player.getUniqueId()).stream()
                        .anyMatch(candidate -> candidate.getUniqueId().equals(targetId))) {
            teleportManaged(
                    player,
                    session.area(),
                    target.getLocation(),
                    success -> {
                        if (success)
                            feedback(
                                    player,
                                    MessageConfig.SPECTATOR_TELEPORTED_TO_PLAYER.replace(
                                            "%player%", target.getName()),
                                    NamedTextColor.LIGHT_PURPLE,
                                    1.2F);
                    });
            player.closeInventory();
        }
    }

    private List<Player> playersInArea(BaseGameInstance area, UUID viewerId) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(viewerId))
                .filter(
                        player ->
                                area == null
                                        || gameManager.getBasePlayerArea(player.getUniqueId())
                                                == area
                                        || gameManager.getPlayerSpectatorStatus(
                                                        player.getUniqueId())
                                                == area
                                        || areaOf(player.getUniqueId()) == area)
                .<Player>map(player -> player)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static GuiConfig.MenuSpec controlMenu(String screen) {
        return GuiConfig.menu(
                "spectator.menus." + screen,
                MAIN_SIZE,
                screen,
                java.util.stream.IntStream.range(0, MAIN_SIZE).boxed().toList());
    }

    private static int menuItemSlot(String screen, String key, int fallback) {
        int slot = GuiConfig.item("spectator.menus." + screen + ".items." + key, Map.of()).slot();
        return slot < 0 ? fallback : slot;
    }

    private static int hotbarSlot(String key, int fallback) {
        int slot = GuiConfig.item("spectator.hotbar." + key, Map.of()).slot();
        return slot < 0 ? fallback : slot;
    }

    private static ItemStack configuredMenuItem(
            String screen,
            String key,
            Map<String, ?> placeholders,
            String state,
            Material materialOverride) {
        GuiConfig.ItemSpec configured =
                GuiConfig.item("spectator.menus." + screen + ".items." + key, state, placeholders);
        return item(
                materialOverride == null ? configured.material() : materialOverride,
                configured.title(),
                configured.lore(),
                configured.glint());
    }

    private static void setMenuItem(
            Inventory inventory,
            String screen,
            String key,
            Map<String, ?> placeholders,
            String state) {
        GuiConfig.ItemSpec configured =
                GuiConfig.item("spectator.menus." + screen + ".items." + key, state, placeholders);
        if (configured.slot() >= 0 && configured.slot() < inventory.getSize())
            inventory.setItem(
                    configured.slot(),
                    item(
                            configured.material(),
                            configured.title(),
                            configured.lore(),
                            configured.glint()));
    }

    private static void setHotbarItem(
            PlayerInventory inventory, String key, Map<String, ?> placeholders, String state) {
        GuiConfig.ItemSpec configured =
                GuiConfig.item("spectator.hotbar." + key, state, placeholders);
        if (configured.slot() >= 0 && configured.slot() < 9)
            inventory.setItem(
                    configured.slot(),
                    item(
                            configured.material(),
                            configured.title(),
                            configured.lore(),
                            configured.glint()));
    }

    private static ItemStack item(
            Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    private static final class ControlHolder implements MenuInventory {
        private final UUID viewer;
        private int page;
        private final Map<Integer, UUID> targets = new HashMap<>();
        private Inventory inventory;

        private ControlHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private record SpectatorSession(
            UUID uuid, BaseGameInstance area, boolean external, boolean nightVision, UUID token) {
        private SpectatorSession(UUID uuid, BaseGameInstance area, boolean external) {
            this(uuid, area, external, true, UUID.randomUUID());
        }

        private SpectatorSession withExternal(boolean value) {
            return value == external
                    ? this
                    : new SpectatorSession(uuid, area, value, nightVision, UUID.randomUUID());
        }

        private SpectatorSession withArea(BaseGameInstance value) {
            return value == area
                    ? this
                    : new SpectatorSession(uuid, value, external, nightVision, UUID.randomUUID());
        }

        private SpectatorSession withNightVision(boolean value) {
            return new SpectatorSession(uuid, area, external, value, token);
        }
    }

    private record InventorySnapshot(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack[] extra,
            GameMode mode,
            boolean allowFlight,
            boolean flying,
            boolean invulnerable,
            boolean collidable,
            boolean noPhysics,
            boolean affectsSpawning,
            boolean canPickupItems,
            boolean sleepingIgnored,
            float flySpeed,
            float walkSpeed,
            List<PotionEffect> effects,
            long capturedAt) {
        private static InventorySnapshot capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            InventorySnapshot snapshot =
                    new InventorySnapshot(
                            cloneItems(inventory.getContents()),
                            cloneItems(inventory.getArmorContents()),
                            cloneItems(inventory.getExtraContents()),
                            player.getGameMode(),
                            player.getAllowFlight(),
                            player.isFlying(),
                            player.isInvulnerable(),
                            player.isCollidable(),
                            player.hasNoPhysics(),
                            player.getAffectsSpawning(),
                            player.getCanPickupItems(),
                            player.isSleepingIgnored(),
                            player.getFlySpeed(),
                            player.getWalkSpeed(),
                            new ArrayList<>(player.getActivePotionEffects()),
                            System.nanoTime());
            if (snapshot.mode() != GameMode.SPECTATOR) return snapshot;
            // No pre-spectate snapshot exists after a cold/mid-session load. Vanilla spectator
            // ability
            // flags cannot be restored as normal player flags; leave this adopted state in
            // Adventure.
            return new InventorySnapshot(
                    snapshot.contents(),
                    snapshot.armor(),
                    snapshot.extra(),
                    GameMode.ADVENTURE,
                    false,
                    false,
                    false,
                    true,
                    false,
                    true,
                    true,
                    false,
                    snapshot.flySpeed(),
                    snapshot.walkSpeed(),
                    snapshot.effects(),
                    snapshot.capturedAt());
        }

        private void restore(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.setContents(cloneItems(contents));
            inventory.setArmorContents(cloneItems(armor));
            inventory.setExtraContents(cloneItems(extra));
            player.setGameMode(mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : mode);
            restoreFlags(player);
        }

        private void restoreFlags(Player player) {
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setInvulnerable(invulnerable);
            player.setCollidable(collidable);
            player.setNoPhysics(noPhysics);
            player.setAffectsSpawning(affectsSpawning);
            player.setCanPickupItems(canPickupItems);
            player.setSleepingIgnored(sleepingIgnored);
            player.setFlySpeed(flySpeed);
            player.setWalkSpeed(walkSpeed);
            for (PotionEffect effect : player.getActivePotionEffects())
                player.removePotionEffect(effect.getType());
            long elapsedTicks = Math.max(0L, (System.nanoTime() - capturedAt) / 50_000_000L);
            for (PotionEffect effect : effects) {
                if (effect.getDuration() != PotionEffect.INFINITE_DURATION
                        && elapsedTicks >= effect.getDuration()) continue;
                int duration =
                        effect.getDuration() == PotionEffect.INFINITE_DURATION
                                ? PotionEffect.INFINITE_DURATION
                                : (int) (effect.getDuration() - elapsedTicks);
                player.addPotionEffect(
                        new PotionEffect(
                                effect.getType(),
                                duration,
                                effect.getAmplifier(),
                                effect.isAmbient(),
                                effect.hasParticles(),
                                effect.hasIcon()));
            }
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++)
                copy[i] = source[i] == null ? null : source[i].clone();
            return copy;
        }
    }
}
