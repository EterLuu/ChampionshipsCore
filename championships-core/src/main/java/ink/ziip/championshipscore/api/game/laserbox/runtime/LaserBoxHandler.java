package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    // Bukkit pre-cancels RIGHT_CLICK_AIR for some items; inspect item-use result rather than
    // ignoreCancelled.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (!participant(event.getPlayer()) || area.isIntroductionPhase()) return;
        boolean denied = event.useItemInHand() == Event.Result.DENY;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || denied) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK) area.use(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && participant(p) || area.owns(event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && participant(p)
                || area.owns(event.getDamager())) event.setCancelled(true);
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
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && participant(p)) event.setCancelled(true);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p && participant(p)) event.setCancelled(true);
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

    @EventHandler
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (participant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        if (participant(event.getPlayer()))
            event.setRespawnLocation(area.getSpectatorSpawnLocation());
    }

    @Override
    public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {
        area.boundary(event.getPlayer());
    }
}
