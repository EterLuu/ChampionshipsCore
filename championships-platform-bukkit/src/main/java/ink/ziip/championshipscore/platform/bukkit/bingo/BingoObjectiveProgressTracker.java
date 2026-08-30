package ink.ziip.championshipscore.platform.bukkit.bingo;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Thread-safe match-scoped implementation of Bingo objective counters. */
public class BingoObjectiveProgressTracker implements BingoObjectiveProgress {
    private final Map<String, Map<UUID, Set<String>>> distinct = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> counts = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> elapsedMillis = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> observedAt = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public BingoObjectiveProgressTracker() {
        this(System::nanoTime);
    }

    BingoObjectiveProgressTracker(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override
    public void recordDistinct(UUID playerId, String bucket, String value) {
        distinct.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(value);
    }

    @Override
    public int distinctCount(UUID playerId, String bucket) {
        return distinct.getOrDefault(bucket, Map.of()).getOrDefault(playerId, Set.of()).size();
    }

    @Override
    public void increment(UUID playerId, String bucket) {
        counts.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>()).merge(playerId, 1, Integer::sum);
    }

    @Override
    public int count(UUID playerId, String bucket) {
        return counts.getOrDefault(bucket, Map.of()).getOrDefault(playerId, 0);
    }

    @Override
    public long observeElapsed(UUID playerId, String bucket, boolean active) {
        Map<UUID, Long> observations = observedAt.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
        long now = nanoTime.getAsLong();
        if (!active) {
            observations.remove(playerId);
            return elapsedMillis.getOrDefault(bucket, Map.of()).getOrDefault(playerId, 0L);
        }
        Long previous = observations.put(playerId, now);
        if (previous == null) return elapsedMillis.getOrDefault(bucket, Map.of()).getOrDefault(playerId, 0L);
        return elapsedMillis.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                .merge(playerId, Math.max(0L, now - previous) / 1_000_000L, Long::sum);
    }

    public void resetAll() {
        distinct.clear();
        counts.clear();
        elapsedMillis.clear();
        observedAt.clear();
    }
}
