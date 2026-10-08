package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

class RiptideIntroductionBlueprintTest {
    @Test
    void raftHasTheSameSevenByNineFootprintAsTheFormalDefault() {
        var blocks = RiptideIntroductionBlueprint.blocks();
        assertEquals(63, blocks.values().stream().filter(m -> m == Material.OAK_PLANKS).count());
        for (int f = -4; f <= 4; f++)
            for (int x = -3; x <= 3; x++) assertEquals(Material.OAK_PLANKS, at(blocks, f, x, 0));
    }

    @Test
    void twoWallsHaveReachableThreeWideThreeHighOpenings() {
        var blocks = RiptideIntroductionBlueprint.blocks();
        for (int f : new int[] {24, 52}) {
            int opening = f == 24 ? -1 : 1;
            for (int x = opening; x < opening + 3; x++)
                for (int y = 1; y <= 3; y++) assertEquals(Material.AIR, at(blocks, f, x, y));
            assertEquals(Material.STRIPPED_SPRUCE_LOG, at(blocks, f, -3, 1));
            assertEquals(Material.STRIPPED_SPRUCE_LOG, at(blocks, f, 0, 4));
        }
    }

    @Test
    void canalLeavesTheWholeRouteClearForRaftAndStoppedFloorMechanics() {
        var blocks = RiptideIntroductionBlueprint.blocks();
        for (int f = 5; f <= 141; f++) {
            for (int x = -3; x <= 3; x++) {
                assertEquals(Material.AIR, at(blocks, f, x, 0));
                assertEquals(Material.WATER, at(blocks, f, x, -2));
                if (f != 24 && f != 52)
                    for (int y = 1; y <= 3; y++) assertEquals(Material.AIR, at(blocks, f, x, y));
            }
        }
    }

    private Material at(
            Map<RiptideIntroductionBlueprint.Cell, Material> blocks, int f, int x, int y) {
        return blocks.getOrDefault(new RiptideIntroductionBlueprint.Cell(f, x, y), Material.AIR);
    }
}
