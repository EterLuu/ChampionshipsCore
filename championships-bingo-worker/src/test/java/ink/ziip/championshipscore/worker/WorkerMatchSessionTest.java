package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

class WorkerMatchSessionTest {

    @Test
    void createsStablePortableIdsForProtocolTeams() {
        assertEquals("ccb_0", WorkerMatchSession.nativeTeamId(0));
        assertEquals("ccb_z", WorkerMatchSession.nativeTeamId(35));
        assertTrue(WorkerMatchSession.nativeTeamId(Integer.MAX_VALUE).length() <= 16);
        assertThrows(IllegalArgumentException.class, () -> WorkerMatchSession.nativeTeamId(-1));
    }

    @Test
    void runningBossBarFormatsBothRoundAndProtectionTimersWithTheStrictSixtySecondBoundary()
            throws Exception {
        var messages =
                Map.of(
                        "bingo.timer",
                        "剩余%time%",
                        "bingo.pvp-protection",
                        "PvP保护%time%",
                        "bingo.pvp-active",
                        "PvP已开启");
        var rules =
                new BingoRuntimeRules(
                        90,
                        100,
                        10,
                        130,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(messages));
        var lifecycle = new MatchStateMachine();
        for (MatchState next :
                List.of(
                        MatchState.PREPARING,
                        MatchState.READY,
                        MatchState.ROUTING,
                        MatchState.COUNTDOWN,
                        MatchState.RUNNING)) lifecycle.transitionTo(next);
        var session = allocate(WorkerMatchSession.class);
        set(session, "lifecycle", lifecycle);
        set(session, "participants", Map.of());
        var update = WorkerMatchSession.class.getDeclaredMethod("updateRunningTimer");
        update.setAccessible(true);
        for (var entry : Map.of(700, "11分40秒", 61, "1分1秒", 60, "60秒").entrySet()) {
            var manifest =
                    new MatchManifest(
                            ProtocolVersion.CURRENT,
                            UUID.randomUUID(),
                            1,
                            1,
                            "worker",
                            MatchRunMode.DAILY,
                            entry.getKey(),
                            1,
                            "config",
                            new BingoScoringRules(1, List.of(1), 0, 0, 0),
                            rules,
                            List.of(
                                    new BingoTaskSpec(
                                            0, "stone", "item", Map.of("material", "STONE"))),
                            List.of(),
                            List.of());
            set(session, "manifest", manifest);
            set(session, "startedAtMillis", System.currentTimeMillis());
            set(session, "pvpEnabled", false);
            update.invoke(session);
            BossBar bar = (BossBar) field(WorkerMatchSession.class, "timerBossBar").get(session);
            assertEquals(
                    "剩余" + entry.getValue() + " • PvP保护2分10秒",
                    PlainTextComponentSerializer.plainText().serialize(bar.name()));
            assertEquals(1F, bar.progress());
            set(session, "pvpEnabled", true);
            update.invoke(session);
            assertEquals(
                    "剩余" + entry.getValue() + " • PvP已开启",
                    PlainTextComponentSerializer.plainText().serialize(bar.name()));
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
