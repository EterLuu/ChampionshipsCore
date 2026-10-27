package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A private selection view; the game supplies current run, role and life admission checks. */
public final class LaserGunMenu implements MenuInventory {
    private static final String PATH = MenuId.LASER_GUN_SELECTION.path();
    private final Object owner;
    private final Player viewer;
    private final BooleanSupplier current;
    private final Consumer<LaserGun> select;
    private final Runnable close;
    private final MenuLayout layout;
    private final Map<Integer, LaserGun> gunsBySlot = new HashMap<>();
    private Inventory inventory;
    private Plugin plugin;
    private NamespacedKey itemKey;
    private LaserGun equipped;
    private int page;
    private boolean selected;
    private boolean closed;

    private LaserGunMenu(
            Object owner,
            Player viewer,
            BooleanSupplier current,
            Consumer<LaserGun> select,
            Runnable close) {
        this.owner = owner;
        this.viewer = viewer;
        this.current = current;
        this.select = select;
        this.close = close;
        layout =
                MenuLayout.load(
                        PATH,
                        GuiConfig.menu(PATH, 27, Component.empty(), List.of()).size(),
                        MenuLayout.range(10, 15),
                        List.of(),
                        Map.of("previous", 18, "page", 22, "next", 26));
    }

    public static void open(
            Plugin plugin,
            NamespacedKey itemKey,
            Object owner,
            Player viewer,
            LaserGun selected,
            BooleanSupplier current,
            Consumer<LaserGun> select,
            Runnable close) {
        if (!connected(viewer) || !current.getAsBoolean() || isOpen(owner, viewer)) return;
        LaserGunMenu menu = new LaserGunMenu(owner, viewer, current, select, close);
        menu.plugin = plugin;
        menu.itemKey = itemKey;
        menu.equipped = selected;
        menu.inventory =
                Bukkit.createInventory(
                        menu,
                        menu.layout.size(),
                        GuiConfig.component(
                                PATH + ".title",
                                Map.of(
                                        "title",
                                        LaserGunItems.text(
                                                MessageConfig.LASER_BOX_GUN_SELECTION_TITLE))));
        menu.render();
        viewer.openInventory(menu.inventory);
        if (!menu.ownsView()) {
            menu.finishClose();
            return;
        }
        viewer.sendActionBar(LegacyText.component(MessageConfig.LASER_BOX_GUN_SELECTION_SUBTITLE));
    }

    private void render() {
        inventory.clear();
        gunsBySlot.clear();
        LaserGun[] guns = LaserGun.values();
        page = layout.clampPage(page, guns.length);
        for (int slot : layout.border()) inventory.setItem(slot, item("border", Map.of()));
        var slots = layout.pageSlots(page, guns.length);
        for (int i = 0; i < slots.size(); i++) {
            LaserGun gun = guns[page * layout.content().size() + i];
            int slot = slots.get(i);
            gunsBySlot.put(slot, gun);
            inventory.setItem(
                    slot,
                    ConfiguredGui.item(
                            PATH + ".items.guns." + gun.id(),
                            gun == equipped ? "selected" : "idle",
                            LaserGunItems.presentation(gun),
                            LaserGunItems.gunItem(plugin, itemKey, gun, equipped)));
        }
        int pages = layout.pageCount(guns.length);
        if (page > 0) put("previous", Map.of());
        if (page + 1 < pages) put("next", Map.of());
        if (pages > 1) put("page", Map.of("page", page + 1, "pages", pages, "count", guns.length));
    }

    private void put(String control, Map<String, ?> values) {
        inventory.setItem(layout.control(control), item(control, values));
    }

    private ItemStack item(String id, Map<String, ?> values) {
        return ConfiguredGui.item(
                PATH + ".items." + id, values, Material.PAPER, Component.empty(), List.of(), false);
    }

    public static boolean isOpen(Object owner, Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof LaserGunMenu menu
                && menu.owner == owner
                && menu.viewer == player
                && menu.ownsView()
                && menu.current.getAsBoolean();
    }

    /** Closing an expired view must still release its game's menu grace. */
    public static void closeOpen(Object owner, Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof LaserGunMenu menu
                && menu.owner == owner
                && menu.viewer == player
                && menu.ownsView()) player.closeInventory();
    }

    public static boolean handleClick(Object owner, InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LaserGunMenu menu)
                || menu.owner != owner) return false;
        event.setCancelled(true);
        if (event.getWhoClicked() != menu.viewer
                || menu.selected
                || event.getView().getTopInventory() != menu.inventory
                || !menu.ownsView()
                || !menu.current.getAsBoolean()) return true;
        int slot = event.getRawSlot();
        if (slot == menu.layout.control("previous") && menu.page > 0) {
            menu.page--;
            menu.render();
            return true;
        }
        if (slot == menu.layout.control("next")
                && menu.page + 1 < menu.layout.pageCount(LaserGun.values().length)) {
            menu.page++;
            menu.render();
            return true;
        }
        LaserGun gun = menu.gunsBySlot.get(slot);
        if (gun == null) return true;
        menu.selected = true;
        menu.select.accept(gun);
        boolean confirmed = menu.current.getAsBoolean();
        if (menu.ownsView()) menu.viewer.closeInventory();
        menu.finishClose();
        if (confirmed && connected(menu.viewer))
            menu.viewer.sendMessage(
                    LegacyText.component(
                            MessageTemplate.render(
                                    LaserGunItems.text(
                                            MessageConfig.LASER_BOX_GUN_SELECTION_CHOSEN),
                                    Map.of("%gun%", LaserGunItems.gunName(gun)))));
        return true;
    }

    public static void handleClose(Object owner, InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LaserGunMenu menu)
                || menu.owner != owner
                || event.getPlayer() != menu.viewer
                || event.getInventory() != menu.inventory) return;
        menu.finishClose();
    }

    private static boolean connected(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private boolean ownsView() {
        return !closed
                && connected(viewer)
                && viewer.getOpenInventory().getTopInventory() == inventory;
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        close.run();
    }

    public static boolean handleDrag(Object owner, InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LaserGunMenu menu)
                || menu.owner != owner) return false;
        event.setCancelled(true);
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
