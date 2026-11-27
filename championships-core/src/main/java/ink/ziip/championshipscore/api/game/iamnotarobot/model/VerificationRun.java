package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.EntryTrack;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse.Room;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The team's clock starts with the match. A split is committed only once when its puzzle opens the
 * gate.
 */
public final class VerificationRun {
    /** Every present member must personally pass this many Stage 2 slider rounds. */
    public static final int SLIDER_PASSES = 3;

    private final EnumMap<Room, Integer> splits = new EnumMap<>(Room.class);
    private final Map<UUID, Integer> sliderPasses = new HashMap<>();
    private final int[] checkpoints = new int[EntryTrack.COUNT];
    private final boolean[] enteredPuzzleRooms = new boolean[EntryTrack.COUNT];
    private Room room = Room.ENTRY;
    private boolean begun = true;
    private int arrivedTracks;
    private int lastGate;

    public Room room() {
        return room;
    }

    public boolean begun() {
        return begun;
    }

    public boolean begin() {
        if (begun) return false;
        begun = true;
        return true;
    }

    public boolean finished() {
        return splits.containsKey(Room.SPRINT);
    }

    public Integer time(Room stage) {
        return splits.get(stage);
    }

    public boolean arrived(int slot) {
        return slot >= 0 && slot < EntryTrack.COUNT && (arrivedTracks & (1 << slot)) != 0;
    }

    public int checkpoint(int slot) {
        return slot >= 0 && slot < EntryTrack.COUNT ? checkpoints[slot] : 0;
    }

    public boolean reachCheckpoint(int slot, int checkpoint) {
        if (room != Room.ENTRY
                || slot < 0
                || slot >= EntryTrack.COUNT
                || arrived(slot)
                || checkpoint <= checkpoints[slot]
                || checkpoint >= EntryTrack.CHECKPOINT_COUNT) {
            return false;
        }
        checkpoints[slot] = checkpoint;
        return true;
    }

    public int arrivalCount() {
        return Integer.bitCount(arrivedTracks);
    }

    public boolean onEntryTrack(int slot) {
        return room == Room.ENTRY || (room == Room.WAREHOUSE && !enteredPuzzleRooms[slot]);
    }

    public void enterPuzzleRooms(int slot) {
        if (room == Room.WAREHOUSE) enteredPuzzleRooms[slot] = true;
    }

    public boolean arrive(int slot, int tick) {
        if (room != Room.ENTRY || slot < 0 || slot >= EntryTrack.COUNT || arrived(slot))
            return false;
        arrivedTracks |= 1 << slot;
        if (arrivalCount() == EntryTrack.COUNT) complete(tick);
        return true;
    }

    public boolean completeWarehouse(int tick) {
        if (!begun || room != Room.WAREHOUSE) return false;
        complete(tick);
        return true;
    }

    public int sliderPasses(UUID player) {
        return sliderPasses.getOrDefault(player, 0);
    }

    public boolean sliderDone(UUID player) {
        return sliderPasses(player) >= SLIDER_PASSES;
    }

    /** Counts one personal slider pass while the hall is open and returns the new total. */
    public int passSlider(UUID player) {
        if (!begun || room != Room.SLIDER) return sliderPasses(player);
        return sliderPasses.merge(player, 1, (a, b) -> Math.min(SLIDER_PASSES, a + b));
    }

    public boolean completeSlider(int tick) {
        if (!begun || room != Room.SLIDER) return false;
        complete(tick);
        return true;
    }

    public boolean completeJoint(int tick) {
        if (!begun || room != Room.JOINT) return false;
        complete(tick);
        return true;
    }

    public boolean crossFinish(boolean gathered, int tick) {
        if (!begun || room != Room.SPRINT || finished() || !gathered) return false;
        complete(tick);
        return true;
    }

    private void complete(int tick) {
        splits.put(room, room == Room.SPRINT ? tick : tick - lastGate);
        lastGate = tick;
        if (room != Room.SPRINT) {
            room = Room.values()[room.ordinal() + 1];
            begun = false;
        }
    }

    /** Competition ranking: equal ticks tie, missing splits earn zero, finish uses total time. */
    public int points(Collection<VerificationRun> field) {
        int total = 0;
        for (var entry : splits.entrySet()) {
            int faster = 0;
            for (var other : field) {
                Integer time = other.time(entry.getKey());
                if (time != null && time < entry.getValue()) faster++;
            }
            int roomPoints = Math.max(10, 100 - 10 * faster);
            total += entry.getKey() == Room.SPRINT ? 2 * roomPoints : roomPoints;
        }
        return total;
    }
}
