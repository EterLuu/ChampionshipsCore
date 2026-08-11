package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Selects the progress line reached after one Ace Race respawn marker. */
public final class AceRaceRespawnPointBindingGui {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 48;
    private static final int START_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private AceRaceRespawnPointBindingGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        final int respawnIndex;
        int page;
        Inventory inventory;

        Holder(@NotNull PrepareSession session, int respawnIndex) {
            this.session = session;
            this.respawnIndex = respawnIndex;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull PrepareSessionManager manager, @NotNull Player player,
                            @NotNull PrepareSession session, int respawnIndex) {
        Holder holder = new Holder(session, respawnIndex);
        holder.inventory = Bukkit.createInventory(holder, 54,
                Component.text("选择重生点所属进度线").decoration(TextDecoration.ITALIC, false));
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
        AceRaceRespawnPointListStep step = new AceRaceRespawnPointListStep();
        AceRaceArea area = step.area(session);
        if (area == null || area.getRespawnPointIndexForConfig(holder.respawnIndex) < 0) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT) {
            if (holder.page > 0) {
                holder.page--;
                refresh(holder);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page + 1 < pageCount(area.getProgressPoints().size())) {
                holder.page++;
                refresh(holder);
            }
            return;
        }
        if (slot == BACK_SLOT) {
            ListStepGui.openEdit(player, session, step, holder.respawnIndex);
            return;
        }
        if (slot == START_SLOT) {
            choose(manager, player, session, step, holder.respawnIndex, -1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        int progressIndex = holder.page * PAGE_SIZE + slot;
        if (progressIndex < area.getProgressPoints().size())
            choose(manager, player, session, step, holder.respawnIndex, progressIndex);
    }

    private static void choose(@NotNull PrepareSessionManager manager, @NotNull Player player,
                               @NotNull PrepareSession session, @NotNull AceRaceRespawnPointListStep step,
                               int respawnIndex, int binding) {
        String message = step.setBinding(session, respawnIndex, binding);
        if (message != null) player.sendMessage(message);
        ListStepGui.openEdit(player, session, step, respawnIndex);
    }

    private static void refresh(@NotNull Holder holder) {
        AceRaceRespawnPointListStep step = new AceRaceRespawnPointListStep();
        AceRaceArea area = step.area(holder.session);
        Inventory inventory = holder.inventory;
        inventory.clear();
        if (area == null) return;
        int pageCount = pageCount(area.getProgressPoints().size());
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));
        int first = holder.page * PAGE_SIZE;
        int current = area.getRespawnPointBinding(holder.respawnIndex);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int progressIndex = first + slot;
            if (progressIndex >= area.getProgressPoints().size()) {
                inventory.setItem(slot, filler());
                continue;
            }
            boolean selected = progressIndex == current;
            inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE,
                    "进度线 #" + (progressIndex + 1) + " 后",
                    selected ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                    selected ? "当前绑定" : "点击绑定到此进度线后"));
        }
        inventory.setItem(PREVIOUS_SLOT, holder.page > 0
                ? item(Material.ARROW, "上一页", NamedTextColor.WHITE, "第 " + holder.page + " 页") : filler());
        inventory.setItem(BACK_SLOT, item(Material.BARRIER, "返回重生点编辑", NamedTextColor.RED));
        inventory.setItem(START_SLOT, item(Material.COMPASS, "起点后（不绑定进度线）", current < 0
                ? NamedTextColor.GREEN : NamedTextColor.YELLOW, current < 0 ? "当前绑定" : "点击选择"));
        inventory.setItem(NEXT_SLOT, holder.page + 1 < pageCount
                ? item(Material.ARROW, "下一页", NamedTextColor.WHITE, "第 " + (holder.page + 2) + " / " + pageCount + " 页")
                : filler());
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static ItemStack item(@NotNull Material material, @NotNull String name,
                                  @NotNull NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.Arrays.stream(lore).map(line -> Component.text(line)
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    private static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
    }
}
