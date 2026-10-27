package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserGunMenu;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;

import org.bukkit.GameMode;
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

/** Event boundary for the laser implementation of Laser Showdown. */
public final class LaserShowdownHandler extends BaseListener {
    private LaserShowdownTeamArea area;

    public LaserShowdownHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    public void setLaserShowdownTeamArea(LaserShowdownTeamArea area) {
        this.area = area;
    }

    private boolean participant(org.bukkit.entity.Player player) {
        return area != null && !area.notAreaPlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (!participant(event.getPlayer())) return;
        org.bukkit.entity.Player player = event.getPlayer();
        if (area.getGameStageEnum() == GameStageEnum.PREPARATION
                || area.getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND && area.holdsGunSelector(player))
                area.openGunMenu(player);
            return;
        }
        if (area.getGameStageEnum() != GameStageEnum.PROGRESS) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        boolean left =
                event.getAction() == Action.LEFT_CLICK_AIR
                        || event.getAction() == Action.LEFT_CLICK_BLOCK;
        boolean right =
                event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (left) {
            event.setUseItemInHand(Event.Result.DENY);
            area.fireFromLeftClick(player);
        } else if (right) {
            if (area.holdsSniper(player)) {
                event.setUseItemInHand(Event.Result.DENY);
                area.beginAiming(player);
            } else if (area.holdsGun(player)) {
                event.setUseItemInHand(
                        area.holdsRightClickAutomaticGun(player)
                                ? Event.Result.ALLOW
                                : Event.Result.DENY);
                area.fireFromRightClick(player);
            } else {
                event.setCancelled(true);
                if (area.holdsGunSelector(player)) area.openGunMenu(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void animation(PlayerAnimationEvent event) {
        if (participant(event.getPlayer())
                && event.getAnimationType() == PlayerAnimationType.ARM_SWING)
            area.fireFromLeftClick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void armSwing(PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && participant(event.getPlayer()))
            area.fireFromLeftClick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void lunge(EntityLungeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player
                && participant(player)
                && area.holdsRightClickAutomaticGun(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player && participant(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.Player player && participant(player)) {
            area.fireFromLeftClick(player);
            event.setCancelled(true);
        }
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
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player && participant(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        LaserGunMenu.handleClose(area, event);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (LaserGunMenu.handleDrag(area, event)) return;
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player && participant(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player && participant(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockBreak(BlockBreakEvent event) {
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockPlace(BlockPlaceEvent event) {
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (!participant(event.getPlayer())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (area.holdsSniper(event.getPlayer())) area.beginAiming(event.getPlayer());
        else if (area.holdsGun(event.getPlayer())) area.fireFromRightClick(event.getPlayer());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        if (participant(event.getPlayer()))
            event.setRespawnLocation(area.getSpectatorSpawnLocation());
    }

    @Override
    public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {
        if (!participant(event.getPlayer()) || area.isIntroductionPhase()) return;
        var player = event.getPlayer();
        if (area.notInPlayerArena(player, event.getTo())) {
            if (area.getGameStageEnum() == GameStageEnum.PREPARATION
                    || area.getGameStageEnum() == GameStageEnum.COUNTDOWN)
                area.teleportPlayerToSpawnLocation(player);
            else if (area.getGameStageEnum() == GameStageEnum.PROGRESS
                    && player.getGameMode() == GameMode.ADVENTURE) area.returnToArena(player);
        }
    }
}
