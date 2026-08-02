package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and refreshes the dedicated prepare-mode hotbar. The hotbar contains the fixed controls and one
 * entry point for the paged, single-row step menu; no step item is placed in the player's main inventory.
 * The WorldEdit wand is deliberately left untagged so WorldEdit's own interact handlers run.
 */
public final class PrepareModeInventory {
    private PrepareModeInventory() {
    }

    /** Wipe the player's inventory completely and lay out the prepare hotbar. */
    public static void apply(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        player.setItemOnCursor(null);
        refresh(player, session);
    }

    /**
     * Re-render the fixed controls without disturbing the spare hotbar slots used for creative-mode
     * building materials. The full inventory is only cleared when a prepare session starts.
     */
    public static void refresh(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, statusItem(player, session));
        inv.setItem(1, teleportItem(session));
        inv.setItem(2, stepsItem(session));
        inv.setItem(3, validateItem());
        inv.setItem(4, publishItem(session));
        if (session.requiresWorldEdit()) inv.setItem(5, wandItem());
        inv.setItem(8, exitItem());
    }

    /** Slots reserved for prepare controls and therefore never valid creative pick-block targets. */
    public static boolean isControlSlot(@NotNull PrepareSession session, int slot) {
        return switch (slot) {
            case 0, 1, 2, 3, 4, 8 -> true;
            case 5 -> session.requiresWorldEdit();
            default -> false;
        };
    }

    /**
     * Finds a safe hotbar target for creative pick-block. Prefer an empty spare slot, then reuse a
     * spare material slot rather than ever overwriting a prepare control.
     */
    public static int creativePickTarget(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!isControlSlot(session, slot) && inv.getItem(slot) == null) return slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (!isControlSlot(session, slot)) return slot;
        }
        return -1;
    }

    private static ItemStack statusItem(@NotNull Player player, @NotNull PrepareSession session) {
        GameTypeEnum game = session.getGameType();
        boolean inWorld = session.getFlow().isInCorrectWorld(player, session.getTarget());
        int done = session.doneCount();
        int total = session.totalSteps();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("游戏：" + game + "   地图：" + session.getAreaName()).color(NamedTextColor.GRAY));
        lore.add(Component.text("目标世界：" + session.getFlow().worldName(session.getTarget())
                        + (inWorld ? "  ✅ 已在正确世界" : "  ❌ 请前往该世界")).color(inWorld ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.text("进度：" + done + "/" + total).color(NamedTextColor.AQUA));
        lore.add(Component.text(session.getTarget().config().isPrepareReady()
                ? "状态：✅ 已发布" : "状态：⚠ 草稿 / 有未发布修改")
                .color(session.getTarget().config().isPrepareReady() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        List<String> pending = new ArrayList<>();
        for (PrepareStep step : session.getSteps()) {
            if (!step.isSet(session)) pending.add(plain(step.displayName()));
        }
        if (pending.isEmpty()) {
            lore.add(Component.text("全部步骤已完成").color(NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("待办：").color(NamedTextColor.YELLOW));
            for (String p : pending) lore.add(Component.text("• " + p).color(NamedTextColor.YELLOW));
        }
        return PrepareKeys.item(Material.PAPER, Component.text("Prepare 模式").color(NamedTextColor.WHITE), lore);
    }

    private static ItemStack teleportItem(@NotNull PrepareSession session) {
        String destination = session.getFlow().editorLocationName(session.getTarget());
        ItemStack item = PrepareKeys.item(Material.ENDER_PEARL,
                Component.text("传送至 " + destination).color(NamedTextColor.AQUA),
                List.of(Component.text("前往 " + session.getFlow().worldName(session.getTarget())
                        + " 世界 / 前往 " + destination).color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "teleport");
        return item;
    }

    private static ItemStack stepsItem(@NotNull PrepareSession session) {
        ItemStack item = PrepareKeys.item(Material.CHEST,
                Component.text("编辑准备步骤").color(NamedTextColor.AQUA),
                List.of(Component.text("打开单行步骤菜单").color(NamedTextColor.GRAY),
                        Component.text("步骤数量：" + session.totalSteps()).color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "steps");
        return item;
    }

    private static ItemStack exitItem() {
        ItemStack item = PrepareKeys.item(Material.BARRIER,
                Component.text("退出 Prepare 模式").color(NamedTextColor.RED),
                List.of(Component.text("还原物品栏并退出").color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "exit");
        return item;
    }

    private static ItemStack validateItem() {
        ItemStack item = PrepareKeys.item(Material.SPYGLASS,
                Component.text("校验地图").color(NamedTextColor.YELLOW),
                List.of(Component.text("列出所有未完成的必需步骤").color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "validate");
        return item;
    }

    private static ItemStack publishItem(PrepareSession session) {
        boolean ready = session.getTarget().config().isPrepareReady();
        ItemStack item = PrepareKeys.item(ready ? Material.LIME_DYE : Material.YELLOW_DYE,
                Component.text(ready ? "地图已发布" : "验证并发布")
                        .color(ready ? NamedTextColor.GREEN : NamedTextColor.GOLD),
                List.of(Component.text(ready ? "再次发布会生成新的 revision" : "校验通过后固化世界并允许开赛")
                        .color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "publish");
        return item;
    }

    /** Untagged on purpose: WorldEdit's wand interact handlers must not be cancelled. */
    private static ItemStack wandItem() {
        return PrepareKeys.item(Material.WOODEN_AXE,
                Component.text("WorldEdit 选区工具").color(NamedTextColor.GOLD),
                List.of(Component.text("左键选 pos1，右键选 pos2（用于边界/模板步骤）").color(NamedTextColor.GRAY)));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
