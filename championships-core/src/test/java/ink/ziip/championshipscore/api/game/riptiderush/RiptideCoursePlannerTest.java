package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static ink.ziip.championshipscore.api.game.riptiderush.RiptideTestFixtures.config;
import static ink.ziip.championshipscore.api.game.riptiderush.RiptideTestFixtures.defaults;

class RiptideCoursePlannerTest {
    @Test void sideSweepsLeaveStoppedRaftClearOfNeighboringBuildings() throws Exception {
        var c = config(); var g = c.resolveGeometry();
        var pass = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var wall = new RiptideCoursePlan.SideWall(pass, "GAP", 0, 1, 1);
        // A three-block-deep neighbor needs nine blocks center-to-center: 3 + raft half 4 + clearance 2.
        for (var example : Map.of(6, false, 8, false, 9, true, 12, true).entrySet()) {
            int gap = example.getKey();
            var stationary = new RiptideCoursePlan.Level(1, 200, pass, "WEAVE", 0, false, 1);
            var sweep = new RiptideCoursePlan.Level(2, 200 + gap, pass, "GAP", 0, false, 2,
                    0, 0, "SIDE", 1, List.of(wall));
            assertEquals(example.getValue(), RiptideCoursePlanner.safeTransition(c, g, stationary, sweep));
            var firstSweep = new RiptideCoursePlan.Level(1, 200, pass, "GAP", 0, false, 1,
                    0, 0, "SIDE", 1, List.of(wall));
            var nextStationary = new RiptideCoursePlan.Level(2, 200 + gap, pass, "WEAVE", 0, false, 2);
            assertEquals(example.getValue(), RiptideCoursePlanner.safeTransition(c, g, firstSweep, nextStationary));
        }
    }

    @Test void fourAccelerationsShareExactBoundariesAndStayBelowSprinting() throws Exception {
        var c = config(); var g = c.resolveGeometry();
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

    @Test void sampledCoursesRespectCountsBandsTransitionsAndTimeBudget() throws Exception {
        var c = config(); var g = c.resolveGeometry();
        var sequences = new HashSet<List<String>>();
        Set<String> shapes = new HashSet<>(); Set<String> operations = new HashSet<>();
        // A representative deterministic sample is enough here; the planner
        // invariants are structural and were previously checked 1,000 times,
        // making the full Core suite needlessly slow.
        for (long seed = 0; seed < 100; seed++) {
            var plan = RiptideCoursePlanner.plan(c, seed);
            assertEquals(32, plan.levels().stream().map(RiptideCoursePlan.Level::number).distinct().count());
            for (var type : RiptideLevelType.values())
                assertEquals(RiptideCoursePlanner.quota(c, type), plan.levels().stream().filter(l -> l.type() == type).map(RiptideCoursePlan.Level::number).distinct().count());
            assertEquals(RiptideLevelType.PASS, plan.levels().getFirst().type());
            assertEquals(RiptideLevelType.PASS, plan.levels().getLast().type());
            for (int band = 0; band < 4; band++) {
                int start = band * 8;
                assertEquals(1, plan.levels().stream().filter(l -> l.number() > start && l.number() <= start + 8 && l.type() == RiptideLevelType.COLOR_FLOOR).count());
            }
            var floors = plan.levels().stream().filter(l -> l.type() == RiptideLevelType.COLOR_FLOOR).toList();
            assertEquals(4, floors.stream().map(RiptideCoursePlan.Level::variant).distinct().count());
            for (int i = 0; i < plan.levels().size(); i++) {
                var level = plan.levels().get(i);
                if (level.type() == RiptideLevelType.PASS) shapes.add(level.variant());
                if (level.type() == RiptideLevelType.MATH) operations.add(level.variant());
                if (level.variant().equals("WEAVE"))
                    assertTrue(RiptideCoursePlanner.speedAt(c, g, level.step() + 3) <= 2.8);
                if (i == 0) continue;
                var previous = plan.levels().get(i - 1);
                assertTrue(RiptideCoursePlanner.safeTransition(c, g, previous, level));
                assertTrue(previous.type() != level.type() || level.type() == RiptideLevelType.PASS
                        || previous.wallGroup() != 0 && previous.wallGroup() == level.wallGroup());
                if (previous.type() == RiptideLevelType.PASS && level.type() == RiptideLevelType.PASS && previous.number() != level.number())
                    assertNotEquals(previous.template().designKey(previous.variant()), level.template().designKey(level.variant()));
            }
            assertEquals(RiptideCoursePlanner.estimateTicks(c, g, plan.levels()), plan.estimatedTicks());
            assertTrue(plan.estimatedTicks() < c.getTimer() * 20);
            sequences.add(plan.levels().stream().map(l -> l.type() + ":" + l.variant()).toList());
        }
        assertEquals(100, sequences.size());
        assertTrue(shapes.contains("CUSTOM"));
        assertEquals(new HashSet<>(RiptideQuestion.variants()), operations);
    }

    @Test void seedReproducesGeometryQuestionsAndEveryFloorRoundWithoutChangingConfig() throws Exception {
        var c = config(); var original = c.getPool().toString();
        var first = RiptideCoursePlanner.plan(c, -9876543210L);
        var second = RiptideCoursePlanner.plan(c, -9876543210L);
        assertEquals(first, second); assertEquals(original, c.getPool().toString());
        for (int i = 0; i < first.levels().size(); i++) {
            var a = first.levels().get(i); var b = second.levels().get(i);
            if (a.type() == RiptideLevelType.MATH) assertEquals(a.question(10, 99), b.question(10, 99));
            if (a.type() != RiptideLevelType.COLOR_FLOOR) continue;
            var runA = new RiptideColorFloorRun(7, 9, RiptideDifficulty.floorRoundTicks(0,500), new Random(a.contentSeed()), RiptideColorFloorRun.Theme.valueOf(a.variant()));
            var runB = new RiptideColorFloorRun(7, 9, RiptideDifficulty.floorRoundTicks(0,500), new Random(b.contentSeed()), RiptideColorFloorRun.Theme.valueOf(b.variant()));
            do {
                assertEquals(runA.floor(), runB.floor()); assertEquals(runA.target(), runB.target());
                while (!runA.tick()) assertFalse(runB.tick());
                assertTrue(runB.tick());
                boolean next = runA.advance(); assertEquals(next, runB.advance());
                if (!next) break;
            } while (true);
        }
    }

    @Test void disabledEntriesAndUseCapsAreHonoredAndDifficultEntriesCannotOpenCourse() throws Exception {
        var c = config(); var rows = new ArrayList<>(c.resolvePool());
        rows.add(new RiptideLevelTemplate("rare", "稀有关", RiptideLevelType.PASS, "GAP", true, 100, 1, 3));
        rows.add(new RiptideLevelTemplate("off", "停用关", RiptideLevelType.PASS, "JUMP", false, 100, 64, 1));
        c.setTemplates(rows);
        for (int seed = 0; seed < 25; seed++) {
            var plan = RiptideCoursePlanner.plan(c, seed);
            assertFalse(plan.levels().stream().anyMatch(l -> l.template().id().equals("off")));
            assertTrue(plan.levels().stream().filter(l -> !l.isSideSweep() && l.template().id().equals("rare")).count()
                    + plan.levels().stream().flatMap(l -> l.sideWalls().stream()).filter(w -> w.template().id().equals("rare")).count() <= 1);
            plan.levels().stream().filter(l -> l.template().id().equals("rare"))
                    .forEach(l -> assertTrue(l.step() / 500D >= .4));
        }
    }

    @Test void impossiblePoolsAndIncompatibleSettingsFailWithBoundedWork() throws Exception {
        var c = config(); c.setPassCount(1);
        assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
        c.setPassCount(20); c.setTimer(200);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1)).getMessage().contains("限时"));
        c.setTimer(300); c.setFinalSpeed(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
        c.setFinalSpeed(4); c.setMinimumLevelSpacing(16);
        assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
        c.setMinimumLevelSpacing(14);
        var rows = new ArrayList<>(c.resolvePool());
        rows.replaceAll(t -> t.type() == RiptideLevelType.PASS ? new RiptideLevelTemplate(t.id(), "仅跳栏", RiptideLevelType.PASS, "JUMP", true, 10, 64, t.difficulty()) : t);
        c.setTemplates(rows);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1)));
    }

    @Test void removingRequiredPoolEntryReportsItsMissingCapacity() throws Exception {
        var c = config(); c.setTemplates(c.resolvePool().stream().filter(t -> t.type() != RiptideLevelType.MATH).toList());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1)).getMessage().contains("解题关卡池不足"));
    }

    @Test void passOnlyRoutesAndSingleTemplatePreviewAreSupported() throws Exception {
        var c = config(); c.setMathCount(0); c.setStoppedCount(0); c.setPassCount(12);
        assertTrue(RiptideCoursePlanner.plan(c, 2).levels().stream().allMatch(l -> l.type() == RiptideLevelType.PASS));
        var single = RiptideCoursePlanner.templatePreview(c, RiptideLevelTemplate.create("new", RiptideLevelType.COLOR_FLOOR), 1);
        assertEquals(1, single.levels().size()); assertEquals(RiptideLevelType.COLOR_FLOOR, single.levels().getFirst().type());
    }

    @Test void templateSerializationValidatesIdentityVariantsAndBounds() {
        for (var type : RiptideLevelType.values()) {
            var t = RiptideLevelTemplate.create(type.name().toLowerCase(Locale.ROOT), type);
            assertEquals(t, RiptideLevelTemplate.parse(t.serialize()));
        }
        assertThrows(IllegalArgumentException.class, () -> new RiptideLevelTemplate("../bad", "x", RiptideLevelType.PASS, "AUTO", true, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RiptideLevelTemplate("bad", "x", RiptideLevelType.MATH, "WEAVE", true, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RiptideLevelTemplate("bad", "x", RiptideLevelType.PASS, "AUTO", true, 0, 1, 1));
    }

    @Test void legacyMigrationPreservesQuotasSettingsAndPublishedMapMetadata() throws Exception {
        var old = defaults(); old.set("course", null); old.set("dont-edit-this.version", 2);
        old.set("levels", List.of("PASS", "MATH", "PASS", "COLOR_FLOOR", "PASS"));
        old.set("timer", 123); old.set("prepare.revision", 3); old.set("prepare.published", true);
        old.set("custom.keep", "自定义"); old.set("rules", List.of(List.of("彩色地板规则", "自定义规则")));
        var migrated = new YamlConfiguration(); migrated.loadFromString(old.saveToString());
        RiptideRushConfig.migrateCourse(old, migrated);
        assertEquals(3, migrated.getInt("course.counts.pass"));
        assertEquals(1, migrated.getInt("course.counts.math")); assertEquals(1, migrated.getInt("course.counts.stopped"));
        assertEquals(389, migrated.getMapList("course.pool").size()); assertTrue(migrated.getStringList("levels").isEmpty());
        assertEquals(123, migrated.getInt("timer")); assertEquals(3, migrated.getInt("prepare.revision"));
        assertTrue(migrated.getBoolean("prepare.published")); assertEquals("自定义", migrated.getString("custom.keep"));
        assertEquals(List.of(List.of("踩色规则", "自定义规则")), migrated.getList("rules"));
        String first = migrated.saveToString(); RiptideRushConfig.migrateCourse(migrated, migrated);
        var before = new YamlConfiguration(); before.loadFromString(first);
        var after = new YamlConfiguration(); after.loadFromString(migrated.saveToString());
        assertEquals(before.getValues(true).keySet(), after.getValues(true).keySet());
        for (String key : before.getKeys(true)) if (!before.isConfigurationSection(key))
            assertEquals(before.get(key), after.get(key), key);
    }
}
