package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import java.util.ArrayDeque;

/** Account for ordinary (not silently pushed) backend packets before spending the map allowance. */
final class SliderBandwidth {
    static final int TILE_BYTES = 16_408;
    private static final int BYTES_PER_SECOND = 52_500_000;

    private record Sample(long time, long bytes) {}

    private final ArrayDeque<Sample> second = new ArrayDeque<>(),
            quarterSecond = new ArrayDeque<>();
    private long bytes, recent;

    void observe(long now, long amount) {
        if (amount > 0) {
            var sample = new Sample(now, amount);
            bytes += amount;
            recent += amount;
            second.addLast(sample);
            quarterSecond.addLast(sample);
        }
        while (!second.isEmpty() && now - second.getFirst().time >= 1_000_000_000L)
            bytes -= second.removeFirst().bytes;
        while (!quarterSecond.isEmpty() && now - quarterSecond.getFirst().time >= 250_000_000L)
            recent -= quarterSecond.removeFirst().bytes;
    }

    int allowed(int mapTilesInLastSecond) {
        long available = BYTES_PER_SECOND - bytes - (long) mapTilesInLastSecond * TILE_BYTES;
        // A burst of chunk packets is likely to continue while the other clients teleport.
        long nextTick = (BYTES_PER_SECOND - 4 * recent) / 20;
        return (int) (Math.max(0, Math.min(available, nextTick)) / TILE_BYTES);
    }
}
