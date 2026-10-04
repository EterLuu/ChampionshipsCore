package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideSideSweep;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideDeparture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Bounded, deterministic planning. No world mutation or player state is consulted. */
public final class RiptideCoursePlanner {
    public static final int VERSION = 28;

    private RiptideCoursePlanner() {}

    /** A template can be tried before the entire pool's quotas have been made satisfiable. */
    public static RiptideCoursePlan templatePreview(
            RiptideRushConfig c, RiptideLevelTemplate template, long seed) {
        if (template.variant().equals("CUSTOM") && template.blueprint() == null)
            throw new IllegalArgumentException("请先编辑并保存建筑，再试玩此变体");
        var g = c.resolveGeometry();
        validateEnvironment(c, g);
        int step =
                (int)
                        (g.totalSteps()
                                * (template.difficulty() == 3
                                                || template.variant().equals("MULTIPLY")
                                        ? .7
                                        : .5));
        if (step < 16 || step + g.halfLength() + 8 >= g.totalSteps())
            throw new IllegalArgumentException("单关试玩需要更长的航道");
        Random random = new Random(seed);
        var variants =
                new ArrayList<>(
                        template.variant().equals("AUTO")
                                ? RiptideLevelTemplate.variants(template.type())
                                        .subList(
                                                1,
                                                RiptideLevelTemplate.variants(template.type())
                                                        .size())
                                : List.of(template.variant()));
        if (speedAt(c, g, step + 3) > 2.8) variants.remove("WEAVE");
        if (!safeMathPair(c, g, template, step)) variants.remove("DOUBLE");
        if (variants.isEmpty())
            throw new IllegalArgumentException("此变体在试玩位置没有足够的换位时间，请调整速度或建筑长度后试玩");
        String variant = variants.get(random.nextInt(variants.size()));
        var level =
                new RiptideCoursePlan.Level(
                        1,
                        step,
                        template,
                        variant,
                        random.nextInt(g.raftWidth() - 2) - g.halfWidth() + 1,
                        (template.type() == RiptideLevelType.PASS || template.blueprint() == null)
                                && random.nextBoolean(),
                        random.nextLong());
        return new RiptideCoursePlan(seed, VERSION, 0, List.of(level), g.totalSteps());
    }

    public static RiptideCoursePlan plan(RiptideRushConfig config, long seed) {
        var geometry = config.resolveGeometry();
        var stoppedAllocation = config.stoppedAllocation(seed);
        List<RiptideLevelTemplate> pool = validateInputs(config, geometry, stoppedAllocation);
        int count =
                Arrays.stream(RiptideLevelType.values())
                        .mapToInt(type -> quota(config, type, stoppedAllocation))
                        .sum();
        return plan(config, seed, geometry, stoppedAllocation, pool, count);
    }

    /** Checks configuration and pool capacity without attempting a course arrangement. */
    public static void validateInputs(RiptideRushConfig config, long seed) {
        validateInputs(config, config.resolveGeometry(), config.stoppedAllocation(seed));
    }

    private static List<RiptideLevelTemplate> validateInputs(
            RiptideRushConfig config,
            RiptideCourseGeometry geometry,
            RiptideStoppedAllocation stoppedAllocation) {
        validateSettings(config, geometry);
        List<RiptideLevelTemplate> pool = config.resolvePool();
        for (var template : pool) {
            if (!template.enabled() || template.blueprint() == null) continue;
            var building = template.blueprint();
            if (building.width() < geometry.raftWidth() + 2
                    || building.width() > RiptideWorkshop.halfWidth(config, geometry) * 2 + 1
                    || building.height() > config.getClearHeight())
                throw new IllegalArgumentException(template.name() + "：建筑尺寸不匹配，请重新编辑保存");
            if (template.type() == RiptideLevelType.COLOR_FLOOR)
                RiptideBlueprint.validateFloor(
                        building.floorMaterials(), geometry.raftWidth(), geometry.raftLength());
        }
        for (RiptideLevelType type : RiptideLevelType.values()) {
            int needed = quota(config, type, stoppedAllocation);
            var enabled = pool.stream().filter(t -> t.enabled() && t.type() == type).toList();
            int available =
                    type == RiptideLevelType.PASS
                            ? (int)
                                    enabled.stream()
                                            .map(RiptideLevelTemplate::usageKey)
                                            .distinct()
                                            .count()
                            : enabled.stream().mapToInt(RiptideLevelTemplate::maxUses).sum();
            if (available < needed)
                throw new IllegalArgumentException(
                        type.displayName()
                                + "关卡池不足：需要 "
                                + needed
                                + " 次，启用关卡最多提供 "
                                + available
                                + " 次");
        }
        return pool;
    }

    private static RiptideCoursePlan plan(
            RiptideRushConfig config,
            long seed,
            RiptideCourseGeometry geometry,
            RiptideStoppedAllocation stoppedAllocation,
            List<RiptideLevelTemplate> pool,
            int count) {
        Random random = new Random(seed);
        // Extra departure time can reject otherwise valid courses. Only extend the search
        // after such a rejection; structurally impossible pools must still fail quickly.
        int attemptLimit = 160;
        for (int attempt = 0; attempt < attemptLimit; attempt++) {
            List<RiptideLevelType> types = new ArrayList<>();
            int[] remaining = new int[RiptideLevelType.values().length];
            for (RiptideLevelType type : RiptideLevelType.values())
                remaining[type.ordinal()] = quota(config, type, stoppedAllocation);
            if (!arrange(
                    types,
                    remaining,
                    count,
                    stoppedAllocation.colorFloor() + stoppedAllocation.sideSweep(),
                    random,
                    new int[] {12000})) continue;
            List<RiptideCoursePlan.Level> levels = new ArrayList<>();
            if (!select(
                    config,
                    geometry,
                    pool,
                    types,
                    levels,
                    new HashMap<>(),
                    random,
                    new int[] {4000})) continue;
            levels = arrangeMathPairs(config, geometry, pool, levels, random);
            levels = RiptideChallengeGroups.arrange(config, geometry, levels);
            levels =
                    RiptideWallGroups.arrange(
                            config, geometry, levels, random, stoppedAllocation.sideSweep());
            if (levels == null) continue;
            levels = RiptideFinalStage.arrange(config, geometry, levels, random);
            if (levels == null || !RiptideWallGroups.uniqueWalls(levels)) continue;
            int ticks = estimateTicks(config, geometry, levels);
            if (ticks >= config.getTimer() * 20L) {
                attemptLimit = 640;
                continue;
            }
            return new RiptideCoursePlan(seed, VERSION, ticks, levels, geometry.totalSteps());
        }
        throw new IllegalArgumentException(
                "无法生成安全赛道：检查配额、池内次数上限和难度；穿越在首次加速前仅难度1，首次后仅2，第二次后2或3；"
                    + "穿越墙体整场最多一次，连续穿越须错开通行位置；停船挑战包括踩色、躲避与侧向；末段三连门与侧向解题须有足够空间且总耗时短于限时；折返不用于高速段；建筑过长时请减少关数或缩短建筑以留足衔接距离");
    }

    public static int quota(RiptideRushConfig config, RiptideLevelType type) {
        return quota(config, type, config.previewStoppedAllocation());
    }

    public static int quota(RiptideRushConfig config, RiptideLevelType type, long seed) {
        return quota(config, type, config.stoppedAllocation(seed));
    }

    private static int quota(
            RiptideRushConfig config,
            RiptideLevelType type,
            RiptideStoppedAllocation stoppedAllocation) {
        return switch (type) {
            case PASS -> config.getPassCount();
            case MATH -> config.getMathCount();
            case COLOR_FLOOR -> stoppedAllocation.colorFloor() + stoppedAllocation.sideSweep();
            case DODGE -> stoppedAllocation.dodge();
            case RHYTHM -> config.getRhythmCount();
        };
    }

    private static void validateSettings(RiptideRushConfig c, RiptideCourseGeometry g) {
        long total =
                c.getPassCount()
                        + (long) c.getMathCount()
                        + c.getStoppedCount()
                        + c.getRhythmCount();
        if (c.getPassCount() < 2
                || c.getMathCount() < 0
                || c.getStoppedCount() < 0
                || c.getRhythmCount() < 0
                || total > 64) throw new IllegalArgumentException("穿越至少2关，解题/停船挑战/节奏不能为负，总数最多64关");
        int spacing = (int) (g.totalSteps() / (total + 1D));
        if (spacing < Math.max(c.getMinimumLevelSpacing(), g.raftLength() + 5))
            throw new IllegalArgumentException("关卡间距不足；请减少关数或延长航线");
        validateEnvironment(c, g);
    }

    private static void validateEnvironment(RiptideRushConfig c, RiptideCourseGeometry g) {
        if (g.totalSteps() > 4096
                || g.raftWidth() > 15
                || g.raftLength() > 15
                || c.getObstacleMargin() < 2
                || c.getObstacleMargin() > 8
                || c.getClearHeight() < 5
                || c.getClearHeight() > 16)
            throw new IllegalArgumentException("航线最多4096格，木筏边长最多15格，边距2–8格，清理高度5–16格");
        if (c.getTimer() < 1 || c.getTimer() > 3600 || !c.hasValidMovementSpeeds())
            throw new IllegalArgumentException("速度须递增且在0.25–5.5格/秒内；限时1–3600秒");
        if (c.getMinimumOperand() < 0
                || c.getMaximumOperand() < c.getMinimumOperand()
                || c.getMaximumOperand() > Integer.MAX_VALUE / 2)
            throw new IllegalArgumentException("解题数字范围无效");
        if (c.getMathPreviewBlocks() < 1
                || !Double.isFinite(c.getHorizontalPadding())
                || c.getHorizontalPadding() < 0
                || !Double.isFinite(c.getFallDistance())
                || c.getFallDistance() <= 0)
            throw new IllegalArgumentException("解题预览距离、木筏边距或坠落距离无效");
    }

    /**
     * Each floor occupies its own proportional band; at most three consecutive PASS when feasible.
     */
    private static boolean arrange(
            List<RiptideLevelType> out,
            int[] remaining,
            int total,
            int floors,
            Random random,
            int[] budget) {
        if (--budget[0] < 0) return false;
        int index = out.size();
        if (index == total) return true;
        int specials =
                remaining[RiptideLevelType.MATH.ordinal()]
                        + remaining[RiptideLevelType.COLOR_FLOOR.ordinal()]
                        + remaining[RiptideLevelType.DODGE.ordinal()]
                        + remaining[RiptideLevelType.RHYTHM.ordinal()];
        int reservedEnd = out.isEmpty() || out.getLast() != RiptideLevelType.PASS ? 1 : 0;
        if (specials > 2 * remaining[RiptideLevelType.PASS.ordinal()] + 2 - reservedEnd)
            return false;
        var choices = new ArrayList<>(List.of(RiptideLevelType.values()));
        Collections.shuffle(choices, random);
        // Prioritize a special after three passes, but allow longer runs for pass-heavy pools.
        if (index >= 3
                && out.subList(index - 3, index).stream().allMatch(t -> t == RiptideLevelType.PASS))
            choices.sort(java.util.Comparator.comparing(t -> t == RiptideLevelType.PASS));
        for (var type : choices) {
            if (remaining[type.ordinal()] == 0) continue;
            if ((index == 0 || index == total - 1) && type != RiptideLevelType.PASS) continue;
            if (type != RiptideLevelType.PASS && index > 0 && out.getLast() == type) continue;
            if (type != RiptideLevelType.PASS
                    && index >= 2
                    && out.getLast() != RiptideLevelType.PASS
                    && out.get(index - 2) != RiptideLevelType.PASS) continue;
            int usedFloors = floors - remaining[RiptideLevelType.COLOR_FLOOR.ordinal()];
            int band = floors == 0 ? 0 : index * floors / total;
            if (floors > 0
                    && (usedFloors < band
                            || type == RiptideLevelType.COLOR_FLOOR && usedFloors != band))
                continue;
            out.add(type);
            remaining[type.ordinal()]--;
            if (arrange(out, remaining, total, floors, random, budget)) return true;
            remaining[type.ordinal()]++;
            out.removeLast();
        }
        return false;
    }

    private static boolean select(
            RiptideRushConfig c,
            RiptideCourseGeometry g,
            List<RiptideLevelTemplate> pool,
            List<RiptideLevelType> types,
            List<RiptideCoursePlan.Level> out,
            Map<String, Integer> uses,
            Random random,
            int[] budget) {
        if (--budget[0] < 0) return false;
        int i = out.size();
        if (i == types.size()) return true;
        int step = g.levelStep(i, types.size());
        double progress = step / (double) g.totalSteps();
        int difficulty = progress < .25 ? 1 : progress < .65 ? 2 : 3;
        var candidates =
                new ArrayList<>(
                        pool.stream()
                                .filter(
                                        t ->
                                                t.enabled()
                                                        && t.type() == types.get(i)
                                                        && (t.type() == RiptideLevelType.PASS
                                                                ? RiptideDifficulty.allowsPass(
                                                                        t.difficulty(),
                                                                        step,
                                                                        g.totalSteps())
                                                                : t.difficulty() <= difficulty)
                                                        && uses.getOrDefault(t.usageKey(), 0)
                                                                < t.maxUses())
                                .toList());
        while (!candidates.isEmpty()) {
            // Prefer a different floor theme within each group of five; a smaller custom pool can
            // repeat.
            if (types.get(i) == RiptideLevelType.COLOR_FLOOR) {
                var recent =
                        out.stream().filter(l -> l.type() == RiptideLevelType.COLOR_FLOOR).toList();
                var used =
                        recent.subList(recent.size() / 7 * 7, recent.size()).stream()
                                .map(l -> l.template().designKey(l.variant()))
                                .toList();
                if (candidates.stream().anyMatch(t -> !used.contains(t.designKey(t.variant()))))
                    candidates.removeIf(t -> used.contains(t.designKey(t.variant())));
            }
            // Decrease repeat weight within a round, while respecting explicit configured weights.
            double totalWeight =
                    candidates.stream()
                            .mapToDouble(t -> t.weight() / (1D + uses.getOrDefault(t.id(), 0)))
                            .sum();
            double draw = random.nextDouble() * totalWeight;
            int selected = 0;
            while (selected < candidates.size() - 1
                    && (draw -=
                                    candidates.get(selected).weight()
                                            / (1D
                                                    + uses.getOrDefault(
                                                            candidates.get(selected).id(), 0)))
                            >= 0) selected++;
            var t = candidates.remove(selected);
            var variants =
                    new ArrayList<>(
                            t.variant().equals("AUTO")
                                    ? RiptideLevelTemplate.variants(t.type())
                                            .subList(
                                                    1,
                                                    RiptideLevelTemplate.variants(t.type()).size())
                                    : List.of(t.variant()));
            Collections.shuffle(variants, random);
            if (t.type() == RiptideLevelType.COLOR_FLOOR && t.variant().equals("AUTO")) {
                var recent =
                        out.stream().filter(l -> l.type() == RiptideLevelType.COLOR_FLOOR).toList();
                int cycleStart = recent.size() / 7 * 7;
                var used =
                        recent.subList(cycleStart, recent.size()).stream()
                                .map(RiptideCoursePlan.Level::variant)
                                .toList();
                variants.removeIf(used::contains);
            }
            for (String variant : variants) {
                if (t.type() == RiptideLevelType.MATH
                        && !RiptideDifficulty.allowsMath(variant, step, g.totalSteps())) continue;
                if (variant.equals("DOUBLE")
                        && step < g.totalSteps() * .8
                        && !safeMathPair(c, g, t, step)) continue;
                if (variant.equals("WEAVE") && (speedAt(c, g, step + 3) > 2.8 || i == 0)) continue;
                if (!out.isEmpty()
                        && t.type() == RiptideLevelType.PASS
                        && out.getLast().type() == t.type()
                        && out.getLast()
                                .template()
                                .designKey(out.getLast().variant())
                                .equals(t.designKey(variant))) continue;
                int opening = random.nextInt(g.raftWidth() - 2) - g.halfWidth() + 1;
                var level =
                        new RiptideCoursePlan.Level(
                                i + 1,
                                step,
                                t,
                                variant,
                                opening,
                                random.nextBoolean(),
                                random.nextLong());
                if (!out.isEmpty() && !RiptideWallGroups.distinctPassages(out.getLast(), level, g))
                    continue;
                if (!out.isEmpty()
                        && step < g.totalSteps() * .8
                        && !safeTransition(c, g, out.getLast(), level)) continue;
                out.add(level);
                uses.merge(t.usageKey(), 1, Integer::sum);
                if (select(c, g, pool, types, out, uses, random, budget)) return true;
                uses.merge(t.usageKey(), -1, Integer::sum);
                out.removeLast();
            }
        }
        return false;
    }

    /** A double occupies more than one uniform slot; borrow space from its adjacent passes. */
    private static List<RiptideCoursePlan.Level> arrangeMathPairs(
            RiptideRushConfig c,
            RiptideCourseGeometry g,
            List<RiptideLevelTemplate> pool,
            List<RiptideCoursePlan.Level> input,
            Random random) {
        var out = new ArrayList<>(input);
        for (int i = 1; i + 1 < out.size(); i++) {
            var single = out.get(i);
            if (single.type() != RiptideLevelType.MATH
                    || single.variant().equals("DOUBLE")
                    || single.variant().startsWith("OBSERVE_")
                    || single.step() >= g.totalSteps() * .8
                    || !RiptideDifficulty.allowsMath("DOUBLE", single.step(), g.totalSteps())
                    || !random.nextBoolean()) continue;
            for (var template : pool) {
                if (!template.enabled()
                        || template.type() != RiptideLevelType.MATH
                        || !List.of("DOUBLE", "AUTO").contains(template.variant())
                        || template.difficulty() > (single.step() < g.totalSteps() * .65 ? 2 : 3)
                        || !safeMathPair(c, g, template, single.step())) continue;
                long uses =
                        out.stream().filter(l -> l.template().id().equals(template.id())).count();
                if (uses >= template.maxUses()) continue;
                var pair =
                        new RiptideCoursePlan.Level(
                                single.number(),
                                single.step(),
                                template,
                                "DOUBLE",
                                single.opening(),
                                single.mirrored(),
                                single.contentSeed());
                var before = out.get(i - 1);
                var after = out.get(i + 1);
                if (before.type() != RiptideLevelType.PASS || after.type() != RiptideLevelType.PASS)
                    continue;
                int reserve =
                        (int)
                                Math.ceil(
                                        Math.max(3D, (g.raftWidth() - 1) / 3D + .5D)
                                                * speedAt(c, g, after.step() + 8));
                var candidate = new ArrayList<>(out);
                candidate.set(i, pair);
                candidate.set(
                        i - 1,
                        moveLevel(
                                before,
                                Math.min(
                                        before.step(),
                                        pair.step() - pair.extent() - before.extent() - reserve)));
                candidate.set(
                        i + 1,
                        moveLevel(
                                after,
                                Math.max(
                                        after.step(),
                                        pair.step() + pair.extent() + after.extent() + reserve)));
                boolean valid =
                        candidate.getFirst().step() > g.halfLength() + 2
                                && candidate.getLast().step() + candidate.getLast().extent()
                                        < g.totalSteps() - g.halfLength();
                for (int j = Math.max(1, i - 1); j < Math.min(out.size(), i + 3); j++)
                    valid &= safeTransition(c, g, candidate.get(j - 1), candidate.get(j));
                for (int j : new int[] {i - 1, i + 1})
                    valid &=
                            RiptideDifficulty.allowsPass(
                                    candidate.get(j).template().difficulty(),
                                    candidate.get(j).step(),
                                    g.totalSteps());
                if (valid) {
                    out = candidate;
                    break;
                }
            }
        }
        return out;
    }

    private static RiptideCoursePlan.Level moveLevel(RiptideCoursePlan.Level l, int step) {
        return new RiptideCoursePlan.Level(
                l.number(),
                step,
                l.template(),
                l.variant(),
                l.opening(),
                l.mirrored(),
                l.contentSeed());
    }

    private static boolean safeMathPair(
            RiptideRushConfig c, RiptideCourseGeometry g, RiptideLevelTemplate template, int step) {
        int extent = template.blueprint() == null ? 0 : template.blueprint().extent();
        return (12 - extent * 2) / speedAt(c, g, step + 6 + extent)
                >= Math.max(3D, (g.raftWidth() - 1) / 3D + .5D);
    }

    static boolean safeTransition(
            RiptideRushConfig c,
            RiptideCourseGeometry g,
            RiptideCoursePlan.Level first,
            RiptideCoursePlan.Level second) {
        int gap = g.occupiedStart(second) - g.occupiedEnd(first);
        if (!RiptideWallGroups.distinctPassages(first, second, g)) return false;
        if (first.type() == RiptideLevelType.PASS
                && second.type() == RiptideLevelType.PASS
                && !first.isSideSweep()
                && !second.isSideSweep()
                && first.template()
                        .designKey(first.variant())
                        .equals(second.template().designKey(second.variant()))) return false;
        // Every stopped child reserves its deck plus entrance and gives its own instructions.
        if (first.stopsRaft() || second.stopsRaft()) return gap >= 2;
        if (first.rhythm().equals("FINAL_TRIPLE") && second.rhythm().equals("FINAL_TRIPLE"))
            return gap >= 2
                    && gap / speedAt(c, g, second.step())
                            >= RiptideFinalStage.transitionSeconds(first, second, g);
        if (first.wallGroup() < 0 && first.wallGroup() == second.wallGroup())
            return first.type() == second.type()
                    && (first.type() == RiptideLevelType.MATH
                            || first.type() == RiptideLevelType.RHYTHM)
                    && gap / speedAt(c, g, second.step() + second.extent())
                            >= RiptideChallengeGroups.seconds(g, first.type());
        if (first.wallGroup() != 0 && first.wallGroup() == second.wallGroup())
            return first.type() == RiptideLevelType.PASS
                    && second.type() == RiptideLevelType.PASS
                    && !first.isSideSweep()
                    && !second.isSideSweep()
                    && gap >= 2
                    && gap / speedAt(c, g, second.step())
                            >= RiptideWallGroups.requiredSeconds(
                                    first,
                                    second,
                                    speedAt(c, g, second.step()),
                                    first.step() >= g.totalSteps() * .8);
        if (gap <= g.halfLength() + 2) return false;
        double seconds = gap / speedAt(c, g, second.step() + second.extent());
        double lateral =
                first.template().blueprint() == null
                                && second.template().blueprint() == null
                                && first.variant().equals("GAP")
                                && second.variant().equals("GAP")
                        ? Math.abs(first.opening() - second.opening())
                        : g.raftWidth() - 1;
        return seconds
                >= Math.max(second.type() == RiptideLevelType.MATH ? 2D : 1.5D, lateral / 3D + .5D);
    }

    public static double speedAt(RiptideRushConfig c, RiptideCourseGeometry g, int step) {
        return c.speedAtProgress(step / (double) g.totalSteps());
    }

    /**
     * Mirrors tickCourse: budget is retained across stops, and the arrival tick does not consume
     * pause time.
     */
    public static int estimateTicks(
            RiptideRushConfig c, RiptideCourseGeometry g, List<RiptideCoursePlan.Level> levels) {
        var stops = new HashMap<Integer, Integer>();
        for (var level : levels) {
            if (!level.stopsRaft()) continue;
            int stoppedStep = g.stoppedStep(level.step());
            int duration =
                    switch (level.kind()) {
                        case COLOR_FLOOR ->
                                RiptideColorFloorRun.INTRO_TICKS
                                        + RiptideDifficulty.floorTicks(stoppedStep, g.totalSteps());
                        case DODGE -> RiptideDodgeRun.DURATION_TICKS;
                        case SIDE_SWEEP ->
                                RiptideSideSweep.totalTicks(
                                        g.halfWidth(),
                                        speedAt(c, g, stoppedStep),
                                        level.sideWalls());
                        default -> throw new IllegalStateException("unsupported stopped stage");
                    };
            stops.put(stoppedStep, duration);
        }
        int step = 0, paused = 0, tick = 0;
        double movement = 0;
        while (step < g.totalSteps() || paused > 0) {
            tick++;
            if (paused > 0) {
                paused--;
                continue;
            }
            movement += speedAt(c, g, step) / 20D;
            while (movement >= 1 && step < g.totalSteps()) {
                step++;
                movement--;
                if (stops.containsKey(step)) {
                    paused = stops.get(step) + RiptideDeparture.WAIT_TICKS;
                    break;
                }
            }
        }
        return tick;
    }
}
