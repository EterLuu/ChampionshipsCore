package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;

/**
 * Lays copies on a square ring spiralling outward from a centre point (e.g. a shared hub), spaced
 * {@code spacing} blocks apart. Within a ring the perimeter is traced clockwise from the top-middle cell
 * {@code (0, R)}, so the first three copies sit at {@code centre + (0, spacing)}, {@code (spacing, spacing)}
 * and {@code (spacing, 0)}. The ring grows as needed — no cap on copy count. All copies share the centre's
 * Y level.
 */
public final class RingArenaGrid implements ArenaGrid {
    private final Vector center;
    private final int spacingX;
    private final int spacingZ;

    public RingArenaGrid(Vector center, int spacing) {
        this(center, spacing, spacing);
    }

    public RingArenaGrid(Vector center, int spacingX, int spacingZ) {
        this.center = center.clone();
        this.spacingX = spacingX;
        this.spacingZ = spacingZ;
    }

    @Override
    public Vector origin(int index) {
        int[] cell = ringCell(index);
        return new Vector(center.getBlockX() + cell[0] * spacingX, center.getBlockY(),
                center.getBlockZ() + cell[1] * spacingZ);
    }

    /**
     * The grid cell (in {@code [x, z]} cell units, centre at the origin) for {@code index}. Walks square
     * rings outward; the first cell of ring 1 is the top-middle {@code (0, 1)}.
     */
    private static int[] ringCell(int index) {
        int ring = 1;
        // Cells in rings 1..(ring-1) total 4*(ring-1)*ring; advance until the index falls inside this ring.
        while (index >= 4 * ring * (ring + 1)) {
            ring++;
        }
        int local = index - 4 * (ring - 1) * ring;

        int seg1 = ring + 1;            // top edge, x = 0..ring at z = ring
        if (local < seg1) return new int[]{local, ring};
        local -= seg1;
        if (local < 2 * ring) return new int[]{ring, ring - 1 - local};          // right edge, z = ring-1..-ring
        local -= 2 * ring;
        if (local < 2 * ring) return new int[]{ring - 1 - local, -ring};         // bottom edge, x = ring-1..-ring
        local -= 2 * ring;
        if (local < 2 * ring) return new int[]{-ring, -ring + 1 + local};        // left edge, z = -ring+1..ring
        local -= 2 * ring;
        return new int[]{-ring + 1 + local, ring};                                // top edge left half, x = -ring+1..-1
    }
}
