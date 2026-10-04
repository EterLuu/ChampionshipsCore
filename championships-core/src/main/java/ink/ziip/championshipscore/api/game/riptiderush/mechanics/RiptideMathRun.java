package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/** Per-player answers are committed at the first forward crossing, never at a raft timer. */
public final class RiptideMathRun {
    static final int TITLE_TICKS = 80;

    public record Gate(int level, int step, RiptideQuestion question) {}

    public enum Result {
        CORRECT,
        WRONG,
        OUTSIDE_GATE
    }

    public record Answer(Gate gate, double lateral, double y, Result result) {}

    private final RiptideCourseGeometry geometry;
    private final List<Gate> gates;
    private Location previous;
    private int nextGate;
    private int shownAt = -1;
    private boolean failed;

    public RiptideMathRun(RiptideCourseGeometry geometry, List<Gate> gates, Location initial) {
        this.geometry = geometry;
        this.gates = List.copyOf(gates);
        this.previous = initial.clone();
    }

    public List<Answer> sample(Location current) {
        List<Answer> answers = new ArrayList<>();
        if (failed) return answers;
        if (!sameWorld(previous, current)) {
            previous = current.clone();
            return answers;
        }
        while (nextGate < gates.size()) {
            Gate gate = gates.get(nextGate);
            double from = geometry.forwardOffset(previous, gate.step());
            double to = geometry.forwardOffset(current, gate.step());
            if (!(from < 0D && to >= 0D)) break;
            double fraction = -from / (to - from);
            double lateral =
                    geometry.lateralOffset(previous, gate.step()) * (1D - fraction)
                            + geometry.lateralOffset(current, gate.step()) * fraction;
            double y = previous.getY() * (1D - fraction) + current.getY() * fraction;
            // Openings are the blocks between the centre pillar and the outer coloured posts.
            boolean inside =
                    Math.abs(lateral) > 0.5D
                            && Math.abs(lateral) < geometry.halfWidth() + 0.5D
                            && y >= geometry.floorY() + 1D - 0.01D
                            && y < geometry.floorY() + 4D;
            Result result =
                    !inside
                            ? Result.OUTSIDE_GATE
                            : gate.question().acceptsLateralOffset(lateral)
                                    ? Result.CORRECT
                                    : Result.WRONG;
            answers.add(new Answer(gate, lateral, y, result));
            nextGate++;
            shownAt = -1;
            if (result != Result.CORRECT) {
                failed = true;
                break;
            }
        }
        previous = current.clone();
        return answers;
    }

    /** A stationary side challenge must not consume the next gate's answer preview. */
    public void suspendPreview() {
        shownAt = -1;
    }

    public Gate preview(
            Location current, double speed, int previewBlocks, int tick, boolean paused) {
        if (failed || nextGate >= gates.size() || paused || !sameWorld(previous, current))
            return null;
        Gate gate = gates.get(nextGate);
        double distance = -geometry.forwardOffset(current, gate.step());
        if (distance <= 0D || distance > Math.min(previewBlocks, speed * TITLE_TICKS / 20D) + 1e-9D)
            return null;
        if (shownAt < 0) shownAt = tick;
        return tick - shownAt < TITLE_TICKS ? gate : null;
    }

    /** Shield teleports consume the failed gate and resume judging subsequent gates. */
    public void recoverAt(Location destination) {
        failed = false;
        shownAt = -1;
        followCourse(destination);
    }

    /** Spectator presentation follows the raft; it must not submit a centre-line answer. */
    public void followCourse(Location center) {
        while (nextGate < gates.size()
                && geometry.forwardOffset(center, gates.get(nextGate).step()) >= 0D) {
            nextGate++;
            shownAt = -1;
        }
        previous = center.clone();
    }

    private static boolean sameWorld(Location from, Location to) {
        return from.getWorld() != null
                && to.getWorld() != null
                && from.getWorld().getName().equals(to.getWorld().getName());
    }
}
