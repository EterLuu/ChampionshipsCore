package ink.ziip.championshipscore.integration.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.session.ClipboardHolder;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class WorldEditManager extends BaseManager {
    private final WorldEdit worldEdit;

    public WorldEditManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        worldEdit = WorldEdit.getInstance();
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {

    }

    public Vector[] getPlayerSelection(@NotNull Player player, boolean blockVector) {
        Vector[] vectors = new Vector[2];
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        RegionSelector selector = worldEdit.getSessionManager().get(bukkitPlayer).getRegionSelector(bukkitPlayer.getWorld());
        BlockVector3 v1 = selector.getRegion().getMinimumPoint();
        BlockVector3 v2 = selector.getRegion().getMaximumPoint();
        if (blockVector) {
            vectors[0] = new Vector(v1.x(), v1.y(), v1.z());
            vectors[1] = new Vector(v2.x(), v2.y(), v2.z());
        } else {
            int x1 = Math.max(v1.x(), v2.x()) + 1;
            int x2 = Math.min(v1.x(), v2.x());
            int y1 = Math.max(v1.y(), v2.y()) + 1;
            int y2 = Math.min(v1.y(), v2.y());
            int z1 = Math.max(v1.z(), v2.z()) + 1;
            int z2 = Math.min(v1.z(), v2.z());
            vectors[0] = new Vector(x1, y1, z1);
            vectors[1] = new Vector(x2, y2, z2);
        }
        return vectors;
    }

    /**
     * Saves {@code player}'s current WorldEdit selection to {@code file} as a Sponge schematic, capturing
     * blocks and entities. The clipboard origin is set to the selection's minimum corner so the schematic
     * pastes back predictably via {@link #pasteSchematic}.
     *
     * @throws Exception if there is no selection or the write fails
     */
    public void saveSelectionAsSchematic(@NotNull Player player, @NotNull File file) throws Exception {
        saveSelectionAsSchematic(player, file, true);
    }

    /** Saves only the selected blocks and block-entity state, without copying living entities. */
    public void saveSelectionAsBlockSchematic(@NotNull Player player, @NotNull File file) throws Exception {
        saveSelectionAsSchematic(player, file, false);
    }

    private void saveSelectionAsSchematic(@NotNull Player player, @NotNull File file, boolean copyEntities)
            throws Exception {
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        com.sk89q.worldedit.world.World weWorld = bukkitPlayer.getWorld();
        RegionSelector selector = worldEdit.getSessionManager().get(bukkitPlayer).getRegionSelector(weWorld);
        if (!selector.isDefined())
            throw new IllegalStateException("WorldEdit 选区未完整设置");
        Region region = selector.getRegion();

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(region.getMinimumPoint());
        try (EditSession editSession = worldEdit.newEditSession(weWorld)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(copyEntities);
            Operations.complete(copy);
        }

        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
        }
    }

    /**
     * Block dimensions {@code (width, height, length)} of the schematic in {@code file} — i.e. how far it
     * extends along +X/+Y/+Z from its minimum corner. Lets callers derive a copy's bounding box from its
     * paste origin without maintaining a separate region.
     *
     * @throws IOException if the file is missing/unreadable or the format cannot be detected
     */
    public Vector getSchematicDimensions(@NotNull File file) throws IOException {
        if (!file.isFile()) throw new IOException("schematic not found: " + file.getName());
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("unknown schematic format: " + file.getName());
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            BlockVector3 dimensions = clipboard.getRegion().getDimensions();
            return new Vector(dimensions.x(), dimensions.y(), dimensions.z());
        }
    }

    /**
     * Reads the exact non-air block inventory stored in a schematic without pasting it into a world.
     * Material manifests use this so their contents always describe the refill source of truth rather
     * than whatever an administrator may currently be editing in the live map.
     */
    public @NotNull SchematicBlockInventory readSchematicBlockInventory(@NotNull File file) throws IOException {
        if (!file.isFile()) throw new IOException("schematic not found: " + file.getName());
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("unknown schematic format: " + file.getName());

        try (ClipboardReader reader = format.getReader(new FileInputStream(file));
             Clipboard clipboard = reader.read()) {
            Map<String, Long> materials = new TreeMap<>();
            Map<String, Long> blockData = new TreeMap<>();
            long nonAirBlocks = 0L;
            for (BlockVector3 position : clipboard) {
                BlockState state = clipboard.getBlock(position);
                if (state.isAir()) continue;
                nonAirBlocks++;
                materials.merge(state.getBlockType().id(), 1L, Long::sum);
                blockData.merge(state.getAsString(), 1L, Long::sum);
            }
            return new SchematicBlockInventory(clipboard.getVolume(), nonAirBlocks,
                    Collections.unmodifiableMap(materials), Collections.unmodifiableMap(blockData));
        }
    }

    public record SchematicBlockInventory(long volume, long nonAirBlocks,
                                          @NotNull Map<String, Long> materials,
                                          @NotNull Map<String, Long> blockData) {
    }

    /**
     * Pastes the schematic in {@code file} into {@code world} so its minimum corner lands exactly at
     * {@code (x, y, z)} — independent of where the schematic was originally copied from. Air is preserved
     * (so the paste overwrites whatever was there). Must run on the main thread.
     *
     * @throws IOException if the file is missing/unreadable or the format cannot be detected
     */
    public void pasteSchematic(@NotNull World world, @NotNull File file, int x, int y, int z) throws IOException {
        if (!file.isFile()) throw new IOException("schematic not found: " + file.getName());
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("unknown schematic format: " + file.getName());

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 origin = clipboard.getOrigin();
            // Paste places the clipboard origin at to; offset so the minimum corner ends up at (x,y,z).
            BlockVector3 to = BlockVector3.at(x, y, z).subtract(min).add(origin);
            try (EditSession editSession = worldEdit.newEditSession(BukkitAdapter.adapt(world))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
            }
        }
    }

    /** Clears an exact minimum-corner/dimensions cuboid before an adaptive layout is stamped again. */
    public void clearCuboid(@NotNull World world, @NotNull Vector origin, @NotNull Vector dimensions) throws Exception {
        BlockVector3 min = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        BlockVector3 max = min.add(dimensions.getBlockX() - 1, dimensions.getBlockY() - 1,
                dimensions.getBlockZ() - 1);
        try (EditSession editSession = worldEdit.newEditSession(BukkitAdapter.adapt(world))) {
            editSession.setBlocks((Region) new CuboidRegion(min, max), BlockTypes.AIR.getDefaultState());
        }
    }
}
