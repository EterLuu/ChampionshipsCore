package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Predicate;

/** Each impact spreads through connected air. Only the last cloud to leave restores a cell. */
final class BasinSnowSmoke {
    static final int DURATION = 100;
    private static final List<BlockFace> SIDES =
            List.of(
                    BlockFace.UP,
                    BlockFace.DOWN,
                    BlockFace.EAST,
                    BlockFace.WEST,
                    BlockFace.NORTH,
                    BlockFace.SOUTH);

    private record Cell(BlockData original, Set<Cloud> owners) {}

    private static final class Cloud {
        final Block center;
        final int end;
        final Deque<Block> next = new ArrayDeque<>();
        final Set<Block> seen = new HashSet<>();
        final List<Block> placed = new ArrayList<>();
        final Predicate<Block> inside;

        Cloud(Block center, int end, Predicate<Block> inside) {
            this.center = center;
            this.end = end;
            this.inside = inside;
            next.add(center);
            seen.add(center);
        }
    }

    private final List<Cloud> clouds = new ArrayList<>();
    private final Map<Block, Cell> cells = new HashMap<>();

    void land(Block block, int now, Predicate<Block> inside) {
        Cloud cloud = new Cloud(block, now + DURATION, inside);
        clouds.add(cloud);
        expand(cloud);
    }

    void tick(int now) {
        for (var it = clouds.iterator(); it.hasNext(); ) {
            var cloud = it.next();
            if (now >= cloud.end) {
                release(cloud);
                it.remove();
            } else expand(cloud);
        }
    }

    private void expand(Cloud cloud) {
        for (int added = 0; added < 9 && !cloud.next.isEmpty(); ) {
            Block b = cloud.next.remove();
            if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) || !cloud.inside.test(b))
                continue;
            Cell cell = cells.get(b);
            if (cell == null) {
                if (!b.getType().isAir()) continue;
                cell = new Cell(b.getBlockData(), new HashSet<>());
                b.setType(Material.POWDER_SNOW, false);
                cells.put(b, cell);
            } else if (b.getType() != Material.POWDER_SNOW) continue;
            cell.owners().add(cloud);
            cloud.placed.add(b);
            added++;
            for (var side : SIDES) {
                Block n = b.getRelative(side);
                int x = n.getX() - cloud.center.getX(),
                        y = n.getY() - cloud.center.getY(),
                        z = n.getZ() - cloud.center.getZ();
                if (9 * (x * x + z * z) + 16 * y * y <= 144 && cloud.seen.add(n)) cloud.next.add(n);
            }
        }
    }

    void conceal(Player p) {
        if (cells.isEmpty()) return;
        Block b = p.getLocation().getBlock();
        if (cells.containsKey(b) && b.getType() == Material.POWDER_SNOW)
            p.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY, 10, 50, true, false, false));
    }

    private void release(Cloud cloud) {
        for (Block b : cloud.placed) {
            var cell = cells.get(b);
            cell.owners().remove(cloud);
            if (!cell.owners().isEmpty()) continue;
            cells.remove(b);
            if (b.getType() == Material.POWDER_SNOW) b.setBlockData(cell.original(), false);
        }
    }

    void close() {
        clouds.forEach(this::release);
        clouds.clear();
    }
}
