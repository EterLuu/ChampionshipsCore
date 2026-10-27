package ink.ziip.championshipscore.api.game.laser.presentation;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class LaserImpactTest {
    @Test
    void emitsOneSmallRedParticleAtTheImpactWithoutAudioOrHudCalls() {
        List<Object[]> emitted = new ArrayList<>();
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "equals" -> p == a[0];
                                            case "spawnParticle" -> {
                                                emitted.add(a);
                                                yield null;
                                            }
                                            default -> throw new AssertionError(m.getName());
                                        });
        Player victim =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "getWorld" -> world;
                                            case "getLocation" -> new Location(world, 2, 64, 3);
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected player feedback: "
                                                                    + m.getName());
                                        });
        LaserImpact.draw(world, new Vector(2, 65.4, 3), List.of(victim));
        assertEquals(1, emitted.size());
        Object[] args = emitted.getFirst();
        assertEquals(Particle.DUST, args[0]);
        assertEquals(2D, args[3]);
        assertEquals(65.4D, args[4]);
        assertEquals(3D, args[5]);
        assertEquals(1, args[6]);
        assertEquals(0D, args[7]);
        assertEquals(0D, args[8]);
        assertEquals(0D, args[9]);
        var dust = (Particle.DustOptions) args[11];
        assertEquals(0.5F, dust.getSize());
        assertEquals(Color.fromRGB(0xA52A2A), dust.getColor());
        emitted.clear();
        LaserImpact.draw(world, new Vector(200, 64, 3), List.of(victim));
        assertTrue(emitted.isEmpty());
    }
}
