package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.step.CountdownBlockDisappearanceStep;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
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

import java.util.ArrayList;
import java.util.List;

/** Editor GUI for the optional opening-countdown block disappearance. */
public final class CountdownBlockDisappearanceGui {
    private static final int SELECTION_SLOT = 10;
    private static final int RANDOM_SLOT = 12;
    private static final int EAST_WEST_SLOT = 13;
    private static final int NORTH_SOUTH_SLOT = 14;
    private static final int VERTICAL_SLOT = 15;
    private static final int DIRECT_SLOT = 16;
    private static final int CLEAR_SLOT = 17;
    private static final int BACK_SLOT = 22;

    private CountdownBlockDisappearanceGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        final CountdownBlockDisappearanceStep step;
        Inventory inventory;

        Holder(@NotNull PrepareSession session, @NotNull CountdownBlockDisappearanceStep step) {
            this.session = session;
            this.step = step;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull PrepareSession session,
                            @NotNull CountdownBlockDisappearanceStep step) {
        Holder holder = new Holder(session, step);
        holder.inventory = Bukkit.createInventory(holder, 27,
                Component.text("倒计时方块消失").decoration(TextDecoration.ITALIC, false));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    public static void handleClick(@NotNull PrepareSessionManager manager,
                                   @NotNull InventoryClickEvent event, @NotNull Player player,
                                   @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == SELECTION_SLOT) {
            String message = holder.step.captureSelection(session, player);
            if (message != null) player.sendMessage(message);
            back(player, session);
            return;
        }
        CountdownBlockDisappearanceStep.Mode mode = switch (slot) {
            case RANDOM_SLOT -> CountdownBlockDisappearanceStep.Mode.RANDOM;
            case EAST_WEST_SLOT -> CountdownBlockDisappearanceStep.Mode.DOOR_EAST_WEST;
            case NORTH_SOUTH_SLOT -> CountdownBlockDisappearanceStep.Mode.DOOR_NORTH_SOUTH;
            case VERTICAL_SLOT -> CountdownBlockDisappearanceStep.Mode.DOOR_VERTICAL;
            case DIRECT_SLOT -> CountdownBlockDisappearanceStep.Mode.DIRECT;
            default -> null;
        };
        if (mode != null) {
            player.sendMessage(holder.step.selectMode(session, mode));
            back(player, session);
            return;
        }
        if (slot == CLEAR_SLOT) {
            player.sendMessage(holder.step.clearSelection(session));
            back(player, session);
            return;
        }
        if (slot == BACK_SLOT) back(player, session);
    }

    private static void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        BaseGameConfig config = holder.session.getTarget().config();
        Vector first = config.getCountdownBlockDisappearancePos1();
        Vector second = config.getCountdownBlockDisappearancePos2();
        boolean enabled = first != null && second != null;
        String selection = enabled ? "已设置（" + volume(first, second) + " 个方块）" : "未设置（功能关闭）";
        inventory.setItem(SELECTION_SLOT, item(Material.GOLDEN_AXE, "设置当前 WorldEdit 选区",
                NamedTextColor.AQUA, selection, "点击读取当前选区"));

        CountdownBlockDisappearanceStep.Mode current =
                CountdownBlockDisappearanceStep.Mode.from(config.getCountdownBlockDisappearanceMode());
        inventory.setItem(RANDOM_SLOT, modeItem(CountdownBlockDisappearanceStep.Mode.RANDOM, current, enabled));
        inventory.setItem(EAST_WEST_SLOT, modeItem(CountdownBlockDisappearanceStep.Mode.DOOR_EAST_WEST, current, enabled));
        inventory.setItem(NORTH_SOUTH_SLOT, modeItem(CountdownBlockDisappearanceStep.Mode.DOOR_NORTH_SOUTH, current, enabled));
        inventory.setItem(VERTICAL_SLOT, modeItem(CountdownBlockDisappearanceStep.Mode.DOOR_VERTICAL, current, enabled));
        inventory.setItem(DIRECT_SLOT, modeItem(CountdownBlockDisappearanceStep.Mode.DIRECT, current, enabled));
        inventory.setItem(CLEAR_SLOT, item(Material.BARRIER, "关闭并清除选区", NamedTextColor.RED,
                "删除已保存的方块消失选区"));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "返回", NamedTextColor.WHITE, "返回准备步骤"));
        for (int slot : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 19, 20, 21, 23, 24, 25, 26})
            inventory.setItem(slot, filler());
    }

    private static ItemStack modeItem(@NotNull CountdownBlockDisappearanceStep.Mode mode,
                                      @NotNull CountdownBlockDisappearanceStep.Mode current,
                                      boolean enabled) {
        return item(mode.icon(), mode.displayName(), mode == current ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                mode == current ? "当前方式" : "点击选择",
                enabled ? "将在开赛倒计时前 3 秒完成" : "请先设置 WorldEdit 选区");
    }

    private static ItemStack item(@NotNull Material material, @NotNull String name,
                                  @NotNull NamedTextColor color, @NotNull String... lore) {
        ItemStack item = new ItemStack(material);
        List<Component> lines = new ArrayList<>();
        for (String line : lore)
            lines.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            meta.lore(lines);
        });
        return item;
    }

    private static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
    }

    private static long volume(Vector first, Vector second) {
        return ((long) Math.abs(first.getBlockX() - second.getBlockX()) + 1L)
                * ((long) Math.abs(first.getBlockY() - second.getBlockY()) + 1L)
                * ((long) Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L);
    }

    private static void back(@NotNull Player player, @NotNull PrepareSession session) {
        player.closeInventory();
        PrepareModeInventory.refresh(player, session);
    }
}
