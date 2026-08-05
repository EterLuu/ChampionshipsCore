package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable build order: a named, star-rated set of {@link BlueprintBlock}s placed relative to a build
 * anchor. Stars drive both the pool it belongs to (1–5 normal, 7 golden) and the points a completion is
 * worth. The block count is the denominator for the completion ratio used when scoring partial builds.
 */
@Getter
public class BuildMartBlueprint {
    private final String id;
    private final String displayName;
    private final int stars;
    private final List<BlueprintBlock> blocks;

    public BuildMartBlueprint(String id, String displayName, int stars, List<BlueprintBlock> blocks) {
        this.id = id;
        this.displayName = displayName;
        this.stars = stars;
        this.blocks = List.copyOf(blocks);
    }

    public int blockCount() {
        return blocks.size();
    }

    /**
     * Counts how many of this blueprint's blocks are already correctly placed at {@code anchor} (the
     * build-zone origin). Matching uses {@link #blockMatches(BlockData, BlockData, boolean)}: strict on
     * {@link BlockData} except for a few visual-equivalence relaxations (trapdoors, fence gates, and
     * covered-above grass/dirt or nylium/netherrack swaps). Extra blocks the player placed elsewhere are
     * ignored.
     */
    public int countMatching(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) return 0;
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        int matched = 0;
        for (BlueprintBlock b : blocks) {
            int x = baseX + b.getX();
            int y = baseY + b.getY();
            int z = baseZ + b.getZ();
            Block block = world.getBlockAt(x, y, z);
            boolean covered = world.getBlockAt(x, y + 1, z).getType().isOccluding();
            if (blockMatches(b.getBlockData(), block.getBlockData(), covered)) {
                matched++;
            }
        }
        return matched;
    }

    /**
     * Whether a placed block satisfies a blueprint reference. Strict {@link BlockData#matches} for most
     * blocks, with these visual-equivalence relaxations (every other state stays strict):
     * <ul>
     *   <li>Trapdoor closed ({@code open=false}): a flat panel, so {@code facing} is ignored; {@code half}
     *       must match.</li>
     *   <li>Trapdoor open ({@code open=true}): a vertical full-height panel, so {@code half} is ignored;
     *       {@code facing} must match.</li>
     *   <li>Fence gate: 180°-symmetric, so {@code facing} is axis-only ({@code N≡S, E≡W}) in any state;
     *       {@code in_wall}, {@code open} and {@code powered} stay strict.</li>
     *   <li>Covered ({@code covered=true}, i.e. an occluding block sits above): grass block ↔ dirt, and
     *       warped/crimson nylium ↔ netherrack, are interchangeable (the occluded top face is the only
     *       difference; these blocks are stateless). The two nylium colours are not interchangeable.</li>
     * </ul>
     * Doors are intentionally left strict.
     */
    private static boolean blockMatches(BlockData reference, BlockData placed, boolean covered) {
        if (covered && isCoveredSubstitution(reference.getMaterial(), placed.getMaterial())) {
            return true;
        }
        if (reference instanceof TrapDoor refTrap && placed instanceof TrapDoor placedTrap) {
            if (reference.getMaterial() != placed.getMaterial()) return false;
            if (refTrap.isOpen() != placedTrap.isOpen()) return false;
            if (refTrap.isWaterlogged() != placedTrap.isWaterlogged()) return false;
            if (refTrap.isPowered() != placedTrap.isPowered()) return false;
            if (!refTrap.isOpen()) {
                // Closed: flat panel, facing is visually irrelevant; half (top/bottom surface) must match.
                return refTrap.getHalf() == placedTrap.getHalf();
            }
            // Open: vertical full-height panel, half is visually irrelevant; facing (hinge side) must match.
            return refTrap.getFacing() == placedTrap.getFacing();
        }
        if (reference instanceof Gate refGate && placed instanceof Gate placedGate) {
            if (reference.getMaterial() != placed.getMaterial()) return false;
            // 180°-symmetric: facing is axis-only (N≡S, E≡W); in_wall, open, powered stay strict.
            return sameFacingAxis(refGate.getFacing(), placedGate.getFacing())
                    && refGate.isOpen() == placedGate.isOpen()
                    && refGate.isInWall() == placedGate.isInWall()
                    && refGate.isPowered() == placedGate.isPowered();
        }
        return reference.matches(placed);
    }

    /**
     * Whether {@code reference} and {@code placed} are an allowed covered-above substitution (grass block
     * ↔ dirt; warped/crimson nylium ↔ netherrack). Same-material pairs return false (handled by the exact
     * match), and warped ↔ crimson is rejected (different colours).
     */
    private static boolean isCoveredSubstitution(Material reference, Material placed) {
        return isMaterialPair(reference, placed, Material.GRASS_BLOCK, Material.DIRT)
                || isMaterialPair(reference, placed, Material.WARPED_NYLIUM, Material.NETHERRACK)
                || isMaterialPair(reference, placed, Material.CRIMSON_NYLIUM, Material.NETHERRACK);
    }

    /** Whether {@code a} and {@code b} are exactly {x, y} in either order. */
    private static boolean isMaterialPair(Material a, Material b, Material x, Material y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    /** Whether two horizontal facings share an axis (N≡S, E≡W). */
    private static boolean sameFacingAxis(BlockFace a, BlockFace b) {
        return isNorthSouth(a) == isNorthSouth(b);
    }

    private static boolean isNorthSouth(BlockFace facing) {
        return facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
    }

    /** Completion fraction in [0,1] of this blueprint built at {@code anchor}. */
    public double completionRatio(Location anchor) {
        int total = blockCount();
        if (total == 0) return 1.0;
        return (double) countMatching(anchor) / total;
    }

    /** Loads a blueprint from a YAML file; returns {@code null} when the file is missing/invalid. */
    @Nullable
    public static BuildMartBlueprint load(ChampionshipsCore plugin, File file) {
        if (file == null || !file.isFile()) return null;
        String id = file.getName().toLowerCase().endsWith(".yml")
                ? file.getName().substring(0, file.getName().length() - 4)
                : file.getName();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String displayName = yaml.getString("name", id);
        int stars = yaml.getInt("stars", 1);
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (String raw : yaml.getStringList("blocks")) {
            BlueprintBlock block = BlueprintBlock.parse(raw);
            if (block != null) blocks.add(block);
        }
        if (blocks.isEmpty()) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, "-", "加载", "蓝图",
                    "蓝图=" + id + " 没有有效方块，已跳过"));
            return null;
        }
        return new BuildMartBlueprint(id, displayName, stars, blocks);
    }

}
