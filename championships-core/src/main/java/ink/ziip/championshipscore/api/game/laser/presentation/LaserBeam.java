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

    public static void draw(
            World world,
            Location eye,
            Vector direction,
            double distance,
            Collection<Player> viewers) {
        List<Player> recipients =
                viewers.stream().filter(player -> player.getWorld().equals(world)).toList();
        for (double d = 0; d <= distance; d += 1.0D)
            world.spawnParticle(
                    Particle.DUST,
                    recipients,
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
