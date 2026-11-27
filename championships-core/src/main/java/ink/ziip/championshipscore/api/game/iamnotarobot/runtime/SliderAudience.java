package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderMapIds;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.BitSet;

/** The tiles physically exposed in this incarnation of the lane. All halls share pixel IDs. */
final class SliderAudience {
    private final Vector origin;
    private final World world;
    private static final SliderHall[] SCREENS = SliderHall.values();
    private final BitSet tracked = new BitSet();
    private final BitSet visible = new BitSet();
    private final SliderMaps.Edition edition;

    SliderAudience(World world, Vector origin, SliderMaps.Edition edition) {
        this.world = world;
        this.origin = origin;
        this.edition = edition;
        edition.halls.add(this);
    }

    void prioritize(Player viewer, Location location) {
        if (visible.isEmpty() || world != location.getWorld()) return;
        int chunks = Math.min(world.getViewDistance(), viewer.getClientViewDistance());
        double x = location.getX() - origin.getX(), z = location.getZ() - origin.getZ();
        int range = Math.min(64, 16 * Math.max(1, chunks));
        if (!SliderHall.inFrameRange(x, z, range)) return;
        selectTracked(visible, tracked, x, z, range);
        edition.delivery.prioritize(viewer.getUniqueId(), tracked);
    }

    static void selectTracked(BitSet visible, BitSet result, double x, double z, int range) {
        result.clear();
        result.or(visible);
        // Rows on a vertical screen share the same horizontal tracking range.
        for (var hall : SCREENS) {
            double dz = z - (hall.wall + (hall.step == 1 ? 1 : 0) + hall.step / 32.0);
            double widthSquared = range * range - dz * dz;
            double first = x - hall.mapX(0) - .5;
            double last = first - hall.step * (SliderHall.MAP_COLUMNS - 1);
            if (Math.max(first * first, last * last) <= widthSquared) continue;
            for (int column = 0; column < SliderHall.MAP_COLUMNS; column++) {
                double dx = first - hall.step * column;
                if (dx * dx <= widthSquared) continue;
                for (int round = 1; round <= 2; round++)
                    for (int row = 0; row < SliderHall.MAP_ROWS; row++)
                        result.clear(
                                SliderMapIds.index(
                                        hall, round, row * SliderHall.MAP_COLUMNS + column));
            }
        }
    }

    void expose(SliderHall hall, int round, int tile) {
        int slot = SliderMapIds.index(hall, round, tile);
        visible.clear(slot ^ SliderMapIds.TILES);
        visible.set(slot);
    }

    void retire() {
        edition.halls.remove(this);
    }
}
