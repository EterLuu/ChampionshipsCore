package ink.ziip.championshipscore.api.game.bingo.task;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player distinct-set / count tracker for the "counting" EventTask triggers (craft_unique,
 * eat_unique, breed_unique, spy_unique, compost_unique, advancement_count, kill_*). Event
 * listeners feed it on each occurrence; the pollable scan reads it to decide whether a counting
 * task's threshold is met.
 *
 * <p>Owned by a round (LOCAL {@code BingoRound} or worker {@code WorkerMatchSession}), so a fresh
 * empty tracker is in place every round with no explicit reset. All access is from the main thread
 * (event handlers and the tracker tick), but concurrent collections are used defensively.
 */
public final class EventProgressTracker {
    /** bucket -> player -> distinct values seen (for the {@code *_unique} triggers). */
    private final Map<String, Map<UUID, Set<String>>> distinct = new ConcurrentHashMap<>();
    /** bucket -> player -> running count (for {@code advancement_count}). */
    private final Map<String, Map<UUID, Integer>> counts = new ConcurrentHashMap<>();
    /** bucket -> player -> accumulated ticks (for {@code wear_duration} and other sustained states). */
    private final Map<String, Map<UUID, Long>> elapsedTicks = new ConcurrentHashMap<>();
    /** bucket -> player -> last monotonic observation; prevents event-driven rechecks inflating time. */
    private final Map<String, Map<UUID, Long>> elapsedObservedAt = new ConcurrentHashMap<>();

    /** Adds {@code value} to the player's distinct set for {@code bucket}; returns the new set size. */
    public int recordDistinct(Player player, String bucket, String value) {
        Set<String> set = distinct.computeIfAbsent(bucket, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(player.getUniqueId(), k -> Collections.synchronizedSet(new HashSet<>()));
        synchronized (set) {
            set.add(value);
            return set.size();
        }
    }

    public int distinctCount(Player player, String bucket) {
        Map<UUID, Set<String>> m = distinct.get(bucket);
        if (m == null) return 0;
        Set<String> set = m.get(player.getUniqueId());
        if (set == null) return 0;
        synchronized (set) {
            return set.size();
        }
    }

    /** Increments the player's counter for {@code bucket}; returns the new value. */
    public int increment(Player player, String bucket) {
        return counts.computeIfAbsent(bucket, k -> new ConcurrentHashMap<>())
                .merge(player.getUniqueId(), 1, Integer::sum);
    }

    public int count(Player player, String bucket) {
        Map<UUID, Integer> m = counts.get(bucket);
        return m == null ? 0 : m.getOrDefault(player.getUniqueId(), 0);
    }

    /** Adds {@code ticks} to the player's sustained-state bucket; returns the new accumulated value. */
    public long addElapsed(Player player, String bucket, long ticks) {
        return elapsedTicks.computeIfAbsent(bucket, k -> new ConcurrentHashMap<>())
                .merge(player.getUniqueId(), ticks, Long::sum);
    }

    public long elapsed(Player player, String bucket) {
        Map<UUID, Long> m = elapsedTicks.get(bucket);
        return m == null ? 0L : m.getOrDefault(player.getUniqueId(), 0L);
    }

    /**
     * Samples a sustained condition using monotonic wall time. Repeated inventory/event observations
     * therefore cannot make a duration objective advance faster than real time. An inactive sample
     * breaks the current interval but intentionally keeps already accumulated time, matching the
     * upstream objective's cumulative "wear for N minutes" semantics.
     */
    public long observeElapsed(Player player, String bucket, boolean active) {
        UUID playerId = player.getUniqueId();
        Map<UUID, Long> observed = elapsedObservedAt.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
        long now = System.nanoTime();
        if (!active) {
            observed.remove(playerId);
            return elapsed(player, bucket);
        }
        Long previous = observed.put(playerId, now);
        if (previous == null) return elapsed(player, bucket);
        long deltaNanos = Math.max(0L, now - previous);
        // Store milliseconds: adequate precision for minute-scale objectives and simple to compare.
        return elapsedTicks.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                .merge(playerId, deltaNanos / 1_000_000L, Long::sum);
    }

    public void resetAll() {
        distinct.clear();
        counts.clear();
        elapsedTicks.clear();
        elapsedObservedAt.clear();
    }
}
