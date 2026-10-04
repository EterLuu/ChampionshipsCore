package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AceRaceEquipmentGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AceRaceRespawnPointBindingGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AreaListGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.BuildMartMaterialZoneGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.CountdownBlockDisappearanceGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.ListStepGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideCourseEditorGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.StepMenuGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.TGTTOSAreaTypeGui;
import ink.ziip.championshipscore.api.gui.MenuInventory;

import io.papermc.paper.event.player.PlayerPickItemEvent;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;

/**
 * Single listener that routes every interaction in the prepare subsystem: sub-GUI clicks (area list
 * / anvil / list-step), prepare-mode inventory clicks and right-click-in-hand use, and the safety
 * events (drop/pickup/quit/join/death) that keep the saved inventory safe and the prepare inventory
 * clean.
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
        if (holder instanceof MenuInventory && MenuInventory.clickedPlayer(event) == null) return;

        if (holder
                instanceof
                ink.ziip.championshipscore.api.game.area.prepare.gui.BuildMartBlueprintGui.Holder
                        h) {
            ink.ziip.championshipscore.api.game.area.prepare.gui.BuildMartBlueprintGui.handleClick(
                    manager, event, player, h);
            return;
        }

        if (holder instanceof RiptideCourseEditorGui.Holder h) {
            RiptideCourseEditorGui.handleClick(manager, event, player, h);
            return;
        }

        if (holder instanceof AreaListGui.Holder h) {
            AreaListGui.handleClick(manager, event, player, h);
            return;
        }
        AnvilInputGui.Holder anvilHolder = AnvilInputGui.getHolder(player, top);
        if (anvilHolder != null) {
            AnvilInputGui.handleClick(manager, event, player, anvilHolder);
            return;
        }
        if (holder instanceof ListStepGui.Holder h) {
            ListStepGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof ListStepGui.EntryHolder h) {
            ListStepGui.handleEntryClick(manager, event, player, h);
            return;
        }
        if (holder instanceof ListStepGui.EditHolder h) {
            ListStepGui.handleEditClick(manager, event, player, h);
            return;
        }
        if (holder instanceof AceRaceEquipmentGui.Holder h) {
            AceRaceEquipmentGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof AceRaceRespawnPointBindingGui.Holder h) {
            AceRaceRespawnPointBindingGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof TGTTOSAreaTypeGui.Holder h) {
            TGTTOSAreaTypeGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof CountdownBlockDisappearanceGui.Holder h) {
            CountdownBlockDisappearanceGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof BuildMartMaterialZoneGui.Holder h) {
            BuildMartMaterialZoneGui.handleClick(manager, event, player, h);
            return;
        }
        if (holder instanceof StepMenuGui.Holder h) {
            StepMenuGui.handleClick(manager, event, player, h);
            return;
        }

        PrepareSession session = manager.getSession(player);
        if (session == null) return;
        // Creative mode uses the top inventory as the item palette. Let that palette and all spare
        // material slots work normally; only the fixed prepare controls remain protected.
        Inventory clicked = event.getClickedInventory();
        if (player.getGameMode() == GameMode.CREATIVE) {
            if (clicked == event.getView().getBottomInventory()
                    && PrepareModeInventory.isControlSlot(session, event.getSlot())) {
                event.setCancelled(true);
                routeControlClick(player, session, event.getCurrentItem());
            }
            return;
        }

        // In non-creative prepare mode, lock the inventory. Only clicks in the player's own hotbar
        // are
        // routed to the control handlers; everything else is cancelled so prepare items cannot be
        // moved out.
        if (clicked == event.getView().getBottomInventory()) {
            event.setCancelled(true);
            routeControlClick(player, session, event.getCurrentItem());
            return;
        }
        event.setCancelled(true);
    }

    private void routeControlClick(
            @NotNull Player player,
            @NotNull PrepareSession session,
            @org.jetbrains.annotations.Nullable ItemStack item) {
        String stepKey = PrepareKeys.stepKeyOf(item);
        if (stepKey != null) {
            manager.handleStepClick(player, session, stepKey);
            return;
        }
        String action = PrepareKeys.actionOf(item);
        if (action != null) manager.handleActionClick(player, session, action);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && AnvilInputGui.getHolder(player, event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MenuInventory) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            PrepareSession session = manager.getSession(player);
            if (session == null) return;
            if (player.getGameMode() == GameMode.CREATIVE) {
                for (int raw : event.getRawSlots()) {
                    if (raw >= 36
                            && raw < 45
                            && PrepareModeInventory.isControlSlot(session, raw - 36)) {
                        event.setCancelled(true);
                        return;
                    }
                }
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PrepareSession session = manager.getSession(player);
        if (session == null) return;
        var workshop = session.getBlueprintWorkshop();
        if (workshop != null
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && workshop.isButton(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            if (event.getHand() == EquipmentSlot.HAND) workshop.submit(player);
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !PrepareKeys.isPrepareItem(item)) {
            if (workshop != null
                    && event.getClickedBlock() != null
                    && (workshop.isBusy()
                            || !workshop.contains(event.getClickedBlock().getLocation()))) {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                if (workshop.isBusy()) event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            }
            return;
        }
        // Block vanilla use (throw / place / etc.) of prepare items; right-click also triggers the
        // step.
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
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

    @EventHandler(ignoreCancelled = true)
    public void onWorkshopPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        PrepareSession session = manager.getSession(event.getPlayer());
        if (session == null || session.getBlueprintWorkshop() == null) return;
        var workshop = session.getBlueprintWorkshop();
        if (workshop.isBusy() || !workshop.contains(event.getBlock().getLocation()))
            event.setCancelled(true);
        if (event instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi
                && multi.getReplacedBlockStates().stream()
                        .anyMatch(state -> !workshop.contains(state.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorkshopBreak(org.bukkit.event.block.BlockBreakEvent event) {
        PrepareSession session = manager.getSession(event.getPlayer());
        if (session == null || session.getBlueprintWorkshop() == null) return;
        var workshop = session.getBlueprintWorkshop();
        if (workshop.isBusy() || !workshop.contains(event.getBlock().getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorkshopBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        checkWorkshopBucket(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorkshopBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        checkWorkshopBucket(event);
    }

    private void checkWorkshopBucket(org.bukkit.event.player.PlayerBucketEvent event) {
        PrepareSession session = manager.getSession(event.getPlayer());
        if (session == null || session.getBlueprintWorkshop() == null) return;
        var workshop = session.getBlueprintWorkshop();
        if (workshop.isBusy() || !workshop.contains(event.getBlock().getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorkshopFluid(org.bukkit.event.block.BlockFromToEvent event) {
        for (var workshop : manager.blueprintWorkshops()) {
            boolean from = workshop.contains(event.getBlock().getLocation());
            boolean to = workshop.contains(event.getToBlock().getLocation());
            if (from != to || (from && workshop.isBusy())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.getSession(player) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Creative pick-block normally writes to the selected hotbar slot. Redirect it away from the
     * fixed prepare controls while leaving the editor able to obtain building materials by
     * middle-click.
     */
    @EventHandler
    public void onCreativePickItem(@NotNull PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        PrepareSession session = manager.getSession(player);
        if (session == null || !PrepareModeInventory.isControlSlot(session, event.getTargetSlot()))
            return;

        int targetSlot = PrepareModeInventory.creativePickTarget(player, session);
        if (targetSlot < 0) {
            event.setCancelled(true);
            return;
        }
        event.setTargetSlot(targetSlot);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AnvilInputGui.close(event.getPlayer());
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
        if (event.getView().getPlayer() instanceof Player player
                && AnvilInputGui.getHolder(player, event.getView().getTopInventory()) != null) {
            AnvilView view = event.getView();
            view.setRepairCost(0);
            view.setMaximumRepairCost(0);
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            AnvilInputGui.clear(player, event.getInventory());
        }
    }
}
