package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

final class RobotHandler extends BaseListener {
    RobotArea area;

    RobotHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent event) {
        area.jointChunkUnloading(event.getChunk());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        area.forgetMaps(event.getPlayer());
    }

    @EventHandler
    public void changeWorld(PlayerChangedWorldEvent event) {
        area.forgetMaps(event.getPlayer());
        area.worldChanged(event.getPlayer());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        area.forgetMaps(event.getPlayer());
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (area.notAreaPlayer(event.getPlayer())) return;
        if (event.getHand() == EquipmentSlot.HAND
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && area.interactJoint(
                        event.getPlayer(), event.getClickedBlock(), event.getBlockFace())) {
            event.setCancelled(true);
            if (RobotProps.kind(event.getItem()) == RobotProps.Kind.JOINT_BLOCK)
                event.getPlayer().updateInventory();
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && area.beginDigJoint(event.getPlayer(), event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            event.setUseItemInHand(Event.Result.ALLOW);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && (event.getAction() == Action.RIGHT_CLICK_BLOCK
                        || event.getAction() == Action.RIGHT_CLICK_AIR)
                && SliderStation.handle(event.getItem())) {
            event.setCancelled(true);
            area.releaseHandle(event.getPlayer());
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && area.canMine(event.getPlayer(), event.getClickedBlock())) return;
        if (event.getHand() == EquipmentSlot.HAND
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && area.openWarehouse(event.getPlayer(), event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        event.setCancelled(true);
        if (RobotProps.kind(event.getItem()) == RobotProps.Kind.JOINT_BLOCK)
            event.getPlayer().updateInventory();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!area.notAreaPlayer(e.getPlayer()) && RobotProps.prop(e.getItemDrop().getItemStack()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!area.notAreaPlayer(e.getPlayer())
                && (RobotProps.prop(e.getMainHandItem()) || RobotProps.prop(e.getOffHandItem())))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        area.breakBlock(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        area.inventoryOpen(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p
                && !area.notAreaPlayer(p)
                && (RobotProps.prop(e.getCurrentItem())
                        || RobotProps.prop(e.getCursor())
                        || (e.getClick() == ClickType.SWAP_OFFHAND
                                && RobotProps.prop(p.getInventory().getItemInOffHand()))
                        || (e.getHotbarButton() >= 0
                                && RobotProps.prop(
                                        p.getInventory().getItem(e.getHotbarButton()))))) {
            e.setCancelled(true);
            return;
        }
        area.inventoryInteract(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p
                && !area.notAreaPlayer(p)
                && (RobotProps.prop(e.getOldCursor())
                        || e.getNewItems().values().stream().anyMatch(RobotProps::prop))) {
            e.setCancelled(true);
            return;
        }
        area.inventoryInteract(e);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!area.notAreaPlayer(event.getPlayer()) && RobotProps.prop(event.getItemInHand()))
            event.setCancelled(true);
    }

    private boolean holdingProp(Player player) {
        return !area.notAreaPlayer(player)
                && (RobotProps.prop(player.getInventory().getItemInMainHand())
                        || RobotProps.prop(player.getInventory().getItemInOffHand()));
    }

    @EventHandler
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if (holdingProp(event.getPlayer())) event.setCancelled(true);
    }

    private boolean sliderFrame(Entity e) {
        return e instanceof GlowItemFrame && e.getPersistentDataContainer().has(SliderStation.KEY);
    }

    @EventHandler
    public void frameClick(PlayerInteractEntityEvent event) {
        if (holdingProp(event.getPlayer()) || sliderFrame(event.getRightClicked()))
            event.setCancelled(true);
    }

    @EventHandler
    public void frameClickAt(PlayerInteractAtEntityEvent event) {
        if (holdingProp(event.getPlayer()) || sliderFrame(event.getRightClicked()))
            event.setCancelled(true);
    }

    @EventHandler
    public void hangingBreak(HangingBreakEvent event) {
        if (sliderFrame(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void damage(EntityDamageEvent event) {
        if (sliderFrame(event.getEntity())) event.setCancelled(true);
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player))
            event.setCancelled(true);
    }
}
