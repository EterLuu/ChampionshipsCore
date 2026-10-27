package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Member identity and formatting used by both daily party summaries. */
final class DailyPartyPresentation {
    private DailyPartyPresentation() {}

    static ItemStack item(
            String path,
            String state,
            Map<String, ?> values,
            DailyParty party,
            String memberStyle) {
        ItemStack item =
                ConfiguredGui.item(
                        path,
                        state,
                        values,
                        Material.PLAYER_HEAD,
                        Component.empty(),
                        List.of(),
                        false);
        List<Component> members =
                party == null
                        ? List.of()
                        : party.members().stream()
                                .map(
                                        member ->
                                                GuiConfig.item(
                                                                "buttons.daily-party-member",
                                                                memberStyle
                                                                        + (party.isLeader(member)
                                                                                ? "-leader"
                                                                                : "-member"),
                                                                Map.of(
                                                                        "player",
                                                                        playerName(member)),
                                                                new GuiConfig.ItemSpec(
                                                                        -1,
                                                                        Material.PAPER,
                                                                        Component.empty(),
                                                                        List.of(),
                                                                        false))
                                                        .title())
                                .toList();
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(
                    GuiItemLore.expand(
                            GuiConfig.itemLoreTemplate(path, state), values, "%members%", members));
            item.setItemMeta(meta);
        }
        return item;
    }

    static String playerName(UUID player) {
        var online = Bukkit.getPlayer(player);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
    }
}
