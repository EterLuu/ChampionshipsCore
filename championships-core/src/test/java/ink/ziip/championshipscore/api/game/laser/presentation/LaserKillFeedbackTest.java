package ink.ziip.championshipscore.api.game.laser.presentation;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

class LaserKillFeedbackTest {
    @Test
    void singleKillsUseTheExpectedNotesAndRejectOutOfRangeCounts() {
        assertEquals(
                List.of(10, 12, 13, 15),
                List.of(
                        (int) LaserKillFeedback.noteForKills(1).getId(),
                        (int) LaserKillFeedback.noteForKills(2).getId(),
                        (int) LaserKillFeedback.noteForKills(3).getId(),
                        (int) LaserKillFeedback.noteForKills(4).getId()));
        assertThrows(IllegalArgumentException.class, () -> LaserKillFeedback.noteForKills(0));
        assertThrows(IllegalArgumentException.class, () -> LaserKillFeedback.noteForKills(5));
    }

    @Test
    void aceMelodyIsEEEThenG() {
        assertEquals(
                List.of(10, 10, 10, 13),
                LaserKillFeedback.aceMelody().stream().map(note -> (int) note.getId()).toList());
    }

    @Test
    void fifthAndEveryLaterKillPlayExactlyFourSeparatedNotes() throws Exception {
        for (int kills : List.of(5, 6, 10, 40)) {
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
    }

    @Test
    void firstFourKillsAreSingleNotesWithoutDelayedTasks() throws Exception {
        for (int kills = 1; kills <= 4; kills++) {
            try (Playback fixture = new Playback()) {
                fixture.play(kills);
                assertEquals(1, fixture.played.size());
                assertEquals(
                        LaserKillFeedback.noteForKills(kills).getId(),
                        fixture.played.getFirst().note());
                assertTrue(fixture.delayed.isEmpty());
            }
        }
    }

    @Test
    void changedRoundStopsRemainingNotes() throws Exception {
        try (Playback fixture = new Playback()) {
            fixture.play(5);
            fixture.currentRound = false;
            fixture.finish();
            assertEquals(1, fixture.played.size());
        }
    }

    @Test
    void disconnectOrSameUuidNewConnectionCannotReceiveOldNotes() throws Exception {
        for (boolean reconnect : List.of(false, true)) {
            try (Playback fixture = new Playback()) {
                fixture.play(5);
                if (reconnect) fixture.connected = proxy(Player.class, (p, m, a) -> null);
                else fixture.online = false;
                fixture.finish();
                assertEquals(1, fixture.played.size());
            }
        }
    }

    @Test
    void disabledPluginStopsRemainingNotes() throws Exception {
        try (Playback fixture = new Playback()) {
            fixture.play(5);
            fixture.enabled = false;
            fixture.finish();
            assertEquals(1, fixture.played.size());
        }
    }

    @Test
    void invalidPlaybackDoesNotEmitEvenTheFirstNote() throws Exception {
        for (String invalid : List.of("round", "offline", "connection", "disabled", "zero")) {
            try (Playback fixture = new Playback()) {
                switch (invalid) {
                    case "round" -> fixture.currentRound = false;
                    case "offline" -> fixture.online = false;
                    case "connection" -> fixture.connected = null;
                    case "disabled" -> fixture.enabled = false;
                }
                fixture.play(invalid.equals("zero") ? 0 : 5);
                assertTrue(fixture.played.isEmpty(), invalid);
                assertTrue(fixture.delayed.isEmpty(), invalid);
            }
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

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
