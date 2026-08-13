package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialIsland;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialZone;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compact editor for repeatable Build Mart material cuboids. */
public final class BuildMartMaterialZoneGui {
    private static final int PAGE_SIZE = 45;
    private static final int SET_SELECTION_SLOT = 45;
    private static final int PREVIOUS_SLOT = 46;
    private static final int CLEAR_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 52;
    private static final int BACK_SLOT = 53;
    private static final long MAX_VOLUME = 200_000L;

    private BuildMartMaterialZoneGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        final String stepKey;
        @Nullable BuildMartMaterialIsland island;
        int page;
        Inventory inventory;

        Holder(@NotNull PrepareSession session, @NotNull PrepareStep step) {
            this.session = session;
            this.stepKey = step.key();
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull PrepareSessionManager manager, @NotNull Player player,
                            @NotNull PrepareSession session, @NotNull PrepareStep step) {
        Holder holder = new Holder(session, step);
        holder.inventory = Bukkit.createInventory(holder, 54,
                Component.text(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-area")).decoration(TextDecoration.ITALIC, false));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    public static void handleClick(@NotNull PrepareSessionManager manager, @NotNull InventoryClickEvent event,
                                   @NotNull Player player, @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        int slot = event.getRawSlot();
        if (holder.island == null && slot >= 0 && slot < BuildMartMaterialIsland.values().length) {
            holder.island = BuildMartMaterialIsland.values()[slot];
            holder.page = 0;
            refresh(holder);
            return;
        }
        if (holder.island != null && slot >= 0 && slot < PAGE_SIZE) {
            List<BuildMartMaterialZone> islandZones = config.getMaterialZones(holder.island);
            int index = holder.page * PAGE_SIZE + slot;
            if (index < islandZones.size()) {
                BuildMartMaterialZone removed = islandZones.get(index);
                List<BuildMartMaterialZone> zones = new ArrayList<>(config.getMaterialZones());
                zones.removeIf(zone -> zone.snapshotId().equals(removed.snapshotId()));
                if (!config.setMaterialZones(zones)) {
                    player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.unable-to-save-material-area-configuration-please-check-server-logs")));
                    return;
                }
                config.deleteMaterialZoneSnapshot(removed);
                session.markDirty();
                player.sendMessage(Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.deleted") + holder.island.displayName()
                        + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.ordinal-infix") + (index + 1) + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.deleted-material-zone-count-suffix")));
                refresh(holder);
            }
            return;
        }
        switch (slot) {
            case SET_SELECTION_SLOT -> saveCurrentSelection(player, holder, config);
            case PREVIOUS_SLOT -> {
                if (holder.island != null && holder.page > 0) {
                    holder.page--;
                    refresh(holder);
                }
            }
            case CLEAR_SLOT -> {
                if (holder.island != null) return;
                List<BuildMartMaterialZone> zones = config.getMaterialZones();
                if (!config.clearMaterialZones()) {
                    player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.unable-to-save-material-area-configuration-please-check-server-logs")));
                    return;
                }
                zones.forEach(config::deleteMaterialZoneSnapshot);
                session.markDirty();
                holder.page = 0;
                player.sendMessage(Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-area-list-cleared")));
                refresh(holder);
            }
            case NEXT_SLOT -> {
                if (holder.island == null) return;
                int pageCount = pageCount(config.getMaterialZones(holder.island).size());
                if (holder.page + 1 < pageCount) {
                    holder.page++;
                    refresh(holder);
                }
            }
            case BACK_SLOT -> {
                if (holder.island != null) {
                    holder.island = null;
                    holder.page = 0;
                    refresh(holder);
                } else {
                    player.closeInventory();
                    PrepareModeInventory.refresh(player, session);
                }
            }
            default -> {
            }
        }
    }

    private static void saveCurrentSelection(Player player, Holder holder, BuildMartConfig config) {
        if (!holder.session.getFlow().isInCorrectWorld(player, holder.session.getTarget())) {
            player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.please-go-to-the-current-map-world-first") + holder.session.getTarget().worldName()));
            return;
        }
        BuildMartMaterialZone pendingSnapshot = null;
        try {
            if (config.getMaterialIslandCenters().size() != BuildMartMaterialIsland.values().length) {
                player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-island-centers-incomplete")));
                return;
            }
            Vector[] selection = holder.session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
            BuildMartMaterialZone zone = new BuildMartMaterialZone(UUID.randomUUID(), selection[0], selection[1]);
            if (zone.volume() <= 0 || zone.volume() > MAX_VOLUME) {
                player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.the-material-area-volume-must-be-between-1-and") + MAX_VOLUME + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.within-blocks")));
                return;
            }
            pendingSnapshot = zone;
            holder.session.getPlugin().getWorldEditManager().saveSelectionAsBlockSchematic(player,
                    config.getMaterialZoneSnapshotFile(zone));
            List<BuildMartMaterialZone> zones = new ArrayList<>(config.getMaterialZones());
            int replacedIndex = sameBoundsIndex(zones, zone);
            BuildMartMaterialZone replaced = replacedIndex < 0 ? null : zones.set(replacedIndex, zone);
            boolean saved = replacedIndex < 0 ? config.addMaterialZone(zone) : config.setMaterialZones(zones);
            if (!saved) {
                config.deleteMaterialZoneSnapshot(zone);
                player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.unable-to-save-material-area-configuration-please-check-server-logs")));
                return;
            }
            pendingSnapshot = null;
            if (replaced != null) config.deleteMaterialZoneSnapshot(replaced);
            holder.session.markDirty();
            BuildMartMaterialIsland assigned = config.classifyMaterialZone(zone);
            holder.island = assigned;
            List<BuildMartMaterialZone> assignedZones = assigned == null
                    ? List.of() : config.getMaterialZones(assigned);
            int assignedIndex = assignedZones.indexOf(assignedZones.stream()
                    .filter(savedZone -> savedZone.snapshotId().equals(zone.snapshotId())).findFirst().orElse(null));
            holder.page = assignedIndex < 0 ? 0 : assignedIndex / PAGE_SIZE;
            if (replacedIndex < 0) {
                player.sendMessage(Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.a-snapshot-of-the-saved-material-area-as-it-is") + zone.volume()
                        + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.squares-automatically-classified") + islandName(assigned)
                        + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.and-update-the-read-only-material-manifest-buildmart-material-manifests")
                        + config.getAreaName() + ".yml"));
            } else {
                player.sendMessage(Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.updated") + islandName(assigned)
                        + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.a-snapshot-of-the-material-area-as-it-is") + zone.volume() + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.blocks")));
            }
            refresh(holder);
        } catch (Exception exception) {
            if (pendingSnapshot != null) config.deleteMaterialZoneSnapshot(pendingSnapshot);
            player.sendMessage(Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.failed-to-save-material-area-snapshot-please-check-worldedit-selection")
                    + exception.getMessage()));
        }
    }

    private static int sameBoundsIndex(@NotNull List<BuildMartMaterialZone> zones,
                                       @NotNull BuildMartMaterialZone candidate) {
        for (int i = 0; i < zones.size(); i++) {
            BuildMartMaterialZone zone = zones.get(i);
            if (zone.minX() == candidate.minX() && zone.maxX() == candidate.maxX()
                    && zone.minY() == candidate.minY() && zone.maxY() == candidate.maxY()
                    && zone.minZ() == candidate.minZ() && zone.maxZ() == candidate.maxZ()) return i;
        }
        return -1;
    }

    private static void refresh(Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        BuildMartConfig config = (BuildMartConfig) holder.session.getTarget().config();
        if (holder.island == null) {
            refreshIslandOverview(holder, config);
            return;
        }
        List<BuildMartMaterialZone> zones = config.getMaterialZones(holder.island);
        int pageCount = pageCount(zones.size());
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));
        int first = holder.page * PAGE_SIZE;
        Map<UUID, Material> dominantMaterials = BuildMartMaterialManifest.readDominantMaterials(config);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = first + slot;
            if (index < zones.size()) {
                BuildMartMaterialZone zone = zones.get(index);
                Material dominant = dominantMaterials.get(zone.snapshotId());
                Material icon = dominant != null && dominant.isItem() ? dominant : Material.STRUCTURE_BLOCK;
                Component name = dominant == null
                        ? Component.text((index + 1) + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-area-snapshot"))
                        : Component.text((index + 1) + ". ").append(Component.translatable(dominant.translationKey()));
                inventory.setItem(slot, item(icon, name,
                        List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.range") + compact(zone), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.full-block-status-will-be-restored-upon-replenishment"), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.click-to-delete-this-material-area")),
                        NamedTextColor.WHITE));
            } else inventory.setItem(slot, filler());
        }
        inventory.setItem(SET_SELECTION_SLOT, item(Material.GOLDEN_AXE, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.save-current-worldedit-selection"),
                List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.record-a-full-block-snapshot-of-the-selection"), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.automatically-classified-into-the-nearest-material-island-according-to-horizontal-distance"),
                        GuiConfig.text("map-editor.games.build-mart.menus.material-zones.update-the-original-snapshot-when-the-range-is-exactly-the-same-as-the-existing-material-area"), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.synchronously-update-the-read-only-material-list")), NamedTextColor.YELLOW));
        if (holder.page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.previous-page"),
                    List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.go-to") + holder.page + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.page-suffix")), NamedTextColor.WHITE));
        }
        Vector center = config.getMaterialIslandCenters().get(holder.island);
        inventory.setItem(CLEAR_SLOT, item(holder.island.icon(), holder.island.displayName(),
                center == null ? List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.the-center-has-not-been-configured-yet")) : List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.center") + compact(center)), NamedTextColor.AQUA));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.ordinal-prefix") + (holder.page + 1) + " / " + pageCount + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.page-suffix"),
                List.of(holder.island.displayName(), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.total-prefix") + zones.size() + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-zone-count-suffix")), NamedTextColor.AQUA));
        if (holder.page + 1 < pageCount) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.next-page"),
                    List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.go-to") + (holder.page + 2) + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.page-suffix")), NamedTextColor.WHITE));
        }
        inventory.setItem(BACK_SLOT, item(Material.ARROW, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.return-to-island-list"), List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.view-all-24-material-islands")), NamedTextColor.WHITE));
    }

    private static void refreshIslandOverview(@NotNull Holder holder, @NotNull BuildMartConfig config) {
        Inventory inventory = holder.inventory;
        Map<BuildMartMaterialIsland, Vector> centers = config.getMaterialIslandCenters();
        Map<BuildMartMaterialIsland, List<BuildMartMaterialZone>> grouped = config.getMaterialZonesByIsland();
        BuildMartMaterialIsland[] islands = BuildMartMaterialIsland.values();
        for (int slot = 0; slot < islands.length; slot++) {
            BuildMartMaterialIsland island = islands[slot];
            Vector center = centers.get(island);
            int count = grouped.getOrDefault(island, List.of()).size();
            Material icon = center == null ? Material.BARRIER : island.icon();
            List<String> lore = center == null
                    ? List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.the-center-has-not-been-configured-yet"), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.unable-to-automatically-classify-material-areas"))
                    : List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.center") + compact(center), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.number-of-material-areas") + count, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.click-to-view-the-material-area-on-the-island"));
            inventory.setItem(slot, item(icon, (slot + 1) + ". " + island.displayName(), lore,
                    center == null ? NamedTextColor.RED : NamedTextColor.WHITE));
        }
        for (int slot = islands.length; slot < PAGE_SIZE; slot++) inventory.setItem(slot, filler());
        inventory.setItem(SET_SELECTION_SLOT, item(Material.GOLDEN_AXE, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.save-current-worldedit-selection"),
                List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.record-a-full-block-snapshot-of-the-selection"), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.automatically-classified-into-the-nearest-material-island-according-to-horizontal-distance"),
                        GuiConfig.text("map-editor.games.build-mart.menus.material-zones.update-the-original-snapshot-when-the-range-is-exactly-the-same"), GuiConfig.text("map-editor.games.build-mart.menus.material-zones.synchronously-update-the-read-only-material-list")), NamedTextColor.YELLOW));
        inventory.setItem(CLEAR_SLOT, item(Material.RED_WOOL, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.clear-all-material-areas"),
                List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.delete-all-material-areas-on-24-islands")), NamedTextColor.RED));
        inventory.setItem(PAGE_SLOT, item(Material.COMPASS, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-island-count-label"),
                List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.total-prefix") + config.getMaterialZones().size() + GuiConfig.text("map-editor.games.build-mart.menus.material-zones.material-zone-count-suffix")), NamedTextColor.AQUA));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, GuiConfig.text("map-editor.games.build-mart.menus.material-zones.return"), List.of(GuiConfig.text("map-editor.games.build-mart.menus.material-zones.return-to-prepare-inventory")), NamedTextColor.WHITE));
    }

    private static @NotNull String islandName(@Nullable BuildMartMaterialIsland island) {
        return island == null ? GuiConfig.text("map-editor.games.build-mart.menus.material-zones.uncategorized") : island.displayName();
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static String compact(BuildMartMaterialZone zone) {
        return zone.minX() + "," + zone.minY() + "," + zone.minZ() + " -> "
                + zone.maxX() + "," + zone.maxY() + "," + zone.maxZ();
    }

    private static String compact(Vector center) {
        return format(center.getX()) + "," + format(center.getY()) + "," + format(center.getZ());
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format("%.2f", value);
    }

    private static ItemStack item(Material material, String name, List<String> lore, NamedTextColor color) {
        return item(material, Component.text(name), lore, color);
    }

    private static ItemStack item(Material material, Component name, List<String> lore, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.color(color).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> Component.text(line).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)).toList());
        });
        return stack;
    }

    private static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), NamedTextColor.DARK_GRAY);
    }
}
