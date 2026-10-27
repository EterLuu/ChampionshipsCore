package ink.ziip.championshipscore.api.gui;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Expand a dynamic row marker before performing the single parameter substitution. */
public final class GuiItemLore {
    private GuiItemLore() {}

    public static List<Component> expand(
            List<String> template, Map<String, ?> values, String marker, List<Component> rows) {
        var tokens = new HashMap<String, String>();
        values.forEach(
                (key, value) ->
                        tokens.put(
                                "%" + key + "%",
                                value instanceof Component c
                                        ? LegacyText.serialize(c)
                                        : String.valueOf(value)));
        List<Component> lore = new ArrayList<>();
        for (String line : template) {
            if (line.equals(marker)) lore.addAll(rows);
            else lore.add(LegacyText.component(MessageTemplate.render(line, tokens)));
        }
        return List.copyOf(lore);
    }
}
