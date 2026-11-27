package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;

public final class WarehouseRoom {
    public static final String STRUCTURE = "iamnotarobot/verification-warehouse.nbt";
    public static final int MIN_X = -10, MAX_X = 28, MIN_Z = -4, EXIT_Z = 14, END_Z = 18;
    public static final int STRUCT_MIN_X = EntryTrack.MIN_X,
            STRUCT_MIN_Y = EntryTrack.MIN_Y,
            STRUCT_WIDTH = EntryTrack.WIDTH,
            STRUCT_HEIGHT = 29,
            STRUCT_LENGTH = END_Z - MIN_Z + 1;
    public static final ArenaGrid GRID =
            i ->
                    VerificationCourse.GRID
                            .origin(i)
                            .add(new Vector(STRUCT_MIN_X, STRUCT_MIN_Y, MIN_Z));

    public static final int RACKS = 8;
    public static final int BAYS_PER_RACK = 3;
    public static final int SLOT_COUNT = RACKS * BAYS_PER_RACK;

    /**
     * Broad curated pool of iron-pickaxe-mineable solid full blocks (excluding white concrete used
     * for quantity clues, polished deepslate used for rack frames, gravity blocks, block entities,
     * oxidizing copper, and diamond-tier blocks). The first {@link #SLOT_COUNT} entries match the
     * bundled template's resting display shelves.
     */
    public static final List<Material> STOCK_POOL =
            List.of(
                    // First 24: default showcase stock matching verification-warehouse.nbt
                    Material.AMETHYST_BLOCK,
                    Material.WAXED_COPPER_BLOCK,
                    Material.END_STONE_BRICKS,
                    Material.PRISMARINE,
                    Material.RED_TERRACOTTA,
                    Material.BLUE_CONCRETE,
                    Material.POLISHED_GRANITE,
                    Material.PURPUR_BLOCK,
                    Material.GOLD_BLOCK,
                    Material.EMERALD_BLOCK,
                    Material.CUT_SANDSTONE,
                    Material.LAPIS_BLOCK,
                    Material.BRICKS,
                    Material.WAXED_OXIDIZED_COPPER,
                    Material.YELLOW_TERRACOTTA,
                    Material.DIAMOND_BLOCK,
                    Material.RED_NETHER_BRICKS,
                    Material.LIME_CONCRETE,
                    Material.QUARTZ_BRICKS,
                    Material.ORANGE_GLAZED_TERRACOTTA,
                    Material.DARK_PRISMARINE,
                    Material.CALCITE,
                    Material.CYAN_GLAZED_TERRACOTTA,
                    Material.IRON_BLOCK,
                    // Stone, deepslate, tuff, basalt, sandstone, brick, nether, end & ice variants
                    Material.STONE,
                    Material.COBBLESTONE,
                    Material.MOSSY_COBBLESTONE,
                    Material.STONE_BRICKS,
                    Material.MOSSY_STONE_BRICKS,
                    Material.CRACKED_STONE_BRICKS,
                    Material.CHISELED_STONE_BRICKS,
                    Material.GRANITE,
                    Material.DIORITE,
                    Material.POLISHED_DIORITE,
                    Material.ANDESITE,
                    Material.POLISHED_ANDESITE,
                    Material.COBBLED_DEEPSLATE,
                    Material.DEEPSLATE_BRICKS,
                    Material.DEEPSLATE_TILES,
                    Material.CHISELED_DEEPSLATE,
                    Material.TUFF,
                    Material.POLISHED_TUFF,
                    Material.TUFF_BRICKS,
                    Material.CHISELED_TUFF_BRICKS,
                    Material.MUD_BRICKS,
                    Material.SANDSTONE,
                    Material.CHISELED_SANDSTONE,
                    Material.RED_SANDSTONE,
                    Material.CHISELED_RED_SANDSTONE,
                    Material.CUT_RED_SANDSTONE,
                    Material.PRISMARINE_BRICKS,
                    Material.NETHERRACK,
                    Material.NETHER_BRICKS,
                    Material.CHISELED_NETHER_BRICKS,
                    Material.BLACKSTONE,
                    Material.POLISHED_BLACKSTONE,
                    Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.END_STONE,
                    Material.PURPUR_PILLAR,
                    Material.QUARTZ_BLOCK,
                    Material.QUARTZ_PILLAR,
                    Material.DRIPSTONE_BLOCK,
                    Material.BASALT,
                    Material.POLISHED_BASALT,
                    Material.SMOOTH_BASALT,
                    Material.BONE_BLOCK,
                    Material.PACKED_ICE,
                    Material.BLUE_ICE,
                    // Mineral & waxed copper variants
                    Material.COAL_BLOCK,
                    Material.REDSTONE_BLOCK,
                    Material.RAW_IRON_BLOCK,
                    Material.RAW_COPPER_BLOCK,
                    Material.RAW_GOLD_BLOCK,
                    Material.WAXED_EXPOSED_COPPER,
                    Material.WAXED_WEATHERED_COPPER,
                    Material.WAXED_CUT_COPPER,
                    Material.WAXED_EXPOSED_CUT_COPPER,
                    Material.WAXED_WEATHERED_CUT_COPPER,
                    Material.WAXED_OXIDIZED_CUT_COPPER,
                    Material.WAXED_CHISELED_COPPER,
                    // Terracotta
                    Material.TERRACOTTA,
                    Material.WHITE_TERRACOTTA,
                    Material.ORANGE_TERRACOTTA,
                    Material.MAGENTA_TERRACOTTA,
                    Material.LIGHT_BLUE_TERRACOTTA,
                    Material.LIME_TERRACOTTA,
                    Material.PINK_TERRACOTTA,
                    Material.GRAY_TERRACOTTA,
                    Material.LIGHT_GRAY_TERRACOTTA,
                    Material.CYAN_TERRACOTTA,
                    Material.PURPLE_TERRACOTTA,
                    Material.BLUE_TERRACOTTA,
                    Material.BROWN_TERRACOTTA,
                    Material.GREEN_TERRACOTTA,
                    Material.BLACK_TERRACOTTA,
                    // Glazed terracotta
                    Material.WHITE_GLAZED_TERRACOTTA,
                    Material.MAGENTA_GLAZED_TERRACOTTA,
                    Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
                    Material.YELLOW_GLAZED_TERRACOTTA,
                    Material.LIME_GLAZED_TERRACOTTA,
                    Material.PINK_GLAZED_TERRACOTTA,
                    Material.GRAY_GLAZED_TERRACOTTA,
                    Material.LIGHT_GRAY_GLAZED_TERRACOTTA,
                    Material.PURPLE_GLAZED_TERRACOTTA,
                    Material.BLUE_GLAZED_TERRACOTTA,
                    Material.BROWN_GLAZED_TERRACOTTA,
                    Material.GREEN_GLAZED_TERRACOTTA,
                    Material.RED_GLAZED_TERRACOTTA,
                    Material.BLACK_GLAZED_TERRACOTTA,
                    // Concrete (excluding WHITE_CONCRETE reserved for quantity console stacks)
                    Material.ORANGE_CONCRETE,
                    Material.MAGENTA_CONCRETE,
                    Material.LIGHT_BLUE_CONCRETE,
                    Material.YELLOW_CONCRETE,
                    Material.PINK_CONCRETE,
                    Material.GRAY_CONCRETE,
                    Material.LIGHT_GRAY_CONCRETE,
                    Material.CYAN_CONCRETE,
                    Material.PURPLE_CONCRETE,
                    Material.BROWN_CONCRETE,
                    Material.GREEN_CONCRETE,
                    Material.RED_CONCRETE,
                    Material.BLACK_CONCRETE);

    public static final List<Material> DEFAULT_STOCK =
            List.copyOf(STOCK_POOL.subList(0, SLOT_COUNT));

    /**
     * Returns the stock slot index ({@code 0..23}) for a mineable shelf block on the 8 double-sided
     * 3-bay industrial racks, or {@code -1} if {@code (x, y, z)} is not a shelf stock position.
     * Each rack stocks 3 distinct materials across {@code cx-1..cx+1} on both lower ({@code y=1})
     * and upper ({@code y=3}) tiers and both north ({@code cz-1}) and south ({@code cz+1}) faces.
     */
    public static int stockSlot(int x, int y, int z) {
        if (y != 1 && y != 3) return -1;
        for (int column = 0; column < 4; column++) {
            int bay = x - (column * 6 - 1);
            if (bay < 0 || bay >= BAYS_PER_RACK) continue;
            if (z == -1 || z == 1) return (2 * column) * BAYS_PER_RACK + bay;
            if (z == 7 || z == 9) return (2 * column + 1) * BAYS_PER_RACK + bay;
        }
        return -1;
    }

    public static Material stock(int x, int y, int z, List<Material> stock) {
        int slot = stockSlot(x, y, z);
        return slot >= 0 && slot < stock.size() ? stock.get(slot) : null;
    }

    public static boolean miner(int slot) {
        return slot == 1 || slot == 2;
    }

    public static int chestX(int slot) {
        return slot == 0 ? -8 : 26;
    }

    public static int barrelX(int index) {
        return 15 - 4 * index;
    }

    public static Vector spawn(int slot) {
        return new Vector(EntryTrack.forSlot(slot).center() + .5, 1, -2.5);
    }

    public static boolean contains(int slot, int x, int y, int z, boolean cleared) {
        if (y < 1 || y >= 8 || z < EntryTrack.FINISH_WALL_Z || z > END_Z) return false;
        if (z <= MIN_Z) return Math.abs(x - EntryTrack.forSlot(slot).center()) <= 1;
        if (x <= MIN_X || x >= MAX_X) return false;
        if (cleared) return true;
        if (z >= EXIT_Z) return false;
        if (slot == 0) return x < -3;
        if (slot == 3) return x > 21;
        return x > -3 && x < 21;
    }

    private static final List<BoundingBox> ENTRANCES =
            List.of(-6, 4, 14, 24).stream()
                    .map(x -> new BoundingBox(x - 1, 1, EntryTrack.FINISH_WALL_Z, x + 2, 4, MIN_Z))
                    .toList();
    private static final List<BoundingBox> EXITS =
            List.of(
                    new BoundingBox(-7, 1, EXIT_Z, -4, 4, EXIT_Z + 1),
                    new BoundingBox(7, 1, EXIT_Z, 12, 4, EXIT_Z + 1),
                    new BoundingBox(23, 1, EXIT_Z, 26, 4, EXIT_Z + 1));

    static boolean gateOccupied(BoundingBox box, boolean entrances) {
        return (entrances ? ENTRANCES : EXITS).stream().anyMatch(box::overlaps);
    }

    public static void setGates(World world, Vector origin, boolean entrances, boolean open) {
        for (var gate : entrances ? ENTRANCES : EXITS)
            VerificationCourse.setGateBlocks(world, origin, gate, open);
    }

    private WarehouseRoom() {}
}
