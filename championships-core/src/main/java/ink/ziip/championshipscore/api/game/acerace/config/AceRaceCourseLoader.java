package ink.ziip.championshipscore.api.game.acerace.config;

import ink.ziip.championshipscore.api.game.acerace.geometry.AceRaceRespawnBindings;
import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceCourse;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceCourse.RespawnMarker;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceLine;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgressPoint;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceRespawnPoint;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.LocationConfig;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** Builds a complete replacement before the area publishes it to gameplay and editor preview. */
public final class AceRaceCourseLoader {
    private AceRaceCourseLoader() {}

    public static AceRaceCourse load(
            AceRaceConfig config,
            String worldName,
            Server server,
            BiConsumer<String, String> warning) {
        World world = server.getWorld(worldName);
        int defaultFallY = world == null ? -64 : world.getMinHeight();
        List<AceRaceProgressPoint> progressPoints =
                loadProgressPoints(config.getProgressPoints(), defaultFallY, warning);
        List<RespawnMarker> loaded =
                loadRespawns(config.getRespawnPoints(), worldName, server, warning);
        if (loaded.isEmpty()) return new AceRaceCourse(progressPoints, loaded);

        AceRaceLine finish =
                config.hasFinishLine()
                        ? new AceRaceLine(config.getFinishLinePos1(), config.getFinishLinePos2())
                        : null;
        World markerWorld = loaded.getFirst().point().destination().getWorld();
        Location finishApproach = finish == null ? null : finish.center(markerWorld);
        List<Integer> inferred =
                AceRaceRespawnBindings.bindRespawnPoints(
                        progressPoints,
                        loaded.stream().map(RespawnMarker::point).toList(),
                        config.getStartSpawnPoint(),
                        finishApproach);
        List<RespawnMarker> bound = new ArrayList<>();
        Set<Integer> boundGates = new HashSet<>();
        for (int index = 0; index < loaded.size(); index++) {
            RespawnMarker marker = loaded.get(index);
            Integer configured =
                    config.getRespawnProgressPointBinding(
                            marker.configIndex(), progressPoints.size());
            int binding =
                    configured != null ? configured : inferred.isEmpty() ? -1 : inferred.get(index);
            bound.add(new RespawnMarker(marker.point(), marker.configIndex(), binding));
            boundGates.add(binding);
        }
        for (int index = 0; index < progressPoints.size(); index++) {
            if (!boundGates.contains(index)) {
                warning.accept(
                        MessageConfig.codeText("ace-race-area.load-course-points-3"),
                        MessageConfig.codeText(
                                "ace-race-area.rebuild-respawn-progress-point-bindings-1",
                                index + 1));
            }
        }
        return new AceRaceCourse(progressPoints, bound);
    }

    private static List<AceRaceProgressPoint> loadProgressPoints(
            ConfigurationSection root, int defaultFallY, BiConsumer<String, String> warning) {
        List<AceRaceProgressPoint> points = new ArrayList<>();
        if (root == null) return points;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Vector pos1 = section.getVector("pos1");
            Vector pos2 = section.getVector("pos2");
            if (pos1 == null || pos2 == null) {
                warning.accept(
                        MessageConfig.codeText("ace-race-area.load-course-points-1"),
                        MessageConfig.codeText("ace-race-area.load-course-points-2", key));
                continue;
            }
            points.add(
                    new AceRaceProgressPoint(
                            section.getInt("order", points.size() + 1),
                            pos1,
                            pos2,
                            section.getInt("fall-y", defaultFallY),
                            AceRaceEquipment.fromConfig(section.getString("equipment"))));
        }
        points.sort(Comparator.comparingInt(AceRaceProgressPoint::order));
        return points;
    }

    private static List<RespawnMarker> loadRespawns(
            List<String> configured,
            String worldName,
            Server server,
            BiConsumer<String, String> warning) {
        List<RespawnMarker> loaded = new ArrayList<>();
        if (configured == null) return loaded;
        for (int configIndex = 0; configIndex < configured.size(); configIndex++) {
            String serialized = configured.get(configIndex);
            Location location;
            try {
                location = LocationConfig.readLocation(serialized, server);
            } catch (IllegalArgumentException exception) {
                warning.accept(
                        MessageConfig.codeText("ace-race-area.load-course-points-3"),
                        MessageConfig.codeText("ace-race-area.load-course-points-4", serialized));
                continue;
            }
            if (location == null) {
                warning.accept(
                        MessageConfig.codeText("ace-race-area.load-course-points-3"),
                        MessageConfig.codeText("ace-race-area.load-course-points-4", serialized));
                continue;
            }
            if (location.getWorld() == null || !worldName.equals(location.getWorld().getName())) {
                warning.accept(
                        MessageConfig.codeText("ace-race-area.load-course-points-3"),
                        MessageConfig.codeText("ace-race-area.load-course-points-5", serialized));
                continue;
            }
            loaded.add(new RespawnMarker(new AceRaceRespawnPoint(location), configIndex, -1));
        }
        return loaded;
    }
}
