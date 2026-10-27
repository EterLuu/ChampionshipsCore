package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

class WorkerTeammateAdmissionTest {
    @Test
    void onlyOriginalLivePlayerSnapshotsInTheSameTeamCanTeleport() throws Exception {
        var source = new PlayerSnapshot(UUID.randomUUID(), "Source", ParticipantRole.PLAYER, 1);
        var target = new PlayerSnapshot(UUID.randomUUID(), "Target", ParticipantRole.PLAYER, 1);
        var roster = new HashMap<UUID, PlayerSnapshot>();
        roster.put(source.uuid(), source);
        roster.put(target.uuid(), target);
        var session = allocate(WorkerMatchSession.class);
        set(session, "participants", roster);
        set(
                session,
                "plugin",
                Proxy.newProxyInstance(
                        Plugin.class.getClassLoader(),
                        new Class<?>[] {Plugin.class},
                        (p, m, a) -> {
                            assertEquals("isEnabled", m.getName());
                            return true;
                        }));
        var lifecycle = new MatchStateMachine();
        set(lifecycle, "state", MatchState.RUNNING);
        set(session, "lifecycle", lifecycle);
        assertTrue(allowed(session, source, target));
        for (var replacement :
                List.of(
                        new PlayerSnapshot(target.uuid(), "Target", ParticipantRole.SPECTATOR, 1),
                        new PlayerSnapshot(target.uuid(), "Target", ParticipantRole.PLAYER, 2),
                        new PlayerSnapshot(target.uuid(), "Target", ParticipantRole.PLAYER, 1))) {
            roster.put(target.uuid(), replacement);
            assertFalse(allowed(session, source, target));
            if (replacement.role() == ParticipantRole.SPECTATOR || replacement.teamId() != 1)
                assertFalse(allowed(session, source, replacement));
            if (replacement.role() == ParticipantRole.PLAYER && replacement.teamId() == 1)
                assertTrue(allowed(session, source, replacement));
        }
        roster.remove(target.uuid());
        assertFalse(allowed(session, source, target));
        roster.put(target.uuid(), target);
        roster.put(
                source.uuid(),
                new PlayerSnapshot(source.uuid(), "Source", ParticipantRole.SPECTATOR, 1));
        assertFalse(allowed(session, source, target));
        roster.put(source.uuid(), source);
        for (var state : MatchState.values())
            if (state != MatchState.RUNNING) {
                set(lifecycle, "state", state);
                assertFalse(allowed(session, source, target), state.name());
            }
    }

    private static boolean allowed(
            WorkerMatchSession session, PlayerSnapshot source, PlayerSnapshot target)
            throws Exception {
        var method =
                WorkerMatchSession.class.getDeclaredMethod(
                        "currentTeammates", PlayerSnapshot.class, PlayerSnapshot.class);
        method.setAccessible(true);
        return (boolean) method.invoke(session, source, target);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void set(Object o, String name, Object value) throws Exception {
        field(o.getClass(), name).set(o, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
