package ink.ziip.championshipscore.api.game.sulfursoccer;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityCollideWithEntityEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import io.papermc.paper.event.entity.SulfurCubeSwallowItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.EnderPearl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

@Setter
public final class SulfurSoccerHandler extends BaseListener {
    private SulfurSoccerArea area;
    public SulfurSoccerHandler(ChampionshipsCore plugin) { super(plugin); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (area.isBall(event.getAttacked())) {
            if (!area.canKick(event.getPlayer())) event.setCancelled(true);
        } else if (!area.notAreaPlayer(event.getPlayer())
                || event.getAttacked() instanceof Player player && !area.notAreaPlayer(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (area.isBall(event.getEntity())
                || event.getEntity() instanceof Player player && !area.notAreaPlayer(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player)) {
            event.setCancelled(true);
            return;
        }
        if (!area.isBall(event.getEntity())) return;
        // Sulfur cubes use a separate attack-push path even when vanilla damage is immune.
        if (!(event instanceof EntityPushedByEntityAttackEvent attack)
                || !(attack.getPushedBy() instanceof Player player) || !area.allowAttackPush(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCollision(EntityCollideWithEntityEvent event) {
        if (event.getEntities().stream().noneMatch(area::isBall)) return;
        if (event.getEntities().stream().filter(entity -> !area.isBall(entity))
                .anyMatch(entity -> !(entity instanceof Player player) || !area.canContactBall(player))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBallInteract(PlayerInteractEntityEvent event) {
        if (area.isBall(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucket(PlayerBucketEntityEvent event) {
        if (area.isBall(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwallow(SulfurCubeSwallowItemEvent event) {
        if (area.isBall(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearlInteract(PlayerInteractEvent event) {
        if (area.isShootout() && !area.notAreaPlayer(event.getPlayer())) {
            event.setCancelled(true);
            area.selectPenaltyDirection(event.getPlayer(), event.getPlayer().getInventory().getHeldItemSlot());
            return;
        }
        if (!area.notAreaPlayer(event.getPlayer()) && event.getItem() != null
                && event.getItem().getType() == Material.ENDER_PEARL && !area.canUsePearl(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player
                && !area.notAreaPlayer(player) && !area.canUsePearl(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLaunched(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player
                && !area.notAreaPlayer(player)) area.recordPearlLaunch(player, pearl);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || area.notAreaPlayer(event.getPlayer())) return;
        if (area.isShootout() || !area.canKick(event.getPlayer()) || !area.validPearlDestination(event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageConfig.SULFUR_SOCCER_PEARL_REJECTED);
        } else event.getPlayer().setFallDistance(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) { if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) { if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) { if (!area.notAreaPlayer(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !area.notAreaPlayer(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        area.selectPenaltyDirection(event.getPlayer(), event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (area.isShootout() && event.getWhoClicked() instanceof Player player && !area.notAreaPlayer(player)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == player.getInventory() && area.selectPenaltyDirection(player, event.getSlot()))
                player.getInventory().setHeldItemSlot(event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (area.isShootout() && event.getWhoClicked() instanceof Player player && !area.notAreaPlayer(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (area.isShootout() && !area.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @Override public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (area.notAreaPlayer(player) || area.isManagedSpectator(player) || area.isIntroductionPhase()
                || event.getTo() == null) return;
        if (area.isPlayPhase() && area.isPaused()) {
            if (!event.getFrom().toVector().equals(event.getTo().toVector())) event.setTo(event.getFrom());
        } else if (area.isPlayPhase() && area.isShootout()) {
            event.setTo(area.penaltyMoveDestination(player, event.getFrom(), event.getTo()));
        } else if (area.isPlayPhase() && area.notInArea(event.getTo())) {
            area.teleportParticipant(player);
        }
    }
}
