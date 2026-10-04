package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class BuildMartBlueprintReviewTest {
    private static final List<BlockFace> FACES =
            List.of(
                    BlockFace.NORTH,
                    BlockFace.EAST,
                    BlockFace.SOUTH,
                    BlockFace.WEST,
                    BlockFace.UP,
                    BlockFace.DOWN);
    private static final BlockData SOLID = data(Material.STONE, true, Set.copyOf(FACES));

    @Test
    void allSixDirectionsAreRequiredIncludingAboveAndBelow() {
        var center = new BuildMartBlueprintReview.Position(3, 3, 3);
        var enclosed = surrounded(center, SOLID);
        assertEquals(List.of(center), BuildMartBlueprintReview.invisible(enclosed, p -> null));
        for (var face : FACES) {
            var open = new ArrayList<>(enclosed);
            var gap = center.offset(face);
            open.removeIf(b -> b.getX() == gap.x() && b.getY() == gap.y() && b.getZ() == gap.z());
            assertTrue(
                    BuildMartBlueprintReview.invisible(open, p -> null).isEmpty(),
                    "Exposed face: " + face);
        }
    }

    @Test
    void floorUsesActualOpacityAndDoesNotMaskAnOpenTop() {
        var center = new BuildMartBlueprintReview.Position(3, 0, 3);
        var building = surrounded(center, SOLID);
        building.removeIf(b -> b.getY() == -1);
        assertEquals(List.of(center), BuildMartBlueprintReview.invisible(building, p -> SOLID));
        assertTrue(
                BuildMartBlueprintReview.invisible(
                                building, p -> data(Material.GLASS, false, Set.copyOf(FACES)))
                        .isEmpty());
        building.removeIf(b -> b.getY() == 1);
        assertTrue(BuildMartBlueprintReview.invisible(building, p -> SOLID).isEmpty());
    }

    @Test
    void partialNeighbourMustSealTheSharedFaceRatherThanItsFarFace() {
        var center = new BuildMartBlueprintReview.Position(3, 3, 3);
        var building = surrounded(center, SOLID);
        building.removeIf(b -> b.getY() == 4);
        building.add(
                new BlueprintBlock(3, 4, 3, data(Material.OAK_SLAB, true, Set.of(BlockFace.UP))));
        assertTrue(BuildMartBlueprintReview.invisible(building, p -> null).isEmpty());
        building.removeIf(b -> b.getY() == 4);
        building.add(
                new BlueprintBlock(3, 4, 3, data(Material.OAK_SLAB, true, Set.of(BlockFace.DOWN))));
        assertEquals(List.of(center), BuildMartBlueprintReview.invisible(building, p -> null));
    }

    @Test
    void solidPanelsAreDirectionalAndTransparentCubesAndLeavesStayVisible() {
        var panel = data(Material.DARK_OAK_TRAPDOOR, false, Set.of(BlockFace.WEST));
        assertTrue(BuildMartBlueprintReview.sealsFace(panel, BlockFace.WEST));
        assertFalse(BuildMartBlueprintReview.sealsFace(panel, BlockFace.EAST));
        assertFalse(
                BuildMartBlueprintReview.sealsFace(
                        data(Material.GLASS, false, Set.copyOf(FACES)), BlockFace.WEST));
        assertFalse(
                BuildMartBlueprintReview.sealsFace(
                        data(Material.OAK_LEAVES, false, Set.copyOf(FACES)), BlockFace.WEST));
    }

    private static ArrayList<BlueprintBlock> surrounded(
            BuildMartBlueprintReview.Position center, BlockData data) {
        var blocks = new ArrayList<BlueprintBlock>();
        blocks.add(new BlueprintBlock(center.x(), center.y(), center.z(), SOLID));
        for (var face : FACES) {
            var p = center.offset(face);
            blocks.add(new BlueprintBlock(p.x(), p.y(), p.z(), data));
        }
        return blocks;
    }

    private static BlockData data(Material material, boolean opaque, Set<BlockFace> fullFaces) {
        return (BlockData)
                Proxy.newProxyInstance(
                        BlockData.class.getClassLoader(),
                        new Class<?>[] {BlockData.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getMaterial" -> material;
                                    case "isOccluding" -> opaque;
                                    case "isFaceSturdy" -> fullFaces.contains(args[0]);
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
