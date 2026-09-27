package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RiptideSpectatorMovementTest {
    @Test void eliminatedPlayerCanFlyFarOutsideRaftWithoutTeleportOrCancellation() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var area = (RiptideRushArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(RiptideRushArea.class);
        UUID id = UUID.randomUUID();
        set(area, "gamePlayers", List.of(id));
        set(area, "eliminatedPlayers", Set.of(id));
        set(area, "gameStageEnum", GameStageEnum.PROGRESS);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    if (method.getName().equals("teleport")) fail("Eliminated spectator must fly freely");
                    return null;
                });
        var handler = new RiptideRushHandler(null);
        handler.setArea(area);
        var destination = new Location(null, 1000, 200, 1000);
        var event = new PlayerMoveEvent(player, new Location(null, 0, 80, 0), destination);
        assertDoesNotThrow(() -> handler.handleRoutedPlayerMoveLow(event));
        assertFalse(event.isCancelled());
        assertEquals(destination, event.getTo());
    }

    @Test void frequentAcceptedMovementDoesNotRunFallChecksOrConsumeGrace() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var area = (RiptideRushArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(RiptideRushArea.class);
        var g = RiptideTestFixtures.config().resolveGeometry();
        UUID id = UUID.randomUUID();
        set(area, "gamePlayers", List.of(id));
        set(area, "eliminatedPlayers", Set.of());
        set(area, "gameStageEnum", GameStageEnum.PROGRESS);
        set(area, "geometry", g);
        set(area, "mathRuns", Map.of(id, new RiptideMathRun(g, List.of(), g.centerAt(0))));
        var checks = new java.util.HashMap<UUID, RiptideFallCheck>();
        set(area, "fallChecks", checks);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    throw new AssertionError("Movement must not probe player fall state: " + method.getName());
                });
        var handler = new RiptideRushHandler(null);
        handler.setArea(area);
        for (int packet = 0; packet < 100; packet++) {
            var event = new PlayerMoveEvent(player, g.centerAt(0), g.centerAt(0).add(10, -1, 0));
            handler.onAcceptedMathMovement(event);
            assertFalse(event.isCancelled());
        }
        assertTrue(checks.isEmpty(), "Only the course tick may create or advance fall confirmations");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
