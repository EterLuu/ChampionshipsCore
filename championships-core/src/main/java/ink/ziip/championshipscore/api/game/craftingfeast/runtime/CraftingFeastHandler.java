package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

public final class CraftingFeastHandler extends BaseListener {
    private CraftingFeastArea area;

    CraftingFeastHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    void attach(CraftingFeastArea area) {
        this.area = area;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void advancement(PlayerAdvancementDoneEvent event) {
        if (area.managesRecipeBook(event.getPlayer())) event.message(null);
    }

    @EventHandler
    public void customerPath(com.destroystokyo.paper.event.entity.EntityPathfindEvent event) {
        if (area.owns(event.getEntity()) && !area.allowsCustomerPath(event.getEntity()))
            event.setCancelled(true);
    }

    // Special guests keep their natural mob behaviour otherwise; none of it may leak into play.
    @EventHandler
    public void customerTarget(EntityTargetEvent event) {
        if (area.owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void customerPotion(EntityPotionEffectEvent event) {
        if (area.owns(event.getEntity())
                && event.getAction() != EntityPotionEffectEvent.Action.REMOVED
                && event.getAction() != EntityPotionEffectEvent.Action.CLEARED)
            event.setCancelled(true);
    }

    @EventHandler
    public void customerTrail(EntityBlockFormEvent event) {
        if (area.owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void customerProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof org.bukkit.entity.Entity shooter
                && area.owns(shooter)) event.setCancelled(true);
    }

    @EventHandler
    public void customerPickup(EntityPickupItemEvent event) {
        if (area.owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (area.notAreaPlayer(event.getPlayer())) return;
        // Paper aborts block breaking when the left-click interaction is cancelled, so the
        // player's own resource blocks must stay minable; BlockBreakEvent then harvests them.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && area.canHarvest(event.getPlayer(), event.getClickedBlock())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && area.interact(event.getPlayer(), event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler
    public void entity(PlayerInteractEntityEvent event) {
        if (!area.owns(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND)
            area.serve(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler
    public void damage(EntityDamageEvent event) {
        if (area.owns(event.getEntity())
                || event.getEntity() instanceof Player player && !area.notAreaPlayer(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void place(BlockPlaceEvent event) {
        if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent event) {
        if (area.notAreaPlayer(event.getPlayer())) return;
        boolean allowed = !event.isCancelled();
        event.setCancelled(true);
        if (allowed) area.harvest(event.getPlayer(), event.getBlock());
    }

    @EventHandler
    public void inventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player
                && !area.notAreaPlayer(player)
                && !area.canUseInventory(player, event.getInventory())) event.setCancelled(true);
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || area.notAreaPlayer(player)) return;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                && (event.getSlot() == 39 || event.getSlot() == 36)) {
            event.setCancelled(true);
            return;
        }
        var top = event.getView().getTopInventory();
        if (!area.canUseInventory(player, top)) {
            event.setCancelled(true);
            return;
        }
        // Supply icons are immutable. Both ordinary clicks on an icon hand out the configured
        // vanilla bundle; the player's own inventory stays usable, but nothing may move across.
        int raw = event.getRawSlot();
        boolean topSlot = raw >= 0 && raw < top.getSize();
        boolean crossing =
                event.isShiftClick()
                        || event.getClick() == ClickType.DOUBLE_CLICK
                        || event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        int slot =
                topSlot
                                && !event.isCancelled()
                                && (event.getClick() == ClickType.LEFT
                                        || event.getClick() == ClickType.RIGHT)
                        ? raw
                        : -1;
        if (area.supplyInventory(player, top, slot) && (topSlot || crossing))
            event.setCancelled(true);
    }

    @EventHandler
    public void inventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || area.notAreaPlayer(player)) return;
        var top = event.getView().getTopInventory();
        if (!area.canUseInventory(player, top)
                || (area.supplyInventory(player, top, -1)
                        && event.getRawSlots().stream().anyMatch(raw -> raw < top.getSize())))
            event.setCancelled(true);
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (!area.notAreaPlayer(event.getPlayer()) && !area.canPlay(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && !area.notAreaPlayer(player)
                && !area.canPlay(player)) event.setCancelled(true);
    }

    @EventHandler
    public void bucket(PlayerBucketEmptyEvent event) {
        if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void consume(PlayerItemConsumeEvent event) {
        if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @Override
    public void handleRoutedPlayerMoveNormal(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!area.notAreaPlayer(player)
                && area.serving()
                && area.isGameplayParticipant(player)
                && !area.inside(player, event.getTo())) area.spawn(player);
    }
}
