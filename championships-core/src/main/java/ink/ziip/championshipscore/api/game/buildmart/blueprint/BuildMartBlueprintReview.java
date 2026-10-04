package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Submission visibility check: every shared face must actually be sealed by an opaque neighbour.
 */
public final class BuildMartBlueprintReview {
    private static final List<BlockFace> FACES =
            List.of(
                    BlockFace.NORTH,
                    BlockFace.EAST,
                    BlockFace.SOUTH,
                    BlockFace.WEST,
                    BlockFace.UP,
                    BlockFace.DOWN);
    private static final Set<Material> SOLID_PANELS =
            Set.of(
                    Material.SPRUCE_TRAPDOOR,
                    Material.DARK_OAK_TRAPDOOR,
                    Material.BIRCH_TRAPDOOR,
                    Material.SPRUCE_DOOR,
                    Material.DARK_OAK_DOOR);

    private BuildMartBlueprintReview() {}

    public record Position(int x, int y, int z) {
        Position offset(BlockFace face) {
            return new Position(x + face.getModX(), y + face.getModY(), z + face.getModZ());
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }

    /**
     * Floor data is explicit: a glass display floor must never be treated as an opaque build floor.
     */
    public static List<Position> invisible(
            List<BlueprintBlock> blocks, Function<Position, BlockData> floor) {
        Map<Position, BlockData> states = new HashMap<>();
        for (BlueprintBlock block : blocks)
            states.put(
                    new Position(block.getX(), block.getY(), block.getZ()), block.getBlockData());
        return blocks.stream()
                .map(block -> new Position(block.getX(), block.getY(), block.getZ()))
                .filter(
                        position ->
                                FACES.stream()
                                        .allMatch(
                                                face -> {
                                                    Position neighbour = position.offset(face);
                                                    BlockData data = states.get(neighbour);
                                                    if (data == null && neighbour.y() == -1)
                                                        data = floor.apply(neighbour);
                                                    return sealsFace(data, face.getOppositeFace());
                                                }))
                .toList();
    }

    public static boolean sealsFace(BlockData data, BlockFace face) {
        return data != null
                && (data.isOccluding() || SOLID_PANELS.contains(data.getMaterial()))
                && data.isFaceSturdy(face, BlockSupport.FULL);
    }
}
