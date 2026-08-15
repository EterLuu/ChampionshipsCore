package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The entry chest GUI: lists every existing area of a game (with a config-completion badge), plus "new
 * area" and "close" buttons. Clicking an area enters an edit session; "new" opens an anvil to read a name.
 */
public final class AreaListGui {
    private static final String MENU_PATH = MenuId.MAP_EDITOR_AREA_LIST.path();

    private AreaListGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final GameTypeEnum gameType;
        private final Map<Integer, String> slotToArea = new HashMap<>();
        private String deleteConfirmation;
        private long deleteConfirmationExpiresAt;
        private int newSlot = -1;
        private int closeSlot = -1;
        private Inventory inventory;

        public Holder(GameTypeEnum gameType) {
            this.gameType = gameType;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public GameTypeEnum gameType() {
            return gameType;
        }
    }

    public static void open(@NotNull PrepareSessionManager manager, @NotNull Player player, @NotNull GameTypeEnum gameType) {
        PrepareFlowDefinition flow = manager.flow(gameType);
        BaseGameInstanceManager<?> areaManager = manager.getPlugin().getGameManager().getAreaManager(gameType);
        List<String> names = areaManager == null ? List.of() : areaManager.getAreaNameList();

        int needed = names.size() + 2; // areas + new + close
        int rows = Math.max(3, Math.min(6, (needed + 8) / 9));
        Holder holder = new Holder(gameType);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Component.text(gameType + GuiConfig.text("map-editor.menus.area-list.copy.venue-list")).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int slot = 0;
        for (String name : names) {
            var target = areaManager.getSetupTarget(gameType, name);
            int done = 0, total = 0;
            if (flow != null && target != null) {
                PrepareSession preview = new PrepareSession(manager.getPlugin(), gameType, name, target, flow);
                done = preview.configDone();
                total = preview.configTotal();
            }
            ItemStack item = PrepareKeys.item(Material.PAPER,
                    Component.text(name).color(NamedTextColor.WHITE),
                    List.of(Component.text(GuiConfig.text("map-editor.menus.area-list.copy.configuration-progress") + done + "/" + total).color(NamedTextColor.GRAY),
                            Component.text(target != null && target.config().isPrepareReady()
                                    ? GuiConfig.text("map-editor.menus.area-list.copy.published") : GuiConfig.text("map-editor.menus.area-list.copy.draft-with-unpublished-changes")).color(
                                    target != null && target.config().isPrepareReady()
                                            ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                            Component.text(GuiConfig.text("map-editor.menus.area-list.copy.left-click-to-enter-editing")).color(NamedTextColor.AQUA),
                            Component.text(GuiConfig.text("map-editor.menus.area-list.copy.right-click-to-delete-map-configuration-keep-world")).color(NamedTextColor.RED)));
            inv.setItem(slot, item);
            holder.slotToArea.put(slot, name);
            slot++;
        }

        holder.newSlot = slot;
        inv.setItem(slot, ConfiguredGui.item(MENU_PATH + ".items.new", null, Map.of(),
                PrepareKeys.item(Material.EMERALD,
                        Component.text(GuiConfig.text(MENU_PATH + ".copy.create-new-map")).color(NamedTextColor.GREEN),
                        List.of(Component.text(GuiConfig.text(MENU_PATH + ".copy.click-and-enter-the-map-name")).color(NamedTextColor.GRAY)))));

        holder.closeSlot = rows * 9 - 1;
        inv.setItem(holder.closeSlot, ConfiguredGui.item(MENU_PATH + ".items.close", null, Map.of(),
                PrepareKeys.item(Material.BARRIER,
                        Component.text(GuiConfig.text(MENU_PATH + ".copy.close")).color(NamedTextColor.RED),
                        List.of(Component.text(GuiConfig.text(MENU_PATH + ".copy.click-to-close-menu")).color(NamedTextColor.GRAY)))));

        player.openInventory(inv);
    }

    public static void handleClick(@NotNull PrepareSessionManager manager, @NotNull InventoryClickEvent event,
                                   @NotNull Player player, @NotNull Holder holder) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) return;
        int slot = event.getRawSlot();

        String areaName = holder.slotToArea.get(slot);
        if (areaName != null) {
            if (event.isRightClick()) {
                if (!areaName.equals(holder.deleteConfirmation)
                        || holder.deleteConfirmationExpiresAt < System.currentTimeMillis()) {
                    holder.deleteConfirmation = areaName;
                    holder.deleteConfirmationExpiresAt = System.currentTimeMillis() + 30_000L;
                    player.sendMessage(Component.text(GuiConfig.text("map-editor.menus.area-list.copy.delete-map-confirmation"))
                            .color(NamedTextColor.RED));
                    return;
                }
                if (manager.deleteArea(player, holder.gameType, areaName)) {
                    open(manager, player, holder.gameType);
                }
                return;
            }
            player.closeInventory();
            manager.enterSession(player, holder.gameType, areaName);
            return;
        }
        if (slot == holder.newSlot) {
            ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui.openName(player, manager, holder.gameType);
            return;
        }
        if (slot == holder.closeSlot) {
            player.closeInventory();
        }
    }
}
