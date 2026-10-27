package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserGunMenu;

import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

public final class LaserBoxHandler extends BaseListener {
    private LaserBoxArea area;

    public LaserBoxHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    void setArea(LaserBoxArea area) {
        this.area = area;
    }

    private boolean participant(Player p) {
        return !area.notAreaPlayer(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (!participant(event.getPlayer())) return;
        if (area.isIntroductionCombat()) {
            if (event.getHand() != EquipmentSlot.HAND) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(
                    area.holdsRightClickAutomaticGun(event.getPlayer())
                            ? Event.Result.ALLOW
                            : Event.Result.DENY);
            if (event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                area.fireFromLeftClick(event.getPlayer());
            } else if (event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (area.holdsSniper(event.getPlayer())) area.beginAiming(event.getPlayer());
                else area.fireFromRightClick(event.getPlayer());
            }
            return;
        }
        if (area.isIntroductionPhase()) return;
        if (area.isGunSelectionStage()) {
            event.setCancelled(true);
            // The selector explicitly reopens the menu; clicking a weapon does not.
            if (event.getHand() == EquipmentSlot.HAND && area.holdsGunSelector(event.getPlayer()))
                area.openGunMenu(event.getPlayer());
            return;
        }
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (leftClick) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (event.getHand() == EquipmentSlot.HAND) area.fireFromLeftClick(event.getPlayer());
            return;
        }
        if (!rightClick) return;
        if (area.holdsSniper(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (event.getHand() == EquipmentSlot.HAND) area.beginAiming(event.getPlayer());
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        if (area.holdsGun(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(
                    area.holdsRightClickAutomaticGun(event.getPlayer())
                            ? Event.Result.ALLOW
                            : Event.Result.DENY);
            area.fireFromRightClick(event.getPlayer());
            return;
        }
        event.setCancelled(true);
        area.use(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void animation(PlayerAnimationEvent event) {
        if (!participant(event.getPlayer())
                || event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        // Only the sniper uses the left-click path. Other weapons are intentionally right-click
        // weapons, so their native arm swing is ignored and canceled damage remains harmless.
        area.fireFromLeftClick(event.getPlayer());
    }

    /** Handles a main-hand sniper swing; the off hand never fires a gun. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void armSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !participant(event.getPlayer())) return;
        area.fireFromLeftClick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void lunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !participant(player)) return;
        if (area.holdsRightClickAutomaticGun(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && participant(p) || area.owns(event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && participant(p)) {
            // Native melee damage is never part of LaserBox. The sniper's left-click path is
            // replayed here when its gold shovel produces an attack packet on a nearby target.
            area.fireFromLeftClick(p);
            event.setCancelled(true);
        } else if (area.owns(event.getDamager())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event) {
        if (area.owns(event.getItem())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player p) area.pickup(p, event.getItem());
        } else if (event.getEntity() instanceof Player p && participant(p))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void projectile(ProjectileHitEvent event) {
        if (!area.owns(event.getEntity())) return;
        event.setCancelled(true);
        area.landed(event.getEntity());
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void swap(PlayerSwapHandItemsEvent event) {
        if (!participant(event.getPlayer())) return;
        event.setCancelled(true);
        area.reloadFromSwap(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void heldItem(PlayerItemHeldEvent event) {
        if (participant(event.getPlayer()))
            area.heldItemChanged(event.getPlayer(), event.getNewSlot());
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (LaserGunMenu.handleClick(area, event)) return;
        if (event.getWhoClicked() instanceof Player player && participant(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        LaserGunMenu.handleClose(area, event);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (LaserGunMenu.handleDrag(area, event)) return;
        if (event.getWhoClicked() instanceof Player player && participant(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && participant(p)) event.setCancelled(true);
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent event) {
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void placeBlock(BlockPlaceEvent event) {
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!participant(player)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (area.holdsSniper(player)) area.beginAiming(player);
        else if (area.holdsGun(player)) area.fireFromRightClick(player);
        else area.use(player);
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        if (participant(event.getPlayer()))
            event.setRespawnLocation(
                    area.isIntroductionCombat()
                            ? area.introductionRespawn(event.getPlayer())
                            : area.getSpectatorSpawnLocation());
    }

    @Override
    public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {
        area.boundary(event.getPlayer());
    }
}
