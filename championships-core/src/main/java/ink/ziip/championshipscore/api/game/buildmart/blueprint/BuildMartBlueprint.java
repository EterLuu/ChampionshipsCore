package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartCopperPolicy;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.configuration.InvalidConfigurationException;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable build order: a named, star-rated set of {@link BlueprintBlock}s placed relative to a build
 * anchor. Stars drive normal-order draw weighting and completion points. Three-star blueprints are also
 * eligible for the golden plot, where the order context overrides their score to 7 stars. The block count
 * is the denominator for the completion ratio used when scoring partial builds.
 */
@Getter
public class BuildMartBlueprint {
    private final String id;
    private final String displayName;
    private final int stars;
    private final List<BlueprintBlock> blocks;
    @Getter(lombok.AccessLevel.NONE)
    private final List<BlockData> comparisonStates;

    public BuildMartBlueprint(String id, String displayName, int stars, List<BlueprintBlock> blocks) {
        this.id = id;
        this.displayName = displayName;
        this.stars = stars;
        validateBlocks(blocks);
        this.blocks = List.copyOf(blocks);
        this.comparisonStates = blocks.stream().map(b -> normalized(b.getBlockData())).toList();
    }

    public int blockCount() {
        return blocks.size();
    }

    /**
     * Counts how many of this blueprint's blocks are already correctly placed at {@code anchor} (the
     * build-zone origin). Matching uses {@link #blockMatches(BlockData, BlockData)} with the
     * visual-equivalence rules below. Grass, dirt, nylium and netherrack remain distinct materials,
     * regardless of neighbouring blocks. Extra blocks the player placed elsewhere are ignored.
     */
    public int countMatching(Location anchor) {
        return compare(anchor).matched();
    }

    public record Comparison(int matched, int missing, int wrongMaterial, int wrongState,
                             List<String> positions) {}

    /** One authoritative world scan; examples use zero-based blueprint coordinates above the floor. */
    public Comparison compare(Location anchor) {
        World world = anchor.getWorld();
        int matched = 0, missing = 0, wrongMaterial = 0, wrongState = 0;
        List<String> positions = new ArrayList<>(3);
        for (int i = 0; i < blocks.size(); i++) {
            BlueprintBlock b = blocks.get(i);
            BlockData placed = world == null ? null : normalized(world.getBlockAt(
                    anchor.getBlockX() + b.getX(), anchor.getBlockY() + b.getY(), anchor.getBlockZ() + b.getZ()).getBlockData());
            BlockData reference = comparisonStates.get(i);
            if (placed == null || isAir(placed)) missing++;
            else if (reference.getMaterial() != placed.getMaterial()) wrongMaterial++;
            else if (!normalizedMatches(reference, placed)) wrongState++;
            else { matched++; continue; }
            if (positions.size() < 3) positions.add("(" + b.getX() + "," + b.getY() + "," + b.getZ() + ")");
        }
        return new Comparison(matched, missing, wrongMaterial, wrongState, List.copyOf(positions));
    }

    /**
     * Whether a placed block satisfies a blueprint reference. Strict {@link BlockData#matches} for most
     * blocks, with these visual-equivalence relaxations (every other state stays strict):
     * <ul>
     *   <li>Trapdoor closed ({@code open=false}): a flat panel, so {@code facing} is ignored; {@code half}
     *       must match.</li>
     *   <li>Trapdoor open ({@code open=true}): a vertical full-height panel, so {@code half} is ignored;
     *       {@code facing} must match.</li>
     *   <li>Fence: connections to neighbouring blocks are ignored; material and waterlogged state remain
     *       strict.</li>
     *   <li>Fence gate: 180°-symmetric, so {@code facing} is axis-only ({@code N≡S, E≡W}) in any state;
     *       {@code in_wall}, {@code open} and {@code powered} stay strict.</li>
     *   <li>Ageable blocks ignore their current growth age. All their other block-data properties still
     *       have to match.</li>
     *   <li>Waxed and unwaxed forms of the same copper block are equivalent; oxidation stage and all other
     *       block-data properties remain strict.</li>
     * </ul>
     * Leaf distance to logs is ignored; leaf species, persistence and waterlogging stay strict.
     * Doors are intentionally left strict.
     */
    static boolean blockMatches(BlockData reference, BlockData placed) {
        return normalizedMatches(normalized(reference), normalized(placed));
    }

    private static boolean normalizedMatches(BlockData reference, BlockData placed) {
        if (reference.getMaterial() != placed.getMaterial()) return false;
        if (reference instanceof Fence refFence && placed instanceof Fence placedFence) {
            return reference.getMaterial() == placed.getMaterial()
                    && refFence.isWaterlogged() == placedFence.isWaterlogged();
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

    private static BlockData normalized(BlockData data) {
        data = BuildMartCopperPolicy.withoutWax(data);
        if (data instanceof Ageable ageable && ageable.getAge() != 0) {
            Ageable copy = (Ageable) data.clone();
            copy.setAge(0);
            data = copy;
        }
        // Leaf distance is computed from surrounding logs and has no visual effect.
        // Persistent, waterlogged and species remain strict.
        if (data instanceof Leaves leaves && leaves.getDistance() != 7) {
            Leaves copy = (Leaves) data.clone();
            copy.setDistance(7);
            data = copy;
        }
        return data;
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
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return fromYaml(id, yaml, BlueprintBlock::parse);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, "-", "加载", "蓝图",
                    "文件=" + file.getName() + " 已整张拒绝: " + exception.getMessage()));
            return null;
        }
    }

    static BuildMartBlueprint fromYaml(String id, YamlConfiguration yaml, Function<String, BlueprintBlock> parser) {
        Object rawBlocks = yaml.get("blocks");
        if (!(rawBlocks instanceof List<?> rows) || rows.isEmpty())
            throw new IllegalArgumentException("blocks 必须为非空列表");
        if (yaml.contains("name") && !yaml.isString("name"))
            throw new IllegalArgumentException("name 必须为文本");
        if (yaml.contains("stars") && !yaml.isInt("stars"))
            throw new IllegalArgumentException("stars 必须为整数");
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Object raw = rows.get(index);
            BlueprintBlock block = raw instanceof String text ? parser.apply(text) : null;
            if (block == null) throw new IllegalArgumentException("blocks 第 " + (index + 1) + " 项格式/方块状态无效");
            blocks.add(block);
        }
        return new BuildMartBlueprint(id, yaml.getString("name", id), yaml.getInt("stars", 1), blocks);
    }

    private static boolean isAir(BlockData data) {
        return switch (data.getMaterial()) {
            case AIR, CAVE_AIR, VOID_AIR -> true;
            default -> false;
        };
    }

    private static void validateBlocks(List<BlueprintBlock> blocks) {
        if (blocks.isEmpty()) throw new IllegalArgumentException("blocks 不能为空");
        Set<Integer> occupied = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            BlueprintBlock b = blocks.get(index);
            String prefix = "blocks 第 " + (index + 1) + " 项";
            if (b == null || b.getBlockData() == null || isAir(b.getBlockData()))
                throw new IllegalArgumentException(prefix + "为空气或无效方块");
            if (b.getX() < 0 || b.getX() > 6 || b.getY() < 0 || b.getY() > 6 || b.getZ() < 0 || b.getZ() > 6)
                throw new IllegalArgumentException(prefix + "坐标越界，必须为 0–6");
            if (!occupied.add(b.getX() * 49 + b.getY() * 7 + b.getZ()))
                throw new IllegalArgumentException(prefix + "坐标重复");
        }
    }
}
