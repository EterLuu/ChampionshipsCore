package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideDifficulty;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideHitwCatalog;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeRun;

import java.util.List;

/** A compact race: real buildings, five speed bands and complete, separate stopped challenges. */
final class RiptideIntroductionLesson {
    static final int DURATION_TICKS = 1800;
    static final int DEPARTURE_TICK = 80;
    static final int COLOR_STEP = 64;
    static final int DODGE_STEP = 96;
    static final int FINISH_STEP = 140;
    static final int COURSE_LENGTH = FINISH_STEP;
    static final long SEED = 20261116L;
    static final List<Integer> COLOR_ROUNDS =
            RiptideDifficulty.floorRoundTicks(COLOR_STEP, COURSE_LENGTH);
    static final int COLOR_TICKS =
            COLOR_ROUNDS.stream().mapToInt(Integer::intValue).sum()
                    + RiptideColorFloorRun.INTRO_TICKS
                    + RiptideDeparture.WAIT_TICKS;
    static final int DODGE_TICKS = RiptideDodgeRun.DURATION_TICKS + RiptideDeparture.WAIT_TICKS;
    private static final int[] STEPS = journey();

    enum Stage {
        WELCOME,
        FOLLOW,
        PASS,
        MATH,
        COLOR,
        TRANSFER,
        DODGE,
        SPEED,
        RHYTHM,
        FINISH;

        int start() {
            return switch (this) {
                case WELCOME -> 0;
                case FOLLOW -> DEPARTURE_TICK;
                case PASS -> arrival(10);
                case MATH -> arrival(38);
                case COLOR -> arrival(COLOR_STEP);
                case TRANSFER -> COLOR.start() + COLOR_TICKS;
                case DODGE -> arrival(DODGE_STEP);
                case SPEED -> DODGE.start() + DODGE_TICKS;
                case RHYTHM -> arrival(106);
                case FINISH -> arrival(FINISH_STEP);
            };
        }
    }

    private RiptideIntroductionLesson() {}

    static RiptideCoursePlan plan() {
        return new RiptideCoursePlan(
                SEED,
                1,
                Stage.FINISH.start(),
                List.of(
                        wall(1, 18, "hitw_rink_d6"),
                        wall(2, 32, "hitw_highrise_e6"),
                        level(3, 50, RiptideLevelType.MATH, "ADD"),
                        level(4, COLOR_STEP + 5, RiptideLevelType.COLOR_FLOOR, "TERRACOTTA"),
                        level(5, DODGE_STEP + 5, RiptideLevelType.DODGE, "ZOMBIE"),
                        level(6, 122, RiptideLevelType.RHYTHM, "CENTER_RELEASE")),
                COURSE_LENGTH);
    }

    private static RiptideCoursePlan.Level wall(int number, int step, String id) {
        var template =
                RiptideHitwCatalog.templates().stream()
                        .filter(t -> t.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        return new RiptideCoursePlan.Level(
                number, step, template, template.variant(), 0, false, SEED);
    }

    private static RiptideCoursePlan.Level level(
            int number, int step, RiptideLevelType type, String variant) {
        var template =
                new RiptideLevelTemplate(
                        "lesson_" + number, type.name(), type, variant, true, 10, 1, 1);
        return new RiptideCoursePlan.Level(number, step, template, variant, 0, false, SEED);
    }

    static Stage stageAt(int tick) {
        Stage current = Stage.WELCOME;
        for (Stage stage : Stage.values()) if (tick >= stage.start()) current = stage;
        return current;
    }

    static int stepAt(int tick) {
        return STEPS[Math.clamp(tick, 0, DURATION_TICKS - 1)];
    }

    static double speedAt(int step) {
        return 2.2 + .75 * RiptideDifficulty.stage(step, COURSE_LENGTH);
    }

    private static int arrival(int step) {
        for (int tick = 0; tick < STEPS.length; tick++) if (STEPS[tick] >= step) return tick;
        throw new IllegalStateException("Lesson exceeds the introduction clock");
    }

    private static int[] journey() {
        int[] steps = new int[DURATION_TICKS];
        int step = 0, pause = 0;
        double movement = 0;
        for (int tick = DEPARTURE_TICK; tick < steps.length; tick++) {
            if (pause > 0) pause--;
            else if (step < FINISH_STEP) {
                movement += speedAt(step) / 20D;
                if (movement >= 1D) {
                    movement -= 1D;
                    step++;
                    if (step == COLOR_STEP) pause = COLOR_TICKS - 1;
                    if (step == DODGE_STEP) pause = DODGE_TICKS - 1;
                }
            }
            steps[tick] = step;
        }
        return steps;
    }

    static int departureCountdown(int tick) {
        return tick >= DEPARTURE_TICK ? 0 : (DEPARTURE_TICK - tick + 19) / 20;
    }
}
