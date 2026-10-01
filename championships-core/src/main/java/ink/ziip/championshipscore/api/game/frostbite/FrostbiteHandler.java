package ink.ziip.championshipscore.api.game.frostbite;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

@Setter
public final class FrostbiteHandler extends BaseListener {
    private FrostbiteArea area;
    public FrostbiteHandler(ChampionshipsCore plugin){super(plugin);}
    @Override public void handleRoutedPlayerMoveLow(PlayerMoveEvent e){area.move(e);}
    @EventHandler(priority=EventPriority.HIGH)
    public void damage(EntityDamageEvent e){
        if(e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player attacker) {
            if(e.getEntity() instanceof Player victim && (area.participant(victim)||area.participant(attacker))) {
                if(!e.isCancelled())area.melee(attacker,victim);e.setCancelled(true);return;
            }
            if(area.prop(e.getEntity())) {area.strikeProp(attacker,e.getEntity());e.setCancelled(true);return;}
            if(area.participant(attacker)){e.setCancelled(true);return;}
        }
        if(e.getEntity() instanceof Player p && area.participant(p) || area.prop(e.getEntity()))e.setCancelled(true);
        if(e instanceof EntityDamageByEntityEvent by && area.ownedProjectile(by.getDamager()))e.setCancelled(true);
    }
    @EventHandler public void interact(PlayerInteractEvent e){
        if(!area.participant(e.getPlayer()))return;
        boolean bow=area.item(e.getItem())==FrostbiteItem.BOW && area.playing(e.getPlayer());
        e.setUseInteractedBlock(Event.Result.DENY);
        if(!bow)e.setUseItemInHand(Event.Result.DENY);
        if(e.getHand()==EquipmentSlot.HAND && e.getAction().isRightClick())area.use(e.getPlayer());
    }
    @EventHandler public void interactEntity(PlayerInteractEntityEvent e){if(area.participant(e.getPlayer()) || area.prop(e.getRightClicked()))e.setCancelled(true);}
    @EventHandler public void manipulate(PlayerArmorStandManipulateEvent e){if(area.participant(e.getPlayer()) || area.prop(e.getRightClicked()))e.setCancelled(true);}
    @EventHandler public void hit(ProjectileHitEvent e){area.hit(e);}
    @EventHandler public void areaEffectCloud(AreaEffectCloudApplyEvent e){area.areaEffectCloud(e);}
    @EventHandler(ignoreCancelled=true) public void shoot(EntityShootBowEvent e){area.shoot(e);}
    @EventHandler public void teleport(PlayerTeleportEvent e){area.teleportEvent(e);}
    @EventHandler public void respawn(PlayerRespawnEvent e){area.respawnEvent(e);}
    @EventHandler public void swap(PlayerSwapHandItemsEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void drop(PlayerDropItemEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p && area.participant(p))e.setCancelled(true);}
    @EventHandler public void arrow(PlayerPickupArrowEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void food(FoodLevelChangeEvent e){if(e.getEntity() instanceof Player p && area.participant(p))e.setCancelled(true);}
    @EventHandler public void inventory(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p && area.participant(p))e.setCancelled(true);}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p && area.participant(p))e.setCancelled(true);}
    @EventHandler public void place(BlockPlaceEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void blockBreak(BlockBreakEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void bucket(PlayerBucketEmptyEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void bucketFill(PlayerBucketFillEvent e){if(area.participant(e.getPlayer()))e.setCancelled(true);}
    // World rules also apply while waiting and during map editing.
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void iceForm(BlockFormEvent e){
        if(!e.getBlock().getWorld().getName().equals(area.getWorldName()))return;
        if(e.getBlock().getType()==Material.WATER && e.getNewState().getType()==Material.ICE)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void iceFade(BlockFadeEvent e){
        if(!e.getBlock().getWorld().getName().equals(area.getWorldName()))return;
        Material material=e.getBlock().getType();
        if(material==Material.ICE || material==Material.FROSTED_ICE)e.setCancelled(true);
    }
}
