package ink.ziip.championshipscore.integration.bingo;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public class BingoTeleportationMenu {

    public static void openBingoTeleportationMenu(Player openMenuPlayer, List<UUID> uuids) {
        ChestGui gui = new ChestGui(5, MessageConfig.BINGO_TELEPORTATION_MENU);

        gui.setOnGlobalClick(event -> event.setCancelled(true));

        OutlinePane navigationPane = new OutlinePane(3, 1, 4, 1);

        for (UUID uuid : uuids) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) playerHead.getItemMeta();
            if (headMeta != null) {
                headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                headMeta.setDisplayName(Bukkit.getOfflinePlayer(uuid).getName());
            }
            playerHead.setItemMeta(headMeta);

            navigationPane.addItem(new GuiItem(playerHead, event -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    openMenuPlayer.teleport(player.getLocation());
                }
            }));
        }

        gui.addPane(navigationPane);
        gui.show(openMenuPlayer);
    }
}
