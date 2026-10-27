package ink.ziip.championshipscore.api.game.laser.presentation;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

/** Native laser trail rendered only to the current game's chosen viewers in the shot's world. */
public final class LaserBeam {
    private static final Particle.DustOptions DUST =
            new Particle.DustOptions(Color.fromRGB(0xE8FFFF), 0.35F);

    private LaserBeam() {}

    /** Capture the nearby audience once per shot, including viewers near its far endpoint. */
    public static List<Player> viewers(
            World world,
            Location eye,
            Vector direction,
            double range,
            Collection<Player> candidates) {
        Vector start = eye.toVector();
        Vector forward = direction.clone().normalize();
        return candidates.stream()
                .filter(player -> player.getWorld().equals(world))
                .filter(
                        player -> {
                            Vector offset = player.getLocation().toVector().subtract(start);
                            double along = Math.clamp(offset.dot(forward), 0.0D, range);
                            return offset.subtract(forward.clone().multiply(along)).lengthSquared()
                                    <= 64 * 64;
                        })
                .toList();
    }

    public static void draw(
            World world, Location eye, Vector direction, double distance, List<Player> viewers) {
        if (viewers.isEmpty()) return;
        for (double d = 0; d <= distance; d += d < 24.0D ? 1.0D : 2.0D)
            world.spawnParticle(
                    Particle.DUST,
                    viewers,
                    null,
                    eye.getX() + direction.getX() * d,
                    eye.getY() + direction.getY() * d,
                    eye.getZ() + direction.getZ() * d,
                    1,
                    0,
                    0,
                    0,
                    0,
                    DUST,
                    false);
    }
}
