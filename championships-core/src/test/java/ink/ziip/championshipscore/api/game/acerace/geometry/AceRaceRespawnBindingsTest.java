package ink.ziip.championshipscore.api.game.acerace.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgressPoint;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceRespawnPoint;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

class AceRaceRespawnBindingsTest {
    static Stream<Arguments> courses() {
        return Stream.of(
                // Markers bind to the preceding gate; the start segment does not wrap around.
                Arguments.of("in order", 20, 22, List.of(0, 12, 18), List.of(-1, 0, 1)),
                // Binding follows coordinates, not configuration order.
                Arguments.of("out of order", 20, 22, List.of(22, 0, 12), List.of(1, -1, 0)),
                // The final gate falls back to the nearest pre-finish marker.
                Arguments.of("none after final", 40, 42, List.of(0, 12, 25), List.of(-1, 0, 1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("courses")
    void respawnMarkersBindToTheGateTheyFollow(
            String name, int secondGateZ, int finishZ, List<Integer> markers, List<Integer> bound) {
        List<AceRaceProgressPoint> gates =
                List.of(
                        new AceRaceProgressPoint(
                                1,
                                new Vector(-10, 64, 10),
                                new Vector(10, 64, 10),
                                0,
                                AceRaceEquipment.NONE),
                        new AceRaceProgressPoint(
                                2,
                                new Vector(-10, 64, secondGateZ),
                                new Vector(10, 64, secondGateZ),
                                0,
                                AceRaceEquipment.NONE));
        List<AceRaceRespawnPoint> respawns =
                markers.stream().map(z -> new AceRaceRespawnPoint(at(z))).toList();
        assertEquals(
                bound,
                AceRaceRespawnBindings.bindRespawnPoints(gates, respawns, at(0), at(finishZ)));
    }

    private static Location at(double z) {
        return new Location(null, 0, 65, z);
    }
}
