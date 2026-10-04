package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;

import java.util.List;

/**
 * Shared whole-block timeline, with a fresh visible warning before every independently chosen wall.
 */
public final class RiptideSideSweep {
    static final int WARNING_TICKS = 30;

    record Frame(int beat, int localTick, int direction, int lateral, boolean moving) {}

    private RiptideSideSweep() {}

    public static int wallCount(int step, int totalSteps) {
        return step >= totalSteps * .6 ? 3 : 2;
    }

    static int radius(int halfWidth, RiptideCoursePlan.SideWall wall) {
        return halfWidth + 7 + wall.thickness();
    }

    public static int beatTicks(int halfWidth, double speed, RiptideCoursePlan.SideWall wall) {
        if (!Double.isFinite(speed) || speed <= 0 || speed > 20)
            throw new IllegalArgumentException("side wall speed must be in (0,20]");
        // Start two blocks farther out; retain the existing exit position.
        return WARNING_TICKS + (int) Math.ceil((2 * radius(halfWidth, wall) - 2) * 20D / speed);
    }

    public static int totalTicks(
            int halfWidth, double speed, List<RiptideCoursePlan.SideWall> walls) {
        return walls.stream().mapToInt(w -> beatTicks(halfWidth, speed, w)).sum();
    }

    public static Frame frame(
            int tick, List<RiptideCoursePlan.SideWall> walls, int halfWidth, double speed) {
        if (tick < 0) throw new IllegalArgumentException("sweep tick outside timeline");
        for (int index = 0; index < walls.size(); index++) {
            var wall = walls.get(index);
            int duration = beatTicks(halfWidth, speed, wall);
            if (tick >= duration) {
                tick -= duration;
                continue;
            }
            int radius = radius(halfWidth, wall);
            int moved =
                    tick < WARNING_TICKS
                            ? 0
                            : Math.min(
                                    2 * radius - 2,
                                    (int)
                                            Math.floor(
                                                    (tick - WARNING_TICKS + 1) * speed / 20D
                                                            + 1E-9));
            return new Frame(
                    index + 1,
                    tick,
                    wall.direction(),
                    (radius - moved) * wall.direction(),
                    tick >= WARNING_TICKS);
        }
        throw new IllegalArgumentException("sweep tick outside timeline");
    }
}
