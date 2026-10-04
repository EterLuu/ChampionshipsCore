package ink.ziip.championshipscore.api.game.riptiderush.course;

import static ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideMathRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideQuestion;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.List;

class RiptideCoursePlannerTest {
    @Nested
    class RiptideCoursePlannerCases {
        @Test
        void sideSweepsLeaveStoppedRaftClearOfNeighboringBuildings() throws Exception {
            var c = config();
            var g = c.resolveGeometry();
            var pass = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
            var wall = new RiptideCoursePlan.SideWall(pass, "GAP", 0, 1, 1);
            // The incoming wall must clear the whole stopped deck and its one-block entrance gap.
            for (var example :
                    Map.of(6, false, 12, false, 13, false, 14, true, 16, true).entrySet()) {
                int gap = example.getKey();
                var stationary = new RiptideCoursePlan.Level(1, 200, pass, "WEAVE", 0, false, 1);
                var sweep =
                        new RiptideCoursePlan.Level(
                                2,
                                200 + gap,
                                pass,
                                "GAP",
                                0,
                                false,
                                2,
                                0,
                                0,
                                "SIDE",
                                1,
                                List.of(wall));
                assertEquals(
                        example.getValue(),
                        RiptideCoursePlanner.safeTransition(c, g, stationary, sweep));
            }
            for (var example : Map.of(4, false, 5, true, 9, true).entrySet()) {
                int gap = example.getKey();
                var firstSweep =
                        new RiptideCoursePlan.Level(
                                1, 200, pass, "GAP", 0, false, 1, 0, 0, "SIDE", 1, List.of(wall));
                var nextStationary =
                        new RiptideCoursePlan.Level(2, 200 + gap, pass, "WEAVE", 0, false, 2);
                assertEquals(
                        example.getValue(),
                        RiptideCoursePlanner.safeTransition(c, g, firstSweep, nextStationary));
            }
        }

        @Test
        void allStoppedChildrenReserveTheSameDeckAndEntrance() throws Exception {
            var c = config();
            var g = c.resolveGeometry();
            var pass = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
            var floor = RiptideLevelTemplate.create("floor", RiptideLevelType.COLOR_FLOOR);
            var dodge = RiptideLevelTemplate.create("dodge", RiptideLevelType.DODGE);
            var children =
                    List.of(
                            new RiptideCoursePlan.Level(2, 250, floor, "COPPER", 0, false, 1),
                            new RiptideCoursePlan.Level(2, 250, dodge, "ZOMBIE", 0, false, 1),
                            new RiptideCoursePlan.Level(
                                    2,
                                    250,
                                    floor,
                                    "COPPER",
                                    0,
                                    false,
                                    1,
                                    0,
                                    0,
                                    "SIDE",
                                    1,
                                    List.of(new RiptideCoursePlan.SideWall(pass, "GAP", 0, 1, 7))));
            var before = new RiptideCoursePlan.Level(1, 230, pass, "WEAVE", 0, false, 1);
            var after = new RiptideCoursePlan.Level(3, 265, pass, "WEAVE", 0, false, 1);
            for (var child : children) {
                assertEquals(0, child.extent());
                assertEquals(245, g.stoppedStep(child.step()));
                assertEquals(241, g.occupiedStart(child));
                assertEquals(250, g.occupiedEnd(child));
                assertTrue(RiptideCoursePlanner.safeTransition(c, g, before, child));
                assertTrue(RiptideCoursePlanner.safeTransition(c, g, child, after));
            }
        }

        @Test
        void fourAccelerationsShareExactBoundariesAndStayBelowSprinting() throws Exception {
            var c = config();
            var g = c.resolveGeometry();
            double[] speeds = {2.2, 2.95, 3.7, 4.45, 5.2};
            for (int step = 0; step <= 500; step++) {
                double expected = speeds[Math.min(step / 100, 4)];
                assertEquals(expected, RiptideCoursePlanner.speedAt(c, g, step), 1E-9);
                assertTrue(expected < 5.6);
            }
            assertTrue(c.hasValidMovementSpeeds());
            c.setFinalSpeed(5.6);
            assertFalse(c.hasValidMovementSpeeds());
        }

        @Test
        void sampledCoursesRespectCountsBandsTransitionsAndTimeBudget() throws Exception {
            var c = config();
            var g = c.resolveGeometry();
            var sequences = new HashSet<List<String>>();
            Set<String> shapes = new HashSet<>();
            Set<String> operations = new HashSet<>();
            for (long seed = 0; seed < 8; seed++) {
                var plan = RiptideCoursePlanner.plan(c, seed);
                assertEquals(
                        32,
                        plan.levels().stream()
                                .map(RiptideCoursePlan.Level::number)
                                .distinct()
                                .count());
                for (var type : RiptideLevelType.values())
                    assertEquals(
                            RiptideCoursePlanner.quota(c, type),
                            plan.levels().stream()
                                    .filter(l -> l.type() == type)
                                    .map(RiptideCoursePlan.Level::number)
                                    .distinct()
                                    .count());
                assertEquals(RiptideLevelType.PASS, plan.levels().getFirst().type());
                assertEquals(RiptideLevelType.PASS, plan.levels().getLast().type());
                for (int band = 0; band < 4; band++) {
                    int start = band * 8;
                    assertEquals(
                            1,
                            plan.levels().stream()
                                    .filter(
                                            l ->
                                                    l.number() > start
                                                            && l.number() <= start + 8
                                                            && l.type()
                                                                    == RiptideLevelType.COLOR_FLOOR)
                                    .count());
                }
                var floors =
                        plan.levels().stream()
                                .filter(l -> l.type() == RiptideLevelType.COLOR_FLOOR)
                                .toList();
                assertEquals(
                        4,
                        floors.stream().map(RiptideCoursePlan.Level::variant).distinct().count());
                for (int i = 0; i < plan.levels().size(); i++) {
                    var level = plan.levels().get(i);
                    if (level.type() == RiptideLevelType.PASS) shapes.add(level.variant());
                    if (level.type() == RiptideLevelType.MATH) operations.add(level.variant());
                    if (level.variant().equals("WEAVE"))
                        assertTrue(RiptideCoursePlanner.speedAt(c, g, level.step() + 3) <= 2.8);
                    if (i == 0) continue;
                    var previous = plan.levels().get(i - 1);
                    assertTrue(RiptideCoursePlanner.safeTransition(c, g, previous, level));
                    assertTrue(
                            previous.type() != level.type()
                                    || level.type() == RiptideLevelType.PASS
                                    || previous.wallGroup() != 0
                                            && previous.wallGroup() == level.wallGroup());
                    if (previous.type() == RiptideLevelType.PASS
                            && level.type() == RiptideLevelType.PASS
                            && previous.number() != level.number())
                        assertNotEquals(
                                previous.template().designKey(previous.variant()),
                                level.template().designKey(level.variant()));
                }
                assertEquals(
                        RiptideCoursePlanner.estimateTicks(c, g, plan.levels()),
                        plan.estimatedTicks());
                assertTrue(plan.estimatedTicks() < c.getTimer() * 20);
                sequences.add(
                        plan.levels().stream().map(l -> l.type() + ":" + l.variant()).toList());
            }
            assertEquals(8, sequences.size());
            assertTrue(shapes.contains("CUSTOM"));
            assertEquals(new HashSet<>(RiptideQuestion.variants()), operations);
        }

        @Test
        void seedReproducesGeometryQuestionsAndEveryFloorRoundWithoutChangingConfig()
                throws Exception {
            var c = config();
            var original = c.getPool().toString();
            var first = RiptideCoursePlanner.plan(c, -9876543210L);
            var second = RiptideCoursePlanner.plan(c, -9876543210L);
            assertEquals(first, second);
            assertEquals(original, c.getPool().toString());
            for (int i = 0; i < first.levels().size(); i++) {
                var a = first.levels().get(i);
                var b = second.levels().get(i);
                if (a.type() == RiptideLevelType.MATH)
                    assertEquals(a.question(10, 99), b.question(10, 99));
                if (a.type() != RiptideLevelType.COLOR_FLOOR) continue;
                var runA =
                        new RiptideColorFloorRun(
                                7,
                                9,
                                RiptideDifficulty.floorRoundTicks(0, 500),
                                new Random(a.contentSeed()),
                                RiptideColorFloorRun.Theme.valueOf(a.variant()));
                var runB =
                        new RiptideColorFloorRun(
                                7,
                                9,
                                RiptideDifficulty.floorRoundTicks(0, 500),
                                new Random(b.contentSeed()),
                                RiptideColorFloorRun.Theme.valueOf(b.variant()));
                do {
                    assertEquals(runA.floor(), runB.floor());
                    assertEquals(runA.target(), runB.target());
                    while (!runA.tick()) assertFalse(runB.tick());
                    assertTrue(runB.tick());
                    boolean next = runA.advance();
                    assertEquals(next, runB.advance());
                    if (!next) break;
                } while (true);
            }
        }

        @Test
        void disabledEntriesAndUseCapsAreHonoredAndDifficultEntriesCannotOpenCourse()
                throws Exception {
            var c = config();
            var rows = new ArrayList<>(c.resolvePool());
            rows.add(
                    new RiptideLevelTemplate(
                            "rare", "稀有关", RiptideLevelType.PASS, "GAP", true, 100, 1, 3));
            rows.add(
                    new RiptideLevelTemplate(
                            "off", "停用关", RiptideLevelType.PASS, "JUMP", false, 100, 64, 1));
            c.setTemplates(rows);
            for (int seed = 0; seed < 4; seed++) {
                var plan = RiptideCoursePlanner.plan(c, seed);
                assertFalse(plan.levels().stream().anyMatch(l -> l.template().id().equals("off")));
                assertTrue(
                        plan.levels().stream()
                                                .filter(
                                                        l ->
                                                                !l.isSideSweep()
                                                                        && l.template()
                                                                                .id()
                                                                                .equals("rare"))
                                                .count()
                                        + plan.levels().stream()
                                                .flatMap(l -> l.sideWalls().stream())
                                                .filter(w -> w.template().id().equals("rare"))
                                                .count()
                                <= 1);
                plan.levels().stream()
                        .filter(l -> l.template().id().equals("rare"))
                        .forEach(l -> assertTrue(l.step() / 500D >= .4));
            }
        }

        @Test
        void impossiblePoolsAndIncompatibleSettingsFailWithBoundedWork() throws Exception {
            var c = config();
            c.setPassCount(1);
            assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
            c.setPassCount(20);
            c.setTimer(200);
            assertTrue(
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> RiptideCoursePlanner.plan(c, 1))
                            .getMessage()
                            .contains("限时"));
            c.setTimer(300);
            c.setFinalSpeed(Double.NaN);
            assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
            c.setFinalSpeed(4);
            c.setMinimumLevelSpacing(16);
            assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
            c.setMinimumLevelSpacing(14);
            var rows = new ArrayList<>(c.resolvePool());
            rows.replaceAll(
                    t ->
                            t.type() == RiptideLevelType.PASS
                                    ? new RiptideLevelTemplate(
                                            t.id(),
                                            "仅跳栏",
                                            RiptideLevelType.PASS,
                                            "JUMP",
                                            true,
                                            10,
                                            64,
                                            t.difficulty())
                                    : t);
            c.setTemplates(rows);
            assertTimeoutPreemptively(
                    Duration.ofSeconds(3),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> RiptideCoursePlanner.plan(c, 1)));
        }

        @Test
        void removingRequiredPoolEntryReportsItsMissingCapacity() throws Exception {
            var c = config();
            c.setTemplates(
                    c.resolvePool().stream()
                            .filter(t -> t.type() != RiptideLevelType.MATH)
                            .toList());
            assertTrue(
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> RiptideCoursePlanner.plan(c, 1))
                            .getMessage()
                            .contains("解题关卡池不足"));
        }

        @Test
        void passOnlyRoutesAndSingleTemplatePreviewAreSupported() throws Exception {
            var c = config();
            c.setMathCount(0);
            c.setStoppedCount(0);
            c.setPassCount(12);
            assertTrue(
                    RiptideCoursePlanner.plan(c, 2).levels().stream()
                            .allMatch(l -> l.type() == RiptideLevelType.PASS));
            var single =
                    RiptideCoursePlanner.templatePreview(
                            c, RiptideLevelTemplate.create("new", RiptideLevelType.COLOR_FLOOR), 1);
            assertEquals(1, single.levels().size());
            assertEquals(RiptideLevelType.COLOR_FLOOR, single.levels().getFirst().type());
        }

        @Test
        void everyPlayableLevelTypeProducesAStandaloneTrialPlan() throws Exception {
            var c = config();
            for (var type : RiptideLevelType.values()) {
                var template =
                        c.resolvePool().stream()
                                .filter(t -> t.type() == type)
                                .findFirst()
                                .orElseGet(
                                        () ->
                                                RiptideLevelTemplate.create(
                                                        "trial-"
                                                                + type.name()
                                                                        .toLowerCase(Locale.ROOT),
                                                        type));
                var plan = RiptideCoursePlanner.templatePreview(c, template, 1234L);
                assertFalse(plan.levels().isEmpty(), type + " should have a trial level");
                assertFalse(
                        plan.trialLevels(plan.levels().getFirst()).isEmpty(),
                        type + " should retain its trial group");
                assertEquals(type, plan.levels().getFirst().type());
            }
        }

        @Test
        void templateSerializationValidatesIdentityVariantsAndBounds() {
            for (var type : RiptideLevelType.values()) {
                var t = RiptideLevelTemplate.create(type.name().toLowerCase(Locale.ROOT), type);
                assertEquals(t, RiptideLevelTemplate.parse(t.serialize()));
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new RiptideLevelTemplate(
                                    "../bad", "x", RiptideLevelType.PASS, "AUTO", true, 1, 1, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new RiptideLevelTemplate(
                                    "bad", "x", RiptideLevelType.MATH, "WEAVE", true, 1, 1, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new RiptideLevelTemplate(
                                    "bad", "x", RiptideLevelType.PASS, "AUTO", true, 0, 1, 1));
        }
    }

    @Nested
    class RiptideMathDoubleCases {
        private static final RiptideLevelTemplate DOUBLE =
                new RiptideLevelTemplate(
                        "math_double", "连续两道数学门", RiptideLevelType.MATH, "DOUBLE", true, 10, 64, 1);

        @Test
        void previewIncludesBothIndependentQuestionsAndEitherSelectionTrialsTheWholePair()
                throws Exception {
            var c = RiptideTestFixtures.config();
            var plan = RiptideCoursePlanner.templatePreview(c, DOUBLE, 123);
            assertEquals(plan, RiptideCoursePlanner.templatePreview(c, DOUBLE, 123));
            assertEquals(2, plan.levels().size());
            var first = plan.levels().getFirst();
            var second = plan.levels().getLast();
            assertEquals(12, second.step() - first.step());
            assertNotEquals(first.contentSeed(), second.contentSeed());
            assertTrue(RiptideCoursePlanner.safeTransition(c, c.resolveGeometry(), first, second));
            for (var level : plan.levels()) {
                assertEquals(plan.levels(), plan.trialLevels(level));
                assertNotNull(level.question(10, 99));
            }
            var g = c.resolveGeometry();
            var gates =
                    plan.levels().stream()
                            .map(
                                    l ->
                                            new RiptideMathRun.Gate(
                                                    l.number(), l.step(), l.question(10, 99)))
                            .toList();
            var run = new RiptideMathRun(g, gates, g.centerAt(first.step() - 1));
            for (var gate : gates) {
                int lateral = gate.question().acceptsLateralOffset(2) ? 2 : -2;
                var before = g.centerAt(gate.step() - 1).add(lateral, 0, 0);
                run.sample(before);
                assertEquals(gate, run.preview(before, 3.7, 18, gate.step(), false));
                var answer = run.sample(g.centerAt(gate.step() + 1).add(lateral, 0, 0));
                assertEquals(1, answer.size());
                assertEquals(RiptideMathRun.Result.CORRECT, answer.getFirst().result());
            }
        }

        @Test
        void fullCoursesCanSelectPairsAndPreserveQuotaAndSafeTransitions() throws Exception {
            var c = RiptideTestFixtures.config();
            var pool = new ArrayList<>(c.resolvePool());
            pool.add(DOUBLE);
            c.setTemplates(pool);
            boolean sawPair = false;
            for (int seed = 0; seed < 8; seed++) {
                var plan = RiptideCoursePlanner.plan(c, seed);
                assertEquals(
                        8,
                        plan.levels().stream()
                                .filter(l -> l.type() == RiptideLevelType.MATH)
                                .map(RiptideCoursePlan.Level::number)
                                .distinct()
                                .count());
                for (int i = 1; i < plan.levels().size(); i++)
                    assertTrue(
                            RiptideCoursePlanner.safeTransition(
                                    c,
                                    c.resolveGeometry(),
                                    plan.levels().get(i - 1),
                                    plan.levels().get(i)),
                            "seed="
                                    + seed
                                    + " previous="
                                    + plan.levels().get(i - 1)
                                    + " next="
                                    + plan.levels().get(i));
                sawPair |= plan.levels().stream().anyMatch(l -> l.rhythm().equals("MATH_DOUBLE"));
            }
            assertTrue(sawPair);
        }
    }

    @Nested
    class RiptideChallengeGroupsCases {
        @Test
        void mathAndRhythmDoublesAndTriplesStartOneStageEarlierAndTrialTheWholeGroup()
                throws Exception {
            var c = RiptideTestFixtures.config();
            var g = c.resolveGeometry();
            for (var type : List.of(RiptideLevelType.MATH, RiptideLevelType.RHYTHM)) {
                for (int step : List.of(50, 140, 250, 350)) {
                    var template = RiptideLevelTemplate.create("challenge", type);
                    var level =
                            new RiptideCoursePlan.Level(
                                    1,
                                    step,
                                    template,
                                    type == RiptideLevelType.MATH ? "ADD" : "SHUTTER",
                                    0,
                                    false,
                                    5);
                    var levels = RiptideChallengeGroups.arrange(c, g, List.of(level));
                    assertEquals(step < 100 ? 1 : step < 200 ? 2 : 3, levels.size());
                    var plan =
                            new RiptideCoursePlan(1, RiptideCoursePlanner.VERSION, 0, levels, 500);
                    for (var gate : levels) assertEquals(levels, plan.trialLevels(gate));
                    for (int i = 1; i < levels.size(); i++) {
                        assertTrue(
                                RiptideCoursePlanner.safeTransition(
                                        c, g, levels.get(i - 1), levels.get(i)));
                        assertNotEquals(
                                levels.get(i - 1).contentSeed(), levels.get(i).contentSeed());
                    }
                    if (type == RiptideLevelType.RHYTHM)
                        assertEquals(
                                levels.size(),
                                levels.stream()
                                        .map(RiptideCoursePlan.Level::variant)
                                        .distinct()
                                        .count());
                    assertEquals(levels, RiptideChallengeGroups.arrange(c, g, List.of(level)));
                }
            }
        }

        @Test
        void noGroupStartsBeforeItsMilestoneOrOverlapsAnotherBuilding() throws Exception {
            var c = RiptideTestFixtures.config();
            var g = c.resolveGeometry();
            var template = RiptideLevelTemplate.create("challenge", RiptideLevelType.MATH);
            for (int step : List.of(100, 101, 200, 201)) {
                var level = new RiptideCoursePlan.Level(1, step, template, "ADD", 0, false, 5);
                var levels = RiptideChallengeGroups.arrange(c, g, List.of(level));
                assertTrue(levels.size() < (step < 200 ? 2 : 3));
            }
            var thick = template.withBlueprint(new RiptideBlueprint("", 7, 15, 12, List.of()));
            var a = new RiptideCoursePlan.Level(1, 205, thick, "ADD", 0, false, 1);
            var b = new RiptideCoursePlan.Level(2, 250, thick, "ADD", 0, false, 2);
            var d = new RiptideCoursePlan.Level(3, 295, thick, "ADD", 0, false, 3);
            var result = RiptideChallengeGroups.arrange(c, g, List.of(a, b, d));
            assertEquals(4, result.size());
            assertEquals(a, result.getFirst());
            assertEquals(d, result.getLast());
            for (int i = 1; i < result.size(); i++)
                assertTrue(
                        RiptideCoursePlanner.safeTransition(
                                c, g, result.get(i - 1), result.get(i)));
        }
    }
}
