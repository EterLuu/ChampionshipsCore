package ink.ziip.championshipscore.loadtest;

final class LatencyHistogram {
    private static final long[] UPPER_BOUNDS_MS = {50, 100, 250, 500, 1000, 2000, 5000, 10000, 30000};
    private final long[] buckets = new long[UPPER_BOUNDS_MS.length + 1];
    private long samples;
    private long totalMillis;
    private long maximumMillis;

    void record(long millis) {
        long value = Math.max(0L, millis);
        samples++;
        totalMillis += value;
        maximumMillis = Math.max(maximumMillis, value);
        int bucket = 0;
        while (bucket < UPPER_BOUNDS_MS.length && value > UPPER_BOUNDS_MS[bucket]) bucket++;
        buckets[bucket]++;
    }

    Snapshot snapshot() {
        return new Snapshot(samples, samples == 0 ? 0 : totalMillis / samples,
                percentile(0.95), percentile(0.99), maximumMillis);
    }

    private long percentile(double percentile) {
        if (samples == 0) return 0L;
        long target = Math.max(1L, (long) Math.ceil(samples * percentile));
        long seen = 0L;
        for (int index = 0; index < buckets.length; index++) {
            seen += buckets[index];
            if (seen >= target) {
                return index < UPPER_BOUNDS_MS.length ? UPPER_BOUNDS_MS[index] : maximumMillis;
            }
        }
        return maximumMillis;
    }

    record Snapshot(long samples, long averageMillis, long p95Millis, long p99Millis, long maximumMillis) {
    }
}
