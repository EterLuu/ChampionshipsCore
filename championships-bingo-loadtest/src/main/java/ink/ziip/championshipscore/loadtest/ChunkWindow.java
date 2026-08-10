package ink.ziip.championshipscore.loadtest;

import java.util.HashSet;
import java.util.Set;

final class ChunkWindow {
    private ChunkWindow() {
    }

    static Set<ChunkPos> around(double blockX, double blockZ, int radius) {
        int centerX = Math.floorDiv((int) Math.floor(blockX), 16);
        int centerZ = Math.floorDiv((int) Math.floor(blockZ), 16);
        int side = radius * 2 + 1;
        Set<ChunkPos> chunks = new HashSet<>(side * side * 2);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return Set.copyOf(chunks);
    }
}
