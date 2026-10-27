package ink.ziip.championshipscore.platform.bukkit.gui;

import ink.ziip.championshipscore.shared.presentation.BingoCardPresentation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Shared card frame; task identities, variants and progress remain owned by the game. */
public final class BingoCardMenu {
    private BingoCardMenu() {}

    public static void open(
            Player player,
            BingoCardPresentation presentation,
            int width,
            Map<String, String> values,
            List<ItemStack> tasks) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        var layout = presentation.layout(width);
        if (tasks.size() != layout.tasks().size())
            throw new IllegalArgumentException("Card task count differs from configured layout");
        var holder = new Holder();
        holder.inventory =
                Bukkit.createInventory(
                        holder, layout.size(), InventoryItems.text(presentation.title(), values));
        holder.inventory.setItem(layout.info(), InventoryItems.render(presentation.info(), values));
        for (int i = 0; i < tasks.size(); i++)
            holder.inventory.setItem(layout.tasks().get(i), tasks.get(i));
        player.openInventory(holder.inventory);
    }

    public static boolean owns(Inventory inventory) {
        return inventory.getHolder(false) instanceof Holder;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
