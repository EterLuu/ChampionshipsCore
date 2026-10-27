package ink.ziip.championshipscore.api.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.shared.presentation.PlayerListPresentation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Core GUI inheritance resolved once for local windows and remote manifests. */
public final class PlayerListMenuConfig {
    private PlayerListMenuConfig() {}

    public static PlayerListPresentation spectatorPlayers() {
        return snapshot(MenuId.SPECTATOR_PLAYER_TELEPORT.path(), "player");
    }

    public static PlayerListPresentation teammates() {
        return snapshot(MenuId.BINGO_TEAMMATE_TELEPORT.path(), "teammate");
    }

    private static PlayerListPresentation snapshot(String path, String entry) {
        var layout =
                MenuLayout.load(
                        path,
                        GuiConfig.integer(path + ".size", 54),
                        MenuLayout.range(0, 45),
                        List.of(),
                        Map.of("previous", 45, "close", 48, "page", 49, "next", 53));
        var items =
                new HashMap<
                        String,
                        ink.ziip.championshipscore.shared.presentation.InventoryItemTemplate>();
        PlayerListPresentation.ITEMS.forEach(
                id ->
                        items.put(
                                id,
                                GuiConfig.itemTemplate(
                                        path + ".items." + (id.equals("player") ? entry : id),
                                        null)));
        return new PlayerListPresentation(
                GuiConfig.text(path + ".title"), layout.snapshot(), items);
    }
}
