package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BuildMartOrderPoolTest {
    @Test
    void submittingAReplacementUpdatesAllPoolsWithoutMutatingExistingOrders() {
        var original = blueprint("edit", 3);
        var other = blueprint("keep", 2);
        var before = new BuildMartOrderPool().withBlueprint(original).withBlueprint(other);
        var replacement = blueprint("edit", 5);
        var after = before.withBlueprint(replacement);
        assertSame(original, before.byId("edit"));
        assertEquals(java.util.List.of(original), before.getGolden());
        assertSame(replacement, after.byId("edit"));
        assertSame(other, after.byId("keep"));
        assertEquals(2, after.getAll().size());
        assertEquals(2, after.getNormal().size());
        assertTrue(after.getGolden().isEmpty());
        assertEquals(java.util.List.of(other, replacement), after.drawNormal(2).stream()
                .sorted(java.util.Comparator.comparing(BuildMartBlueprint::getStars)).toList());
    }

    private static BuildMartBlueprint blueprint(String id, int stars) {
        var data = (org.bukkit.block.data.BlockData) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.block.data.BlockData.class.getClassLoader(), new Class<?>[]{org.bukkit.block.data.BlockData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> org.bukkit.Material.STONE;
                    case "getAsString" -> "minecraft:stone";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new BuildMartBlueprint(id, id, stars, java.util.List.of(new BlueprintBlock(0, 0, 0, data)));
    }

    @Test
    void allOneToFiveStarBlueprintsAreNormalOrders() {
        for (int stars = 1; stars <= 5; stars++) {
            assertTrue(BuildMartOrderPool.isNormalRating(stars));
        }
        assertFalse(BuildMartOrderPool.isNormalRating(7));
    }

    @Test
    void onlyThreeStarBlueprintsFeedTheGoldenPool() {
        assertTrue(BuildMartOrderPool.isGoldenSourceRating(3));
        assertFalse(BuildMartOrderPool.isGoldenSourceRating(2));
        assertFalse(BuildMartOrderPool.isGoldenSourceRating(5));
        assertFalse(BuildMartOrderPool.isGoldenSourceRating(7));
    }
}
