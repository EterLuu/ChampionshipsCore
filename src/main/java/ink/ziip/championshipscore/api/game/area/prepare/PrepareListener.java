package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AreaListGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.ListStepGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;

/**
 * Single listener that routes every interaction in the prepare subsystem: sub-GUI clicks (area list /
 * anvil / list-step), prepare-mode inventory clicks and right-click-in-hand use, and the safety events
 * (drop/pickup/quit/join/death) that keep the saved inventory safe and the prepare inventory clean.
 */
public class PrepareListener extends BaseListener {
    private final PrepareSessionManager manager;

    public PrepareListener(ChampionshipsCore plugin, PrepareSessionManager manager) {
        super(plugin);
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof AreaListGui.Holder h) {
            AreaListGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof AnvilInputGui.Holder h) {
            AnvilInputGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof ListStepGui.Holder h) {
            ListStepGui.handleClick(manager, event, player, h);
            return;
        }

        PrepareSession session = manager.getSession(player);
        if (session == null) return;

        // In prepare mode: lock the inventory. Only clicks in the player's own 36-slot inventory are routed
        // to step/action handlers; everything else (crafting grid, external inventories) is just cancelled
        // so prepare items can't be moved out.
        Inventory clicked = event.getClickedInventory();
        if (clicked == event.getView().getBottomInventory()) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            String stepKey = PrepareKeys.stepKeyOf(item);
            if (stepKey != null) {
                manager.handleStepClick(player, session, stepKey);
                return;
            }
            String action = PrepareKeys.actionOf(item);
            if (action != null) {
                manager.handleActionClick(player, session, action);
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof AreaListGui.Holder || holder instanceof AnvilInputGui.Holder || holder instanceof ListStepGui.Holder) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player && manager.getSession(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PrepareSession session = manager.getSession(player);
        if (session == null) return;
        ItemStack item = event.getItem();
        if (item == null || !PrepareKeys.isPrepareItem(item)) return;
        // Block vanilla use (throw / place / etc.) of prepare items; right-click also triggers the step.
        event.setCancelled(true);
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        String stepKey = PrepareKeys.stepKeyOf(item);
        if (stepKey != null) {
            manager.handleStepClick(player, session, stepKey);
            return;
        }
        String action = PrepareKeys.actionOf(item);
        if (action != null) {
            manager.handleActionClick(player, session, action);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.getSession(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.getSession(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (manager.getSession(event.getPlayer()) != null) {
            manager.exitSession(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.restorePendingSnapshot(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (manager.getSession(event.getEntity()) != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
        }
    }

    @EventHandler
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AnvilInputGui.Holder) {
            AnvilView view = event.getView();
            view.setRepairCost(0);
            view.setMaximumRepairCost(0);
        }
    }
}
