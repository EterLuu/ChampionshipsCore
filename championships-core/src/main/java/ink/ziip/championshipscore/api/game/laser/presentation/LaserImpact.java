package ink.ziip.championshipscore.api.game.laser.presentation;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** A single subtle blood speck at an accepted hit, without shooter HUD or audio cues. */
public final class LaserImpact {
    private static final Particle.DustOptions BLOOD =
            new Particle.DustOptions(Color.fromRGB(0xA52A2A), 0.5F);

    private LaserImpact() {}

    public static void draw(World world, Vector point, List<Player> viewers) {
        List<Player> nearby =
                viewers.stream()
                        .filter(player -> player.getWorld().equals(world))
                        .filter(
                                player ->
                                        player.getLocation().toVector().distanceSquared(point)
                                                <= 32 * 32)
                        .toList();
        if (nearby.isEmpty()) return;
        world.spawnParticle(
                Particle.DUST,
                nearby,
                null,
                point.getX(),
                point.getY(),
                point.getZ(),
                1,
                0,
                0,
                0,
                0,
                BLOOD,
                false);
    }
}
