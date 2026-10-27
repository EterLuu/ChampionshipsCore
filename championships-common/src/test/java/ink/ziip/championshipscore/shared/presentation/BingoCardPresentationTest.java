package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import org.junit.jupiter.api.Test;

import java.util.*;

class BingoCardPresentationTest {
    @Test
    void legacyManifestsKeepOriginalLayoutsAndHashInputsWithoutWritingDefaults() {
        var messages = new HashMap<>(Map.of("card.title", "Legacy", "card.win_hint", "Hint"));
        var presentation = new BingoPresentation(messages);
        var before = Map.copyOf(presentation.messages());
        var card = BingoCardPresentation.from(presentation);
        assertEquals("%title%", card.title());
        assertEquals(List.of(3, 4, 5, 12, 13, 14, 21, 22, 23), card.layout(3).tasks());
        assertEquals(42, card.layout(5).tasks().getLast());
        assertEquals(before, presentation.messages());
        assertThrows(UnsupportedOperationException.class, () -> card.layout(3).tasks().add(26));
    }

    @Test
    void roundTripPreservesEveryFieldAndExplicitEmptyLore() {
        var legacy = BingoCardPresentation.from(new BingoPresentation(Map.of()));
        var layouts = new HashMap<>(legacy.layouts());
        layouts.put(
                3,
                new BingoCardPresentation.Layout(
                        54, List.of(40, 38, 36, 31, 29, 27, 22, 20, 18), 53));
        var source =
                new BingoCardPresentation(
                        "%title% $\\",
                        layouts,
                        new InventoryItemTemplate("DIAMOND", "%card-title%", List.of(), true));
        var frozen = new BingoPresentation(source.attributes());
        layouts.clear();
        assertEquals(source, BingoCardPresentation.from(frozen));
        assertTrue(BingoCardPresentation.from(frozen).info().lore().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> source.layouts().clear());
    }

    @Test
    void partialAndUnknownSchemasNeverFallBackToLegacySilently() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BingoCardPresentation.from(
                                new BingoPresentation(Map.of("gui.card.title", "Partial"))));
        var fields =
                new HashMap<>(
                        BingoCardPresentation.from(new BingoPresentation(Map.of())).attributes());
        fields.put("gui.card.version", "99");
        assertThrows(
                IllegalArgumentException.class,
                () -> BingoCardPresentation.from(new BingoPresentation(fields)));
        fields.put("gui.card.version", "1");
        fields.remove("gui.card.x4.tasks");
        assertThrows(
                IllegalArgumentException.class,
                () -> BingoCardPresentation.from(new BingoPresentation(fields)));
    }

    @Test
    void slotContractsRejectCollisionsOverflowDuplicatesAndMissingCells() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BingoCardPresentation.Layout(27, List.of(0, 1), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BingoCardPresentation.Layout(27, List.of(1, 1), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BingoCardPresentation.Layout(27, List.of(27), 0));
        var old = BingoCardPresentation.from(new BingoPresentation(Map.of()));
        var layouts = new HashMap<>(old.layouts());
        layouts.put(3, new BingoCardPresentation.Layout(27, List.of(1, 2), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BingoCardPresentation("", layouts, old.info()));
    }
}
