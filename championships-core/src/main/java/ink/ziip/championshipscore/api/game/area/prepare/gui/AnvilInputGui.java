package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Anvil-rename input used for two prompts: reading a new area name ({@link #openName}) and reading a stamp
 * copy count ({@link #openNumber}). The player types in the rename field and clicks the result slot (slot
 * 2) to confirm; {@link AnvilInventory#getRenameText()} yields the typed text. Repair cost is forced to 0
 * (via {@link org.bukkit.event.inventory.PrepareAnvilEvent}) so no XP is ever charged.
 */
public final class AnvilInputGui {
    private static final Map<UUID, Holder> OPEN_INPUTS = new HashMap<>();

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
        open(player, Mode.NAME, GuiConfig.text("prepare-gui-anvilinputgui.text-001"), text -> {
            String error = validateName(manager, gameType, text);
            if (error != null) {
                player.sendMessage(error);
                return;
            }
            close(player);
            manager.createAndEnter(player, gameType, text);
        });
    }

    public static void openNumber(@NotNull Player player, @NotNull IntConsumer onCount) {
        open(player, Mode.NUMBER, GuiConfig.text("prepare-gui-anvilinputgui.text-002"), text -> {
            int n;
            try {
                n = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                Utils.sendAdminError(player, GuiConfig.text("prepare-gui-anvilinputgui.text-003"));
                return;
            }
            if (n < 1) {
                Utils.sendAdminError(player, GuiConfig.text("prepare-gui-anvilinputgui.text-004"));
                return;
            }
            close(player);
            onCount.accept(n);
        });
    }

    /** Opens a signed integer prompt. An empty input accepts the supplied default value. */
    public static void openInteger(@NotNull Player player, @NotNull String prompt, int defaultValue,
                                   @NotNull IntConsumer onValue) {
        open(player, Mode.NUMBER, prompt, text -> {
            int value = defaultValue;
            if (!text.isBlank()) {
                try {
                    value = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    Utils.sendAdminError(player, GuiConfig.text("prepare-gui-anvilinputgui.text-005"));
                    return;
                }
            }
            close(player);
            onValue.accept(value);
        });
    }

    private static void open(@NotNull Player player, @NotNull Mode mode, @NotNull String prompt, @NotNull Consumer<String> callback) {
        Holder holder = new Holder(mode, callback);
        AnvilView view = MenuType.ANVIL.create(player, Component.text(prompt));
        player.openInventory(view);
        AnvilInventory inv = view.getTopInventory();
        holder.inventory = inv;
        OPEN_INPUTS.put(player.getUniqueId(), holder);
        inv.setFirstItem(PrepareKeys.item(Material.PAPER, Component.text(prompt),
                List.of(Component.text(GuiConfig.text("prepare-gui-anvilinputgui.text-006")).color(NamedTextColor.GRAY))));
        view.setMaximumRepairCost(0);
        view.setRepairCost(0);
    }

    public static @Nullable Holder getHolder(@NotNull Player player, @NotNull Inventory inventory) {
        Holder holder = OPEN_INPUTS.get(player.getUniqueId());
        return holder != null && holder.inventory == inventory ? holder : null;
    }

    public static void clear(@NotNull Player player, @NotNull Inventory inventory) {
        Holder holder = getHolder(player, inventory);
        if (holder != null) {
            holder.inventory.clear();
            OPEN_INPUTS.remove(player.getUniqueId());
        }
    }

    /** Remove the input placeholder before its container is closed, so it cannot enter a player inventory. */
    public static void close(@NotNull Player player) {
        Holder holder = OPEN_INPUTS.remove(player.getUniqueId());
        if (holder == null) return;
        holder.inventory.clear();
        if (player.getOpenInventory().getTopInventory() == holder.inventory) {
            player.closeInventory();
        }
    }

    private static @Nullable String validateName(@NotNull PrepareSessionManager manager, @NotNull GameTypeEnum gameType, @Nullable String name) {
        if (name == null || name.isBlank()) return Utils.formatAdminError(GuiConfig.text("prepare-gui-anvilinputgui.text-007"));
        String trimmed = name.trim();
        if (trimmed.length() > 32) return Utils.formatAdminError(GuiConfig.text("prepare-gui-anvilinputgui.text-008"));
        if (trimmed.matches(".*[\\\\/:*?\"<>|].*")) return Utils.formatAdminError(GuiConfig.text("prepare-gui-anvilinputgui.text-009"));
        BaseGameInstanceManager<?> mgr = manager.getPlugin().getGameManager().getAreaManager(gameType);
        if (mgr != null && mgr.getArea(trimmed) != null) return Utils.formatAdminError(GuiConfig.text("prepare-gui-anvilinputgui.text-010") + trimmed + GuiConfig.text("prepare-gui-anvilinputgui.text-011"));
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
