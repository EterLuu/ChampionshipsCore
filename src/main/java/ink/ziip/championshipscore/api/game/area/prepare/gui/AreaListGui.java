package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
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
    private AreaListGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final GameTypeEnum gameType;
        private final Map<Integer, String> slotToArea = new HashMap<>();
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
        BaseAreaManager<?> areaManager = manager.getPlugin().getGameManager().getAreaManager(gameType);
        List<String> names = areaManager == null ? List.of() : areaManager.getAreaNameList();

        int needed = names.size() + 2; // areas + new + close
        int rows = Math.max(3, Math.min(6, (needed + 8) / 9));
        Holder holder = new Holder(gameType);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Component.text(gameType + " 场地列表").decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int slot = 0;
        for (String name : names) {
            BaseArea area = areaManager.getArea(name);
            int done = 0, total = 0;
            if (flow != null && area != null) {
                PrepareSession preview = new PrepareSession(manager.getPlugin(), gameType, name, area, flow);
                done = preview.configDone();
                total = preview.configTotal();
            }
            ItemStack item = PrepareKeys.item(Material.PAPER,
                    Component.text(name).color(NamedTextColor.WHITE),
                    List.of(Component.text("配置进度：" + done + "/" + total).color(NamedTextColor.GRAY),
                            Component.text("点击进入编辑").color(NamedTextColor.AQUA)));
            inv.setItem(slot, item);
            holder.slotToArea.put(slot, name);
            slot++;
        }

        holder.newSlot = slot;
        inv.setItem(slot, PrepareKeys.item(Material.EMERALD,
                Component.text("新建场地").color(NamedTextColor.GREEN),
                List.of(Component.text("点击后输入场地名").color(NamedTextColor.GRAY))));

        holder.closeSlot = rows * 9 - 1;
        inv.setItem(holder.closeSlot, PrepareKeys.item(Material.BARRIER,
                Component.text("关闭").color(NamedTextColor.RED),
                List.of(Component.text("点击关闭菜单").color(NamedTextColor.GRAY))));

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
