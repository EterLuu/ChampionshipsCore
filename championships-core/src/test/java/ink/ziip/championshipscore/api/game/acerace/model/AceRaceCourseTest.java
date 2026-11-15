package ink.ziip.championshipscore.api.game.acerace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.acerace.model.AceRaceCourse.RespawnMarker;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class AceRaceCourseTest {
    @Test
    void snapshotsOwnTheirListsAndRetainRawSourceRows() {
        var markers = new ArrayList<>(List.of(marker(30, 7), marker(10, 2)));
        var course = new AceRaceCourse(List.of(), markers);
        markers.clear();
        assertEquals(2, course.respawns().size());
        assertEquals(0, course.loadedIndexForConfig(7));
        assertEquals(1, course.loadedIndexForConfig(2));
        assertEquals(-1, course.loadedIndexForConfig(1));
        assertThrows(UnsupportedOperationException.class, () -> course.respawns().clear());
        assertThrows(UnsupportedOperationException.class, () -> course.progressPoints().clear());
    }

    @Test
    void oneFastMovementCapturesInTravelOrderAndOmitsPreviouslyCapturedMarkers() {
        var course =
                new AceRaceCourse(List.of(), List.of(marker(30, 0), marker(10, 2), marker(20, 4)));
        assertEquals(List.of(1, 2, 0), course.crossedRespawns(at(0), at(40), Set.of()));
        assertEquals(List.of(0, 2, 1), course.crossedRespawns(at(40), at(0), Set.of()));
        assertEquals(List.of(2, 0), course.crossedRespawns(at(0), at(40), Set.of(1)));
        assertTrue(course.crossedRespawns(at(-10), at(-5), Set.of()).isEmpty());
    }

    @Test
    void equalDistanceMarkersKeepConfigurationOrderAndStationaryMovementCanCapture() {
        var course = new AceRaceCourse(List.of(), List.of(marker(10, 7), marker(10, 2)));
        assertEquals(List.of(0, 1), course.crossedRespawns(at(0), at(20), Set.of()));
        assertEquals(List.of(0, 1), course.crossedRespawns(at(10), at(10), Set.of()));
        assertTrue(course.crossedRespawns(at(0), at(0), Set.of()).isEmpty());
    }

    @Test
    void duplicateSourceRowsAndBindingsOutsideTheSnapshotAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AceRaceCourse(List.of(), List.of(marker(0, 1), marker(10, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AceRaceCourse(
                                List.of(),
                                List.of(new RespawnMarker(new AceRaceRespawnPoint(at(0)), 1, 0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RespawnMarker(new AceRaceRespawnPoint(at(0)), -1, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RespawnMarker(new AceRaceRespawnPoint(at(0)), 0, -2));
    }

    private static RespawnMarker marker(int z, int rawIndex) {
        return new RespawnMarker(new AceRaceRespawnPoint(at(z)), rawIndex, -1);
    }

    private static Location at(double z) {
        return new Location(null, 0, 70, z);
    }
}
