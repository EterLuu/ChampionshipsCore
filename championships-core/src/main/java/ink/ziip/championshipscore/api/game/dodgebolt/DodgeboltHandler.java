package ink.ziip.championshipscore.api.game.dodgebolt;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;

@Setter
public final class DodgeboltHandler extends BaseListener {
    private DodgeboltArea area;

    public DodgeboltHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && area != null
                && !area.notAreaPlayer(player) && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || area.notAreaPlayer(player)) return;
        if (!area.canShoot(player, event.getConsumable())) {
            event.setCancelled(true);
            Utils.sendActionBar(player, MessageConfig.DODGEBOLT_CANT_SHOOT);
            return;
        }
        area.registerShot(player, event.getProjectile());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBowDraw(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (area.notAreaPlayer(player) || event.getHand() != EquipmentSlot.HAND
                || event.getItem() == null || event.getItem().getType() != Material.BOW
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if (!area.canShoot(player)) {
            event.setCancelled(true);
            Utils.sendActionBar(player, MessageConfig.DODGEBOLT_CANT_SHOOT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !area.isTrackedProjectile(arrow)) return;
        Player hit = event.getHitEntity() instanceof Player player ? player : null;
        area.resolveProjectile(arrow, hit);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !area.isTrackedProjectile(arrow)) return;
        event.setCancelled(true);
        Player hit = event.getEntity() instanceof Player player ? player : null;
        area.resolveProjectile(arrow, hit);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player)) {
            event.setCancelled(true);
            if (area.getGameStageEnum() == GameStageEnum.PROGRESS && area.isAlive(player) && !area.isPaused()
                    && (event.getCause() == EntityDamageEvent.DamageCause.VOID
                    || event.getCause() == EntityDamageEvent.DamageCause.LAVA)) {
                area.eliminate(player, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (area.isEliminatedPlayer(player)) {
            event.setCancelled(true);
            return;
        }
        if (!area.isTokenArrow(event.getItem().getItemStack())) return;
        if (!area.canPickUpToken(player, event.getItem())) {
            event.setCancelled(true);
            return;
        }
        area.tokenPickedUp(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @Override
    public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (area.notAreaPlayer(player) || event.getTo() == null) return;
        if (area.isIntroductionPhase()) return;
        if (area.isEliminatedPlayer(player)) {
            if (!area.isSpectatorLocationAllowed(event.getTo())) area.teleportToSpectatorArea(player);
            return;
        }
        if (area.isManagedSpectator(player)) return;
        if (area.isPaused() && area.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (changedBlock(event.getFrom(), event.getTo())) event.setCancelled(true);
            return;
        }
        if (area.getGameStageEnum() == GameStageEnum.PREPARATION && area.notInArea(event.getTo())) {
            area.teleportParticipant(player);
            return;
        }
        if (area.getGameStageEnum() != GameStageEnum.PROGRESS || !area.isAlive(player)) return;
        if (area.notInArea(event.getTo())) {
            area.eliminate(player, false);
            return;
        }
        if (!area.inOwnArea(player, event.getTo()) && changedBlock(event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            Utils.sendActionBar(player, MessageConfig.DODGEBOLT_CANT_CROSS);
            return;
        }
        area.updateArrowAccess(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEliminatedInteract(PlayerInteractEvent event) {
        if (area.isEliminatedPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEliminatedInteractEntity(PlayerInteractEntityEvent event) {
        if (area.isEliminatedPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEliminatedPlace(BlockPlaceEvent event) {
        if (area.isEliminatedPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerBreak(BlockBreakEvent event) {
        if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    private static boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
