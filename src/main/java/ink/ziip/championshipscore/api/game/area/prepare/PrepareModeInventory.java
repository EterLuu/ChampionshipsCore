package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and refreshes the dedicated prepare-mode player inventory: a status item, a teleport-to-copy-0
 * control, an exit control, an untagged WorldEdit wand (so WE selection still works), and one item per
 * {@link PrepareStep} showing its current state. All step/control items are PDC-tagged via {@link PrepareKeys}
 * so {@code PrepareListener} can route clicks; the wand is deliberately left untagged so WorldEdit's own
 * interact handlers run.
 */
public final class PrepareModeInventory {
    private PrepareModeInventory() {
    }

    /** Wipe the player's inventory completely and lay out the prepare items. */
    public static void apply(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        player.setItemOnCursor(null);
        refresh(player, session);
    }

    /** Re-render the items (re-reads each step's state). Does not touch saved contents. */
    public static void refresh(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, statusItem(player, session));
        inv.setItem(1, teleportItem(session));
        inv.setItem(2, exitItem());
        inv.setItem(3, wandItem());

        List<PrepareStep> steps = session.getSteps();
        for (int i = 0; i < steps.size() && (4 + i) < 36; i++) {
            inv.setItem(4 + i, stepItem(session, steps.get(i)));
        }
        for (int i = 4 + steps.size(); i < 36; i++) {
            inv.setItem(i, null);
        }
    }

    private static ItemStack statusItem(@NotNull Player player, @NotNull PrepareSession session) {
        GameTypeEnum game = session.getGameType();
        boolean inWorld = session.getFlow().isInCorrectWorld(player, session.getTarget());
        int done = session.doneCount();
        int total = session.totalSteps();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("游戏：" + game + "   场地：" + session.getAreaName()).color(NamedTextColor.GRAY));
        lore.add(Component.text("目标世界：" + session.getFlow().worldName(session.getTarget())
                        + (inWorld ? "  ✅ 已在正确世界" : "  ❌ 请前往该世界")).color(inWorld ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.text("进度：" + done + "/" + total).color(NamedTextColor.AQUA));

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
        ItemStack item = PrepareKeys.item(Material.ENDER_PEARL,
                Component.text("传送至 0 号场地").color(NamedTextColor.AQUA),
                List.of(Component.text("前往 " + session.getFlow().worldName(session.getTarget())
                        + " 世界 / 回到 0 号场地").color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "teleport");
        return item;
    }

    private static ItemStack exitItem() {
        ItemStack item = PrepareKeys.item(Material.BARRIER,
                Component.text("退出 Prepare 模式").color(NamedTextColor.RED),
                List.of(Component.text("还原物品栏并退出").color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "exit");
        return item;
    }

    /** Untagged on purpose: WorldEdit's wand interact handlers must not be cancelled. */
    private static ItemStack wandItem() {
        return PrepareKeys.item(Material.WOODEN_AXE,
                Component.text("WorldEdit 选区工具").color(NamedTextColor.GOLD),
                List.of(Component.text("左键选 pos1，右键选 pos2（用于边界/模板步骤）").color(NamedTextColor.GRAY)));
    }

    private static ItemStack stepItem(@NotNull PrepareSession session, @NotNull PrepareStep step) {
        List<Component> lore = new ArrayList<>();
        lore.add(step.description().color(NamedTextColor.GRAY));
        lore.add(Component.text(statusText(session, step)).color(step.isSet(session) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        ItemStack item = PrepareKeys.item(step.icon(), step.displayName().color(NamedTextColor.WHITE), lore);
        PrepareKeys.setStep(item, step.key());
        return item;
    }

    private static String statusText(@NotNull PrepareSession session, @NotNull PrepareStep step) {
        return switch (step.captureType()) {
            case CONFIRM_WORLD -> session.isWorldConfirmed() ? "✅ 已确认所在世界" : "⬜ 未确认";
            case STAMP -> session.isStamped() ? "✅ 已盖章生成" : "⬜ 未盖章";
            case LIST -> step.isSet(session) ? ("✅ 已设置（" + step.listCount(session) + " 个）") : "⬜ 未设置";
            default -> step.isSet(session) ? "✅ 已设置" : "⬜ 未设置";
        };
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
