package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
        Inventory inv = Bukkit.createInventory(holder, rows * 9, GuiConfig.component(MENU_PATH + ".title",
                Map.of("game", gameType)));
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
            boolean published = target != null && target.config().isPrepareReady();
            ItemStack blank = PrepareKeys.item(Material.PAPER, Component.empty(), List.of());
            ItemStack item = ConfiguredGui.item(MENU_PATH + ".items.area", published ? "published" : "draft",
                    Map.of("name", name, "done", done, "total", total), blank);
            inv.setItem(slot, item);
            holder.slotToArea.put(slot, name);
            slot++;
        }

        holder.newSlot = slot;
        inv.setItem(slot, ConfiguredGui.item(MENU_PATH + ".items.new", null, Map.of(),
                PrepareKeys.item(Material.EMERALD, Component.empty(), List.of())));

        holder.closeSlot = rows * 9 - 1;
        inv.setItem(holder.closeSlot, ConfiguredGui.item(MENU_PATH + ".items.close", null, Map.of(),
                PrepareKeys.item(Material.BARRIER, Component.empty(), List.of())));

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
                    player.sendMessage(Component.text(MessageConfig.MAP_EDITOR_AREA_DELETE_CONFIRMATION)
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
