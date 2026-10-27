package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.api.gui.GuiMenu;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A full lore template inserts computed metric rows at its explicit records marker. */
final class DailyRecordItem {
    private DailyRecordItem() {}

    static ItemStack render(
            String path,
            GameTypeEnum game,
            String name,
            Map<String, ?> parameters,
            List<Component> records,
            boolean glint) {
        var values = new HashMap<String, Object>(parameters);
        values.put("name", name);
        if (game != null) values.put("game", game.toString());
        var spec =
                GuiConfig.item(
                        path,
                        game == null ? null : game.name(),
                        values,
                        new GuiConfig.ItemSpec(
                                -1, Material.PAPER, Component.text(name), List.of(), glint));
        return GuiMenu.item(
                spec.material(),
                spec.title(),
                lore(path, game == null ? null : game.name(), values, records),
                spec.glint());
    }

    static List<Component> lore(
            String path, String state, Map<String, ?> values, List<Component> records) {
        return expandRecords(GuiConfig.itemLoreTemplate(path, state), values, records);
    }

    static List<Component> expandRecords(
            List<String> template, Map<String, ?> values, List<Component> records) {
        return GuiItemLore.expand(template, values, "%records%", records);
    }
}
