package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerObjectivesTest {
    @Test
    void completedCellsCanBeExcludedBeforeInventoryScanning() {
        WorkerObjectives objectives = new WorkerObjectives(java.util.List.of(
                new BingoTaskSpec(3, "jumps", "statistic", Map.of("statistic", "JUMP", "target", "2"))));
        Player player = playerWithStatistic(2);

        assertEquals(java.util.List.of(3), objectives.matching(player, cell -> true));
        assertTrue(objectives.matching(player, cell -> cell != 3).isEmpty());
    }

    private static Player playerWithStatistic(int value) {
        UUID playerId = UUID.randomUUID();
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getStatistic" -> value;
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> invocation.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new IllegalArgumentException("Unsupported primitive " + type);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
