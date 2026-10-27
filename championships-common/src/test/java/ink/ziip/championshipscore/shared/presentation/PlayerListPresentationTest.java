package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PlayerListPresentationTest {
    @Test
    void teammateLanguageAndPlayerStyleSurviveWhileAllTargetsGainPagination() {
        var values = new HashMap<>(legacyMessages());
        values.put("compass.menu_title", "team window");
        values.put("compass.teammate_hint", "teammate hint");
        var teammate = PlayerListPresentation.teammates(new BingoPresentation(values));
        assertEquals("team window", teammate.title());
        assertEquals(List.of("teammate hint"), teammate.items().get("player").lore());
        assertEquals("%formatted-player%", teammate.items().get("player").title());
        assertEquals(3, teammate.layout().pageCount(101));
        assertEquals(
                teammate,
                PlayerListPresentation.teammates(
                        new BingoPresentation(
                                teammate.attributes(PlayerListPresentation.TEAMMATE_PREFIX))));
        values.put(PlayerListPresentation.TEAMMATE_PREFIX + "version", "1");
        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerListPresentation.teammates(new BingoPresentation(values)));
    }

    @Test
    void completeSnapshotRoundTripsWithSparsePagesAndLiteralOrEmptyLore() {
        var controls = Map.of("previous", 0, "close", 1, "page", 2, "next", 3);
        var layout =
                new InventoryMenuLayout(
                        27, List.of(4, 7, 9), List.of(26), controls, Map.of(2, List.of(17, 19)));
        var items = new HashMap<String, InventoryItemTemplate>();
        PlayerListPresentation.ITEMS.forEach(
                id ->
                        items.put(
                                id,
                                new InventoryItemTemplate("PAPER", "%player%", List.of(), false)));
        items.put(
                "player",
                new InventoryItemTemplate(
                        "ENDER_PEARL", "%player%", List.of("line", "%literal%", "last"), true));
        var original = new PlayerListPresentation("window", layout, items);
        var encoded = original.attributes(PlayerListPresentation.SPECTATOR_PREFIX);
        var copy = PlayerListPresentation.spectator(new BingoPresentation(encoded));
        assertEquals(original, copy);
        assertEquals(List.of(17, 19), copy.layout().pageSlots(1, 5));
        assertEquals(4, copy.layout().index(1, 19, 5));
        assertEquals(3, copy.layout().pageCount(7));
        items.clear();
        assertEquals(6, original.items().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.items().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.layout().content().clear());
    }

    @Test
    void legacyUsesFrozenLanguageAndNewPartialSchemaCannotSilentlyFallBack() {
        var messages = new HashMap<>(legacyMessages());
        messages.put("spectator.teleport.menu_title", "original");
        messages.put("spectator.teleport.page", "%page%/%pages%");
        var legacy = PlayerListPresentation.spectator(new BingoPresentation(messages));
        assertEquals("original", legacy.title());
        assertEquals("%page%/%pages%", legacy.items().get("page").title());
        assertEquals("ENDER_PEARL", legacy.items().get("player").material());
        assertEquals(List.of("%team-label%", "%click-hint%"), legacy.items().get("player").lore());
        assertEquals(3, legacy.layout().pageCount(101));
        messages.put(PlayerListPresentation.SPECTATOR_PREFIX + "version", "1");
        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerListPresentation.spectator(new BingoPresentation(messages)));
        messages.put(PlayerListPresentation.SPECTATOR_PREFIX + "version", "2");
        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerListPresentation.spectator(new BingoPresentation(messages)));
    }

    @Test
    void invalidLayoutAndItemFieldsFailBeforeAWindowCanOpen() {
        var original = PlayerListPresentation.spectator(new BingoPresentation(legacyMessages()));
        for (var change :
                Map.of(
                                "item.player.glint",
                                "maybe",
                                "item.player.lore.size",
                                "-1",
                                "content",
                                "48",
                                "control.next",
                                "48",
                                "content-duplicate",
                                "4,4",
                                "size",
                                "10")
                        .entrySet()) {
            var values =
                    new HashMap<>(original.attributes(PlayerListPresentation.SPECTATOR_PREFIX));
            values.put(
                    PlayerListPresentation.SPECTATOR_PREFIX
                            + (change.getKey().equals("content-duplicate")
                                    ? "content"
                                    : change.getKey()),
                    change.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PlayerListPresentation.spectator(new BingoPresentation(values)),
                    change.getKey());
        }
    }

    private static Map<String, String> legacyMessages() {
        return Map.of(
                "spectator.teleport.menu_title",
                "title",
                "spectator.teleport.previous",
                "prev",
                "spectator.teleport.close",
                "close",
                "spectator.teleport.page",
                "%page%/%pages%",
                "spectator.teleport.next",
                "next");
    }
}
