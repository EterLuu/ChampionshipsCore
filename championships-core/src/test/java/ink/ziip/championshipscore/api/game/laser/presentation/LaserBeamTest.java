package ink.ziip.championshipscore.api.game.laser.presentation;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class LaserBeamTest {
    private World world(List<Double> points) {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "equals" -> p == a[0];
                                    case "spawnParticle" -> {
                                        points.add((Double) a[5]);
                                        yield null;
                                    }
                                    default -> throw new AssertionError(m.getName());
                                });
    }

    private Player viewer(Location at) {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getWorld" -> at.getWorld();
                                    case "getLocation" -> at.clone();
                                    default -> throw new AssertionError(m.getName());
                                });
    }

    @Test
    void includesFarEndpointViewersButExcludesOtherWorldAndDistantArenas() {
        World w = world(new ArrayList<>()), other = world(new ArrayList<>());
        var near = viewer(new Location(w, 0, 64, 0));
        var endpoint = viewer(new Location(w, 0, 64, 130));
        var far = viewer(new Location(w, 100, 64, 40));
        var foreign = viewer(new Location(other, 0, 64, 0));
        var behind = viewer(new Location(w, 0, 64, -70));
        var viewers =
                LaserBeam.viewers(
                        w,
                        new Location(w, 0, 64, 0),
                        new Vector(0, 0, 1),
                        80,
                        List.of(near, endpoint, far, foreign, behind));
        assertEquals(2, viewers.size());
        assertSame(near, viewers.get(0));
        assertSame(endpoint, viewers.get(1));
    }

    @Test
    void retainsNearDetailAndReducesLongBeamCallsWithoutExtendingPastImpact() {
        List<Double> points = new ArrayList<>();
        World w = world(points);
        Location eye = new Location(w, 0, 64, 0);
        LaserBeam.draw(w, eye, new Vector(0, 0, 1), 80, List.of(viewer(eye)));
        assertEquals(53, points.size());
        assertEquals(80D, points.getLast());
        assertEquals(23D, points.get(23));
        assertEquals(24D, points.get(24));
        assertEquals(26D, points.get(25));
        points.clear();
        LaserBeam.draw(w, eye, new Vector(0, 0, 1), 2.5, List.of(viewer(eye)));
        assertEquals(List.of(0D, 1D, 2D), points);
    }

    @Test
    void emptyAudienceDoesNoParticleWork() {
        List<Double> points = new ArrayList<>();
        World w = world(points);
        LaserBeam.draw(w, new Location(w, 0, 64, 0), new Vector(0, 0, 1), 80, List.of());
        assertTrue(points.isEmpty());
    }
}
