package ink.ziip.championshipscore.api.game.frostbite.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class FrostbiteRoundTest {
    @Test
    void selectedPhysicalArenasRetainTheFullRosterAndSharedKillLedger() {
        var roster = teams(4);
        for (var selected : List.of(List.of(2), List.of(3, 1))) {
            for (int round = 0; round < 4; round++) {
                var game = new FrostbiteRound(roster, round, selected);
                assertEquals(16, game.seats().size());
                assertTrue(
                        game.seats().values().stream()
                                .allMatch(seat -> selected.contains(seat.arena())));
                UUID attacker = roster.get(0).get(0);
                UUID victim =
                        roster.get(1).stream()
                                .filter(id -> game.enemies(attacker, id))
                                .findFirst()
                                .orElseThrow();
                assertEquals(attacker, game.instantKill(attacker, victim, 0));
                assertEquals(1, game.kills(attacker));
                assertEquals(1, game.kills().size());
            }
        }
    }

    private List<List<UUID>> teams(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        t ->
                                java.util.stream.IntStream.range(0, 4)
                                        .mapToObj(p -> new UUID(t + 1, p + 1))
                                        .toList())
                .toList();
    }

    @Test
    void eachArenaHasExactlyOneMemberFromEveryTeamInEveryRound() {
        for (int count = 2; count <= 16; count++)
            for (int r = 0; r < 4; r++) {
                var game = new FrostbiteRound(teams(count), r);
                for (int arena = 0; arena < 4; arena++) {
                    int a = arena;
                    var seats = game.seats().values().stream().filter(s -> s.arena() == a).toList();
                    assertEquals(count, seats.size());
                    assertEquals(
                            count,
                            seats.stream().map(FrostbiteRound.Seat::team).distinct().count());
                }
            }
    }

    @Test
    void fourTeamsMeetEveryOpposingPlayerExactlyOnceAcrossFourRounds() {
        var teams = teams(4);
        Map<String, Integer> encounters = new HashMap<>();
        for (int r = 0; r < 4; r++) {
            var game = new FrostbiteRound(teams, r);
            for (var a : game.seats().keySet())
                for (var b : game.seats().keySet())
                    if (game.enemies(a, b)) encounters.merge(a + ":" + b, 1, Integer::sum);
        }
        assertEquals(16 * 12, encounters.size());
        assertTrue(encounters.values().stream().allMatch(n -> n == 1));
    }

    @Test
    void memberIterationOrderDoesNotChangeGrouping() {
        var roster = teams(4);
        var reordered =
                roster.stream()
                        .map(
                                t -> {
                                    var l = new ArrayList<>(t);
                                    Collections.reverse(l);
                                    return (List<UUID>) l;
                                })
                        .toList();
        assertEquals(
                new FrostbiteRound(roster, 2).seats(), new FrostbiteRound(reordered, 2).seats());
    }

    @Test
    void shortTeamsLeaveTheirUnusedArenaSeatsEmpty() {
        var roster = new ArrayList<>(teams(4));
        roster.set(0, roster.get(0).subList(0, 2));
        var game = new FrostbiteRound(roster, 0);
        assertEquals(14, game.seats().size());
        assertEquals(2, game.seats().values().stream().filter(seat -> seat.team() == 0).count());
    }

    @Test
    void rejectsIncompleteOversizedDuplicateAndSoloRosters() {
        assertThrows(IllegalArgumentException.class, () -> new FrostbiteRound(teams(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new FrostbiteRound(teams(17), 0));
        var incomplete = new ArrayList<>(teams(4));
        incomplete.set(0, List.of());
        assertThrows(IllegalArgumentException.class, () -> new FrostbiteRound(incomplete, 0));
        var oversized = new ArrayList<>(teams(4));
        oversized.set(
                0,
                List.of(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new FrostbiteRound(oversized, 0));
        var duplicate = new ArrayList<>(teams(4));
        duplicate.set(1, duplicate.get(0));
        assertThrows(IllegalArgumentException.class, () -> new FrostbiteRound(duplicate, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrostbiteRound(teams(4), 4));
    }

    @Test
    void freezeScoresOnlyOnExpiryAndOnlyOnce() {
        var t = teams(4);
        var game = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0);
        assertTrue(game.freeze(a, b, 20, 100));
        assertEquals(0, game.kills(a));
        assertTrue(game.expired(119).isEmpty());
        assertEquals(List.of(b), game.expired(120));
        assertEquals(a, game.die(b));
        assertNull(game.die(b));
        assertEquals(1, game.kills(a));
    }

    @Test
    void heatIsImmuneUntilExactEndTick() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0);
        g.heat(b, 60);
        assertFalse(g.freeze(a, b, 59, 100));
        assertNull(g.instantKill(a, b, 59));
        assertTrue(g.freeze(a, b, 60, 100));
    }

    @Test
    void freezeCannotBeRefreshedOrStolen() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0), c = t.get(2).get(0);
        assertTrue(g.freeze(a, b, 0, 100));
        assertFalse(g.freeze(c, b, 90, 100));
        assertEquals(new FrostbiteRound.Freeze(a, 100), g.freezeState(b));
        assertEquals(a, g.die(b));
        assertEquals(0, g.kills(c));
    }

    @Test
    void axeCanFinishFrozenTargetAndTakesAttribution() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0), c = t.get(2).get(0);
        g.freeze(a, b, 0, 100);
        assertEquals(c, g.instantKill(c, b, 1));
        assertEquals(1, g.kills(c));
        assertEquals(0, g.kills(a));
        assertNull(g.die(b));
    }

    @Test
    void rescueClearsAttributionAndDoesNotAwardPoints() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0);
        g.freeze(a, b, 0, 100);
        assertTrue(g.thaw(b));
        assertNull(g.die(b));
        assertEquals(0, g.kills(a));
        assertTrue(g.freeze(a, b, 1, 100));
    }

    @Test
    void leavingFrozenAwardsOnceAndCannotReenterSameRound() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0);
        g.freeze(a, b, 0, 100);
        assertEquals(a, g.leave(b));
        assertNull(g.leave(b));
        assertEquals(1, g.kills(a));
        assertFalse(g.active(b));
        assertFalse(g.reconnect(b));
        assertFalse(g.freeze(a, b, 101, 100));
        assertTrue(new FrostbiteRound(t, 1).active(b));
    }

    @Test
    void reconnectRestoresBothAttackDirectionsAndKeepsArenaAndKills() {
        var roster = teams(4);
        var round = new FrostbiteRound(roster, 2);
        UUID player = roster.get(0).get(0);
        UUID opponent =
                roster.get(1).stream()
                        .filter(id -> round.enemies(player, id))
                        .findFirst()
                        .orElseThrow();
        var seat = round.seats().get(player);
        assertEquals(player, round.instantKill(player, opponent, 0));

        assertNull(round.disconnect(player));
        assertFalse(round.active(player));
        assertFalse(round.freeze(player, opponent, 1, 20));
        assertFalse(round.freeze(opponent, player, 1, 20));
        assertTrue(round.reconnect(player));
        assertEquals(seat, round.seats().get(player));
        assertEquals(1, round.kills(player));
        assertTrue(round.freeze(player, opponent, 2, 20));
        round.thaw(opponent);
        assertTrue(round.freeze(opponent, player, 3, 20));
    }

    @Test
    void frozenDisconnectAwardsOnceAndReconnectHasNoStaleFreeze() {
        var roster = teams(2);
        var round = new FrostbiteRound(roster, 0);
        UUID attacker = roster.get(0).get(0), victim = roster.get(1).get(0);
        assertTrue(round.freeze(attacker, victim, 10, 100));
        assertEquals(attacker, round.disconnect(victim));
        assertNull(round.disconnect(victim));
        assertNull(round.freezeState(victim));
        assertTrue(round.expired(110).isEmpty());
        assertEquals(1, round.kills(attacker));
        assertTrue(round.reconnect(victim));
        assertTrue(round.canAct(victim));
        assertNull(round.disconnect(victim));
        assertEquals(1, round.kills(attacker));
    }

    @Test
    void repeatedReconnectPreservesHeatDeadlineAndCannotAddAnUnknownPlayer() {
        var roster = teams(2);
        var round = new FrostbiteRound(roster, 0);
        UUID player = roster.get(0).get(0);
        round.heat(player, 60);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertNull(round.disconnect(player));
            assertTrue(round.reconnect(player));
            assertTrue(round.heated(player, 59));
            assertFalse(round.heated(player, 60));
        }
        assertFalse(round.reconnect(UUID.randomUUID()));
    }

    @Test
    void crossArenaAndSameTeamAttacksNeverScore() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0);
        assertFalse(g.freeze(a, t.get(1).get(1), 0, 100));
        assertFalse(g.freeze(a, t.get(0).get(1), 0, 100));
        assertNull(g.instantKill(a, a, 0));
    }

    @Test
    void selfExplosionAndEnvironmentalDeathHaveNoInventedKiller() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0);
        g.selfFreeze(a, 0, 100);
        assertNull(g.die(a));
        assertNull(g.die(a));
        assertTrue(g.kills().isEmpty());
    }

    @Test
    void placedHazardsMayFreezeWhileTheirOwnerIsFrozen() {
        var t = teams(4);
        var g = new FrostbiteRound(t, 0);
        UUID a = t.get(0).get(0), b = t.get(1).get(0);
        g.selfFreeze(a, 0, 100);
        assertTrue(g.freeze(a, b, 1, 100));
        assertNull(g.instantKill(a, b, 2));
    }
}
