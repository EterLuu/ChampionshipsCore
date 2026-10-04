package ink.ziip.championshipscore.api.game.sulfursoccer.mechanics;

import ink.ziip.championshipscore.api.game.sulfursoccer.config.SulfurSoccerConfig;
import ink.ziip.championshipscore.api.game.sulfursoccer.geometry.SulfurSoccerGeometry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Resolves the midfield row and the isolated white keeper marker in the loaded pitch. */
public final class SulfurSoccerSpawns {
    public record Layout(List<Location> right, List<Location> left, double floorY) {}

    public static Layout resolve(World world, SulfurSoccerConfig config) {
        BoundingBox rightHalf =
                SulfurSoccerGeometry.box(config.getRightAreaPos1(), config.getRightAreaPos2());
        BoundingBox leftHalf =
                SulfurSoccerGeometry.box(config.getLeftAreaPos1(), config.getLeftAreaPos2());
        Vector rightGoal =
                SulfurSoccerGeometry.box(config.getRightGoalPos1(), config.getRightGoalPos2())
                        .getCenter();
        Vector leftGoal =
                SulfurSoccerGeometry.box(config.getLeftGoalPos1(), config.getLeftGoalPos2())
                        .getCenter();
        boolean alongX =
                Math.abs(rightGoal.getX() - leftGoal.getX())
                        >= Math.abs(rightGoal.getZ() - leftGoal.getZ());
        // Existing spawn Y is the standing surface; never use the field's upper boundary or pearl
        // impact height.
        double floorY =
                Math.floor(config.parseSpawn(config.getRightSpawnPoints().getFirst()).getY() - 0.01)
                        + 1;
        return new Layout(
                side(
                        world,
                        config,
                        config.getRightSpawnPoints(),
                        rightHalf,
                        rightGoal,
                        alongX,
                        "右队"),
                side(world, config, config.getLeftSpawnPoints(), leftHalf, leftGoal, alongX, "左队"),
                floorY);
    }

    private static List<Location> side(
            World world,
            SulfurSoccerConfig config,
            List<String> raw,
            BoundingBox half,
            Vector goal,
            boolean alongX,
            String name) {
        List<Location> references = raw.stream().map(config::parseSpawn).toList();
        double depth =
                references.subList(0, 3).stream()
                        .mapToDouble(p -> longitudinal(p, alongX))
                        .average()
                        .orElseThrow();
        double low = references.stream().mapToDouble(p -> lateral(p, alongX)).min().orElseThrow();
        double high = references.stream().mapToDouble(p -> lateral(p, alongX)).max().orElseThrow();
        if (high - low < 2) {
            double min = alongX ? half.getMinZ() : half.getMinX();
            double max = alongX ? half.getMaxZ() : half.getMaxX();
            low = min + (max - min) / 4;
            high = max - (max - min) / 4;
        }
        double floorY = references.getFirst().getY();
        float yaw = references.getFirst().getYaw();
        List<Location> result = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double across = low + (high - low) * i / 2;
            Location spawn =
                    new Location(
                            world,
                            alongX ? depth : across,
                            floorY,
                            alongX ? across : depth,
                            yaw,
                            0);
            if (!half.contains(spawn.toVector()) || insideGoal(config, spawn.toVector()))
                throw new IllegalArgumentException(name + "中场三点必须位于己方半场内、球门外");
            result.add(spawn);
        }
        int groundY = (int) Math.floor(floorY - 0.01);
        Location keeper = null;
        int candidates = 0;
        boolean configuredKeeper = false;
        Location fourth = references.get(3);
        double goalDepth = alongX ? goal.getX() : goal.getZ();
        double direction = Math.signum(goalDepth - depth);
        for (int x = (int) Math.floor(half.getMinX()); x < Math.ceil(half.getMaxX()); x++) {
            for (int z = (int) Math.floor(half.getMinZ()); z < Math.ceil(half.getMaxZ()); z++) {
                if ((longitudinal(x + 0.5, z + 0.5, alongX) - depth) * direction <= 1
                        || !marker(world.getBlockAt(x, groundY, z).getType())
                        || !isolated(world, x, groundY, z)) continue;
                Location candidate = new Location(world, x + 0.5, groundY + 1, z + 0.5, yaw, 0);
                if (!half.contains(candidate.toVector())
                        || insideGoal(config, candidate.toVector())
                        || !world.getBlockAt(x, groundY + 1, z).isPassable()
                        || !world.getBlockAt(x, groundY + 2, z).isPassable()) continue;
                candidates++;
                if (fourth.getBlockX() == x && fourth.getBlockZ() == z) {
                    configuredKeeper = true;
                    keeper = candidate;
                } else if (keeper == null) keeper = candidate;
            }
        }
        if (keeper == null)
            throw new IllegalArgumentException(name + "球门前未找到独立石英或白色混凝土出生点：请在禁区外保留独立白色标记，且上方两格为空");
        if (candidates > 1 && !configuredKeeper)
            throw new IllegalArgumentException(name + "球门前有多块独立白色标记：请在地图编辑器将第 4 个出生点设在禁区外的目标标记上");
        result.add(keeper);
        return List.copyOf(result);
    }

    private static boolean insideGoal(SulfurSoccerConfig config, Vector point) {
        return SulfurSoccerGeometry.box(config.getRightGoalPos1(), config.getRightGoalPos2())
                        .contains(point)
                || SulfurSoccerGeometry.box(config.getLeftGoalPos1(), config.getLeftGoalPos2())
                        .contains(point);
    }

    private static boolean isolated(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && marker(world.getBlockAt(x + dx, y, z + dz).getType()))
                    return false;
        return true;
    }

    private static boolean marker(Material material) {
        return material == Material.QUARTZ_BLOCK
                || material == Material.SMOOTH_QUARTZ
                || material == Material.WHITE_CONCRETE;
    }

    private static double longitudinal(Location p, boolean alongX) {
        return longitudinal(p.getX(), p.getZ(), alongX);
    }

    private static double longitudinal(double x, double z, boolean alongX) {
        return alongX ? x : z;
    }

    private static double lateral(Location p, boolean alongX) {
        return alongX ? p.getZ() : p.getX();
    }
}
