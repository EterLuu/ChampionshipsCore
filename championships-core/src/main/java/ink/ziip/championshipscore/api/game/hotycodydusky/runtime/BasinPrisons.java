package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Player;

import java.util.*;

/** Places a temporary cage only in air or water. Overlapping cages release shared cells last. */
final class BasinPrisons {
    private record Cell(BlockData original, BlockData placed, Set<UUID> occupants) {}

    private record Cage(int expires, List<Block> blocks) {}

    private final Map<Block, Cell> cells = new HashMap<>();
    private final Map<UUID, Cage> cages = new HashMap<>();

    boolean contains(Player p) {
        return cages.containsKey(p.getUniqueId());
    }

    void place(Player p, int now) {
        UUID id = p.getUniqueId();
        release(id);
        Location at = p.getLocation();
        Block floor = at.getBlock();
        Location center = floor.getLocation().add(0.5, 1, 0.5);
        center.setDirection(at.getDirection());
        if (!center.getBlock().isPassable()
                || !center.clone().add(0, 1, 0).getBlock().isPassable()
                || !p.teleport(center)) return;
        List<Block> placed = new ArrayList<>();
        cages.put(id, new Cage(now + 50, placed));
        try {
            for (int y = 0; y < 4; y++)
                for (int x = -1; x <= 1; x++)
                    for (int z = -1; z <= 1; z++) {
                        if (y > 0 && y < 3 && x == 0 && z == 0) continue;
                        Block block = floor.getRelative(x, y, z);
                        Cell prior = cells.get(block);
                        if (prior != null) {
                            prior.occupants().add(id);
                        } else {
                            if (!block.getType().isAir() && block.getType() != Material.WATER)
                                continue;
                            BlockData material = material(x, y, z);
                            cells.put(
                                    block,
                                    new Cell(
                                            block.getBlockData(),
                                            material,
                                            new HashSet<>(Set.of(id))));
                            placed.add(block);
                            block.setBlockData(material, false);
                            continue;
                        }
                        placed.add(block);
                    }
            p.getWorld().playSound(at, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1, 0.5f);
        } catch (RuntimeException failure) {
            release(id);
            throw failure;
        }
    }

    private BlockData material(int x, int y, int z) {
        if (y == 3) return Material.POLISHED_TUFF_SLAB.createBlockData();
        if (y == 0)
            return (x == 0 && z == 0 ? Material.TUFF_BRICK_SLAB : Material.CHISELED_TUFF)
                    .createBlockData();
        MultipleFacing bars = (MultipleFacing) Material.IRON_BARS.createBlockData();
        for (BlockFace face :
                List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST)) {
            int dx = x + face.getModX(), dz = z + face.getModZ();
            bars.setFace(face, Math.max(Math.abs(dx), Math.abs(dz)) == 1);
        }
        return bars;
    }

    void tick(int now) {
        if (cages.isEmpty()) return;
        for (UUID id : List.copyOf(cages.keySet())) if (now >= cages.get(id).expires()) release(id);
    }

    void release(UUID id) {
        Cage cage = cages.remove(id);
        if (cage == null) return;
        for (Block block : cage.blocks()) {
            Cell cell = cells.get(block);
            cell.occupants().remove(id);
            if (cell.occupants().isEmpty()) {
                if (block.getBlockData().equals(cell.placed()))
                    block.setBlockData(cell.original(), false);
                cells.remove(block);
            }
        }
    }

    void close() {
        for (UUID id : List.copyOf(cages.keySet())) release(id);
    }
}
