package ink.ziip.championshipscore.api.game.riptiderush.course;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;

import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Material;
import org.bukkit.World;

import java.io.*;
import java.util.*;

/**
 * Map-owned block and block-entity snapshot. Copies share immutable bytes, never a mutable world
 * region/file.
 */
public record RiptideBlueprint(
        String schematic, int extent, int width, int height, List<String> floor) {
    public RiptideBlueprint {
        if (schematic == null
                || schematic.length() > 1_000_000
                || extent < 0
                || extent > RiptideWorkshop.BUILDING_EXTENT
                || width < 3
                || width > 29
                || width % 2 == 0
                || height < 1
                || height > 16)
            throw new IllegalArgumentException(MessageConfig.codeText("riptide-blueprint.text-1"));
        Base64.getDecoder().decode(schematic);
        floor = List.copyOf(floor);
        if (floor.size() > 225)
            throw new IllegalArgumentException(MessageConfig.codeText("riptide-blueprint.text-2"));
    }

    public Map<String, Object> serialize() {
        return Map.of(
                "schematic",
                schematic,
                "extent",
                extent,
                "width",
                width,
                "height",
                height,
                "floor",
                floor);
    }

    public static RiptideBlueprint parse(Map<?, ?> map) {
        return new RiptideBlueprint(
                String.valueOf(map.get("schematic")),
                number(map, "extent"),
                number(map, "width"),
                number(map, "height"),
                map.get("floor") instanceof List<?> list
                        ? list.stream().map(Object::toString).toList()
                        : List.of());
    }

    private static int number(Map<?, ?> map, String key) {
        return Integer.parseInt(String.valueOf(map.get(key)));
    }

    public List<Material> floorMaterials() {
        return floor.stream().map(Material::valueOf).toList();
    }

    public static RiptideBlueprint capture(
            RiptideRushConfig config, int step, RiptideLevelType type) {
        var g = config.resolveGeometry();
        int height = config.getClearHeight(), radius = RiptideWorkshop.BUILDING_EXTENT;
        World world = g.centerAt(step).getWorld();
        int outer = RiptideWorkshop.halfWidth(config, g);
        // Catch construction just beyond the visible work area instead of silently dropping those
        // blocks.
        for (int f = -radius - 1; f <= radius + 1; f++)
            for (int l = -outer - 1; l <= outer + 1; l++)
                for (int y = 1; y <= height + 1; y++) {
                    if (Math.abs(f) <= radius && Math.abs(l) <= outer && y <= height) continue;
                    // The four guide posts are outside the saved volume.
                    if (Math.abs(f) == radius + 1
                            && Math.abs(l) == outer + 1
                            && y <= height
                            && world.getBlockAt(
                                                    g.blockX(step + f, l),
                                                    g.floorY() + y,
                                                    g.blockZ(step + f, l))
                                            .getType()
                                    == Material.LIGHT_BLUE_STAINED_GLASS) continue;
                    if (!world.getBlockAt(
                                    g.blockX(step + f, l), g.floorY() + y, g.blockZ(step + f, l))
                            .getType()
                            .isAir())
                        throw new IllegalArgumentException(
                                MessageConfig.codeText("riptide-blueprint.capture-1"));
                }
        List<String> floor = new ArrayList<>();
        if (type == RiptideLevelType.COLOR_FLOOR) {
            for (int f = -g.halfLength(); f <= g.halfLength(); f++)
                for (int l = -g.halfWidth(); l <= g.halfWidth(); l++)
                    floor.add(
                            world.getBlockAt(
                                            g.blockX(step + f, l),
                                            g.floorY(),
                                            g.blockZ(step + f, l))
                                    .getType()
                                    .name());
            validateFloor(
                    floor.stream().map(Material::valueOf).toList(), g.raftWidth(), g.raftLength());
        }
        if (type == RiptideLevelType.MATH) {
            int gateSide = g.halfWidth() + 1;
            for (int y = 1; y <= 3; y++) {
                if (world.getBlockAt(g.blockX(step, 0), g.floorY() + y, g.blockZ(step, 0))
                        .isPassable())
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("riptide-blueprint.capture-2"));
                if (world.getBlockAt(
                                                g.blockX(step, gateSide),
                                                g.floorY() + y,
                                                g.blockZ(step, gateSide))
                                        .getType()
                                != Material.RED_CONCRETE
                        || world.getBlockAt(
                                                g.blockX(step, -gateSide),
                                                g.floorY() + y,
                                                g.blockZ(step, -gateSide))
                                        .getType()
                                != Material.LIGHT_BLUE_CONCRETE)
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("riptide-blueprint.capture-3"));
            }
        }
        boolean[][][] solid =
                type == RiptideLevelType.PASS
                        ? null
                        : new boolean[radius * 2 + 1][g.raftWidth()][height * 2];
        int extent = 0, blocks = 0;
        var region =
                new CuboidRegion(
                        BlockVector3.at(-outer, 1, -radius),
                        BlockVector3.at(outer, height, radius));
        try (var clipboard = new BlockArrayClipboard(region);
                var edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            clipboard.setOrigin(BlockVector3.ZERO);
            for (var local : region) {
                int f = local.z(), l = local.x(), y = local.y();
                var block =
                        world.getBlockAt(
                                g.blockX(step + f, l), g.floorY() + y, g.blockZ(step + f, l));
                clipboard.setBlock(
                        local,
                        edit.getFullBlock(
                                BlockVector3.at(block.getX(), block.getY(), block.getZ())));
                if (!block.getType().isAir()) {
                    extent = Math.max(extent, Math.abs(f));
                    blocks++;
                }
                if (solid != null && Math.abs(l) <= g.halfWidth())
                    markCollision(
                            solid[f + radius][l + g.halfWidth()],
                            y - 1,
                            block.getCollisionShape().getBoundingBoxes());
            }
            validateMechanicClearance(solid, type);
            if (type == RiptideLevelType.PASS && blocks == 0)
                throw new IllegalArgumentException(
                        MessageConfig.codeText("riptide-blueprint.capture-5"));
            var bytes = new ByteArrayOutputStream();
            try (var writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(bytes)) {
                writer.write(clipboard);
            }
            return new RiptideBlueprint(
                    Base64.getEncoder().encodeToString(bytes.toByteArray()),
                    extent,
                    outer * 2 + 1,
                    height,
                    floor);
        } catch (IOException | com.sk89q.worldedit.WorldEditException error) {
            throw new IllegalStateException(
                    MessageConfig.codeText("riptide-blueprint.capture-4"), error);
        }
    }

    /** Bukkit collision boxes are block-local; fences can extend above the block itself. */
    static void markCollision(
            boolean[] column, int blockY, Collection<org.bukkit.util.BoundingBox> boxes) {
        for (var box : boxes) {
            if (box.getVolume() <= 0) continue;
            int from = Math.max(0, (int) Math.floor((blockY + box.getMinY()) * 2 + 1e-7));
            int to = Math.min(column.length, (int) Math.ceil((blockY + box.getMaxY()) * 2 - 1e-7));
            for (int y = from; y < to; y++) column[y] = true;
        }
    }

    /** Only mechanic-specific headroom; building traversal is left to the map author. */
    static void validateMechanicClearance(boolean[][][] solid, RiptideLevelType type) {
        if (type == RiptideLevelType.PASS) return;
        int length = solid.length, width = solid[0].length;
        if (type == RiptideLevelType.MATH) {
            for (int x = 0; x < width; x++)
                for (int y = 0; y < 6; y++)
                    if (x != width / 2 && solid[length / 2][x][y])
                        throw new IllegalArgumentException(
                                MessageConfig.codeText(
                                        "riptide-blueprint.validate-mechanic-clearance-1"));
        }
        if (type == RiptideLevelType.COLOR_FLOOR) {
            for (var slice : solid)
                for (var column : slice)
                    if (!clear(column, 0, 6))
                        throw new IllegalArgumentException(
                                MessageConfig.codeText(
                                        "riptide-blueprint.validate-mechanic-clearance-2"));
            return;
        }
    }

    private static boolean clear(boolean[] column, int from, int to) {
        if (from < 0 || to > column.length) return false;
        for (int y = from; y < to; y++) if (column[y]) return false;
        return true;
    }

    public static void validateFloor(List<Material> floor, int width, int length) {
        var allowed = new HashSet<Material>();
        for (var theme : RiptideColorFloorRun.Theme.values())
            allowed.addAll(RiptideColorFloorRun.materials(theme));
        if (floor.size() != width * length || !allowed.containsAll(floor))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-blueprint.validate-floor-1"));
        var palette = new HashSet<>(floor);
        if (palette.size() < 2)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-blueprint.validate-floor-2"));
        for (var target : palette)
            for (int i = 0; i < floor.size(); i++) {
                int nearest = Integer.MAX_VALUE;
                for (int j = 0; j < floor.size(); j++)
                    if (floor.get(j) == target)
                        nearest =
                                Math.min(
                                        nearest,
                                        (i % width - j % width) * (i % width - j % width)
                                                + (i / width - j / width)
                                                        * (i / width - j / width));
                if (nearest > 36)
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("riptide-blueprint.validate-floor-3"));
            }
    }

    public void paste(RiptideCourseGeometry g, int step) {
        paste(g, step, false);
    }

    public void paste(RiptideCourseGeometry g, int step, boolean mirrored) {
        paste(g, step, mirrored, false);
    }

    public void paste(RiptideCourseGeometry g, int step, boolean mirrored, boolean math) {
        try (var reader =
                        BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(
                                new ByteArrayInputStream(Base64.getDecoder().decode(schematic)));
                var clipboard = reader.read();
                var edit =
                        WorldEdit.getInstance()
                                .newEditSession(BukkitAdapter.adapt(g.centerAt(step).getWorld()))) {
            var dimensions = clipboard.getDimensions();
            if (dimensions.x() != width
                    || dimensions.y() != height
                    || (dimensions.z() != 7
                            && dimensions.z() != RiptideWorkshop.BUILDING_EXTENT * 2 + 1))
                throw new IllegalArgumentException(
                        MessageConfig.codeText("riptide-blueprint.paste-2"));
            if (math)
                for (var position : clipboard.getRegion()) {
                    if (clipboard.getBlock(position).getBlockType()
                            == com.sk89q.worldedit.world.block.BlockTypes.CYAN_CONCRETE)
                        clipboard.setBlock(
                                position,
                                com.sk89q.worldedit.world.block.BlockTypes.LIGHT_BLUE_CONCRETE
                                        .getDefaultState());
                }
            var holder = new ClipboardHolder(clipboard);
            holder.setTransform(
                    new AffineTransform()
                            .rotateY(Math.toDegrees(Math.atan2(g.stepX(), g.stepZ())))
                            .scale(mirrored ? -1 : 1, 1, 1));
            Operations.complete(
                    holder.createPaste(edit)
                            .to(BlockVector3.at(g.blockX(step, 0), g.floorY(), g.blockZ(step, 0)))
                            // The corridor was cleared first. Empty padding must not erase a
                            // neighbouring building.
                            .ignoreAirBlocks(true)
                            .copyEntities(false)
                            .build());
        } catch (IOException | com.sk89q.worldedit.WorldEditException error) {
            throw new IllegalStateException(
                    MessageConfig.codeText("riptide-blueprint.paste-1"), error);
        }
    }
}
