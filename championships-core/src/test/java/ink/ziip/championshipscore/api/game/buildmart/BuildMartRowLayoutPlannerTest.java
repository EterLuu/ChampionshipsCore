package ink.ziip.championshipscore.api.game.buildmart;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartRowLayoutPlannerTest {
    @Test
    void alignsStepAndKeepsEveryBaseBeyondViewDistance() {
        Vector step = BuildMartRowLayoutPlanner.step(new Vector(45, 27, 54));

        assertEquals(new Vector(432, 0, 0), step);
        assertTrue(step.getBlockX() - 45 >= BuildMartRowLayoutPlanner.ROW_CLEARANCE_BLOCKS);
    }

    @Test
    void generatedRowStartsBeyondInfrastructureAndOnAChunkBoundary() {
        Vector source = new Vector(167, 69, 167);
        double occupiedMaxX = 912.0;

        Vector first = BuildMartRowLayoutPlanner.generatedOrigin(source, occupiedMaxX);

        assertEquals(0, first.getBlockX() % 16);
        assertTrue(first.getX() >= occupiedMaxX + BuildMartRowLayoutPlanner.ROW_CLEARANCE_BLOCKS);
        assertEquals(source.getY(), first.getY());
        assertEquals(source.getZ(), first.getZ());
    }
}
