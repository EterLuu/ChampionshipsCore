package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideBlueprint;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlanner;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideHitwCatalog;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideWallGroups;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class RiptidePassRunTest {
    @Test
    void sideWarningsUseTitlesKeepQuestionsAndClearOnClose() throws Exception {
        var config = RiptideTestFixtures.config();
        var g = config.resolveGeometry();
        var messages = ink.ziip.championshipscore.configuration.config.message.MessageConfig.class;
        var warning = messages.getField("RIPTIDE_RUSH_SWEEP_TITLE");
        var oldWarning = warning.get(null);
        var title = messages.getField("RIPTIDE_RUSH_QUESTION_TITLE");
        var subtitle = messages.getField("RIPTIDE_RUSH_QUESTION_SUBTITLE");
        var oldTitle = title.get(null);
        var oldSubtitle = subtitle.get(null);
        warning.set(null, "&e侧向来墙！");
        title.set(null, "%question%");
        subtitle.set(null, "left %left% | right %right%");
        try {
            for (String rhythm : List.of("SIDE", "SIDE_MATH")) {
                var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
                var walls =
                        List.of(
                                new RiptideCoursePlan.SideWall(template, "GAP", 0, 1, 4),
                                new RiptideCoursePlan.SideWall(template, "GAP", 0, -1, 4));
                var level =
                        new RiptideCoursePlan.Level(
                                1, 400, template, "GAP", 0, false, 123, 0, 0, rhythm, 1, walls);
                var run = new RiptidePassRun(g, List.of(level), config);
                var titles = new ArrayList<List<String>>();
                var resets = new AtomicInteger();
                var actionbars = new AtomicInteger();
                var stays = new ArrayList<Integer>();
                var player =
                        (org.bukkit.entity.Player)
                                Proxy.newProxyInstance(
                                        org.bukkit.entity.Player.class.getClassLoader(),
                                        new Class[] {org.bukkit.entity.Player.class},
                                        (p, m, a) -> {
                                            switch (m.getName()) {
                                                case "hashCode":
                                                    return 1;
                                                case "equals":
                                                    return p == a[0];
                                                case "sendTitle":
                                                    titles.add(
                                                            List.of((String) a[0], (String) a[1]));
                                                    stays.add((Integer) a[3]);
                                                    break;
                                                case "resetTitle":
                                                    resets.incrementAndGet();
                                                    break;
                                                case "sendActionBar":
                                                    actionbars.incrementAndGet();
                                                    break;
                                                case "getLocation":
                                                    return g.centerAt(g.stoppedStep(400));
                                            }
                                            return null;
                                        });
                // Hold rendering on an existing frame to exercise presentation without a server
                // world.
                for (int tick : List.of(0, 10, 20, 30, 40, 100, 110)) {
                    for (var e :
                            Map.<String, Object>of(
                                            "active",
                                            level,
                                            "speed",
                                            5.2,
                                            "tick",
                                            tick,
                                            "rendered",
                                            RiptideSideSweep.frame(tick, walls, 3, 5.2))
                                    .entrySet()) {
                        var field = RiptidePassRun.class.getDeclaredField(e.getKey());
                        field.setAccessible(true);
                        field.set(run, e.getValue());
                    }
                    run.tick(List.of(player));
                }
                assertEquals(rhythm.equals("SIDE") ? 1 : 7, titles.size());
                assertEquals(0, actionbars.get());
                if (rhythm.equals("SIDE")) {
                    assertEquals(List.of("§e侧向来墙！", ""), titles.getFirst());
                    assertEquals(List.of(30), stays);
                } else {
                    var question =
                            RiptideQuestionDisplay.of(
                                    RiptideSideMath.question(level, 1, 10, 99), 1);
                    assertEquals(question.title(), titles.getFirst().getFirst());
                    assertTrue(titles.getFirst().get(1).contains(question.subtitle()));
                    assertTrue(titles.getFirst().get(1).contains("侧向来墙！"));
                    assertTrue(
                            titles.subList(3, titles.size()).stream()
                                    .noneMatch(t -> t.get(1).contains("侧向来墙！")));
                }
                run.close();
                run.close();
                assertEquals(1, resets.get());
            }
        } finally {
            warning.set(null, oldWarning);
            title.set(null, oldTitle);
            subtitle.set(null, oldSubtitle);
        }
    }

    @Test
    void thickWallsAnimateLeadingCellsFirstOnEveryCourseAxis() {
        var cells = new ArrayList<RiptideMovingWall.Cell>();
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) cells.add(new RiptideMovingWall.Cell(x, 1, z, null));
        for (var face :
                List.of(
                        org.bukkit.block.BlockFace.NORTH,
                        org.bukkit.block.BlockFace.SOUTH,
                        org.bukkit.block.BlockFace.EAST,
                        org.bukkit.block.BlockFace.WEST)) {
            int dx = face.getModX(), dz = face.getModZ();
            assertEquals(face, RiptideNativePiston.facing(dx, dz));
            cells.sort(RiptideMovingWall.leadingFirst(dx, dz));
            for (int i = 0; i < cells.size(); i++) {
                var target = cells.get(i);
                for (int j = i + 1; j < cells.size(); j++) {
                    var later = cells.get(j);
                    assertFalse(
                            later.x() - dx == target.x() && later.z() - dz == target.z(),
                            "a later temporary source piston would erase an earlier moving block");
                }
            }
        }
    }

    @Test
    void rotatedSideWallsFitTheCommonStoppedDeckAndNeverTouchItsEntrance() throws Exception {
        var start = RiptideTestFixtures.config().resolveGeometry().centerAt(0);
        for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            for (int length : List.of(3, 9, 15)) {
                var g =
                        RiptideCourseGeometry.resolve(
                                start,
                                start.clone().add(axis[0] * 500, 0, axis[1] * 500),
                                7,
                                length);
                for (int direction : List.of(-1, 1))
                    for (boolean mirrored : List.of(false, true)) {
                        var transform = RiptideMovingWall.transform(g, direction, mirrored);
                        var cells = new ArrayList<RiptideMovingWall.Cell>();
                        for (int x = -7; x <= 7; x++)
                            for (int y = 1; y <= 12; y++)
                                for (int z = -3; z <= 3; z++) {
                                    var p =
                                            transform.apply(
                                                    com.sk89q.worldedit.math.Vector3.at(x, y, z));
                                    cells.add(
                                            new RiptideMovingWall.Cell(
                                                    (int) p.x(), (int) p.y(), (int) p.z(), null));
                                }
                        var fitted = RiptideMovingWall.fitStoppedDeck(g, cells);
                        assertEquals(Math.min(15, length) * 12 * 7, fitted.size());
                        for (var cell : fitted) {
                            int forward =
                                    g.stoppedStep(250)
                                            + cell.x() * g.stepX()
                                            + cell.z() * g.stepZ();
                            assertTrue(
                                    forward
                                            >= g.occupiedStart(
                                                    new RiptideCoursePlan.Level(
                                                            1,
                                                            250,
                                                            RiptideLevelTemplate.create(
                                                                    "dodge",
                                                                    RiptideLevelType.DODGE),
                                                            "ZOMBIE",
                                                            0,
                                                            false,
                                                            1)));
                            assertTrue(
                                    forward < 250,
                                    "Moving wall and its temporary pistons must stay before the"
                                            + " gold entrance");
                        }
                    }
            }
    }

    @Test
    void everyConfiguredSideSpeedAllowsVanillaPistonToFinishItsTwoMovingTicks() {
        var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var wall = new RiptideCoursePlan.SideWall(template, "GAP", 0, 1, 4);
        for (double speed : List.of(2.2, 2.95, 3.7, 4.45, 5.2, 5.5)) {
            int previousMove = -100,
                    previousLateral = RiptideSideSweep.frame(0, List.of(wall), 3, speed).lateral();
            for (int tick = 1; tick < RiptideSideSweep.beatTicks(3, speed, wall); tick++) {
                int lateral = RiptideSideSweep.frame(tick, List.of(wall), 3, speed).lateral();
                if (lateral != previousLateral) {
                    assertTrue(
                            tick - previousMove >= 3,
                            "moving piston needs two motion ticks and a completion tick");
                    previousMove = tick;
                    previousLateral = lateral;
                }
            }
        }
    }

    @Test
    void upcomingSideSweepSuppressesNormalMathTitleForItsDisplayWindow() throws Exception {
        var config = RiptideTestFixtures.config();
        var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var level =
                new RiptideCoursePlan.Level(
                        1,
                        100,
                        template,
                        "SIDE",
                        0,
                        false,
                        1,
                        0,
                        0,
                        "SIDE",
                        1,
                        List.of(new RiptideCoursePlan.SideWall(template, "GAP", 0, 1, 4)));
        var run = new RiptidePassRun(config.resolveGeometry(), List.of(level), config);

        // 12 title ticks at 5.2 blocks/s rounds up to four blocks of advance suppression.
        assertFalse(run.startsWithin(90, 5.2));
        assertTrue(run.startsWithin(91, 5.2));
        assertTrue(run.startsWithin(100, 5.2));
        assertEquals(95, run.nextStartStep());
    }

    @Test
    void lateralBudgetReservesSpeedForKeepingUpWithTheRaft() {
        assertTrue(RiptideWallGroups.lateralSpeed(4.45, false) < 3.5);
        assertTrue(RiptideWallGroups.lateralSpeed(5.2, false) < 2);
        assertEquals(3.5, RiptideWallGroups.lateralSpeed(5.2, true));
        var a = RiptideHitwCatalog.templates().getFirst();
        var b = RiptideHitwCatalog.templates().get(1);
        var first = new RiptideCoursePlan.Level(1, 395, a, "CUSTOM", 0, false, 1);
        var second = new RiptideCoursePlan.Level(2, 405, b, "CUSTOM", 0, false, 2);
        assertTrue(
                RiptideWallGroups.requiredSeconds(first, second, 5.2, false)
                        > RiptideWallGroups.requiredSeconds(first, second, 5.2, true));
    }

    @Test
    void groupsRespectPhysicalUseCapsAndSharedTrialBoundaries() throws Exception {
        var c = RiptideTestFixtures.config();
        var templates = c.resolvePool();
        int groups = 0, sweeps = 0;
        for (long seed = 0; seed < 8; seed++) {
            var plan = RiptideCoursePlanner.plan(c, seed);
            var uses = new HashMap<String, Integer>();
            for (var l : plan.levels()) {
                if (!l.isSideSweep()) uses.merge(l.template().id(), 1, Integer::sum);
                else {
                    sweeps++;
                    assertEquals(
                            RiptideSideSweep.wallCount(
                                    c.resolveGeometry().stoppedStep(l.step()), 500),
                            l.sideWalls().size());
                    for (var w : l.sideWalls()) uses.merge(w.template().id(), 1, Integer::sum);
                    assertEquals(List.of(l), plan.trialLevels(l));
                }
                if (l.wallGroup() > 0) {
                    groups++;
                    var selected = plan.trialLevels(l);
                    assertTrue(selected.size() >= 2);
                    assertTrue(selected.stream().allMatch(w -> w.wallGroup() == l.wallGroup()));
                    if (l.rhythm().equals("FINAL_TRIPLE")) assertEquals(3, selected.size());
                    else
                        assertEquals(
                                selected.size(),
                                selected.stream()
                                        .map(w -> w.template().designKey(w.variant()))
                                        .distinct()
                                        .count());
                }
            }
            for (var template : templates)
                assertTrue(uses.getOrDefault(template.id(), 0) <= template.maxUses());
            assertEquals(
                    plan.estimatedTicks(),
                    RiptideCoursePlanner.estimateTicks(c, c.resolveGeometry(), plan.levels()));
        }
        assertTrue(groups > 0);
        assertTrue(sweeps > 0);
    }

    @Test
    void templateTrialsRandomizeMirrorWithoutChangingTheSavedBuilding() throws Exception {
        var c = RiptideTestFixtures.config();
        var template = RiptideHitwCatalog.templates().getFirst();
        var mirrors = new HashSet<Boolean>();
        for (long seed = 0; seed < 100; seed++) {
            var plan = RiptideCoursePlanner.templatePreview(c, template, seed);
            mirrors.add(plan.levels().getFirst().mirrored());
            assertEquals(template, plan.levels().getFirst().template());
            assertEquals(plan, RiptideCoursePlanner.templatePreview(c, template, seed));
        }
        assertEquals(Set.of(false, true), mirrors);
    }

    @Test
    void independentDirectionsWarnAndMoveWholeBlocksAtEverySpeed() {
        var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        for (var directions :
                List.of(
                        List.of(1, 1),
                        List.of(-1, -1),
                        List.of(1, -1),
                        List.of(-1, 1),
                        List.of(1, 1, -1))) {
            var walls =
                    directions.stream()
                            .map(d -> new RiptideCoursePlan.SideWall(template, "GAP", 0, d, 4))
                            .toList();
            for (double speed : List.of(2.2, 3.7, 4.45, 5.2)) {
                int duration = RiptideSideSweep.beatTicks(3, speed, walls.getFirst());
                for (int beat = 0; beat < walls.size(); beat++) {
                    int start = beat * duration;
                    assertEquals(beat + 1, RiptideSideSweep.frame(start, walls, 3, speed).beat());
                    assertFalse(RiptideSideSweep.frame(start + 29, walls, 3, speed).moving());
                    assertTrue(RiptideSideSweep.frame(start + 30, walls, 3, speed).moving());
                    assertEquals(
                            10 * directions.get(beat),
                            RiptideSideSweep.frame(start, walls, 3, speed).lateral());
                    assertEquals(
                            -8 * directions.get(beat),
                            RiptideSideSweep.frame(start + duration - 1, walls, 3, speed)
                                    .lateral());
                    for (int t = 1; t < duration; t++) {
                        int delta =
                                Math.abs(
                                        RiptideSideSweep.frame(start + t, walls, 3, speed).lateral()
                                                - RiptideSideSweep.frame(
                                                                start + t - 1, walls, 3, speed)
                                                        .lateral());
                        assertTrue(delta == 0 || delta == 1);
                    }
                }
                int total = RiptideSideSweep.totalTicks(3, speed, walls);
                assertEquals(walls.size() * duration, total);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RiptideSideSweep.frame(total, walls, 3, speed));
            }
        }
    }

    @Test
    void poolSelectionIncludesCustomBuildingsRespectsLimitsAndAllowsAllDirectionPairs()
            throws Exception {
        var c = RiptideTestFixtures.config();
        var g = c.resolveGeometry();
        var original = RiptideHitwCatalog.templates().getFirst();
        var authored =
                new RiptideLevelTemplate(
                        "my_wall",
                        "我的墙",
                        RiptideLevelType.PASS,
                        "CUSTOM",
                        true,
                        10,
                        3,
                        1,
                        original.blueprint());
        var alternative =
                RiptideHitwCatalog.templates().stream()
                        .filter(t -> t.difficulty() == 2)
                        .filter(t -> !t.designKey("CUSTOM").equals(authored.designKey("CUSTOM")))
                        .filter(t -> !RiptideHitwCatalog.passages(t, false).isEmpty())
                        .filter(
                                t ->
                                        RiptideHitwCatalog.passages(t, false).stream()
                                                .noneMatch(
                                                        p ->
                                                                RiptideHitwCatalog.passages(
                                                                                authored, false)
                                                                        .stream()
                                                                        .anyMatch(
                                                                                q ->
                                                                                        Math.abs(
                                                                                                        p
                                                                                                                        .lateral()
                                                                                                                - q
                                                                                                                        .lateral())
                                                                                                < .75)))
                        .findFirst()
                        .orElseThrow();
        var disabled =
                new RiptideLevelTemplate(
                        "disabled", "禁用", RiptideLevelType.PASS, "JUMP", false, 100, 64, 1);
        var difficult =
                new RiptideLevelTemplate(
                        "hard", "高难", RiptideLevelType.PASS, "JUMP", true, 100, 64, 3);
        c.setTemplates(List.of(authored, alternative, disabled, difficult));
        var directions = new HashSet<List<Integer>>();
        for (int seed = 0; seed < 100; seed++) {
            var walls =
                    RiptideWallGroups.selectSideWalls(c, g, 200, new HashMap<>(), new Random(seed));
            assertEquals(2, walls.size());
            assertEquals(
                    Set.of(authored, alternative),
                    walls.stream()
                            .map(RiptideCoursePlan.SideWall::template)
                            .collect(java.util.stream.Collectors.toSet()));
            var firstPassages = RiptideWallGroups.sidePassages(walls.getFirst());
            var secondPassages = RiptideWallGroups.sidePassages(walls.getLast());
            assertTrue(
                    firstPassages.stream()
                            .noneMatch(
                                    p ->
                                            secondPassages.stream()
                                                    .anyMatch(
                                                            q ->
                                                                    Math.abs(
                                                                                    p.lateral()
                                                                                            - q
                                                                                                    .lateral())
                                                                            < .75)));
            directions.add(walls.stream().map(RiptideCoursePlan.SideWall::direction).toList());
            assertEquals(
                    walls,
                    RiptideWallGroups.selectSideWalls(
                            c, g, 200, new HashMap<>(), new Random(seed)));
        }
        assertEquals(
                Set.of(List.of(1, 1), List.of(1, -1), List.of(-1, 1), List.of(-1, -1)), directions);
        assertTrue(
                RiptideWallGroups.selectSideWalls(
                                c,
                                g,
                                200,
                                new HashMap<>(Map.of(authored.usageKey(), 1)),
                                new Random(1))
                        .isEmpty());
        assertEquals(2, RiptideSideSweep.wallCount(299, 500));
        assertEquals(3, RiptideSideSweep.wallCount(300, 500));
        assertTrue(
                RiptideWallGroups.selectSideWalls(c, g, 300, new HashMap<>(), new Random(1))
                        .isEmpty());
    }

    @Test
    void thickBuildingsStayOutsideRaftDuringWarningAndUseTheirOwnDuration() {
        var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var thin = new RiptideCoursePlan.SideWall(template, "GAP", 0, 1, 4);
        var authored = template.withBlueprint(new RiptideBlueprint("", 7, 15, 12, List.of()));
        var thick = new RiptideCoursePlan.SideWall(authored, "CUSTOM", 0, -1, 7);
        var walls = List.of(thin, thick, thin);
        int first = RiptideSideSweep.beatTicks(3, 3.7, thin);
        int second = RiptideSideSweep.beatTicks(3, 3.7, thick);
        assertTrue(second > first);
        assertEquals(17, RiptideSideSweep.radius(3, thick));
        assertEquals(-17, RiptideSideSweep.frame(first, walls, 3, 3.7).lateral());
        assertEquals(15, RiptideSideSweep.frame(first + second - 1, walls, 3, 3.7).lateral());
        assertEquals(3, RiptideSideSweep.frame(first + second, walls, 3, 3.7).beat());
        assertEquals(2 * first + second, RiptideSideSweep.totalTicks(3, 3.7, walls));
        assertEquals(7, RiptideSideSweep.radius(3, thick) - thick.thickness() - 3);
    }

    @Test
    void rotationPointsEveryAuthoredBuildingIntoRaftForAllCourseAxes() throws Exception {
        var base = RiptideTestFixtures.config().resolveGeometry();
        for (int[] axis :
                List.of(new int[] {0, 1}, new int[] {0, -1}, new int[] {1, 0}, new int[] {-1, 0})) {
            var start = base.centerAt(0);
            var finish = start.clone().add(axis[0] * 500, 0, axis[1] * 500);
            var g = RiptideCourseGeometry.resolve(start, finish, 7, 9);
            for (int direction : List.of(-1, 1)) {
                var transform = RiptideMovingWall.transform(g, direction);
                var forward = transform.apply(com.sk89q.worldedit.math.Vector3.at(0, 0, 1));
                assertEquals(-direction * g.stepZ(), forward.x(), 1E-9);
                assertEquals(direction * g.stepX(), forward.z(), 1E-9);
                var across = transform.apply(com.sk89q.worldedit.math.Vector3.at(1, 2, 0));
                assertEquals(direction * g.stepX(), across.x(), 1E-9);
                assertEquals(direction * g.stepZ(), across.z(), 1E-9);
                assertEquals(2, across.y(), 1E-9);
            }
        }
    }

    @Test
    void sideMirrorReflectsLocalWidthButKeepsTravelDirectionAndHeight() throws Exception {
        var base = RiptideTestFixtures.config().resolveGeometry();
        for (int[] axis :
                List.of(new int[] {0, 1}, new int[] {0, -1}, new int[] {1, 0}, new int[] {-1, 0})) {
            var start = base.centerAt(0);
            var g =
                    RiptideCourseGeometry.resolve(
                            start, start.clone().add(axis[0] * 500, 0, axis[1] * 500), 7, 9);
            for (int direction : List.of(-1, 1)) {
                var ordinary = RiptideMovingWall.transform(g, direction, false);
                var mirrored = RiptideMovingWall.transform(g, direction, true);
                for (int x = -4; x <= 4; x++)
                    for (int z = -2; z <= 2; z++)
                        assertEquals(
                                ordinary.apply(com.sk89q.worldedit.math.Vector3.at(-x, 2, z)),
                                mirrored.apply(com.sk89q.worldedit.math.Vector3.at(x, 2, z)));
            }
        }
    }

    @Test
    void eachSideWallDrawsAnIndependentMirrorAndLogsIt() throws Exception {
        var c = RiptideTestFixtures.config();
        var seen = new HashSet<List<Boolean>>();
        for (int seed = 0; seed < 100; seed++) {
            var walls =
                    RiptideWallGroups.selectSideWalls(
                            c, c.resolveGeometry(), 200, new HashMap<>(), new Random(seed));
            seen.add(walls.stream().map(RiptideCoursePlan.SideWall::mirrored).toList());
            var l =
                    new RiptideCoursePlan.Level(
                            1,
                            200,
                            walls.getFirst().template(),
                            "GAP",
                            0,
                            false,
                            seed,
                            0,
                            0,
                            "SIDE",
                            1,
                            walls);
            var plan = new RiptideCoursePlan(seed, RiptideCoursePlanner.VERSION, 0, List.of(l));
            var logged = (List<?>) plan.logLevels().getFirst().get("sideWalls");
            for (int i = 0; i < walls.size(); i++)
                assertEquals(walls.get(i).mirrored(), ((Map<?, ?>) logged.get(i)).get("mirrored"));
        }
        assertEquals(
                Set.of(
                        List.of(false, false),
                        List.of(false, true),
                        List.of(true, false),
                        List.of(true, true)),
                seen);
    }

    @Test
    void movingWallDoesNotInspectPlayerPositionOrContact() throws Exception {
        var config = RiptideTestFixtures.config();
        var template = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var walls =
                List.of(
                        new RiptideCoursePlan.SideWall(template, "GAP", 0, 1, 4),
                        new RiptideCoursePlan.SideWall(template, "GAP", 0, 1, 4));
        var level =
                new RiptideCoursePlan.Level(
                        1, 200, template, "GAP", 0, false, 1, 0, 0, "SIDE", 1, walls);
        var run = new RiptidePassRun(config.resolveGeometry(), List.of(level), config);
        // A moving tick that has not accumulated another whole block needs no world access.
        for (var entry :
                Map.<String, Object>of(
                                "active",
                                level,
                                "tick",
                                31,
                                "speed",
                                3.7,
                                "rendered",
                                RiptideSideSweep.frame(
                                        30, walls, config.resolveGeometry().halfWidth(), 3.7))
                        .entrySet()) {
            var field = RiptidePassRun.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(run, entry.getValue());
        }
        var player =
                (org.bukkit.entity.Player)
                        Proxy.newProxyInstance(
                                org.bukkit.entity.Player.class.getClassLoader(),
                                new Class[] {org.bukkit.entity.Player.class},
                                (p, m, args) -> {
                                    throw new AssertionError(
                                            "unexpected player inspection: " + m.getName());
                                });
        run.tick(List.of(player));
        assertTrue(run.active());
        run.close();
    }

    @Test
    void cleanupRestoresOriginalBlocksWithoutPhysicsAndIsIdempotent() throws Exception {
        var run =
                new RiptidePassRun(
                        RiptideTestFixtures.config().resolveGeometry(),
                        List.of(),
                        RiptideTestFixtures.config());
        var removed = new AtomicInteger();
        var block =
                (BlockState)
                        Proxy.newProxyInstance(
                                BlockState.class.getClassLoader(),
                                new Class[] {BlockState.class},
                                (p, m, a) -> {
                                    if (m.getName().equals("update")) {
                                        assertEquals(true, a[0]);
                                        assertEquals(false, a[1]);
                                        removed.incrementAndGet();
                                        return true;
                                    }
                                    throw new AssertionError(m.getName());
                                });
        var field = RiptidePassRun.class.getDeclaredField("blocks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var blocks = (List<BlockState>) field.get(run);
        blocks.add(block);
        blocks.add(block);
        run.close();
        run.close();
        assertEquals(2, removed.get());
        assertFalse(run.active());
        assertTrue(blocks.isEmpty());
    }
}
