package ink.ziip.championshipscore.api.gui;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class GuiItemLoreTest {
    @Test
    void dynamicRowsAndValuesRemainLiteralAndExplicitEmptyLoreStaysEmpty() {
        var values = Map.of("name", "Literal %rows% $\\", "row", "second");
        List<Component> rows = List.of(Component.text("first %name%"), Component.text("second"));
        assertEquals(
                List.of("before", "first %name%", "second", "Literal %rows% $\\", "after"),
                GuiItemLore.expand(
                                List.of("before", "%rows%", "%name%", "after"),
                                values,
                                "%rows%",
                                rows)
                        .stream()
                        .map(PlainTextComponentSerializer.plainText()::serialize)
                        .toList());
        assertTrue(GuiItemLore.expand(List.of(), values, "%rows%", rows).isEmpty());
    }
}
