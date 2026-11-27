package ink.ziip.championshipscore.api.game.hotycodydusky.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

class CodyWaveTest {
    @Test
    void freezeStopsTheSharedClockButNotHeadStartOrEnvironmentalInfection() {
        var players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var wave = new CodyWave();
        wave.start(List.of(players));
        UUID source = wave.holders().getFirst();
        UUID target = players.stream().filter(id -> !wave.holds(id)).findFirst().orElseThrow();
        for (int i = 0; i < 6; i++) assertTrue(wave.freeze());
        assertFalse(wave.freeze());
        for (int i = 0; i < 3; i++) assertFalse(wave.tick());
        assertEquals(55, wave.remaining());
        assertTrue(wave.pass(source, target, false));
        assertTrue(wave.infect(source));
        assertFalse(wave.infect(source));
        assertEquals(2, wave.holders().size());
        for (int i = 0; i < 28; i++) wave.tick();
        assertEquals(54, wave.remaining());
        wave.beginIntermission();
        assertFalse(wave.freeze());
        assertFalse(wave.infect(target));
    }

    @Test
    void smallRostersChaseInTheSelectedArenas() {
        var red = UUID.randomUUID();
        var blue = UUID.randomUUID();
        assertEquals(
                Map.of(red, 3, blue, 3),
                CodyWave.allocate(List.of(List.of(red), List.of(blue)), List.of(3, 2, 1, 0)));
    }

    @Test
    void openingScatterRunsFifteenSecondsBeforeWaveOne() {
        var players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var wave = new CodyWave();
        wave.beginScatter();
        assertTrue(wave.scattering());
        assertFalse(wave.active());
        assertFalse(wave.intermission());
        assertEquals(15, wave.remaining());
        assertTrue(wave.holders().isEmpty());
        assertFalse(wave.freeze());
        assertFalse(wave.infect(players.getFirst()));
        assertFalse(wave.isImmune());
        assertFalse(wave.isFrenzy());
        for (int i = 1; i < 15; i++) assertFalse(wave.tick());
        assertTrue(wave.tick());
        wave.start(List.of(players));
        assertTrue(wave.active());
        assertEquals(1, wave.number());
        assertEquals(55, wave.remaining());
        assertEquals(1, wave.holders().size());
    }

    @Test
    void fourTenPlayerArenasFinishTogetherWithGenerousPacing() {
        var arenas =
                IntStream.range(0, 4)
                        .mapToObj(
                                i ->
                                        new ArrayList<>(
                                                IntStream.range(0, 10)
                                                        .mapToObj(j -> UUID.randomUUID())
                                                        .toList()))
                        .toList();
        var wave = new CodyWave();
        int seconds = 0;
        wave.beginScatter();
        do {
            seconds++;
        } while (!wave.tick());
        for (int survivors : List.of(6, 4, 3, 2, 1)) {
            wave.start(arenas);
            do {
                seconds++;
            } while (!wave.tick());
            var caught = wave.holders();
            for (var arena : arenas) {
                arena.removeAll(caught);
                assertEquals(survivors, arena.size());
            }
            if (survivors > 1) {
                wave.beginIntermission();
                do {
                    seconds++;
                } while (!wave.tick());
            }
        }
        // 15s scatter + (55 + 50 + 45 + 40 + 35) waves + 4*5s intermissions = 260s
        assertEquals(260, seconds);
    }

    @Test
    void waveDurationsStepSmoothlyFrom55To30SecondFloor() {
        var players = IntStream.range(0, 10).mapToObj(i -> UUID.randomUUID()).toList();
        var wave = new CodyWave();
        for (int duration : new int[] {55, 50, 45, 40, 35, 30, 30, 30}) {
            wave.start(List.of(players));
            assertEquals(4, wave.holders().size());
            assertEquals(duration, wave.remaining());
            for (int i = 1; i < duration; i++) assertFalse(wave.tick());
            assertTrue(wave.tick());
            wave.beginIntermission();
            assertTrue(wave.holders().isEmpty());
            for (int i = 1; i < 5; i++) assertFalse(wave.tick());
            assertTrue(wave.tick());
        }
    }

    @Test
    void passesRequireImmunityToExpireAndAnEmptyTarget() {
        var players = IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID()).toList();
        var solo = UUID.randomUUID();
        var wave = new CodyWave();
        wave.start(List.of(players, List.of(solo)));
        var held = wave.holders();
        assertEquals(2, held.size());
        assertFalse(held.contains(solo));
        UUID empty = players.stream().filter(id -> !held.contains(id)).findFirst().orElseThrow();
        for (int i = 0; i < 3; i++) {
            assertTrue(wave.isImmune());
            assertEquals(3 - i, wave.immunityRemaining());
            assertFalse(wave.pass(held.getFirst(), empty, false));
            wave.tick();
        }
        assertFalse(wave.isImmune());
        assertEquals(0, wave.immunityRemaining());
        assertFalse(wave.pass(held.getFirst(), held.getLast(), false));
        assertTrue(wave.pass(held.getFirst(), empty, false));
        assertFalse(wave.pass(held.getFirst(), held.getFirst(), false));
        wave.remove(held.getLast());
        wave.remove(empty);
        assertTrue(wave.tick());
    }

    @Test
    void freezeFrenzyAndIntermissionExposeBossBarCountdownState() {
        var players = List.of(UUID.randomUUID(), UUID.randomUUID());
        var wave = new CodyWave();
        wave.start(List.of(players));
        assertEquals(3, wave.immunityRemaining());
        assertEquals(0, wave.frozenRemaining());
        assertFalse(wave.isFrenzy());
        assertTrue(wave.freeze());
        assertEquals(5, wave.frozenRemaining());
        wave.tick();
        assertEquals(2, wave.immunityRemaining());
        assertEquals(4, wave.frozenRemaining());
        for (int i = 0; i < 4; i++) wave.tick();
        assertEquals(0, wave.frozenRemaining());
        // Advance down to 10s remaining to verify frenzy activation.
        while (wave.remaining() > 11) {
            assertFalse(wave.isFrenzy());
            wave.tick();
        }
        assertFalse(wave.isFrenzy());
        wave.tick();
        assertEquals(10, wave.remaining());
        assertTrue(wave.isFrenzy());
        wave.beginIntermission();
        assertFalse(wave.isImmune());
        assertFalse(wave.isFrenzy());
        assertEquals(0, wave.immunityRemaining());
        assertEquals(0, wave.frozenRemaining());
    }

    @Test
    void tagBackLockoutBlocksInstantPingPongWhileAllowingThirdPartyPasses() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        var wave = new CodyWave();
        wave.start(List.of(List.of(a, b, c)));
        for (var id : wave.holders()) wave.remove(id);
        wave.infect(a);
        for (int i = 0; i < 3; i++) wave.tick();

        // Multi-player pass A -> B locks B -> A for 60 ticks, while B -> C is allowed immediately.
        assertTrue(wave.pass(a, b, false));
        assertEquals(CodyWave.TAGBACK_TICKS, wave.tagBackRemainingTicks(b, a));
        assertEquals(0, wave.tagBackRemainingTicks(b, c));
        assertFalse(wave.canPass(b, a, false));
        assertFalse(wave.pass(b, a, false));

        for (int i = 0; i < CodyWave.TAGBACK_TICKS - 1; i++) wave.nextServerTick();
        assertEquals(1, wave.tagBackRemainingTicks(b, a));
        assertFalse(wave.canPass(b, a, false));

        wave.nextServerTick();
        assertEquals(0, wave.tagBackRemainingTicks(b, a));
        assertTrue(wave.canPass(b, a, false));

        // Passing B -> C while lock could exist still works immediately, then C -> B is locked.
        assertTrue(wave.pass(b, c, false));
        assertFalse(wave.pass(c, b, false));
        assertTrue(wave.pass(c, a, false));

        // 1v1 duel lockout lasts 30 ticks.
        assertTrue(wave.pass(a, b, true));
        assertEquals(CodyWave.DUEL_TAGBACK_TICKS, wave.tagBackRemainingTicks(b, a));
        assertFalse(wave.pass(b, a, true));
        for (int i = 0; i < CodyWave.DUEL_TAGBACK_TICKS; i++) wave.nextServerTick();
        assertTrue(wave.pass(b, a, true));
    }

    @Test
    void fairSelectionDistributesDrawsEvenlyAcrossWaves() {
        var players = IntStream.range(0, 4).mapToObj(i -> UUID.randomUUID()).toList();
        var wave = new CodyWave();
        Map<UUID, Integer> counts = new HashMap<>();
        for (int round = 0; round < 4; round++) {
            wave.start(List.of(players));
            assertEquals(1, wave.holders().size());
            counts.merge(wave.holders().getFirst(), 1, Integer::sum);
        }
        assertEquals(4, counts.size());
        counts.values().forEach(count -> assertEquals(1, count));
    }
}
