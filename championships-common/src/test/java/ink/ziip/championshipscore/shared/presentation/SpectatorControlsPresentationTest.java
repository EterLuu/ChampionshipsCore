package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SpectatorControlsPresentationTest {
    @Test
    void fullTemplatesRoundTripAndFreezeSlotsLoreAndExplicitEmptyFeedback() {
        var slots = new HashMap<>(Map.of("teleport", 8, "speed", 3));
        var items =
                new HashMap<>(
                        Map.of(
                                "teleport",
                                        new InventoryItemTemplate(
                                                "ENDER_PEARL", "", List.of(), true),
                                "speed",
                                        new InventoryItemTemplate(
                                                "PAPER",
                                                "%speed%",
                                                List.of("first", "%speed%", "last"),
                                                false)));
        var frozen = new SpectatorControlsPresentation(5, slots, items, "");
        var encoded = frozen.attributes();
        assertEquals(frozen, SpectatorControlsPresentation.from(new BingoPresentation(encoded)));
        slots.clear();
        items.clear();
        assertEquals(8, frozen.slots().get("teleport"));
        assertEquals(3, frozen.items().get("speed").lore().size());
        assertThrows(UnsupportedOperationException.class, () -> frozen.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> encoded.clear());
    }

    @Test
    void rejectsPartialSchemasBadSlotsAndIncompleteItemsWithoutLegacyFallback() {
        var valid =
                SpectatorControlsPresentation.from(new BingoPresentation(legacy())).attributes();
        for (var change :
                Map.of(
                                "version",
                                "2",
                                "card-slot",
                                "0",
                                "speed.slot",
                                "9",
                                "teleport.slot",
                                "-1",
                                "speed.glint",
                                "yes",
                                "speed.lore.size",
                                "-1")
                        .entrySet()) {
            var values = new HashMap<>(valid);
            values.put(SpectatorControlsPresentation.PREFIX + change.getKey(), change.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpectatorControlsPresentation.from(new BingoPresentation(values)),
                    change.getKey());
        }
        for (String key : valid.keySet()) {
            var values = new HashMap<>(valid);
            values.remove(key);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpectatorControlsPresentation.from(new BingoPresentation(values)),
                    key);
        }
        var partial = new HashMap<>(legacy());
        partial.put(SpectatorControlsPresentation.PREFIX + "speed.title", "partial");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpectatorControlsPresentation.from(new BingoPresentation(partial)));
    }

    @Test
    void historicalLanguageIsReadOnlyAndParameterLikeTextIsNotRecursivelyExpanded() {
        var presentation = new BingoPresentation(legacy());
        var original = new HashMap<>(presentation.messages());
        var controls = SpectatorControlsPresentation.from(presentation);
        assertEquals(1, controls.cardSlot());
        assertEquals(Map.of("teleport", 0, "speed", 7), controls.slots());
        assertEquals("&d自定义 %speed.name%", controls.items().get("teleport").title());
        assertEquals("速度 %speed%", controls.speedFeedback());
        assertEquals(List.of("加速", "减速"), controls.items().get("speed").lore());
        assertEquals(original, presentation.messages());
        assertEquals("&a&l%value%", SpectatorControlsPresentation.legacyStyle("teleport.title"));
    }

    private static Map<String, String> legacy() {
        return Map.of(
                "spectator.teleport.name",
                "&d自定义 %speed.name%",
                "spectator.teleport.hint",
                "点击",
                "spectator.speed.name",
                "飞行 %speed%",
                "spectator.speed.faster",
                "加速",
                "spectator.speed.slower",
                "减速",
                "spectator.speed.feedback",
                "速度 %speed%");
    }
}
