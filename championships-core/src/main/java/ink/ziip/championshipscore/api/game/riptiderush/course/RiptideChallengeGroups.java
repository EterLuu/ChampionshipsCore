package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideQuestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Expands a logical challenge only when its stage has room for every physical gate. */
final class RiptideChallengeGroups {
    private RiptideChallengeGroups() {}

    static double seconds(RiptideCourseGeometry g, RiptideLevelType type) {
        return Math.max(type == RiptideLevelType.MATH ? 3 : 2, (g.raftWidth() - 1) / 3D + .5);
    }

    static List<RiptideCoursePlan.Level> arrange(
            RiptideRushConfig c, RiptideCourseGeometry g, List<RiptideCoursePlan.Level> input) {
        var out = new ArrayList<>(input);
        for (var source : input) {
            int index = -1;
            for (int i = 0; i < out.size(); i++)
                if (out.get(i).number() == source.number()) {
                    index = i;
                    break;
                }
            var original = out.get(index);
            if (original.type() != RiptideLevelType.MATH
                    && original.type() != RiptideLevelType.RHYTHM) continue;
            int stage = RiptideDifficulty.stage(original.step(), g.totalSteps());
            // The finale still owns side-math conversion; rhythm groups can continue into it.
            if (stage == 0 || stage == 4 || original.wallGroup() != 0) continue;
            for (int count = stage >= 2 ? 3 : 2; count >= 2; count--) {
                if (count == 2 && original.variant().equals("DOUBLE")) break;
                var group = gates(c, g, original, count);
                var candidate = new ArrayList<>(out);
                candidate.remove(index);
                candidate.addAll(index, group);
                if (!retime(c, g, candidate, index, index + count - 1)) continue;
                out = candidate;
                break;
            }
        }
        return List.copyOf(out);
    }

    private static List<RiptideCoursePlan.Level> gates(
            RiptideRushConfig c,
            RiptideCourseGeometry g,
            RiptideCoursePlan.Level original,
            int count) {
        int extent =
                original.template().blueprint() == null
                        ? 0
                        : original.template().blueprint().extent();
        int stageEnd =
                (int)
                                Math.ceil(
                                        (RiptideDifficulty.stage(original.step(), g.totalSteps())
                                                        + 1)
                                                * g.totalSteps()
                                                / 5D)
                        - 1;
        int gap =
                2 * extent
                        + (int)
                                Math.ceil(
                                        seconds(g, original.type())
                                                * RiptideCoursePlanner.speedAt(c, g, stageEnd));
        int first = original.step() - (count - 1) * gap / 2;
        var result = new ArrayList<RiptideCoursePlan.Level>();
        var random = new Random(original.contentSeed());
        var rhythmVariants =
                original.type() == RiptideLevelType.RHYTHM
                        ? distinctRhythmVariants(original.variant(), count, random)
                        : List.<String>of();
        for (int beat = 1; beat <= count; beat++) {
            int step = first + (beat - 1) * gap;
            String variant = original.variant();
            if (variant.equals("DOUBLE")) {
                var choices =
                        RiptideQuestion.variants().stream()
                                .filter(v -> RiptideDifficulty.allowsMath(v, step, g.totalSteps()))
                                .toList();
                variant = choices.get(random.nextInt(choices.size()));
            } else if (original.type() == RiptideLevelType.RHYTHM) {
                variant = rhythmVariants.get(beat - 1);
            }
            result.add(
                    new RiptideCoursePlan.Level(
                            original.number(),
                            step,
                            original.template(),
                            variant,
                            original.opening(),
                            original.mirrored(),
                            random.nextLong(),
                            -original.number(),
                            beat,
                            original.type().name() + (count == 3 ? "_TRIPLE" : "_DOUBLE"),
                            0));
        }
        return result;
    }

    /** Keep every physical gate in a rhythm group mechanically distinct. */
    static List<String> distinctRhythmVariants(String preferred, int count, Random random) {
        var available =
                new ArrayList<>(
                        RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM)
                                .subList(
                                        1,
                                        RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM)
                                                .size()));
        var result = new ArrayList<String>(count);
        if (!preferred.equals("AUTO")) {
            result.add(preferred);
            available.remove(preferred);
        }
        Collections.shuffle(available, random);
        for (String variant : available) {
            if (result.size() == count) break;
            result.add(variant);
        }
        return List.copyOf(result);
    }

    private static boolean retime(
            RiptideRushConfig c,
            RiptideCourseGeometry g,
            ArrayList<RiptideCoursePlan.Level> levels,
            int first,
            int last) {
        int count = last - first + 1;
        if (RiptideDifficulty.stage(levels.get(first).step(), g.totalSteps()) < (count == 3 ? 2 : 1)
                || RiptideDifficulty.stage(levels.get(first).step(), g.totalSteps())
                        != RiptideDifficulty.stage(levels.get(last).step(), g.totalSteps()))
            return false;
        // Propagate only within existing acceleration bands. This preserves difficulty and
        // reserves the final fifth for its separate packing pass.
        for (int i = first - 1; i >= 0; i--) {
            var original = levels.get(i);
            var placed = original;
            while (!RiptideCoursePlanner.safeTransition(c, g, placed, levels.get(i + 1))) {
                placed = move(placed, placed.step() - 1);
                if (!sameBand(g, original, placed)) return false;
            }
            levels.set(i, placed);
        }
        for (int i = last + 1; i < levels.size(); i++) {
            var original = levels.get(i);
            if (original.step() >= g.totalSteps() * .8) break;
            var placed = original;
            while (!RiptideCoursePlanner.safeTransition(c, g, levels.get(i - 1), placed)) {
                placed = move(placed, placed.step() + 1);
                if (!sameBand(g, original, placed)) return false;
            }
            levels.set(i, placed);
        }
        if (levels.getFirst().step() - levels.getFirst().extent() <= g.halfLength() + 2
                || levels.getLast().step() + levels.getLast().extent()
                        >= g.totalSteps() - g.halfLength()) return false;
        for (int i = 0; i < levels.size(); i++) {
            var l = levels.get(i);
            if (l.type() == RiptideLevelType.PASS
                    && !RiptideDifficulty.allowsPass(
                            l.template().difficulty(), l.step(), g.totalSteps())) return false;
            if (l.type() == RiptideLevelType.MATH
                    && !RiptideDifficulty.allowsMath(l.variant(), l.step(), g.totalSteps()))
                return false;
            if (i > 0
                    && l.step() < g.totalSteps() * .8
                    && !RiptideCoursePlanner.safeTransition(c, g, levels.get(i - 1), l))
                return false;
        }
        return true;
    }

    private static boolean sameBand(
            RiptideCourseGeometry g,
            RiptideCoursePlan.Level original,
            RiptideCoursePlan.Level placed) {
        return placed.step() >= 0
                && placed.step() <= g.totalSteps()
                && RiptideDifficulty.stage(original.step(), g.totalSteps())
                        == RiptideDifficulty.stage(placed.step(), g.totalSteps())
                && (original.step() < g.totalSteps() * .8 || original.step() == placed.step());
    }

    private static RiptideCoursePlan.Level move(RiptideCoursePlan.Level l, int step) {
        return new RiptideCoursePlan.Level(
                l.number(),
                step,
                l.template(),
                l.variant(),
                l.opening(),
                l.mirrored(),
                l.contentSeed(),
                l.wallGroup(),
                l.beat(),
                l.rhythm(),
                l.sweep(),
                l.sideWalls());
    }
}
