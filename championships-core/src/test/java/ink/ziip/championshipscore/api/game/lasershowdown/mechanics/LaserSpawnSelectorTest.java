package ink.ziip.championshipscore.api.game.lasershowdown.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class LaserSpawnSelectorTest {
    private World world(boolean wall) {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "equals" -> p == a[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    case "getUID" -> new UUID(0, 1);
                                    case "rayTraceBlocks" ->
                                            wall && ((Vector) a[1]).getZ() > 0.1
                                                    ? new RayTraceResult(new Vector(0, 64, 5))
                                                    : null;
                                    default -> throw new AssertionError(m.getName());
                                });
    }

    @Test
    void prefersCoverOverAnExposedSpawnAtTheSameDistance() {
        World w = world(true);
        Location covered = new Location(w, 0, 64, 30), exposed = new Location(w, 30, 64, 0);
        var selector = new LaserSpawnSelector();
        assertEquals(
                covered,
                selector.select(
                        UUID.randomUUID(),
                        List.of(exposed, covered),
                        List.of(new Location(w, 0, 65.62, 0)),
                        0));
    }

    @Test
    void nearbyEnemyIsWorseThanDistantLineOfSight() {
        World w = world(true);
        Location near = new Location(w, 0, 64, 5), far = new Location(w, 50, 64, 0);
        assertEquals(
                far,
                new LaserSpawnSelector()
                        .select(
                                UUID.randomUUID(),
                                List.of(near, far),
                                List.of(new Location(w, 0, 65.62, 0)),
                                0));
    }

    @Test
    void avoidsDeathLocationAndRecentSpawnsWithoutMutatingConfiguration() {
        World w = world(false);
        Location a = new Location(w, 0, 64, 0), b = new Location(w, 40, 64, 0);
        var selector = new LaserSpawnSelector();
        UUID id = UUID.randomUUID();
        selector.died(id, a);
        List<Location> spawns = List.of(a, b);
        Location chosen = selector.select(id, spawns, List.of(), 0);
        assertEquals(b, chosen);
        chosen.add(100, 100, 100);
        assertEquals(new Location(w, 40, 64, 0), b);
        assertEquals(a, selector.select(UUID.randomUUID(), spawns, List.of(), 1));
        assertEquals(List.of(a, b), spawns);
    }

    @Test
    void expiredUsageAndClearedDeathHistoryDoNotBiasTheNextRound() {
        World w = world(false);
        Location a = new Location(w, 0, 64, 0), b = new Location(w, 40, 64, 0);
        var selector = new LaserSpawnSelector();
        UUID id = UUID.randomUUID();
        selector.select(id, List.of(b), List.of(), 0);
        selector.died(id, a);
        assertEquals(b, selector.select(id, List.of(a, b), List.of(), 100));
        selector.clear();
        selector.died(id, b);
        assertEquals(a, selector.select(id, List.of(a, b), List.of(), 0));
    }

    @Test
    void ignoresOtherWorldEnemiesAndSupportsASingleConfiguredSpawn() {
        World w = world(false), other = world(false);
        Location a = new Location(w, 0, 64, 0);
        assertEquals(
                a,
                new LaserSpawnSelector()
                        .select(
                                UUID.randomUUID(),
                                List.of(a),
                                List.of(new Location(other, 0, 64, 0)),
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LaserSpawnSelector().select(UUID.randomUUID(), List.of(), List.of(), 0));
    }
}
