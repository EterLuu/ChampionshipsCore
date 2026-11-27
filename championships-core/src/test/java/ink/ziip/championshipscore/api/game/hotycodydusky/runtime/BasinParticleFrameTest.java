package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class BasinParticleFrameTest {
    @Test
    void crowdedTileUsesClosestActualPointForEachViewerAndKeepsFloorsSeparate() {
        var frame = new BasinParticleFrame();
        Location left = at(-1.9, 64, 0), right = at(-0.1, 64, 0), upstairs = at(-0.1, 66, 0);
        for (Location point : List.of(left, right, upstairs)) frame.add(point);

        assertEquals(Set.of(left, upstairs), Set.copyOf(frame.nearest(left, 6, 12)));
        assertEquals(Set.of(right, upstairs), Set.copyOf(frame.nearest(right, 6, 12)));
    }

    @Test
    void denseSceneKeepsNearestTilesAcrossChunkEdgesWithinTheBudget() {
        var frame = new BasinParticleFrame();
        for (int x = -33; x < 33; x += 2) frame.add(at(x, 64, 0));
        Location center = at(-16.4, 64, 0);
        assertEquals(
                Set.of(at(-17, 64, 0), at(-15, 64, 0), at(-19, 64, 0)),
                Set.copyOf(frame.nearest(center, 16, 3)));
        assertTrue(frame.nearest(at(0, 81, 0), 16, 3).isEmpty());
    }

    @Test
    void radiusIncludesItsBoundaryAndExcludesNearbyChunkCornersOutsideTheSphere() {
        var frame = new BasinParticleFrame();
        Location boundary = at(3, 64, 4);
        for (Location point : List.of(boundary, at(4, 64, 4), at(0, 70, 0))) frame.add(point);
        assertEquals(List.of(boundary), frame.nearest(at(0, 64, 0), 5, 12));
    }

    private static Location at(double x, double y, double z) {
        return new Location(null, x, y, z);
    }
}
