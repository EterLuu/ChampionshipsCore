package ink.ziip.championshipscore.api.game.hotycodydusky.mechanics;

import ink.ziip.championshipscore.api.game.start.StartAllocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared wave clock and tag-passing rules across all selected Hoty Cody Dusky arenas. Supports an
 * opening scatter phase, generous wave durations, fair anti-repeat selection, directional tag-back
 * lockout, freeze pauses, and final-ten-second frenzy.
 */
public final class CodyWave {
    public enum Phase {
        SCATTER,
        ACTIVE,
        INTERMISSION
    }

    public static final int SCATTER = 15;
    public static final int INTERMISSION = 5;
    public static final int IMMUNITY = 3;
    public static final int FRENZY_THRESHOLD = 10;
    public static final int TAGBACK_TICKS = 60;
    public static final int DUEL_TAGBACK_TICKS = 30;
    private static final int[] SOURCE_THRESHOLDS = {2, 5, 7, 9, 12, 16, 20, 24};

    private record TagBackLock(UUID previousHolder, int expiresTick) {}

    private final Set<UUID> holders = new LinkedHashSet<>();
    private final Map<UUID, TagBackLock> tagBackLocks = new HashMap<>();
    private final Map<UUID, Integer> drawCounts = new HashMap<>();
    private Set<UUID> lastDraw = Set.of();
    private Phase phase = Phase.INTERMISSION;
    private int number;
    private int remaining;
    private int frozen;
    private int elapsed;
    private int currentTick;

    public static Map<UUID, Integer> allocate(List<List<UUID>> roster, List<Integer> selected) {
        int players = roster.stream().mapToInt(List::size).sum();
        int count = Math.min(selected.size(), Math.max(1, players / 2));
        while (count > 1) {
            var allocation = StartAllocation.spread(roster, selected.subList(0, count));
            if (selected.subList(0, count).stream()
                    .allMatch(arena -> Collections.frequency(allocation.values(), arena) >= 2)) {
                return allocation;
            }
            count--;
        }
        return StartAllocation.spread(roster, selected.subList(0, 1));
    }

    /** Smoothly steps down from 55s to a 30s floor so late waves keep real chase counterplay. */
    public static int duration(int number) {
        int round = Math.max(1, number);
        return Math.max(30, 60 - 5 * round);
    }

    public void beginScatter() {
        phase = Phase.SCATTER;
        remaining = SCATTER;
        frozen = 0;
        elapsed = 0;
        holders.clear();
        tagBackLocks.clear();
    }

    public void start(Collection<? extends List<UUID>> arenas) {
        number++;
        phase = Phase.ACTIVE;
        frozen = 0;
        elapsed = 0;
        remaining = duration(number);
        tagBackLocks.clear();
        holders.clear();
        for (var arena : arenas) {
            var candidates = new ArrayList<>(arena);
            Collections.shuffle(candidates);
            candidates.sort(
                    Comparator.comparing(lastDraw::contains)
                            .thenComparingInt(id -> drawCounts.getOrDefault(id, 0)));
            int count = 0;
            for (int threshold : SOURCE_THRESHOLDS) {
                if (candidates.size() < threshold) break;
                count++;
            }
            List<UUID> picked = candidates.subList(0, count);
            holders.addAll(picked);
            for (UUID id : picked) drawCounts.merge(id, 1, Integer::sum);
        }
        lastDraw = Set.copyOf(holders);
    }

    public void beginIntermission() {
        tagBackLocks.clear();
        holders.clear();
        phase = Phase.INTERMISSION;
        frozen = 0;
        elapsed = 0;
        remaining = INTERMISSION;
    }

    public boolean tick() {
        if (phase == Phase.ACTIVE && holders.isEmpty()) return true;
        elapsed++;
        if (phase == Phase.ACTIVE && frozen > 0) {
            frozen--;
            return false;
        }
        return --remaining <= 0;
    }

    public boolean freeze() {
        if (phase != Phase.ACTIVE || frozen > 25) return false;
        frozen += 5;
        return true;
    }

    public boolean infect(UUID target) {
        return phase == Phase.ACTIVE && holders.add(target);
    }

    public boolean holds(UUID id) {
        return holders.contains(id);
    }

    public void nextServerTick() {
        currentTick++;
        if (!tagBackLocks.isEmpty()) {
            tagBackLocks.values().removeIf(lock -> currentTick >= lock.expiresTick());
        }
    }

    public int tagBackRemainingTicks(UUID from, UUID to) {
        TagBackLock lock = tagBackLocks.get(from);
        if (lock == null || !lock.previousHolder().equals(to)) return 0;
        return Math.max(0, lock.expiresTick() - currentTick);
    }

    public boolean canPass(UUID from, UUID to, boolean duel) {
        return phase == Phase.ACTIVE
                && elapsed >= IMMUNITY
                && !holders.contains(to)
                && holders.contains(from)
                && tagBackRemainingTicks(from, to) == 0;
    }

    public int immunityRemaining() {
        return phase != Phase.ACTIVE ? 0 : Math.max(0, IMMUNITY - elapsed);
    }

    public boolean isImmune() {
        return immunityRemaining() > 0;
    }

    public boolean isFrenzy() {
        return phase == Phase.ACTIVE && !isImmune() && remaining <= FRENZY_THRESHOLD;
    }

    public int frozenRemaining() {
        return phase != Phase.ACTIVE ? 0 : frozen;
    }

    public boolean pass(UUID from, UUID to, boolean duel) {
        if (!canPass(from, to, duel)) return false;
        int lockout = duel ? DUEL_TAGBACK_TICKS : TAGBACK_TICKS;
        tagBackLocks.remove(from);
        tagBackLocks.put(to, new TagBackLock(from, currentTick + lockout));
        holders.remove(from);
        holders.add(to);
        return true;
    }

    public void remove(UUID id) {
        holders.remove(id);
        tagBackLocks.remove(id);
    }

    public List<UUID> holders() {
        return List.copyOf(holders);
    }

    public Phase phase() {
        return phase;
    }

    public boolean scattering() {
        return phase == Phase.SCATTER;
    }

    public boolean active() {
        return phase == Phase.ACTIVE;
    }

    public boolean intermission() {
        return phase == Phase.INTERMISSION;
    }

    public int number() {
        return number;
    }

    public int remaining() {
        return remaining;
    }
}
