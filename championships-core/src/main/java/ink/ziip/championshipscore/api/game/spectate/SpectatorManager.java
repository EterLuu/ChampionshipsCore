package ink.ziip.championshipscore.api.game.spectate;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single lifecycle owner for spectator presentation. The game manager remains the routing facade for
 * compatibility, while this module owns the mode, inventory, effects, controls, reconnect and cleanup
 * contract shared by every game area.
 */
public final class SpectatorManager extends BaseManager implements Listener {
    public static final String OWNER = "spectator:controls";
    private static final int MAIN_SIZE = 9;
    private static final float MIN_SPEED = 0.05F;
    private static final float MAX_SPEED = 1.0F;
    private static final PotionEffect NIGHT_VISION = new PotionEffect(PotionEffectType.NIGHT_VISION,
            PotionEffect.INFINITE_DURATION, 0, true, false, false);

    private final GameManager gameManager;
    private final Map<UUID, SpectatorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> participantControlItems = new ConcurrentHashMap<>();
    private BukkitTask presentationTask;

    public SpectatorManager(@NotNull ChampionshipsCore plugin, @NotNull GameManager gameManager) {
        super(plugin);
        this.gameManager = gameManager;
    }

    @Override
    public void load() {
        if (presentationTask != null) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        presentationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updatePresentation, 10L, 10L);
    }

    @Override
    public void unload() {
        if (presentationTask != null) presentationTask.cancel();
        presentationTask = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            SpectatorSession session = sessions.get(player.getUniqueId());
            if (session != null) clearPresentation(player, session.external());
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ControlHolder)
                player.closeInventory();
        }
        for (SpectatorSession session : sessions.values()) {
            if (session.area() != null) session.area().onlyRemoveSpectatorFromList(session.uuid());
        }
        sessions.clear();
        snapshots.clear();
        participantControlItems.clear();
        HandlerList.unregisterAll(this);
    }

    public boolean isSpectatorLike(@NotNull UUID uuid) {
        return sessions.containsKey(uuid) || gameManager.getPlayerSpectatorStatus(uuid) != null;
    }

    @org.jetbrains.annotations.Nullable
    public BaseGameInstance areaOf(@NotNull UUID uuid) {
        SpectatorSession session = sessions.get(uuid);
        return session == null ? gameManager.getPlayerSpectatorStatus(uuid) : session.area();
    }

    /** Called by the routing facade before an external spectator is teleported into an area. */
    public void prepareExternal(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        BaseGameInstance area = gameManager.getPlayerSpectatorStatus(uuid);
        snapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
        sessions.compute(uuid, (ignored, current) -> current == null
                ? new SpectatorSession(uuid, area, true)
                : current.withArea(area).withExternal(true));
        applyPresentation(player);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    /** Called when a game internally turns a participant into an eliminated spectator. */
    private void prepareParticipant(@NotNull Player player, @NotNull BaseGameInstance area) {
        UUID uuid = player.getUniqueId();
        ItemStack controlSlot = player.getInventory().getItem(8);
        if (controlSlot != null) participantControlItems.putIfAbsent(uuid, controlSlot.clone());
        sessions.computeIfAbsent(uuid, ignored -> new SpectatorSession(uuid, area, false));
        plugin.getVisibilityManager().reconcilePlayer(uuid);
    }

    public void onAreaReleased(@NotNull BaseGameInstance area) {
        for (SpectatorSession session : List.copyOf(sessions.values())) {
            // External spectators are released by BaseGameInstance.releaseAllSpectators (or held for
            // the next event round) and must not be stolen by participant cleanup here.
            if (session.area() != area || session.external()) continue;
            Player player = Bukkit.getPlayer(session.uuid());
            sessions.remove(session.uuid(), session);
            if (player != null) clearPresentation(player, session.external());
            snapshots.remove(session.uuid());
            participantControlItems.remove(session.uuid());
            plugin.getVisibilityManager().clearManualOverrides(session.uuid());
        }
    }

    public void openControls(@NotNull Player player) {
        if (!isSpectatorLike(player.getUniqueId())) return;
        openControlScreen(player, ControlScreen.VISIBILITY);
    }

    public void openMainMenu(@NotNull Player player) {
        gameManager.openSpectateMenu(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (event.getNewGameMode() != GameMode.SPECTATOR) return;
        BaseGameInstance area = gameManager.getBasePlayerArea(uuid);
        BaseGameInstance external = gameManager.getPlayerSpectatorStatus(uuid);
        if (external != null) {
            sessions.computeIfAbsent(uuid, ignored -> new SpectatorSession(uuid, external, true));
        } else if (area != null && gameManager.isInstanceActivelyRunning(area) && !area.isIntroductionPhase()) {
            prepareParticipant(player, area);
        } else {
            return;
        }
        // Never expose vanilla SPECTATOR even for the one event tick. The presentation module owns the
        // replacement Adventure state; areas request elimination through the area-level API.
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isSpectatorLike(uuid)) applyPresentation(player);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (isProtectedSpectator(event.getEntity()) || event instanceof EntityDamageByEntityEvent damage
                && isSpectatorSource(damage.getDamager()))
            event.setCancelled(true);
    }

    /** Cancelling the modern hit event keeps projectiles from affecting protected spectators. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(@NotNull ProjectileHitEvent event) {
        if (isProtectedSpectator(event.getHitEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(@NotNull ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (shooter instanceof Player player && isSpectatorLike(player.getUniqueId())) event.setCancelled(true);
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
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        SpectatorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        int slot = player.getInventory().getHeldItemSlot();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!session.external()) {
            if (slot == hotbarSlot("participant-controls", 8)) {
                Bukkit.getScheduler().runTask(plugin, () -> openControlScreen(player,
                        rightClick ? ControlScreen.PLAYER_TELEPORT : ControlScreen.VISIBILITY));
            }
            return;
        }
        if (slot == hotbarSlot("night-vision", 0)) toggleNightVision(player, session);
        else if (slot == hotbarSlot("player-teleport", 1)) {
            if (rightClick) Bukkit.getScheduler().runTask(plugin,
                    () -> openControlScreen(player, ControlScreen.PLAYER_TELEPORT));
        }
        else if (slot == hotbarSlot("player-visibility", 4)) {
            Bukkit.getScheduler().runTask(plugin, () -> openControlScreen(player, ControlScreen.VISIBILITY));
        } else if (slot == hotbarSlot("leave", 5)) {
            if (gameManager.leaveSpectating(player))
                feedback(player, GuiConfig.text("spectator.copy.left"), NamedTextColor.RED, 0.8F);
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
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.spigot().respawn();
            applyPresentation(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            if (isSpectatorLike(uuid)) applyPresentation(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (gameManager.getPlayerSpectatorStatus(uuid) == null
                    && gameManager.getBasePlayerArea(uuid) == null) {
                sessions.remove(uuid);
                snapshots.remove(uuid);
                plugin.getVisibilityManager().clearManualOverrides(uuid);
            }
        });
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
            if (event.getClickedInventory() == player.getInventory() && event.getRawSlot() == 8
                    && player.getInventory().getItem(8) != null) openControls(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isSpectatorLike(player.getUniqueId()))
            event.setCancelled(true);
    }

    public void leavePresentation(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        SpectatorSession session = sessions.remove(uuid);
        if (session == null) {
            plugin.getVisibilityManager().clearManualOverrides(uuid);
            return;
        }
        clearPresentation(player, session.external());
        snapshots.remove(uuid);
        participantControlItems.remove(uuid);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    /**
     * Returns an internally eliminated participant to live play without touching their game inventory.
     * External spectators must continue to use {@link #leavePresentation(Player)} so their pre-spectate
     * inventory snapshot is restored instead.
     */
    public void resumeParticipant(@NotNull Player player, @NotNull BaseGameInstance area) {
        UUID uuid = player.getUniqueId();
        SpectatorSession session = sessions.get(uuid);
        if (session == null || session.external() || session.area() != area) return;
        if (!sessions.remove(uuid, session)) return;
        clearPassiveState(player);
        player.getInventory().setItem(8, participantControlItems.remove(uuid));
        snapshots.remove(uuid);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
        plugin.getVisibilityManager().reconcilePlayer(uuid);
    }

    /** Drops an offline session after its area has removed the UUID from its spectator roster. */
    public void forget(@NotNull UUID uuid) {
        sessions.remove(uuid);
        snapshots.remove(uuid);
        participantControlItems.remove(uuid);
        plugin.getVisibilityManager().clearManualOverrides(uuid);
    }

    private void applyPresentation(@NotNull Player player) {
        SpectatorSession session = sessions.get(player.getUniqueId());
        if (session != null && !session.external() && session.area() != null)
            session.area().applyManagedSpectatorPresentation(player);
        enforcePassiveState(player, session);
        // External spectators have no game-owned inventory. Internal eliminated participants (notably
        // Bingo) retain their read-only card items and only receive the common control compass.
        if (session == null || session.external()) player.getInventory().clear();
        if (session != null && session.external()) {
            applyExternalControlItems(player, session);
        } else {
            setHotbarItem(player.getInventory(), "participant-controls", Map.of(), null);
        }
    }

    private void clearPresentation(@NotNull Player player, boolean restoreSnapshot) {
        clearPassiveState(player);
        InventorySnapshot snapshot = restoreSnapshot ? snapshots.get(player.getUniqueId()) : null;
        if (snapshot != null) snapshot.restore(player);
        else {
            player.getInventory().clear();
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void clearPassiveState(@NotNull Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setAffectsSpawning(true);
        player.setCanPickupItems(true);
        player.setSleepingIgnored(false);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.setFallDistance(0F);
        player.setFireTicks(0);
    }

    private void updatePresentation() {
        for (SpectatorSession session : sessions.values()) {
            Player viewer = Bukkit.getPlayer(session.uuid());
            if (viewer == null || !viewer.isOnline()) continue;
            enforcePassiveState(viewer, session);
        }
    }

    private void enforcePassiveState(@NotNull Player player, SpectatorSession session) {
        if (player.getGameMode() != GameMode.ADVENTURE) player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setAffectsSpawning(false);
        player.setCanPickupItems(false);
        player.setSleepingIgnored(true);
        player.setFallDistance(0F);
        player.setFireTicks(0);
        if (session == null || session.nightVision()) player.addPotionEffect(NIGHT_VISION);
        else player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    private void applyExternalControlItems(@NotNull Player player, @NotNull SpectatorSession session) {
        setHotbarItem(player.getInventory(), "night-vision", Map.of(),
                session.nightVision() ? "enabled" : "disabled");
        setHotbarItem(player.getInventory(), "player-teleport", Map.of(), null);
        setHotbarItem(player.getInventory(), "player-visibility", Map.of(), null);
        setHotbarItem(player.getInventory(), "leave", Map.of(), null);
        setHotbarItem(player.getInventory(), "flight-speed", Map.of("speed", speedText(player)), null);
        setHotbarItem(player.getInventory(), "venue-selector", Map.of(), null);
    }

    private void toggleNightVision(@NotNull Player player, @NotNull SpectatorSession session) {
        boolean enabled = !session.nightVision();
        if (enabled) player.addPotionEffect(NIGHT_VISION);
        else player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        SpectatorSession updated = session.withNightVision(enabled);
        sessions.put(player.getUniqueId(), updated);
        if (updated.external()) applyExternalControlItems(player, updated);
        feedback(player, GuiConfig.text("spectator.copy.night-vision",
                        Map.of("state", stateText(enabled))),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED, enabled ? 1.2F : 0.8F);
    }

    private void openControlScreen(@NotNull Player player, @NotNull ControlScreen screen) {
        String menuName = switch (screen) {
            case VISIBILITY -> "visibility";
            case PLAYER_TELEPORT -> "player-teleport-selector";
            case PLAYER_VISIBILITY -> "player-visibility-selector";
            case TEAM_VISIBILITY -> "team-visibility-selector";
        };
        ControlHolder holder = new ControlHolder(player.getUniqueId(), screen);
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
        feedback(player, GuiConfig.text("spectator.copy.flight-speed", Map.of("speed", speedText(player))), NamedTextColor.YELLOW,
                delta > 0 ? 1.25F : 0.8F);
    }

    private static String speedText(@NotNull Player player) {
        return Math.round(player.getFlySpeed() * 100F) + "%";
    }

    private static void feedback(@NotNull Player player, @NotNull String message,
                                 @NotNull NamedTextColor color, float pitch) {
        player.sendActionBar(Component.text(message, color).decorate(TextDecoration.BOLD));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    private boolean isProtectedSpectator(Entity entity) {
        return entity instanceof Player player && isSpectatorLike(player.getUniqueId());
    }

    private boolean isSpectatorSource(Entity entity) {
        if (isProtectedSpectator(entity)) return true;
        if (!(entity instanceof Projectile projectile)) return false;
        return projectile.getShooter() instanceof Player player && isSpectatorLike(player.getUniqueId());
    }

    private void refresh(@NotNull ControlHolder holder) {
        holder.inventory.clear();
        Player player = Bukkit.getPlayer(holder.viewer);
        if (player == null) return;
        SpectatorSession session = sessions.get(holder.viewer);
        if (session == null) return;
        if (holder.screen == ControlScreen.PLAYER_TELEPORT || holder.screen == ControlScreen.PLAYER_VISIBILITY) {
            holder.targets.clear();
            String screen = holder.screen == ControlScreen.PLAYER_TELEPORT
                    ? "player-teleport-selector" : "player-visibility-selector";
            GuiConfig.MenuSpec menu = controlMenu(screen);
            List<Player> targets = playersInArea(session.area(), holder.viewer);
            int pageSize = menu.contentSlots().size();
            int pageCount = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
            holder.page = Math.min(holder.page, pageCount - 1);
            int from = holder.page * pageSize;
            int to = Math.min(from + pageSize, targets.size());
            for (int i = from; i < to; i++) {
                Player target = targets.get(i);
                int slot = menu.contentSlots().get(i - from);
                holder.inventory.setItem(slot, configuredMenuItem(screen, "player",
                        Map.of("player", target.getName()), null, null));
                holder.targets.put(slot, target.getUniqueId());
            }
            setMenuItem(holder.inventory, screen, "close", Map.of(), null);
            if (holder.screen == ControlScreen.PLAYER_TELEPORT) {
                if (holder.page > 0) setMenuItem(holder.inventory, screen, "previous", Map.of(), null);
                setMenuItem(holder.inventory, screen, "page",
                        Map.of("page", holder.page + 1, "pages", pageCount), null);
                if (holder.page + 1 < pageCount) setMenuItem(holder.inventory, screen, "next", Map.of(), null);
            }
            return;
        }
        if (holder.screen == ControlScreen.TEAM_VISIBILITY) {
            holder.teams.clear();
            String screen = "team-visibility-selector";
            GuiConfig.MenuSpec menu = controlMenu(screen);
            if (session.area() instanceof BaseMultiTeamGameInstance multi) {
                List<ChampionshipTeam> teams = multi.getGameTeams();
                for (int i = 0; i < Math.min(menu.contentSlots().size(), teams.size()); i++) {
                    ChampionshipTeam team = teams.get(i);
                    int slot = menu.contentSlots().get(i);
                    holder.teams.put(slot, team);
                    holder.inventory.setItem(slot, configuredMenuItem(screen, "team",
                            Map.of("team", team.getName(), "team_color", team.getColorCode()),
                            null, SpectatorTeamIcon.wool(team.getColorName())));
                }
            }
            setMenuItem(holder.inventory, screen, "close", Map.of(), null);
            return;
        }
        setMenuItem(holder.inventory, "visibility", "show-all", Map.of(), null);
        setMenuItem(holder.inventory, "visibility", "show-player", Map.of(), null);
        setMenuItem(holder.inventory, "visibility", "show-team", Map.of(), null);
        setMenuItem(holder.inventory, "visibility", "close", Map.of(), null);
    }

    private void clickControl(@NotNull Player player, @NotNull ControlHolder holder, int slot) {
        SpectatorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (holder.screen == ControlScreen.PLAYER_TELEPORT || holder.screen == ControlScreen.PLAYER_VISIBILITY) {
            String screen = holder.screen == ControlScreen.PLAYER_TELEPORT
                    ? "player-teleport-selector" : "player-visibility-selector";
            if (slot == menuItemSlot(screen, "close", 8)) {
                player.closeInventory(); return;
            }
            if (holder.screen == ControlScreen.PLAYER_TELEPORT) {
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
            }
            UUID targetId = holder.targets.get(slot);
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            if (target != null && playersInArea(session.area(), player.getUniqueId()).stream()
                    .anyMatch(candidate -> candidate.getUniqueId().equals(targetId))) {
                if (holder.screen == ControlScreen.PLAYER_TELEPORT) {
                    player.teleportAsync(target.getLocation());
                    feedback(player, GuiConfig.text("spectator.copy.teleported-to-player",
                            Map.of("player", target.getName())), NamedTextColor.LIGHT_PURPLE, 1.2F);
                } else {
                plugin.getVisibilityManager().showOnlyPlayers(player.getUniqueId(), Set.of(targetId));
                feedback(player, GuiConfig.text("spectator.copy.visibility-player",
                        Map.of("player", target.getName())), NamedTextColor.LIGHT_PURPLE, 1.2F);
                }
                player.closeInventory();
            }
            return;
        }
        if (holder.screen == ControlScreen.TEAM_VISIBILITY) {
            String screen = "team-visibility-selector";
            if (slot == menuItemSlot(screen, "close", 8)) { player.closeInventory(); return; }
            ChampionshipTeam team = holder.teams.get(slot);
            if (team != null && session.area() instanceof BaseMultiTeamGameInstance) {
                plugin.getVisibilityManager().showOnlyTeams(player.getUniqueId(), Set.of(team.getId()));
                feedback(player, GuiConfig.text("spectator.copy.visibility-team",
                        Map.of("team", team.getName())), NamedTextColor.GOLD, 1.2F);
                player.closeInventory();
            }
            return;
        }
        if (slot == menuItemSlot("visibility", "show-all", 2)) {
            plugin.getVisibilityManager().clearManualOverrides(player.getUniqueId());
            feedback(player, GuiConfig.text("spectator.copy.visibility-all"), NamedTextColor.GREEN, 1.2F);
            player.closeInventory();
        } else if (slot == menuItemSlot("visibility", "show-player", 4)) {
            openControlScreen(player, ControlScreen.PLAYER_VISIBILITY);
        } else if (slot == menuItemSlot("visibility", "show-team", 6)) {
            openControlScreen(player, ControlScreen.TEAM_VISIBILITY);
        } else if (slot == menuItemSlot("visibility", "close", 8)) {
            player.closeInventory(); return;
        }
    }

    private List<Player> playersInArea(BaseGameInstance area, UUID viewerId) {
        if (area == null) return List.of();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(viewerId))
                .filter(player -> gameManager.getBasePlayerArea(player.getUniqueId()) == area)
                .filter(player -> !isSpectatorLike(player.getUniqueId()))
                .<Player>map(player -> player)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static GuiConfig.MenuSpec controlMenu(String screen) {
        return GuiConfig.menu("spectator.menus." + screen, MAIN_SIZE, screen,
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

    private static ItemStack configuredMenuItem(String screen, String key, Map<String, ?> placeholders,
                                                String state, Material materialOverride) {
        GuiConfig.ItemSpec configured = GuiConfig.item(
                "spectator.menus." + screen + ".items." + key, state, placeholders);
        return item(materialOverride == null ? configured.material() : materialOverride,
                configured.title(), configured.lore(), configured.glint());
    }

    private static void setMenuItem(Inventory inventory, String screen, String key,
                                    Map<String, ?> placeholders, String state) {
        GuiConfig.ItemSpec configured = GuiConfig.item(
                "spectator.menus." + screen + ".items." + key, state, placeholders);
        if (configured.slot() >= 0 && configured.slot() < inventory.getSize())
            inventory.setItem(configured.slot(),
                    item(configured.material(), configured.title(), configured.lore(), configured.glint()));
    }

    private static void setHotbarItem(PlayerInventory inventory, String key,
                                      Map<String, ?> placeholders, String state) {
        GuiConfig.ItemSpec configured = GuiConfig.item("spectator.hotbar." + key, state, placeholders);
        if (configured.slot() >= 0 && configured.slot() < 9)
            inventory.setItem(configured.slot(),
                    item(configured.material(), configured.title(), configured.lore(), configured.glint()));
    }

    private static String stateText(boolean enabled) {
        return GuiConfig.text(enabled ? "spectator.copy.states.enabled" : "spectator.copy.states.disabled");
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    private enum ControlScreen { VISIBILITY, PLAYER_TELEPORT, PLAYER_VISIBILITY, TEAM_VISIBILITY }

    private static final class ControlHolder implements InventoryHolder {
        private final UUID viewer;
        private ControlScreen screen;
        private int page;
        private final Map<Integer, UUID> targets = new HashMap<>();
        private final Map<Integer, ChampionshipTeam> teams = new HashMap<>();
        private Inventory inventory;

        private ControlHolder(UUID viewer, ControlScreen screen) { this.viewer = viewer; this.screen = screen; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private record SpectatorSession(UUID uuid, BaseGameInstance area, boolean external, boolean nightVision) {
        private SpectatorSession(UUID uuid, BaseGameInstance area, boolean external) {
            this(uuid, area, external, true);
        }
        private SpectatorSession withExternal(boolean value) { return new SpectatorSession(uuid, area, value, nightVision); }
        private SpectatorSession withArea(BaseGameInstance value) { return new SpectatorSession(uuid, value, external, nightVision); }
        private SpectatorSession withNightVision(boolean value) { return new SpectatorSession(uuid, area, external, value); }
    }

    private record InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra,
                                     GameMode mode, boolean allowFlight, boolean flying, boolean invulnerable,
                                     boolean collidable, boolean affectsSpawning, boolean canPickupItems,
                                     boolean sleepingIgnored, float flySpeed, float walkSpeed, List<PotionEffect> effects) {
        private static InventorySnapshot capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            return new InventorySnapshot(cloneItems(inventory.getContents()), cloneItems(inventory.getArmorContents()),
                    cloneItems(inventory.getExtraContents()), player.getGameMode(), player.getAllowFlight(), player.isFlying(),
                    player.isInvulnerable(), player.isCollidable(), player.getAffectsSpawning(), player.getCanPickupItems(),
                    player.isSleepingIgnored(), player.getFlySpeed(), player.getWalkSpeed(),
                    new ArrayList<>(player.getActivePotionEffects()));
        }
        private void restore(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.setContents(cloneItems(contents)); inventory.setArmorContents(cloneItems(armor));
            inventory.setExtraContents(cloneItems(extra));
            player.setGameMode(mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : mode);
            player.setAllowFlight(allowFlight); player.setFlying(allowFlight && flying);
            player.setInvulnerable(invulnerable); player.setCollidable(collidable);
            player.setAffectsSpawning(affectsSpawning); player.setCanPickupItems(canPickupItems);
            player.setSleepingIgnored(sleepingIgnored);
            player.setFlySpeed(flySpeed); player.setWalkSpeed(walkSpeed);
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            for (PotionEffect effect : effects) player.addPotionEffect(effect);
        }
        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
            return copy;
        }
    }
}
