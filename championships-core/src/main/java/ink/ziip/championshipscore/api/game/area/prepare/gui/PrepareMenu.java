package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One editor window owns its viewer, original session and immutable configured layout. */
abstract class PrepareMenu implements MenuInventory {
    final UUID viewer;
    final PrepareSession session;
    final MenuLayout layout;
    final String path;
    final Inventory inventory;

    PrepareMenu(
            Player player,
            PrepareSession session,
            MenuId menu,
            int size,
            List<Integer> content,
            List<Integer> border,
            Map<String, Integer> controls,
            Map<String, ?> parameters) {
        viewer = player.getUniqueId();
        this.session = session;
        path = menu.path();
        var spec = GuiConfig.menu(path, size, "", content);
        layout = MenuLayout.load(path, spec.size(), content, border, controls);
        inventory =
                Bukkit.createInventory(
                        this, layout.size(), GuiConfig.component(path + ".title", parameters));
    }

    final boolean available(PrepareSessionManager manager, Player player) {
        if (!viewer.equals(player.getUniqueId())
                || !player.hasPermission("cc.admin")
                || manager.getSession(player) != session) {
            player.closeInventory();
            return false;
        }
        return true;
    }

    final ItemStack item(String id, String state, Map<String, ?> parameters) {
        return ConfiguredGui.item(
                path + ".items." + id,
                state,
                parameters,
                Material.PAPER,
                Component.empty(),
                List.of(),
                false);
    }

    final void put(String id, String state, Map<String, ?> parameters) {
        inventory.setItem(layout.control(id), item(id, state, parameters));
    }

    final void clear() {
        inventory.clear();
        ItemStack border = item("border", null, Map.of());
        for (int slot : layout.border()) inventory.setItem(slot, border);
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }
}
