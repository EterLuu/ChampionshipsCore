package ink.ziip.championshipscore.api.game.area.prepare.gui;

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
                Component.text("材料区").decoration(TextDecoration.ITALIC, false));
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
                    player.sendMessage(Utils.formatAdminError("无法保存材料区配置，请查看服务器日志。"));
                    return;
                }
                config.deleteMaterialZoneSnapshot(removed);
                session.markDirty();
                player.sendMessage(Utils.formatAdminSuccess("已删除 &#fff566" + holder.island.displayName()
                        + "&#ededed 的第 &#fff566" + (index + 1) + "&#ededed 个材料区。"));
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
                    player.sendMessage(Utils.formatAdminError("无法保存材料区配置，请查看服务器日志。"));
                    return;
                }
                zones.forEach(config::deleteMaterialZoneSnapshot);
                session.markDirty();
                holder.page = 0;
                player.sendMessage(Utils.formatAdminSuccess("已清空材料区列表。"));
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
            player.sendMessage(Utils.formatAdminError("请先前往当前地图世界 " + holder.session.getTarget().worldName()));
            return;
        }
        BuildMartMaterialZone pendingSnapshot = null;
        try {
            if (config.getMaterialIslandCenters().size() != BuildMartMaterialIsland.values().length) {
                player.sendMessage(Utils.formatAdminError("材料岛中心配置不完整，无法自动归类新选区。"));
                return;
            }
            Vector[] selection = holder.session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
            BuildMartMaterialZone zone = new BuildMartMaterialZone(UUID.randomUUID(), selection[0], selection[1]);
            if (zone.volume() <= 0 || zone.volume() > MAX_VOLUME) {
                player.sendMessage(Utils.formatAdminError("材料区体积必须在 1 到 " + MAX_VOLUME + " 个方块以内。"));
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
                player.sendMessage(Utils.formatAdminError("无法保存材料区配置，请查看服务器日志。"));
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
                player.sendMessage(Utils.formatAdminSuccess("已保存材料区原样快照（&#fff566" + zone.volume()
                        + "&#ededed 个方块），自动归入 &#fff566" + islandName(assigned)
                        + "&#ededed，并更新只读材料清单：&#fff566buildmart/material-manifests/"
                        + config.getAreaName() + ".yml"));
            } else {
                player.sendMessage(Utils.formatAdminSuccess("已更新 &#fff566" + islandName(assigned)
                        + "&#ededed 的材料区原样快照（&#fff566" + zone.volume() + "&#ededed 个方块）。"));
            }
            refresh(holder);
        } catch (Exception exception) {
            if (pendingSnapshot != null) config.deleteMaterialZoneSnapshot(pendingSnapshot);
            player.sendMessage(Utils.formatAdminError("保存材料区快照失败，请检查 WorldEdit 选区：&#fff566"
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
                        ? Component.text((index + 1) + ". 材料区快照")
                        : Component.text((index + 1) + ". ").append(Component.translatable(dominant.translationKey()));
                inventory.setItem(slot, item(icon, name,
                        List.of("范围：" + compact(zone), "完整方块状态将在补充时还原", "点击删除此材料区"),
                        NamedTextColor.WHITE));
            } else inventory.setItem(slot, filler());
        }
        inventory.setItem(SET_SELECTION_SLOT, item(Material.GOLDEN_AXE, "保存当前 WorldEdit 选区",
                List.of("记录选区的完整方块快照", "按水平距离自动归入最近材料岛",
                        "范围与已有材料区完全相同时更新原快照", "同步更新只读材料清单"), NamedTextColor.YELLOW));
        if (holder.page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "上一页",
                    List.of("前往第 " + holder.page + " 页"), NamedTextColor.WHITE));
        }
        Vector center = config.getMaterialIslandCenters().get(holder.island);
        inventory.setItem(CLEAR_SLOT, item(holder.island.icon(), holder.island.displayName(),
                center == null ? List.of("中心尚未配置") : List.of("中心：" + compact(center)), NamedTextColor.AQUA));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER, "第 " + (holder.page + 1) + " / " + pageCount + " 页",
                List.of(holder.island.displayName(), "共 " + zones.size() + " 个材料区"), NamedTextColor.AQUA));
        if (holder.page + 1 < pageCount) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "下一页",
                    List.of("前往第 " + (holder.page + 2) + " 页"), NamedTextColor.WHITE));
        }
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "返回岛屿列表", List.of("查看全部 24 个材料岛"), NamedTextColor.WHITE));
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
                    ? List.of("中心尚未配置", "无法自动归类材料区")
                    : List.of("中心：" + compact(center), "材料区数量：" + count, "点击查看岛内材料区");
            inventory.setItem(slot, item(icon, (slot + 1) + ". " + island.displayName(), lore,
                    center == null ? NamedTextColor.RED : NamedTextColor.WHITE));
        }
        for (int slot = islands.length; slot < PAGE_SIZE; slot++) inventory.setItem(slot, filler());
        inventory.setItem(SET_SELECTION_SLOT, item(Material.GOLDEN_AXE, "保存当前 WorldEdit 选区",
                List.of("记录选区的完整方块快照", "按水平距离自动归入最近材料岛",
                        "范围完全相同时更新原快照", "同步更新只读材料清单"), NamedTextColor.YELLOW));
        inventory.setItem(CLEAR_SLOT, item(Material.RED_WOOL, "清空全部材料区",
                List.of("删除 24 个岛上的全部材料区"), NamedTextColor.RED));
        inventory.setItem(PAGE_SLOT, item(Material.COMPASS, "24 个材料岛",
                List.of("共 " + config.getMaterialZones().size() + " 个材料区"), NamedTextColor.AQUA));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "返回", List.of("回到 prepare 物品栏"), NamedTextColor.WHITE));
    }

    private static @NotNull String islandName(@Nullable BuildMartMaterialIsland island) {
        return island == null ? "未分类" : island.displayName();
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
