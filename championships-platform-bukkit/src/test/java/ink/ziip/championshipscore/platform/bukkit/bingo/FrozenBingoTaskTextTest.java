package ink.ziip.championshipscore.platform.bukkit.bingo;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FrozenBingoTaskTextTest {
    @Test
    void roundTripRetainsTranslationsStylesEventsLiteralTokensAndEmptyLore() {
        var name =
                Component.translatable("item.minecraft.diamond", Component.text("%subject%"))
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("%name%")))
                        .clickEvent(ClickEvent.suggestCommand("/example %name%"));
        for (var lore :
                List.of(
                        List.<Component>of(),
                        List.<Component>of(Component.text("literal {0} %name%")))) {
            var text = new FrozenBingoTaskText(name, lore);
            assertEquals(text, FrozenBingoTaskText.from(text.attributes()).orElseThrow());
        }
    }

    @Test
    void rejectsPartialUnknownAndMalformedMarkedSnapshots() {
        var valid =
                new FrozenBingoTaskText(Component.text("name"), List.of(Component.text("line")))
                        .attributes();
        for (String key : List.of("display.name", "display.lore-size", "display.lore.0")) {
            var incomplete = new HashMap<>(valid);
            incomplete.remove(key);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FrozenBingoTaskText.from(incomplete),
                    key);
        }
        for (var mutation :
                List.of(
                        Map.entry("display.text-version", "2"),
                        Map.entry("display.name", "{"),
                        Map.entry("display.lore.0", "{"),
                        Map.entry("display.lore-size", "NaN"),
                        Map.entry("display.lore-size", "-1"),
                        Map.entry("display.lore-size", "2147483647"))) {
            var invalid = new HashMap<>(valid);
            invalid.put(mutation.getKey(), mutation.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FrozenBingoTaskText.from(invalid),
                    mutation.toString());
        }
    }

    @Test
    void unmarkedHistoricalFieldsRemainUnchangedForCallerCompatibility() {
        var old = Map.of("display.name", "{", "display.lore-size", "garbage");
        var original = Map.copyOf(old);
        assertTrue(FrozenBingoTaskText.from(old).isEmpty());
        assertEquals(original, old);
    }

    @Test
    void snapshotCopiesLoreAndCannotBeMutatedByItsCaller() {
        var lore = new ArrayList<Component>(List.of(Component.text("original")));
        var snapshot = new FrozenBingoTaskText(Component.text("name"), lore);
        lore.clear();
        assertEquals(List.of(Component.text("original")), snapshot.lore());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.lore().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.attributes().clear());
        assertThrows(NullPointerException.class, () -> new FrozenBingoTaskText(null, List.of()));
    }
}
