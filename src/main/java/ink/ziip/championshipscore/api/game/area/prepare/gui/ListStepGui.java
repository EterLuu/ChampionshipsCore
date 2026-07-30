package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sub-GUI for a {@link ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType#LIST} step: add the
 * player's current location, clear the list, see the current count, or go back. Opened on top of the
 * prepare-mode inventory; the prepare inventory is refreshed after each add/clear so the step's lore badge
 * stays in sync.
 */
public final class ListStepGui {
    private ListStepGui() {
    }

    private static final int ADD_SLOT = 0;
    private static final int CLEAR_SLOT = 1;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 8;

    public static final class Holder implements InventoryHolder {
        final String stepKey;
        Inventory inventory;

        Holder(String stepKey) {
            this.stepKey = stepKey;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull PrepareSessionManager manager, @NotNull Player player,
                            @NotNull PrepareSession session, @NotNull PrepareStep step) {
        Holder holder = new Holder(step.key());
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("点位列表：" + PlainTextComponentSerializer.plainText().serialize(step.displayName()))
                        .decoration(TextDecoration.ITALIC, false));
        holder.inventory = inv;
        refresh(inv, session, step);
        player.openInventory(inv);
    }

    private static void refresh(@NotNull Inventory inv, @NotNull PrepareSession session, @NotNull PrepareStep step) {
        inv.setItem(ADD_SLOT, item(Material.LIME_WOOL, Component.text("添加当前点位"), NamedTextColor.GREEN,
                List.of(Component.text("站到目标位置后点击").color(NamedTextColor.GRAY))));
        inv.setItem(CLEAR_SLOT, item(Material.RED_WOOL, Component.text("清空列表"), NamedTextColor.RED,
                List.of(Component.text("移除已添加的全部点位").color(NamedTextColor.GRAY))));
        inv.setItem(INFO_SLOT, item(Material.BOOK, Component.text("当前点位数：" + step.listCount(session)), NamedTextColor.AQUA,
                List.of(Component.text(step.isSet(session) ? "已设置" : "未设置").color(NamedTextColor.GRAY))));
        inv.setItem(BACK_SLOT, item(Material.ARROW, Component.text("返回"), NamedTextColor.WHITE,
                List.of(Component.text("回到 prepare 物品栏").color(NamedTextColor.GRAY))));
    }

    public static void handleClick(@NotNull PrepareSessionManager manager, @NotNull InventoryClickEvent event,
                                   @NotNull Player player, @NotNull Holder holder) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) return;
        PrepareSession session = manager.getSession(player);
        if (session == null) {
            player.closeInventory();
            return;
        }
        PrepareStep step = session.step(holder.stepKey);
        if (step == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        switch (slot) {
            case ADD_SLOT -> {
                if (!session.getFlow().isInCorrectWorld(player, session.getTarget())) {
                    Utils.sendAdminError(player, "请先前往当前地图世界 " + session.getTarget().worldName());
                    return;
                }
                String m = step.listAdd(session, player);
                if (m != null) player.sendMessage(m);
                refresh(top, session, step);
                PrepareModeInventory.refresh(player, session);
            }
            case CLEAR_SLOT -> {
                String m = step.listClear(session, player);
                if (m != null) player.sendMessage(m);
                refresh(top, session, step);
                PrepareModeInventory.refresh(player, session);
            }
            case BACK_SLOT -> {
                player.closeInventory();
                PrepareModeInventory.refresh(player, session);
            }
            default -> {
            }
        }
    }

    private static ItemStack item(Material mat, Component name, NamedTextColor nameColor, List<Component> lore) {
        ItemStack is = new ItemStack(mat);
        is.editMeta(meta -> {
            meta.displayName(name.color(nameColor).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return is;
    }
}
