package ink.ziip.championshipscore.api.game.riptiderush.course;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Immutable map snapshots: a finish-relative building and the start-relative scenery it owns. */
public final class RiptideFinishPlatform {
    private final String building;
    private final String background;
    private final Bounds buildingBounds;
    private final Bounds backgroundBounds;

    public RiptideFinishPlatform(String building, String background) {
        this.building = building;
        this.background = background;
        try (var a = read(building);
                var b = read(background)) {
            buildingBounds = bounds(a);
            backgroundBounds = bounds(b);
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (minX < -64
                    || maxX > 64
                    || minY < -32
                    || maxY > 64
                    || minZ < 0
                    || maxZ > 4224
                    || minX > maxX
                    || minY > maxY
                    || minZ > maxZ
                    || volume > 1_000_000)
                throw new IllegalArgumentException("Invalid finish platform bounds");
        }

        boolean contains(Bounds other, int forward) {
            return minX <= other.minX
                    && maxX >= other.maxX
                    && minY <= other.minY
                    && maxY >= other.maxY
                    && minZ <= other.minZ + forward
                    && maxZ >= other.maxZ + forward;
        }

        public Vector minimum(RiptideCourseGeometry g) {
            return corner(g, true);
        }

        public Vector maximum(RiptideCourseGeometry g) {
            return corner(g, false);
        }

        private Vector corner(RiptideCourseGeometry g, boolean minimum) {
            int ax = g.blockX(minZ, minX), bx = g.blockX(maxZ, maxX);
            int az = g.blockZ(minZ, minX), bz = g.blockZ(maxZ, maxX);
            return new Vector(
                    minimum ? Math.min(ax, bx) : Math.max(ax, bx),
                    g.floorY() + (minimum ? minY : maxY),
                    minimum ? Math.min(az, bz) : Math.max(az, bz));
        }
    }

    public void validate(int minimumLength, int maximumLength, int halfRaftLength) {
        if (buildingBounds.minZ <= halfRaftLength
                || buildingBounds.maxZ > 128
                || !backgroundBounds.contains(buildingBounds, minimumLength)
                || !backgroundBounds.contains(buildingBounds, maximumLength))
            throw new IllegalArgumentException(
                    "Finish platform must clear the raft and fit its reserved scenery");
    }

    public Bounds backgroundBounds() {
        return backgroundBounds;
    }

    public List<Location> preloadLocations(RiptideCourseGeometry g) {
        var min = backgroundBounds.minimum(g);
        var max = backgroundBounds.maximum(g);
        var locations = new ArrayList<Location>();
        for (int x = min.getBlockX() >> 4; x <= max.getBlockX() >> 4; x++)
            for (int z = min.getBlockZ() >> 4; z <= max.getBlockZ() >> 4; z++)
                locations.add(new Location(g.centerAt(0).getWorld(), x * 16, g.floorY(), z * 16));
        return locations;
    }

    public Session open(RiptideCourseGeometry g) {
        return new Session(g);
    }

    /**
     * One cancellable generation owns these decoded snapshots; no previous-world state survives.
     */
    public final class Session implements AutoCloseable {
        private final RiptideCourseGeometry geometry;
        private final Clipboard scenery;
        private final Clipboard platform;
        private int forward = backgroundBounds.minZ;
        private int buildingForward = buildingBounds.minZ;

        private Session(RiptideCourseGeometry geometry) {
            this.geometry = geometry;
            scenery = read(background);
            try {
                platform = read(building);
            } catch (RuntimeException failure) {
                scenery.close();
                throw failure;
            }
        }

        public boolean restoring() {
            return forward <= backgroundBounds.maxZ;
        }

        /** Bounded slices also restore air, water and full block-entity data from the map. */
        public void restoreNext() {
            if (!restoring()) return;
            write(
                    scenery,
                    new Bounds(
                            backgroundBounds.minX,
                            backgroundBounds.minY,
                            forward,
                            backgroundBounds.maxX,
                            backgroundBounds.maxY,
                            forward),
                    0);
            forward++;
        }

        public boolean placing() {
            return buildingForward <= buildingBounds.maxZ;
        }

        public void placeNext() {
            if (!placing()) return;
            write(
                    platform,
                    new Bounds(
                            buildingBounds.minX,
                            buildingBounds.minY,
                            buildingForward,
                            buildingBounds.maxX,
                            buildingBounds.maxY,
                            buildingForward),
                    geometry.totalSteps());
            buildingForward++;
        }

        public void place() {
            while (placing()) placeNext();
        }

        private void write(Clipboard source, Bounds range, int offset) {
            var transform =
                    new AffineTransform()
                            .rotateY(
                                    Math.toDegrees(Math.atan2(geometry.stepX(), geometry.stepZ())));
            var oriented = new BlockTransformExtent(source, transform);
            try (var edit =
                    WorldEdit.getInstance()
                            .newEditSession(BukkitAdapter.adapt(geometry.centerAt(0).getWorld()))) {
                for (int z = range.minZ; z <= range.maxZ; z++)
                    for (int x = range.minX; x <= range.maxX; x++)
                        for (int y = range.minY; y <= range.maxY; y++)
                            edit.setBlock(
                                    BlockVector3.at(
                                            geometry.blockX(z + offset, x),
                                            geometry.floorY() + y,
                                            geometry.blockZ(z + offset, x)),
                                    oriented.getFullBlock(BlockVector3.at(x, y, z)));
            } catch (com.sk89q.worldedit.WorldEditException failure) {
                throw new IllegalStateException("Unable to place finish platform scenery", failure);
            }
        }

        @Override
        public void close() {
            platform.close();
            scenery.close();
        }
    }

    private static Bounds bounds(Clipboard clipboard) {
        if (!clipboard.getOrigin().equals(BlockVector3.ZERO))
            throw new IllegalArgumentException("Finish platform schematic origin must be zero");
        var min = clipboard.getMinimumPoint();
        var max = clipboard.getMaximumPoint();
        return new Bounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    private static Clipboard read(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 4_000_000)
            throw new IllegalArgumentException("Invalid finish platform schematic");
        try (var reader =
                BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(
                        new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            return reader.read();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Unreadable finish platform schematic", failure);
        }
    }
}
