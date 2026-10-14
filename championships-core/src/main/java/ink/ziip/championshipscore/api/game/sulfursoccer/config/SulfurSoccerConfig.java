package ink.ziip.championshipscore.api.game.sulfursoccer.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.sulfursoccer.geometry.SulfurSoccerGeometry;
import ink.ziip.championshipscore.api.game.sulfursoccer.geometry.SulfurSoccerPenaltyLayout;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Getter
@Setter
public final class SulfurSoccerConfig extends BaseGameConfig {
    private final String resourceName = "sulfursoccer/area.yml";
    private final String folderName = "sulfursoccer/";

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "area-pos1", nullable = true)
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2", nullable = true)
    private Vector areaPos2;

    @ConfigOption(path = "right-area-pos1", nullable = true)
    private Vector rightAreaPos1;

    @ConfigOption(path = "right-area-pos2", nullable = true)
    private Vector rightAreaPos2;

    @ConfigOption(path = "left-area-pos1", nullable = true)
    private Vector leftAreaPos1;

    @ConfigOption(path = "left-area-pos2", nullable = true)
    private Vector leftAreaPos2;

    @ConfigOption(path = "right-goal-pos1", nullable = true)
    private Vector rightGoalPos1;

    @ConfigOption(path = "right-goal-pos2", nullable = true)
    private Vector rightGoalPos2;

    @ConfigOption(path = "left-goal-pos1", nullable = true)
    private Vector leftGoalPos1;

    @ConfigOption(path = "left-goal-pos2", nullable = true)
    private Vector leftGoalPos2;

    @ConfigOption(path = "right-spawn-points")
    private List<String> rightSpawnPoints = new ArrayList<>();

    @ConfigOption(path = "left-spawn-points")
    private List<String> leftSpawnPoints = new ArrayList<>();

    @ConfigOption(path = "right-team-color-blocks")
    private List<String> rightTeamColorBlocks = new ArrayList<>();

    @ConfigOption(path = "left-team-color-blocks")
    private List<String> leftTeamColorBlocks = new ArrayList<>();

    @ConfigOption(path = "ball-spawn-point", nullable = true)
    private Location ballSpawnPoint;

    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "goals-to-win")
    private int goalsToWin = 5;

    @ConfigOption(path = "kickoff-countdown")
    private int kickoffCountdown = 5;

    public SulfurSoccerConfig(ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 2;
    }

    public static Vector parseColorBlock(String raw) {
        if (raw == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.parse-color-block-1"));
        String[] parts = raw.split(":", -1);
        if (parts.length != 3)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.parse-color-block-2"));
        try {
            int x = Integer.parseInt(parts[0]),
                    y = Integer.parseInt(parts[1]),
                    z = Integer.parseInt(parts[2]);
            if (x < -30_000_000
                    || x >= 30_000_000
                    || z < -30_000_000
                    || z >= 30_000_000
                    || y < -64
                    || y >= 320) throw new NumberFormatException();
            return new Vector(x, y, z);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.parse-color-block-3"));
        }
    }

    public Location bind(Location location) {
        if (location == null) return null;
        Location result = location.clone();
        result.setWorld(Bukkit.getWorld(getConfiguredWorld()));
        return result;
    }

    /**
     * Parse without requiring a loaded Bukkit world, so publication can validate every coordinate.
     */
    public Location parseSpawn(String raw) {
        ConfiguredLocation point = ConfiguredLocation.read(raw);
        if (point == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.parse-spawn-1"));
        return point.requireWorld(getConfiguredWorld()).resolve(identifier -> null);
    }

    public void validate() {
        if (getConfiguredWorld().isBlank())
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-1"));
        if (goalsToWin < 1 || kickoffCountdown < 1 || kickoffCountdown > 60)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-2"));
        BoundingBox field = SulfurSoccerGeometry.box(areaPos1, areaPos2);
        BoundingBox right = SulfurSoccerGeometry.box(rightAreaPos1, rightAreaPos2);
        BoundingBox left = SulfurSoccerGeometry.box(leftAreaPos1, leftAreaPos2);
        BoundingBox rightGoal = SulfurSoccerGeometry.box(rightGoalPos1, rightGoalPos2);
        BoundingBox leftGoal = SulfurSoccerGeometry.box(leftGoalPos1, leftGoalPos2);
        if (!field.contains(right) || !field.contains(left) || right.overlaps(left))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-3"));
        if (!right.contains(rightGoal) || !left.contains(leftGoal))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-4"));
        validateSpawns(
                rightSpawnPoints,
                right,
                rightGoal,
                MessageConfig.codeText("sulfur-soccer-config.validate-5"));
        validateSpawns(
                leftSpawnPoints,
                left,
                leftGoal,
                MessageConfig.codeText("sulfur-soccer-config.validate-6"));
        if (ballSpawnPoint == null
                || !SulfurSoccerGeometry.finite(ballSpawnPoint.toVector())
                || !field.contains(ballSpawnPoint.toVector())
                || rightGoal.contains(ballSpawnPoint.toVector())
                || leftGoal.contains(ballSpawnPoint.toVector()))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-7"));
        if (spectatorSpawnPoint == null
                || !SulfurSoccerGeometry.finite(spectatorSpawnPoint.toVector())
                || field.contains(spectatorSpawnPoint.toVector()))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-8"));
        double floorY = Math.floor(parseSpawn(rightSpawnPoints.getFirst()).getY() - 0.01) + 1;
        SulfurSoccerPenaltyLayout.resolve(field, rightGoal, leftGoal, floorY);
        SulfurSoccerPenaltyLayout.resolve(field, leftGoal, rightGoal, floorY);
        HashSet<Vector> colorBlocks = new HashSet<>();
        BoundingBox protectedPitch = field.clone().expand(0, 1, 0, 0, 0, 0);
        validateColorBlocks(rightTeamColorBlocks, colorBlocks, protectedPitch);
        validateColorBlocks(leftTeamColorBlocks, colorBlocks, protectedPitch);
    }

    private static void validateColorBlocks(
            List<String> blocks, HashSet<Vector> unique, BoundingBox pitch) {
        if (blocks == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-color-blocks-1"));
        for (String raw : blocks) {
            Vector point = parseColorBlock(raw);
            if (!unique.add(point) || pitch.contains(point))
                throw new IllegalArgumentException(
                        MessageConfig.codeText("sulfur-soccer-config.validate-color-blocks-2"));
        }
    }

    private void validateSpawns(
            List<String> spawns, BoundingBox half, BoundingBox goal, String name) {
        if (spawns == null || spawns.size() != 4)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("sulfur-soccer-config.validate-spawns-1", name));
        HashSet<Vector> unique = new HashSet<>();
        for (String raw : spawns) {
            Vector point = parseSpawn(raw).toVector();
            if (!half.contains(point) || goal.contains(point) || !unique.add(point))
                throw new IllegalArgumentException(
                        MessageConfig.codeText("sulfur-soccer-config.validate-spawns-2", name));
        }
    }
}
