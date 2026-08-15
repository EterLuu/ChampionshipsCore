package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and opens the read-only bingo card as a chest GUI. The task grid is laid out via
 * {@link CardSize#getCardInventorySlot(int)}; clicks are cancelled by {@link CardMenuListener}.
 */
public final class CardView {
    private static final String MENU_PATH = MenuId.BINGO_CARD.path();
    private CardView() {
    }

    public static void open(Player player, BingoCard card, CardDisplayInfo displayInfo, Component title, String viewerTeamId) {
        CardSize size = card.size;
        CardInventoryHolder holder = new CardInventoryHolder();
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, size.size * 9, title,
                java.util.stream.IntStream.range(0, size.size * 9).boxed().toList());
        Inventory inv = Bukkit.createInventory(holder, menu.size(), menu.title());
        holder.setInventory(inv);

        // Info item in the top-left corner.
        ItemStack info = new ItemStack(Material.MAP);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            var msg = MessageService.global();
            meta.displayName(msg.component("card.title"));
            List<Component> lore = new ArrayList<>();
            lore.add(msg.component("card.win_hint"));
            meta.lore(lore);
            info.setItemMeta(meta);
        }
        inv.setItem(ConfiguredGui.slot(MENU_PATH + ".items.info", 0),
                ConfiguredGui.item(MENU_PATH + ".items.info", null, Map.of(), info));

        List<GameTask> tasks = card.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            inv.setItem(size.getCardInventorySlot(i), tasks.get(i).toItem(displayInfo, viewerTeamId));
        }

        player.openInventory(inv);
    }
}
