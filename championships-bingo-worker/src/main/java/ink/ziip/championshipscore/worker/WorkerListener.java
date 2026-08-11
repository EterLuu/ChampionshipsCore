package ink.ziip.championshipscore.worker;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoStarterKitService;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.platform.bukkit.text.ChampionshipTabText;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

final class WorkerListener implements Listener {
    private final WorkerMatchRegistry registry;
    private final PlatformScheduler scheduler;

    WorkerListener(Plugin plugin, WorkerMatchRegistry registry) {
        this.registry = registry;
        this.scheduler = new PlatformScheduler(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        WorkerPlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        event.joinMessage(Component.translatable("multiplayer.player.joined",
                ChampionshipTabText.playerIdentityComponent(presentation.label(), presentation.teamColorCode(),
                        presentation.activePlayer(), player.getName())));
        registry.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        WorkerPlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        event.quitMessage(Component.translatable("multiplayer.player.left",
                ChampionshipTabText.playerIdentityComponent(presentation.label(), presentation.teamColorCode(),
                        presentation.activePlayer(), player.getName())));
        registry.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        WorkerPlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        event.renderer((source, sourceDisplayName, message, viewer) ->
                ChampionshipTabText.chatLine(presentation.label(), presentation.teamColorCode(),
                        presentation.activePlayer(), player.getName(), message));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            registry.requestObserve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            registry.requestObserve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMutation(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            registry.requestObserve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!registry.isRunningPlayer(event.getPlayer().getUniqueId())) event.message(null);
        registry.observeAdvancement(event.getPlayer(), event.getAdvancement());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedDrop(PlayerDropItemEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedPlace(BlockPlaceEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedBreak(BlockBreakEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedInteract(PlayerInteractEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedInteractEntity(PlayerInteractEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedArrowPickup(PlayerPickupArrowEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedDamageByEntity(EntityDamageByEntityEvent event) {
        UUID attacker = attackerId(event.getDamager());
        if (attacker != null && registry.isProtectedParticipant(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFinalCountdownMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !registry.isFinalCountdownPlayer(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (!registry.isPlaying(event.getPlayer().getUniqueId())) return;
        NamespacedKey key = event.getAdvancement().getKey();
        if (key != null && BingoStarterKitService.conflictingAdvancementKeys().contains(key.getKey())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = registry.respawnLocation(event.getPlayer());
        if (location == null) return;
        event.setRespawnLocation(location);
        scheduler.runEntityLater(event.getPlayer(), () -> registry.restoreAfterRespawn(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCompass(PlayerInteractEvent event) {
        if (event.getItem() == null || !registry.canUseBingoUi(event.getPlayer().getUniqueId())) return;
        org.bukkit.Material type = event.getItem().getType();
        if (type != org.bukkit.Material.COMPASS && type != org.bukkit.Material.FILLED_MAP) return;
        if (type == org.bukkit.Material.COMPASS
                && !registry.isRunningPlayer(event.getPlayer().getUniqueId())) return;
        Integer selectedTeam = type == org.bukkit.Material.FILLED_MAP
                ? registry.boundCardTeam(event.getItem()) : null;
        if (type == org.bukkit.Material.FILLED_MAP && selectedTeam == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (type == org.bukkit.Material.FILLED_MAP && !rightClick) return;
        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) return;
        if (type == org.bukkit.Material.FILLED_MAP && event.getHand() == EquipmentSlot.HAND
                && isOffhandWeapon(event.getPlayer().getInventory().getItemInOffHand().getType())) return;
        event.setCancelled(true);
        if (type == org.bukkit.Material.FILLED_MAP) {
            // Opening in the interaction callback can be overwritten by the client's use-item
            // acknowledgement on Folia. Defer exactly one entity tick, preserving ownership.
            scheduler.runEntityLater(event.getPlayer(),
                    () -> registry.openCard(event.getPlayer(), selectedTeam), 1L);
        } else {
            scheduler.runEntityLater(event.getPlayer(), () -> registry.openTeammates(event.getPlayer()), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!WorkerMenuService.isReadOnly(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID target = WorkerMenuService.teammateTarget(event.getView().getTopInventory(), event.getRawSlot());
        if (target != null) {
            player.closeInventory();
            registry.teleportToTeammate(player, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (WorkerMenuService.isReadOnly(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        org.bukkit.inventory.ItemStack item = event.getItemDrop().getItemStack();
        if (!registry.canUseBingoUi(event.getPlayer().getUniqueId())) return;
        if (item.getType() == org.bukkit.Material.COMPASS || registry.boundCardTeam(item) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBoundCardPickup(EntityPickupItemEvent event) {
        Integer teamId = registry.boundCardTeam(event.getItem().getItemStack());
        if (teamId == null) return;
        if (!(event.getEntity() instanceof Player player)
                || !registry.canPickupCard(player.getUniqueId(), teamId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        UUID attacker = attackerId(event.getDamager());
        if (attacker != null && registry.isRunningPlayer(victim.getUniqueId())
                && registry.sameTeam(attacker, victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        UUID thrower = shooterId(event.getPotion().getShooter());
        if (thrower == null || !registry.isRunningPlayer(thrower)) return;
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim
                && !victim.getUniqueId().equals(thrower)
                && registry.sameTeam(thrower, victim.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        UUID source = shooterId(cloud.getSource());
        if (source == null || !registry.isRunningPlayer(source)) return;
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim
                && !victim.getUniqueId().equals(source)
                && registry.sameTeam(source, victim.getUniqueId()));
    }

    private static UUID attackerId(Entity damager) {
        if (damager instanceof Player player) return player.getUniqueId();
        if (damager instanceof Projectile projectile) return shooterId(projectile.getShooter());
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Tameable pet && pet.isTamed()) return pet.getOwnerUniqueId();
        return null;
    }

    private static UUID shooterId(ProjectileSource source) {
        return source instanceof Player player ? player.getUniqueId() : null;
    }

    private static boolean isOffhandWeapon(org.bukkit.Material material) {
        return material == org.bukkit.Material.SHIELD || material == org.bukkit.Material.TRIDENT
                || material == org.bukkit.Material.BOW || material == org.bukkit.Material.CROSSBOW;
    }
}
