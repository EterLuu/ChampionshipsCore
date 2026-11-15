package ink.ziip.championshipscore.api.game.laser.presentation;

import static ink.ziip.championshipscore.support.TestObjects.proxy;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

class LaserKillFeedbackTest {
    @Test
    void noteForKillsRejectsCountsOutsideOneToFour() {
        assertThrows(IllegalArgumentException.class, () -> LaserKillFeedback.noteForKills(0));
        assertThrows(IllegalArgumentException.class, () -> LaserKillFeedback.noteForKills(5));
    }

    @ParameterizedTest
    @CsvSource({"1,10", "2,12", "3,13", "4,15"})
    void firstFourKillsPlayOneImmediateNoteWithoutDelayedTasks(int kills, int note)
            throws Exception {
        try (Playback fixture = new Playback()) {
            fixture.play(kills);
            assertEquals(List.of(new Played(0, note)), fixture.played);
            assertTrue(fixture.delayed.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 10, 40})
    void fifthAndEveryLaterKillPlayTheSeparatedAceMelody(int kills) throws Exception {
        try (Playback fixture = new Playback()) {
            fixture.play(kills);
            assertEquals(List.of(new Played(0, 10)), fixture.played);
            assertEquals(List.of(3L, 6L, 9L), fixture.delayed.keySet().stream().toList());
            fixture.finish();
            assertEquals(
                    List.of(
                            new Played(0, 10), new Played(3, 10),
                            new Played(6, 10), new Played(9, 13)),
                    fixture.played);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"round", "offline", "reconnect", "disabled"})
    void invalidationDuringPlaybackStopsRemainingNotes(String invalidation) throws Exception {
        try (Playback fixture = new Playback()) {
            fixture.play(5);
            // "reconnect" is a new connection with the same UUID; it must not hear old notes.
            switch (invalidation) {
                case "round" -> fixture.currentRound = false;
                case "offline" -> fixture.online = false;
                case "reconnect" -> fixture.connected = proxy(Player.class, (p, m, a) -> null);
                case "disabled" -> fixture.enabled = false;
            }
            fixture.finish();
            assertEquals(List.of(new Played(0, 10)), fixture.played);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"round", "offline", "connection", "disabled", "zero"})
    void invalidPlaybackDoesNotEmitEvenTheFirstNote(String invalid) throws Exception {
        try (Playback fixture = new Playback()) {
            switch (invalid) {
                case "round" -> fixture.currentRound = false;
                case "offline" -> fixture.online = false;
                case "connection" -> fixture.connected = null;
                case "disabled" -> fixture.enabled = false;
            }
            fixture.play(invalid.equals("zero") ? 0 : 5);
            assertTrue(fixture.played.isEmpty());
            assertTrue(fixture.delayed.isEmpty());
        }
    }

    private record Played(long tick, int note) {}

    private static final class Playback implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final List<Played> played = new ArrayList<>();
        final NavigableMap<Long, Runnable> delayed = new TreeMap<>();
        final Field serverField = Bukkit.class.getDeclaredField("server");
        final Object previousServer;
        boolean online = true, enabled = true, currentRound = true;
        long tick;
        final Player player;
        Player connected;
        final Plugin plugin;

        Playback() throws Exception {
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            EntityScheduler scheduler =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                assertEquals(
                                        "runDelayed",
                                        m.getName(),
                                        "notes use the entity scheduler on Paper and Folia");
                                long delay = (long) a[3];
                                @SuppressWarnings("unchecked")
                                Consumer<ScheduledTask> callback = (Consumer<ScheduledTask>) a[1];
                                delayed.put(delay, () -> callback.accept(null));
                                return null;
                            });
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "isOnline" -> online;
                                        case "getScheduler" -> scheduler;
                                        default -> throw new AssertionError(m.getName());
                                    });
            connected = player;
            plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isEnabled" -> enabled;
                                        default -> throw new AssertionError(m.getName());
                                    });
            serverField.set(
                    null,
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                assertEquals("getPlayer", m.getName());
                                assertEquals(id, a[0]);
                                return connected;
                            }));
        }

        void play(int kills) {
            LaserKillFeedback.play(
                    player,
                    kills,
                    plugin,
                    () -> currentRound,
                    note -> played.add(new Played(tick, note.getId())));
        }

        void finish() {
            delayed.forEach(
                    (delay, run) -> {
                        tick = delay;
                        run.run();
                    });
        }

        @Override
        public void close() throws Exception {
            serverField.set(null, previousServer);
        }
    }
}
