package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

class RiptideIntroductionBlueprintTest {
    @Test
    void seaLeavesTheFullRealBuildingEnvelopeAndMovingDeckClear() {
        var blocks = RiptideIntroductionBlueprint.blocks();
        for (int f = -4; f <= 144; f++)
            for (int x = -7; x <= 7; x++) {
                assertEquals(Material.WATER, at(blocks, f, x, -2));
                assertEquals(
                        Math.abs(f) <= 4 && Math.abs(x) <= 3 ? Material.OAK_PLANKS : Material.AIR,
                        at(blocks, f, x, 0));
                for (int y = 1; y <= 12; y++) assertEquals(Material.AIR, at(blocks, f, x, y));
            }
        assertEquals(
                Material.SPRUCE_PLANKS, at(blocks, 145, 0, 0), "pier joins final raft edge at 144");
    }

    @Test
    void sceneryFitsMigrationFootprintAndWaterCannotEscape() {
        var blocks = RiptideIntroductionBlueprint.blocks();
        for (var cell : blocks.keySet()) {
            assertTrue(cell.forward() >= RiptideIntroductionBlueprint.MIN_FORWARD);
            assertTrue(cell.forward() <= RiptideIntroductionBlueprint.MAX_FORWARD);
            assertTrue(Math.abs(cell.lateral()) <= RiptideIntroductionBlueprint.MAX_LATERAL);
            assertTrue(cell.height() >= RiptideIntroductionBlueprint.MIN_HEIGHT);
            assertTrue(cell.height() <= RiptideIntroductionBlueprint.MAX_HEIGHT);
        }
        for (int f = -16; f <= 154; f++)
            for (int x : new int[] {-24, 24})
                assertEquals(Material.SANDSTONE, at(blocks, f, x, -2));
        for (int x = -24; x <= 24; x++)
            for (int f : new int[] {-16, 154})
                assertEquals(Material.SANDSTONE, at(blocks, f, x, -2));
    }

    private Material at(
            Map<RiptideIntroductionBlueprint.Cell, Material> blocks, int f, int x, int y) {
        return blocks.getOrDefault(new RiptideIntroductionBlueprint.Cell(f, x, y), Material.AIR);
    }
}
