package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.BasinLoot;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@Getter
@Setter
public class HotyCodyDuskyHandler extends BaseListener {
    private HotyCodyDuskyTeamArea hotyCodyDuskyArea;

    @EventHandler(ignoreCancelled = true)
    public void onPowderFall(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock
                && event.getTo().isAir()
                && event.getBlock().getType().name().endsWith("_CONCRETE_POWDER")
                && event.getBlock().getWorld().getName().equals(hotyCodyDuskyArea.getWorldName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        var run = hotyCodyDuskyArea.terrain();
        Player player = event.getPlayer();
        if (hotyCodyDuskyArea.notAreaPlayer(player)) return;
        boolean right = event.getAction().isRightClick();
        if (run == null) {
            if (right
                    && hotyCodyDuskyArea.hasHotbar(player)
                    && !BasinHotbar.toolSlot(player.getInventory().getHeldItemSlot()))
                event.setCancelled(true);
            return;
        }
        var items = hotyCodyDuskyArea.basinItems();
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!right && !event.getAction().isLeftClick()) return;
        if (run.recentlyClickedRope(player)) {
            event.setCancelled(true);
            return;
        }
        if (run.handleRope(player, null, right)) {
            event.setCancelled(true);
            return;
        }
        if (!right) return;
        if (hotyCodyDuskyArea.isWornVest(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        if (!BasinHotbar.toolSlot(slot)) {
            event.setCancelled(true);
            if (slot == BasinHotbar.SKILL_SLOT && hotyCodyDuskyArea.holdsFish(player)) {
                if (player.isSneaking()) run.cycle(player);
                else run.activate(player);
            }
            return;
        }
        if ((items != null && items.tools.use(player, event.getItem()))
                || (player.getInventory().getItemInMainHand().getType().isAir()
                        && run.activate(player))) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        var items = hotyCodyDuskyArea.basinItems();
        var run = hotyCodyDuskyArea.terrain();
        if ((items != null
                        && (items.claim(event.getPlayer(), event.getRightClicked())
                                || items.tools.isDecoy(event.getRightClicked())
                                || items.tools.useKey(event.getPlayer(), event.getRightClicked())))
                || (run != null
                        && (run.pressLift(event.getPlayer(), event.getRightClicked())
                                || (event.getRightClicked()
                                                .getScoreboardTags()
                                                .contains("trap.quicksand.rope")
                                        && run.handleRope(
                                                event.getPlayer(), event.getRightClicked(), true))))
                || hotyCodyDuskyArea.isWornVest(
                        event.getPlayer().getInventory().getItemInMainHand()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        onClick(event);
    }

    @EventHandler
    public void onPunch(PrePlayerAttackEntityEvent event) {
        var items = hotyCodyDuskyArea.basinItems();
        var run = hotyCodyDuskyArea.terrain();
        if ((items != null
                        && (items.claim(event.getPlayer(), event.getAttacked())
                                || items.tools.decoyHit(event.getPlayer(), event.getAttacked())))
                || (run != null
                        && (run.pressLift(event.getPlayer(), event.getAttacked())
                                || (event.getAttacked()
                                                .getScoreboardTags()
                                                .contains("trap.quicksand.rope")
                                        && run.handleRope(
                                                event.getPlayer(), event.getAttacked(), false)))))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent event) {
        Player p = event.getPlayer();
        BasinLoot kind = BasinLoot.of(event.getItemStack());
        if (hotyCodyDuskyArea.notAreaPlayer(p)
                || !(kind == BasinLoot.TRIDENT && event.getProjectile() instanceof Trident
                        || kind == BasinLoot.SMOKE
                                && event.getProjectile() instanceof ThrownPotion)) return;
        var items = hotyCodyDuskyArea.basinItems();
        if (items == null
                || !hotyCodyDuskyArea.canUseBasinItem(p)
                || (kind == BasinLoot.TRIDENT && !hotyCodyDuskyArea.activeWave()))
            event.setCancelled(true);
        else items.tools.trackThrown(p, event.getProjectile(), kind);
    }

    @EventHandler
    public void onSmokeCloud(LingeringPotionSplashEvent event) {
        if (event.getEntity().getWorld().getName().equals(hotyCodyDuskyArea.getWorldName())
                && BasinLoot.of(event.getEntity().getItem()) == BasinLoot.SMOKE)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChorusTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || hotyCodyDuskyArea.notAreaPlayer(p)) return;
        var items = hotyCodyDuskyArea.basinItems();
        if (items == null
                || !hotyCodyDuskyArea.canUseBasinItem(p)
                || items.tools.isJailed(p)
                || event.getTo() == null
                || !hotyCodyDuskyArea.insideBasin(
                        hotyCodyDuskyArea.arenaOf(p.getUniqueId()), event.getTo()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        var kind = BasinLoot.of(event.getItem());
        if ((kind != BasinLoot.BAIT
                        && kind != BasinLoot.MUSHROOM
                        && kind != BasinLoot.CHORUS
                        && kind != BasinLoot.HORN)
                || hotyCodyDuskyArea.notAreaPlayer(p)) return;
        var items = hotyCodyDuskyArea.basinItems();
        if (items == null
                || event.getHand() != EquipmentSlot.HAND
                || !hotyCodyDuskyArea.canUseBasinItem(p)
                || ((kind == BasinLoot.BAIT || kind == BasinLoot.HORN)
                        && !hotyCodyDuskyArea.activeWave())) event.setCancelled(true);
        else items.tools.finishConsumption(p, kind);
    }

    @EventHandler
    public void onShot(ProjectileHitEvent event) {
        var items = hotyCodyDuskyArea.basinItems();
        if (items != null
                && items.tools.hit(
                        event.getEntity(),
                        event.getHitEntity(),
                        event.getHitBlock(),
                        event.getHitBlockFace())) event.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (hotyCodyDuskyArea.notAreaPlayer(player) || !hotyCodyDuskyArea.hasHotbar(player)) return;
        event.setCancelled(true);
        var run = hotyCodyDuskyArea.terrain();
        if (run != null) {
            if (player.isSneaking()) run.cycle(player);
            else run.activate(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var items = hotyCodyDuskyArea.basinItems();
        if (items != null && event.hasChangedPosition() && items.tools.isJailed(event.getPlayer()))
            event.setCancelled(true);
    }

    protected HotyCodyDuskyHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (hotyCodyDuskyArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (hotyCodyDuskyArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    private boolean canSort(Player p, InventoryType type) {
        return type == InventoryType.CRAFTING
                && hotyCodyDuskyArea.isGameplayParticipant(p)
                && hotyCodyDuskyArea.getGameStageEnum() == GameStageEnum.PROGRESS;
    }

    private static boolean lootOrEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || BasinLoot.of(stack) != null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || hotyCodyDuskyArea.notAreaPlayer(p))
            return;
        boolean allowed =
                canSort(p, event.getView().getTopInventory().getType())
                        && p.getInventory().equals(event.getClickedInventory())
                        && BasinHotbar.toolSlot(event.getSlot())
                        && lootOrEmpty(event.getCursor())
                        && lootOrEmpty(event.getCurrentItem())
                        && switch (event.getClick()) {
                            case LEFT, RIGHT -> true;
                            case NUMBER_KEY ->
                                    BasinHotbar.toolSlot(event.getHotbarButton())
                                            && lootOrEmpty(
                                                    p.getInventory()
                                                            .getItem(event.getHotbarButton()));
                            default -> false;
                        };
        if (!allowed) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || hotyCodyDuskyArea.notAreaPlayer(p))
            return;
        var view = event.getView();
        if (!canSort(p, view.getTopInventory().getType())
                || !lootOrEmpty(event.getOldCursor())
                || event.getRawSlots().stream()
                        .anyMatch(
                                raw ->
                                        !p.getInventory().equals(view.getInventory(raw))
                                                || !BasinHotbar.toolSlot(view.convertSlot(raw))
                                                || !lootOrEmpty(view.getItem(raw))))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLooseItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && !hotyCodyDuskyArea.notAreaPlayer(p))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (hotyCodyDuskyArea.notAreaPlayer(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        var items = hotyCodyDuskyArea.basinItems();
        if (event instanceof EntityDamageByEntityEvent hit
                && items != null
                && items.tools.isGameTrident(hit.getDamager())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player target)
                || hotyCodyDuskyArea.notAreaPlayer(target)) return;
        GameStageEnum stage = hotyCodyDuskyArea.getGameStageEnum();
        if (stage != GameStageEnum.PROGRESS) return;
        if (event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof Player from) {
            hotyCodyDuskyArea.passFish(from, target);
        }
        event.setCancelled(true);
    }
}
