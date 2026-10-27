package ink.ziip.championshipscore.api.game.bingo.config;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.shared.presentation.BingoCardPresentation;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;
import ink.ziip.championshipscore.shared.presentation.InventoryItemTemplate;

import java.util.HashMap;
import java.util.List;

/** Resolves effective GUI inheritance once for local rendering or an immutable remote manifest. */
public final class BingoCardMenuConfig {
    private BingoCardMenuConfig() {}

    public static BingoTaskPresentation taskSnapshot() {
        String root = MenuId.BINGO_CARD.path();
        String path = root + ".task-presentation";
        var states = new HashMap<BingoTaskPresentation.State, InventoryItemTemplate>();
        for (var state : BingoTaskPresentation.State.values())
            states.put(
                    state,
                    GuiConfig.itemTemplate(
                            root + ".items.task",
                            state == BingoTaskPresentation.State.NORMAL ? null : state.key()));
        return new BingoTaskPresentation(
                states,
                GuiConfig.text(path + ".fallback-material").toUpperCase(java.util.Locale.ROOT),
                GuiConfig.text(path + ".separator"),
                GuiConfig.text(path + ".player"),
                GuiConfig.lines(path + ".completion-summary"),
                GuiConfig.text(path + ".time.unknown"),
                GuiConfig.text(path + ".time.short"),
                GuiConfig.text(path + ".time.long"));
    }

    public static BingoCardPresentation snapshot() {
        String root = MenuId.BINGO_CARD.path();
        var layouts = new HashMap<Integer, BingoCardPresentation.Layout>();
        for (int width : BingoCardPresentation.WIDTHS) {
            String path = root + ".screens.x" + width;
            layouts.put(
                    width,
                    new BingoCardPresentation.Layout(
                            GuiConfig.integer(path + ".size", width * 9),
                            GuiConfig.slots(path + ".layout.content", List.of()),
                            GuiConfig.controlSlot(path, root, "info", 0)));
        }
        return new BingoCardPresentation(
                GuiConfig.text(root + ".title"),
                layouts,
                GuiConfig.itemTemplate(root + ".items.info", null));
    }
}
