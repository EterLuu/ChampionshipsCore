package ink.ziip.championshipscore.api.game.riptiderush.course;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

class RiptideCourseRevealTest {
    @Test
    void everyStationaryTypeHasTheSameFacadeAndRestoresAtTenBlocksOnEveryAxis() throws Exception {
        for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (var type : RiptideLevelType.values()) {
                var world = new RiptideTestFixtures.TestWorld();
                var config = RiptideTestFixtures.config(world, axis);
                var g = config.resolveGeometry();
                var template = RiptideLevelTemplate.create("test", type);
                String variant =
                        switch (type) {
                            case PASS -> "WEAVE";
                            case MATH -> "ADD";
                            case COLOR_FLOOR -> "WOOD";
                            case DODGE -> "ZOMBIE";
                            case RHYTHM -> "PULSE";
                        };
                var level = new RiptideCoursePlan.Level(1, 100, template, variant, 0, true, 1);
                var plan = new RiptideCoursePlan(1, 1, 1, List.of(level));
                RiptideCourseGenerator.build(
                        world.world, g, plan, Material.OAK_PLANKS, Material.STONE);
                var original = new HashMap<>(world.blocks);
                int front = level.step() - level.extent();
                try (var reveal = new RiptideCourseReveal(config, g, plan.levels())) {
                    for (int lateral = -7; lateral <= 7; lateral++)
                        for (int y = 1; y <= 12; y++)
                            assertEquals(
                                    Material.STRIPPED_SPRUCE_WOOD, world.at(g, front, lateral, y));
                    if (type == RiptideLevelType.PASS)
                        assertEquals(
                                Material.AIR,
                                world.at(g, 103, -1, 2),
                                "rear building must also be hidden");
                    assertEquals(
                            15 * 12,
                            world.blocks.values().stream()
                                    .filter(m -> m == Material.STRIPPED_SPRUCE_WOOD)
                                    .count());
                    reveal.tick(front - g.halfLength() - 11);
                    assertEquals(Material.STRIPPED_SPRUCE_WOOD, world.at(g, front, 0, 1));
                    reveal.tick(front - g.halfLength() - 10);
                    assertEquals(
                            original,
                            world.blocks,
                            "restore the generated mirror and all holes exactly");
                    int writes = world.writes;
                    reveal.tick(100);
                    assertEquals(writes, world.writes, "revealed buildings are not repainted");
                }
                assertEquals(original, world.blocks);
            }
        }
    }

    @Test
    void tallThickBlueprintAndCancelledTrialRestoreWithoutTouchingDeckOrScenery() throws Exception {
        var world = new RiptideTestFixtures.TestWorld();
        var config = RiptideTestFixtures.config(world, new int[] {0, 1});
        var g = config.resolveGeometry();
        var template =
                RiptideLevelTemplate.create("custom", RiptideLevelType.PASS)
                        .withBlueprint(new RiptideBlueprint("", 7, 15, 12, List.of()));
        var level = new RiptideCoursePlan.Level(1, 100, template, "CUSTOM", 0, false, 1);
        world.set(g, 107, 7, 12, Material.SPRUCE_STAIRS);
        world.set(g, 93, -7, 1, Material.SPRUCE_SLAB);
        world.set(g, 100, 0, 0, Material.OAK_PLANKS);
        world.set(g, 108, 7, 12, Material.DIAMOND_BLOCK);
        var original = new HashMap<>(world.blocks);
        var reveal = new RiptideCourseReveal(config, g, List.of(level));
        assertEquals(Material.STRIPPED_SPRUCE_WOOD, world.at(g, 93, 7, 12));
        assertEquals(Material.AIR, world.at(g, 107, 7, 12));
        assertEquals(Material.OAK_PLANKS, world.at(g, 100, 0, 0));
        assertEquals(Material.DIAMOND_BLOCK, world.at(g, 108, 7, 12));
        reveal.close();
        assertEquals(original, world.blocks);
        int writes = world.writes;
        reveal.close();
        assertEquals(writes, world.writes);
    }

    @Test
    void consecutiveGatesRevealIndividuallyAndCleanupDoesNotOverwriteAnActiveGate()
            throws Exception {
        var world = new RiptideTestFixtures.TestWorld();
        var config = RiptideTestFixtures.config(world, new int[] {0, 1});
        var g = config.resolveGeometry();
        var template = RiptideLevelTemplate.create("math", RiptideLevelType.MATH);
        var plan =
                new RiptideCoursePlan(
                        1,
                        1,
                        1,
                        List.of(
                                new RiptideCoursePlan.Level(
                                        1, 100, template, "DOUBLE", 0, false, 1)),
                        500);
        RiptideCourseGenerator.build(world.world, g, plan, Material.OAK_PLANKS, Material.STONE);
        var reveal = new RiptideCourseReveal(config, g, plan.levels());
        reveal.tick(94 - g.halfLength() - 10);
        assertEquals(Material.AIR, world.at(g, 94, 1, 1));
        assertEquals(Material.STRIPPED_SPRUCE_WOOD, world.at(g, 106, 1, 1));
        world.set(g, 94, 1, 1, Material.IRON_BLOCK);
        reveal.close();
        assertEquals(Material.AIR, world.at(g, 106, 1, 1));
        assertEquals(Material.IRON_BLOCK, world.at(g, 94, 1, 1));
    }

    @Test
    void sideStagesHaveAForwardPlaceholderWithoutLeavingASolidWallOnTheRaft() throws Exception {
        var world = new RiptideTestFixtures.TestWorld();
        var config = RiptideTestFixtures.config(world, new int[] {0, 1});
        var g = config.resolveGeometry();
        var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var walls = List.of(new RiptideCoursePlan.SideWall(template, "WEAVE", 0, 1, 7));
        var level =
                new RiptideCoursePlan.Level(
                        1, 200, template, "WEAVE", 0, false, 1, 0, 0, "SIDE", 1, walls);
        try (var reveal = new RiptideCourseReveal(config, g, List.of(level))) {
            assertEquals(Material.STRIPPED_SPRUCE_WOOD, world.at(g, 200, 0, 1));
            assertEquals(15 * 12, world.blocks.size());
            reveal.tick(200 - g.halfLength() - 10);
            assertTrue(world.blocks.isEmpty());
        }
    }
}
