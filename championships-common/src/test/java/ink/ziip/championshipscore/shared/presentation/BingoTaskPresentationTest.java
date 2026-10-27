package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import org.junit.jupiter.api.Test;

import java.util.*;

class BingoTaskPresentationTest {
    @Test
    void everyPerspectiveHidesUnclaimedTasksButRetainsCompletedAndOccupiedStates() {
        for (int bits = 0; bits < 64; bits++) {
            boolean own = (bits & 1) != 0,
                    completed = (bits & 2) != 0,
                    neutral = (bits & 4) != 0,
                    domination = (bits & 8) != 0,
                    hidden = (bits & 16) != 0,
                    locked = (bits & 32) != 0;
            var state =
                    BingoTaskPresentation.state(
                            own, completed, neutral, domination, hidden, locked);
            if (own && !neutral) assertEquals(BingoTaskPresentation.State.COMPLETED, state);
            else if (completed && !neutral && domination)
                assertEquals(BingoTaskPresentation.State.OCCUPIED, state);
            else if (!completed && (hidden || locked))
                assertTrue(
                        state == BingoTaskPresentation.State.HIDDEN
                                || state == BingoTaskPresentation.State.LOCKED);
            else assertEquals(BingoTaskPresentation.State.NORMAL, state);
            assertEquals(
                    !completed && (hidden || locked),
                    BingoTaskPresentation.masked(completed, hidden, locked));
        }
    }

    @Test
    void immutableRoundTripAndLegacyReadDoNotMutatePersistedManifestFields() {
        var wire = new BingoPresentation(Map.of("card.title", "old"));
        var before = Map.copyOf(wire.messages());
        var legacy = BingoTaskPresentation.from(wire);
        assertEquals(before, wire.messages());
        assertEquals(
                legacy, BingoTaskPresentation.from(new BingoPresentation(legacy.attributes())));
        assertThrows(UnsupportedOperationException.class, () -> legacy.states().clear());
        assertThrows(UnsupportedOperationException.class, () -> legacy.completionSummary().clear());
        var partial = new HashMap<>(legacy.attributes());
        partial.remove(BingoTaskPresentation.PREFIX + "locked.title");
        assertThrows(
                IllegalArgumentException.class,
                () -> BingoTaskPresentation.from(new BingoPresentation(partial)));
        partial.put(BingoTaskPresentation.PREFIX + "version", "99");
        assertThrows(
                IllegalArgumentException.class,
                () -> BingoTaskPresentation.from(new BingoPresentation(partial)));
    }

    @Test
    void maskedStatesCannotReintroduceSecretTaskFieldsOrIcons() {
        var original = BingoTaskPresentation.from(new BingoPresentation(Map.of()));
        for (String field : List.of("material", "title", "lore.0")) {
            var fields = new HashMap<>(original.attributes());
            String prefix = BingoTaskPresentation.PREFIX + "hidden.";
            fields.put(prefix + field, field.equals("material") ? "TASK" : "%task%");
            if (field.equals("lore.0")) fields.put(prefix + "lore.size", "1");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BingoTaskPresentation.from(new BingoPresentation(fields)));
        }
    }

    @Test
    void clocksRetainAsciiDigitsAcrossLocalesAndSupportHoursAndUnknownTime() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            var original = BingoTaskPresentation.from(new BingoPresentation(Map.of()));
            assertEquals("--:--", original.clock(-1));
            assertEquals("00:00", original.clock(0));
            assertEquals("59:59", original.clock(3599));
            assertEquals("1:00:00", original.clock(3600));
            assertEquals("2:03:04", original.clock(7384));
            assertEquals("?", DurationText.clock(-1, "?", "short", "long"));
            assertEquals(
                    "2h03m04s",
                    DurationText.clock(
                            7384, "?", "short", "%hours%h%minutes-padded%m%seconds-padded%s"));
        } finally {
            Locale.setDefault(before);
        }
    }
}
