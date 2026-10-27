package ink.ziip.championshipscore.api.game.bingo.config;

import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.shared.presentation.InventoryItemTemplate;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;
import ink.ziip.championshipscore.shared.presentation.SpectatorControlsPresentation;

import java.util.HashMap;
import java.util.Map;

/** Resolves Core GUI settings and the selected Bingo language before freezing a worker match. */
public final class BingoSpectatorControlsConfig {
    public static final String PATH = "games.bingo.hotbar.spectator";

    private BingoSpectatorControlsConfig() {}

    public static SpectatorControlsPresentation snapshot() {
        var language = MessageService.global();
        var items = new HashMap<String, InventoryItemTemplate>();
        var slots = new HashMap<String, Integer>();
        for (String id : SpectatorControlsPresentation.CONTROLS) {
            String path = PATH + "." + id;
            var template = GuiConfig.itemTemplate(path, null);
            var arguments = new HashMap<String, String>();
            arguments.put("%name%", language.tr("spectator." + id + ".name"));
            if (id.equals("teleport"))
                arguments.put("%hint%", language.tr("spectator.teleport.hint"));
            else {
                arguments.put("%faster%", language.tr("spectator.speed.faster"));
                arguments.put("%slower%", language.tr("spectator.speed.slower"));
            }
            items.put(
                    id,
                    new InventoryItemTemplate(
                            template.material(),
                            MessageTemplate.render(template.title(), arguments),
                            template.lore().stream()
                                    .map(line -> MessageTemplate.render(line, arguments))
                                    .toList(),
                            template.glint()));
            slots.put(id, GuiConfig.integer(path + ".slot", -1));
        }
        return new SpectatorControlsPresentation(
                GuiConfig.integer(PATH + ".card.slot", -1),
                slots,
                items,
                MessageTemplate.render(
                        GuiConfig.text(PATH + ".speed.feedback"),
                        Map.of("%message%", language.tr("spectator.speed.feedback"))));
    }
}
