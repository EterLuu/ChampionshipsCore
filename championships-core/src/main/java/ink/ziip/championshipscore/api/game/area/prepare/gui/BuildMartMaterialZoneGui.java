package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartCopperPolicy;
import ink.ziip.championshipscore.api.game.buildmart.model.BuildMartMaterialZone;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartMaterialIsland;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.gui.GuiMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
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
    private static final String PATH = MenuId.BUILD_MART_MATERIAL_ZONES.path();
    private static final long MAX_VOLUME = 200_000L;

    private BuildMartMaterialZoneGui() {}

    public static final class Holder implements MenuInventory {
        final PrepareSession session;
        final String stepKey;
        @Nullable BuildMartMaterialIsland island;
        int page;
        int overviewPage;
        final MenuLayout layout = layout();
        final Map<Integer, BuildMartMaterialIsland> islandTargets = new java.util.HashMap<>();
        final Map<Integer, UUID> zoneTargets = new java.util.HashMap<>();
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

    public static void open(
            @NotNull PrepareSessionManager manager,
            @NotNull Player player,
            @NotNull PrepareSession session,
            @NotNull PrepareStep step) {
        Holder holder = new Holder(session, step);
        holder.inventory =
                Bukkit.createInventory(
                        holder, holder.layout.size(), GuiConfig.component(PATH + ".title"));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    public static void handleClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
            @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        int slot = event.getRawSlot();
        BuildMartMaterialIsland selectedIsland = holder.islandTargets.get(slot);
        if (holder.island == null && selectedIsland != null) {
            holder.overviewPage = holder.page;
            holder.island = selectedIsland;
            holder.page = 0;
            refresh(holder);
            return;
        }
        UUID selectedZone = holder.zoneTargets.get(slot);
        if (holder.island != null && selectedZone != null) {
            List<BuildMartMaterialZone> islandZones = config.getMaterialZones(holder.island);
            BuildMartMaterialZone removed =
                    islandZones.stream()
                            .filter(zone -> zone.snapshotId().equals(selectedZone))
                            .findFirst()
                            .orElse(null);
            if (removed == null) {
                refresh(holder);
                return;
            }
            int index = islandZones.indexOf(removed);
            List<BuildMartMaterialZone> zones = new ArrayList<>(config.getMaterialZones());
            zones.removeIf(zone -> zone.snapshotId().equals(selectedZone));
            if (!config.setMaterialZones(zones)) {
                player.sendMessage(
                        CoreMessages.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_SAVE_FAILED));
                return;
            }
            config.deleteMaterialZoneSnapshot(removed);
            session.markDirty();
            player.sendMessage(
                    CoreMessages.formatAdminSuccess(
                            MessageConfig.MAP_EDITOR_BUILD_ZONE_DELETED
                                    .replace("%island%", holder.island.displayName())
                                    .replace("%index%", String.valueOf(index + 1))));
            refresh(holder);
            return;
        }
        if (slot == holder.layout.control("save")) saveCurrentSelection(player, holder, config);
        else if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refresh(holder);
        } else if (slot == holder.layout.control("next")) {
            int count =
                    holder.island == null
                            ? BuildMartMaterialIsland.values().length
                            : config.getMaterialZones(holder.island).size();
            if (holder.page + 1 < holder.layout.pageCount(count)) {
                holder.page++;
                refresh(holder);
            }
        } else if (slot == holder.layout.control("clear") && holder.island == null) {
            List<BuildMartMaterialZone> zones = config.getMaterialZones();
            if (!config.clearMaterialZones()) {
                player.sendMessage(
                        CoreMessages.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_SAVE_FAILED));
                return;
            }
            zones.forEach(config::deleteMaterialZoneSnapshot);
            session.markDirty();
            holder.page = 0;
            player.sendMessage(
                    CoreMessages.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_ZONES_CLEARED));
            refresh(holder);
        } else if (slot == holder.layout.control("back")) {
            if (holder.island != null) {
                holder.island = null;
                holder.page = holder.overviewPage;
                refresh(holder);
            } else {
                player.closeInventory();
                PrepareModeInventory.refresh(player, session);
            }
        }
    }

    private static void saveCurrentSelection(Player player, Holder holder, BuildMartConfig config) {
        if (!holder.session.getFlow().isInCorrectWorld(player, holder.session.getTarget())) {
            player.sendMessage(
                    CoreMessages.formatAdminError(
                            MessageConfig.MAP_EDITOR_STEP_GO_TO_MAP_WORLD_FIRST.replace(
                                    "%world%", holder.session.getTarget().worldName())));
            return;
        }
        BuildMartMaterialZone pendingSnapshot = null;
        try {
            if (config.getMaterialIslandCenters().size()
                    != BuildMartMaterialIsland.values().length) {
                player.sendMessage(
                        CoreMessages.formatAdminError(
                                MessageConfig.MAP_EDITOR_BUILD_CENTERS_INCOMPLETE));
                return;
            }
            Vector[] selection =
                    holder.session
                            .getPlugin()
                            .getWorldEditManager()
                            .getPlayerSelection(player, true);
            BuildMartMaterialZone zone =
                    new BuildMartMaterialZone(UUID.randomUUID(), selection[0], selection[1]);
            if (zone.volume() <= 0 || zone.volume() > MAX_VOLUME) {
                player.sendMessage(
                        CoreMessages.formatAdminError(
                                MessageConfig.MAP_EDITOR_BUILD_ZONE_VOLUME_INVALID.replace(
                                        "%max%", String.valueOf(MAX_VOLUME))));
                return;
            }
            pendingSnapshot = zone;
            holder.session
                    .getPlugin()
                    .getWorldEditManager()
                    .saveSelectionAsBlockSchematic(
                            player, config.getMaterialZoneSnapshotFile(zone));
            WorldEditManager.rewriteSchematicBlockStates(
                    config.getMaterialZoneSnapshotFile(zone), BuildMartCopperPolicy::withoutWax);
            List<BuildMartMaterialZone> zones = new ArrayList<>(config.getMaterialZones());
            int replacedIndex = sameBoundsIndex(zones, zone);
            BuildMartMaterialZone replaced =
                    replacedIndex < 0 ? null : zones.set(replacedIndex, zone);
            boolean saved =
                    replacedIndex < 0
                            ? config.addMaterialZone(zone)
                            : config.setMaterialZones(zones);
            if (!saved) {
                config.deleteMaterialZoneSnapshot(zone);
                player.sendMessage(
                        CoreMessages.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_SAVE_FAILED));
                return;
            }
            pendingSnapshot = null;
            if (replaced != null) config.deleteMaterialZoneSnapshot(replaced);
            holder.session.markDirty();
            BuildMartMaterialIsland assigned = config.classifyMaterialZone(zone);
            holder.island = assigned;
            List<BuildMartMaterialZone> assignedZones =
                    assigned == null ? List.of() : config.getMaterialZones(assigned);
            int assignedIndex =
                    assignedZones.indexOf(
                            assignedZones.stream()
                                    .filter(
                                            savedZone ->
                                                    savedZone
                                                            .snapshotId()
                                                            .equals(zone.snapshotId()))
                                    .findFirst()
                                    .orElse(null));
            holder.page = assignedIndex < 0 ? 0 : assignedIndex / holder.layout.content().size();
            if (replacedIndex < 0) {
                player.sendMessage(
                        CoreMessages.formatAdminSuccess(
                                MessageConfig.MAP_EDITOR_BUILD_ZONE_SAVED
                                        .replace("%blocks%", String.valueOf(zone.volume()))
                                        .replace("%island%", islandName(assigned))
                                        .replace("%file%", config.getAreaName() + ".yml")));
            } else {
                player.sendMessage(
                        CoreMessages.formatAdminSuccess(
                                MessageConfig.MAP_EDITOR_BUILD_ZONE_UPDATED
                                        .replace("%island%", islandName(assigned))
                                        .replace("%blocks%", String.valueOf(zone.volume()))));
            }
            refresh(holder);
        } catch (Exception exception) {
            if (pendingSnapshot != null) config.deleteMaterialZoneSnapshot(pendingSnapshot);
            player.sendMessage(
                    CoreMessages.formatAdminError(
                            MessageConfig.MAP_EDITOR_BUILD_SNAPSHOT_FAILED.replace(
                                    "%error%", exception.getMessage())));
        }
    }

    private static int sameBoundsIndex(
            @NotNull List<BuildMartMaterialZone> zones, @NotNull BuildMartMaterialZone candidate) {
        for (int i = 0; i < zones.size(); i++) {
            BuildMartMaterialZone zone = zones.get(i);
            if (zone.minX() == candidate.minX()
                    && zone.maxX() == candidate.maxX()
                    && zone.minY() == candidate.minY()
                    && zone.maxY() == candidate.maxY()
                    && zone.minZ() == candidate.minZ()
                    && zone.maxZ() == candidate.maxZ()) return i;
        }
        return -1;
    }

    private static void refresh(Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.islandTargets.clear();
        holder.zoneTargets.clear();
        for (int slot : holder.layout.border())
            inventory.setItem(
                    slot, configured("border", null, Map.of(), Material.GRAY_STAINED_GLASS_PANE));
        BuildMartConfig config = (BuildMartConfig) holder.session.getTarget().config();
        boolean overview = holder.island == null;
        List<BuildMartMaterialZone> zones =
                overview ? List.of() : config.getMaterialZones(holder.island);
        var islands = BuildMartMaterialIsland.values();
        int count = overview ? islands.length : zones.size();
        holder.page = holder.layout.clampPage(holder.page, count);
        int pages = holder.layout.pageCount(count);
        Map<UUID, Material> materials =
                overview ? Map.of() : BuildMartMaterialManifest.readDominantMaterials(config);
        var centers = config.getMaterialIslandCenters();
        var grouped =
                overview
                        ? config.getMaterialZonesByIsland()
                        : Map.<BuildMartMaterialIsland, List<BuildMartMaterialZone>>of();
        for (int slot : holder.layout.content()) {
            int index = holder.layout.index(holder.page, slot);
            if (index >= count) {
                inventory.setItem(
                        slot,
                        configured("border", null, Map.of(), Material.GRAY_STAINED_GLASS_PANE));
            } else if (overview) {
                BuildMartMaterialIsland island = islands[index];
                Vector center = centers.get(island);
                inventory.setItem(
                        slot,
                        configured(
                                "island",
                                center == null ? "unconfigured" : null,
                                Map.of(
                                        "order",
                                        index + 1,
                                        "name",
                                        island.displayName(),
                                        "count",
                                        grouped.getOrDefault(island, List.of()).size(),
                                        "center",
                                        center == null ? "" : compact(center)),
                                island.icon()));
                holder.islandTargets.put(slot, island);
            } else {
                BuildMartMaterialZone zone = zones.get(index);
                Material dominant = materials.get(zone.snapshotId());
                Material icon =
                        dominant != null && dominant.isItem() ? dominant : Material.STRUCTURE_BLOCK;
                ItemStack item =
                        configured(
                                "zone",
                                dominant == null ? "no-material" : null,
                                Map.of("order", index + 1, "range", compact(zone)),
                                icon);
                if (dominant != null)
                    item.editMeta(
                            meta ->
                                    meta.displayName(
                                            meta.displayName()
                                                    .append(
                                                            Component.translatable(
                                                                    dominant.translationKey()))));
                inventory.setItem(slot, item);
                holder.zoneTargets.put(slot, zone.snapshotId());
            }
        }
        inventory.setItem(
                holder.layout.control("save"),
                configured("save", null, Map.of(), Material.GOLDEN_AXE));
        if (overview)
            inventory.setItem(
                    holder.layout.control("clear"),
                    configured(
                            "clear", null, Map.of("islands", islands.length), Material.RED_WOOL));
        else {
            Vector center = centers.get(holder.island);
            inventory.setItem(
                    holder.layout.control("clear"),
                    configured(
                            "island-summary",
                            center == null ? "unconfigured" : null,
                            Map.of(
                                    "name",
                                    holder.island.displayName(),
                                    "center",
                                    center == null ? "" : compact(center)),
                            holder.island.icon()));
        }
        inventory.setItem(
                holder.layout.control("page"),
                configured(
                        "page",
                        overview ? null : "detail",
                        Map.of(
                                "page",
                                holder.page + 1,
                                "pages",
                                pages,
                                "count",
                                overview ? config.getMaterialZones().size() : zones.size(),
                                "island",
                                overview ? "" : holder.island.displayName()),
                        overview ? Material.COMPASS : Material.PAPER));
        if (holder.page > 0)
            inventory.setItem(
                    holder.layout.control("previous"),
                    configured(
                            "previous",
                            null,
                            Map.of("page", holder.page, "pages", pages),
                            Material.ARROW));
        if (holder.page + 1 < pages)
            inventory.setItem(
                    holder.layout.control("next"),
                    configured(
                            "next",
                            null,
                            Map.of("page", holder.page + 2, "pages", pages),
                            Material.ARROW));
        inventory.setItem(
                holder.layout.control("back"),
                configured(
                        "back",
                        overview ? "overview" : null,
                        Map.of("islands", islands.length),
                        Material.ARROW));
    }

    static MenuLayout layout() {
        int size = GuiConfig.menu(PATH, 54, "", MenuLayout.range(0, 45)).size();
        return MenuLayout.load(
                PATH,
                size,
                MenuLayout.range(0, 45),
                List.of(),
                Map.of(
                        "save",
                        45,
                        "previous",
                        46,
                        "clear",
                        48,
                        "page",
                        49,
                        "next",
                        52,
                        "back",
                        53));
    }

    private static ItemStack configured(
            String id, String state, Map<String, ?> placeholders, Material fallback) {
        return ConfiguredGui.item(
                PATH + ".items." + id,
                state,
                placeholders,
                GuiMenu.item(fallback, Component.empty(), List.of(), false));
    }

    private static @NotNull String islandName(@Nullable BuildMartMaterialIsland island) {
        return island == null
                ? GuiConfig.text(
                        "map-editor.games.build-mart.menus.material-zones.items.uncategorized.title")
                : island.displayName();
    }

    private static String compact(BuildMartMaterialZone zone) {
        return GuiConfig.text(
                PATH + ".items.range-format.title",
                Map.of(
                        "min-x",
                        zone.minX(),
                        "min-y",
                        zone.minY(),
                        "min-z",
                        zone.minZ(),
                        "max-x",
                        zone.maxX(),
                        "max-y",
                        zone.maxY(),
                        "max-z",
                        zone.maxZ()));
    }

    private static String compact(Vector center) {
        return GuiConfig.text(
                PATH + ".items.coordinate-format.title",
                Map.of(
                        "x",
                        format(center.getX()),
                        "y",
                        format(center.getY()),
                        "z",
                        format(center.getZ())));
    }

    private static String format(double value) {
        return value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
