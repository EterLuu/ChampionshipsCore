package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Anvil-rename input used for two prompts: reading a new area name ({@link #openName}) and reading a stamp
 * copy count ({@link #openNumber}). The player types in the rename field and clicks the result slot (slot
 * 2) to confirm; {@link AnvilInventory#getRenameText()} yields the typed text. Repair cost is forced to 0
 * (via {@link org.bukkit.event.inventory.PrepareAnvilEvent}) so no XP is ever charged.
 */
public final class AnvilInputGui {
    private AnvilInputGui() {
    }

    public enum Mode { NAME, NUMBER }

    public static final class Holder implements InventoryHolder {
        final Mode mode;
        final Consumer<String> callback;
        Inventory inventory;

        Holder(Mode mode, Consumer<String> callback) {
            this.mode = mode;
            this.callback = callback;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void openName(@NotNull Player player, @NotNull PrepareSessionManager manager, @NotNull GameTypeEnum gameType) {
        open(player, Mode.NAME, "输入场地名", text -> {
            String error = validateName(manager, gameType, text);
            if (error != null) {
                player.sendMessage(error);
                return;
            }
            player.closeInventory();
            manager.createAndEnter(player, gameType, text);
        });
    }

    public static void openNumber(@NotNull Player player, @NotNull IntConsumer onCount) {
        open(player, Mode.NUMBER, "输入份数", text -> {
            int n;
            try {
                n = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                Utils.sendAdminError(player, "请输入有效数字。");
                return;
            }
            if (n < 1) {
                Utils.sendAdminError(player, "份数必须至少为 #fff5661");
                return;
            }
            player.closeInventory();
            onCount.accept(n);
        });
    }

    private static void open(@NotNull Player player, @NotNull Mode mode, @NotNull String prompt, @NotNull Consumer<String> callback) {
        Holder holder = new Holder(mode, callback);
        AnvilInventory inv = (AnvilInventory) Bukkit.createInventory(holder, InventoryType.ANVIL,
                Component.text(prompt).decoration(TextDecoration.ITALIC, false));
        holder.inventory = inv;
        inv.setFirstItem(PrepareKeys.item(Material.PAPER, Component.text(prompt),
                List.of(Component.text("在上方重命名栏输入，再点击右侧结果格确认").color(NamedTextColor.GRAY))));
        AnvilView view = (AnvilView) player.openInventory(inv);
        view.setMaximumRepairCost(0);
        view.setRepairCost(0);
    }

    private static @Nullable String validateName(@NotNull PrepareSessionManager manager, @NotNull GameTypeEnum gameType, @Nullable String name) {
        if (name == null || name.isBlank()) return Utils.formatAdminError("场地名不能为空。");
        String trimmed = name.trim();
        if (trimmed.length() > 32) return Utils.formatAdminError("场地名不能超过 #fff56632 #ededed个字符。");
        if (trimmed.matches(".*[\\\\/:*?\"<>|].*")) return Utils.formatAdminError("场地名包含无效字符。");
        BaseAreaManager<?> mgr = manager.getPlugin().getGameManager().getAreaManager(gameType);
        if (mgr != null && mgr.getArea(trimmed) != null) return Utils.formatAdminError("场地 #fff566" + trimmed + " #ededed已存在。");
        return null;
    }

    public static void handleClick(@NotNull PrepareSessionManager manager, @NotNull InventoryClickEvent event,
                                   @NotNull Player player, @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return; // only the result slot confirms
        String text = ((AnvilView) event.getView()).getRenameText();
        holder.callback.accept(text == null ? "" : text.trim());
    }
}
