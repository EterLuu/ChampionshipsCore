package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class TestRosterTest {
    private static List<String> botNames() {
        return java.util.stream.IntStream.range(0, TestRoster.TOTAL_SEATS)
                .mapToObj(index -> "testbot" + index)
                .toList();
    }

    @Test
    void createsExactlySixteenTeamsWithFourSeats() {
        TestRoster roster = TestRoster.fromBotUsernames(botNames());

        assertEquals(TestRoster.TOTAL_SEATS, roster.snapshot().size());
        assertEquals(4, roster.snapshot().stream().filter(seat -> seat.teamNumber() == 16).count());
        assertFalse(roster.isComplete());
        assertFalse(roster.isFrozen());
    }

    @Test
    void bindsObservedServerUuidAndRejectsDuplicates() {
        TestRoster roster = TestRoster.fromBotUsernames(botNames());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        roster.bindBot("TESTBOT0", first);
        assertEquals(List.of(first), roster.teamMembers(1).subList(0, 1));
        assertThrows(IllegalStateException.class, () -> roster.bindBot("testbot1", first));
        roster.bindBot("testbot1", second);
        assertEquals(2, roster.allMembers().size());
    }

    @Test
    void developerReplacesOneBotSeatBeforeFreeze() {
        TestRoster roster = TestRoster.fromBotUsernames(botNames());
        UUID developer = UUID.randomUUID();
        assertEquals("testbot8", roster.replaceWithDeveloper(3, developer, "Developer"));

        assertEquals(developer, roster.teamMembers(3).getFirst());
        for (int index = 0; index < TestRoster.TOTAL_SEATS; index++) {
            if (index != 8) roster.bindBot("testbot" + index, UUID.randomUUID());
        }
        assertTrue(roster.isComplete());
        roster.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> roster.replaceWithDeveloper(3, UUID.randomUUID(), "Second"));
        assertThrows(
                IllegalStateException.class, () -> roster.bindBot("testbot8", UUID.randomUUID()));
    }

    @Test
    void developerCanReleaseTheSeatBeforeFreeze() {
        TestRoster roster = TestRoster.fromBotUsernames(botNames());
        UUID developer = UUID.randomUUID();
        roster.replaceWithDeveloper(3, developer, "Developer");

        assertEquals("testbot8", roster.removeDeveloper(3, developer));
        assertNull(roster.snapshot().get(8).occupantUuid());
        roster.bindBot("testbot8", UUID.randomUUID());
    }

    @Test
    void frozenRosterAllowsOnlyTheSameBotUuidToReconnect() {
        TestRoster roster = TestRoster.fromBotUsernames(botNames());
        UUID first = UUID.randomUUID();
        roster.bindBot("testbot0", first);
        for (int index = 1; index < TestRoster.TOTAL_SEATS; index++)
            roster.bindBot("testbot" + index, UUID.randomUUID());
        roster.freeze();

        roster.bindBot("testbot0", first);
        assertThrows(
                IllegalStateException.class, () -> roster.bindBot("testbot0", UUID.randomUUID()));
    }

    @Test
    void freezeRequiresAllSeatsAndValidatesTeamNumber() {
        TestRoster roster = TestRoster.fromBotUsernames(botNames());

        assertThrows(IllegalStateException.class, roster::freeze);
        assertThrows(IllegalArgumentException.class, () -> roster.teamMembers(0));
        assertThrows(IllegalArgumentException.class, () -> roster.teamMembers(17));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestRoster.fromBotUsernames(botNames().subList(0, 63)));
    }

    @Test
    void rejectsDuplicateNamesIgnoringCase() {
        List<String> names = new java.util.ArrayList<>(botNames());
        names.set(1, names.getFirst().toUpperCase());

        assertThrows(IllegalArgumentException.class, () -> TestRoster.fromBotUsernames(names));
    }
}
