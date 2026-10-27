package ink.ziip.championshipscore.api.game.lasershowdown.mechanics;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Scores configured spawns on the combat thread; never stores live player references. */
public final class LaserSpawnSelector {
    private final Map<Location, Integer> recent = new HashMap<>();
    private final Map<UUID, Location> deaths = new HashMap<>();

    public void died(UUID player, Location location) {
        deaths.put(player, location.clone());
    }

    public Location select(
            UUID player, List<Location> candidates, List<Location> enemyEyes, int tick) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("No configured laser spawns");
        recent.entrySet().removeIf(entry -> tick - entry.getValue() >= 100);
        double best = Double.POSITIVE_INFINITY;
        List<Location> ties = new ArrayList<>();
        Location death = deaths.get(player);
        for (Location spawn : candidates) {
            double score = recent.containsKey(spawn) ? 200.0D : 0.0D;
            if (death != null && Objects.equals(spawn.getWorld(), death.getWorld()))
                score += Math.max(0.0D, 16.0D - spawn.distance(death)) * 10.0D;
            Location eye = spawn.clone().add(0, 1.62D, 0);
            for (Location enemy : enemyEyes) {
                if (!Objects.equals(eye.getWorld(), enemy.getWorld())) continue;
                double distance = eye.distance(enemy);
                score += Math.max(0.0D, 64.0D - distance);
                if (distance < 12.0D) score += 10000.0D;
                if (distance <= 80.0D && exposed(enemy, eye, distance)) score += 1000.0D;
            }
            if (score < best) {
                best = score;
                ties.clear();
            }
            if (score == best) ties.add(spawn);
        }
        Location selected = ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
        recent.put(selected.clone(), tick);
        return selected.clone();
    }

    private static boolean exposed(Location from, Location to, double distance) {
        if (distance < 0.001D) return true;
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        return from.getWorld()
                        .rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true)
                == null;
    }

    public void clear() {
        recent.clear();
        deaths.clear();
    }
}
