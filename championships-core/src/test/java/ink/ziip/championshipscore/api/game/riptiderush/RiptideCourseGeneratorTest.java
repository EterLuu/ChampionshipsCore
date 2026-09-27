package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static ink.ziip.championshipscore.api.game.riptiderush.RiptideTestFixtures.*;

class RiptideCourseGeneratorTest {
    @Test void doubleMathBuildsTwoDoorsAtExactlyTheJudgedPlanes() throws Exception {
        var world = new TestWorld(); var c = config(world, new int[]{0,1}); var g = c.resolveGeometry();
        var template = new RiptideLevelTemplate("pair", "连续两道", RiptideLevelType.MATH, "DOUBLE", true, 10, 64, 1);
        var plan = RiptideCoursePlanner.templatePreview(c, template, 42);
        clear(world, c);
        RiptideCourseGenerator.build(world.world, g, plan, Material.OAK_PLANKS, Material.STONE);
        assertEquals(2, plan.levels().size());
        for (var level : plan.levels()) {
            assertEquals(Material.STONE, world.at(g, level.step(), 0, 2));
            assertEquals(Material.RED_CONCRETE, world.at(g, level.step(), g.halfWidth()+1, 2));
            assertEquals(Material.LIGHT_BLUE_CONCRETE, world.at(g, level.step(), -g.halfWidth()-1, 2));
            for (int y=1;y<=3;y++) {
                assertEquals(Material.AIR, world.at(g,level.step(),1,y));
                assertEquals(Material.AIR, world.at(g,level.step(),-1,y));
            }
        }
    }

    @Test void generatedBlocksMatchPlanAndRerollRemovesOldObstaclesInAllDirections() throws Exception {
        for (int[] axis : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var world = new TestWorld();
            var c = config(world, axis);
            var g = c.resolveGeometry();
            var outside = new Pos(g.blockX(90, 9), g.floorY(), g.blockZ(90, 9));
            world.blocks.put(outside, Material.DIAMOND_BLOCK);
            for (int seed : new int[]{13, 94}) {
                var plan = builtinPlan(c, seed);
                clear(world, c);
                RiptideCourseGenerator.build(world.world, g, plan, Material.OAK_PLANKS, Material.STONE);
                assertEquals(Material.DIAMOND_BLOCK, world.blocks.get(outside));
                for (int step = g.halfLength() + 1; step < g.totalSteps(); step++)
                    for (int lateral = -g.halfWidth(); lateral <= g.halfWidth(); lateral++)
                        assertEquals(Material.AIR, world.at(g, step, lateral, 0), "obstacle invaded deck layer");
                for (var level : plan.levels()) {
                    int step = level.step();
                    switch (level.type()) {
                        case MATH -> {
                            if (level.sideMath()) {
                                assertEquals(Material.AIR, world.at(g, step, 0, 2));
                                continue;
                            }
                            assertEquals(Material.RED_CONCRETE, world.at(g, step, g.halfWidth() + 1, 2));
                            assertEquals(Material.LIGHT_BLUE_CONCRETE, world.at(g, step, -g.halfWidth() - 1, 2));
                            assertEquals(Material.STONE, world.at(g, step, 0, 2));
                            assertEquals(Material.AIR, world.at(g, step, 1, 2));
                            assertEquals(Material.AIR, world.at(g, step, -1, 2));
                            var answer = level.question(10, 99);
                            if (answer instanceof RiptideMathQuestion arithmetic)
                                assertEquals(RiptideMathQuestion.Operation.valueOf(level.variant()), arithmetic.operation());
                            else assertEquals(level.variant(), ((RiptideObservationQuestion) answer).variant());
                        }
                        case COLOR_FLOOR, RHYTHM -> {
                            for (int lateral = -g.halfWidth(); lateral <= g.halfWidth(); lateral++)
                                for (int y = 1; y <= 3; y++)
                                    assertEquals(Material.AIR, world.at(g, step, lateral, y));
                        }
                        case PASS -> {
                            if(level.isSideSweep()) {
                                for(int lateral=-g.halfWidth();lateral<=g.halfWidth();lateral++)
                                    for(int y=1;y<=4;y++)assertEquals(Material.AIR,world.at(g,step,lateral,y));
                                continue;
                            }
                            if (level.variant().equals("GAP")) {
                                assertEquals(Material.AIR, world.at(g, step, level.opening(), 1));
                                assertEquals(Material.AIR, world.at(g, step, level.opening(), 2));
                                assertEquals(Material.STONE, world.at(g, step, level.opening(), 3));
                            } else if (level.variant().equals("JUMP")) {
                                assertEquals(Material.STONE, world.at(g, step, 0, 1));
                                assertEquals(Material.AIR, world.at(g, step, 0, 2));
                            } else {
                                int direction = level.mirrored() ? -1 : 1;
                                assertEquals(Material.STONE, world.at(g, step - 3, -direction, 2));
                                assertEquals(Material.AIR, world.at(g, step - 3, direction, 2));
                                assertEquals(Material.AIR, world.at(g, step + 3, -direction, 2));
                                assertEquals(Material.STONE, world.at(g, step + 3, direction, 2));
                            }
                        }
                    }
                }
                // A fresh world built from the new plan must exactly match the reused world.
                var fresh = new TestWorld();
                RiptideCourseGenerator.build(fresh.world, g, plan, Material.OAK_PLANKS, Material.STONE);
                var actual = new HashMap<>(world.blocks); actual.remove(outside);
                assertEquals(fresh.blocks, actual);
            }
        }
    }

    @Test void expandedWorkshopMarksAllBoundsAndGenerationRemovesItsPreviewInEveryDirection() throws Exception {
        for (int[] axis : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            var world = new TestWorld(); var c = config(world,axis); var g = c.resolveGeometry();
            var template = c.resolvePool().stream().filter(t -> t.variant().equals("JUMP")).findFirst().orElseThrow();
            var level = new RiptideCoursePlan.Level(1,250,template,"JUMP",1,false,1);
            world.blocks.put(new Pos(g.blockX(257,7),g.floorY()+12,g.blockZ(257,7)),Material.STONE);
            RiptideWorkshop.decorate(c,level,true);
            assertEquals(Material.AIR,world.at(g,257,7,12),"blank must clear the far upper corner");
            assertEquals(Material.ORANGE_CONCRETE,world.at(g,258,0,0));
            assertEquals(Material.LIGHT_BLUE_CONCRETE,world.at(g,250,8,0));
            assertEquals(Material.LIGHT_BLUE_STAINED_GLASS,world.at(g,258,8,12));
            assertEquals(Material.OAK_PLANKS,world.at(g,250,0,0));
            world.blocks.put(new Pos(g.blockX(257,7),g.floorY()+12,g.blockZ(257,7)),Material.STONE);
            clear(world,c);
            assertTrue(world.blocks.isEmpty(),"reroll must remove outer construction and workshop guides");
        }
    }

    @Test void realAndTrialRaftMovementPreservesPatternAndLeavesNoOldDeck() throws Exception {
        var world = new TestWorld(); var g = config(world, new int[]{0, 1}).resolveGeometry();
        for (int z = -g.halfLength(); z <= g.halfLength(); z++)
            for (int x = -g.halfWidth(); x <= g.halfWidth(); x++)
                world.blocks.put(new Pos(g.blockX(z, x), g.floorY(), g.blockZ(z, x)),
                        x % 2 == 0 ? Material.OAK_PLANKS : Material.SPRUCE_PLANKS);
        var original = new HashMap<>(world.blocks);
        RiptideRaftBlocks.move(g, 0, 1, Material.AIR);
        RiptideRaftBlocks.move(g, 1, 200, Material.AIR);
        assertEquals(63, world.blocks.size());
        RiptideRaftBlocks.move(g, 200, 0, Material.AIR);
        assertEquals(original, world.blocks);
    }

    @Test void scheduledGenerationUnlocksBeforeCompletionAndStopsWritingWhenCancelled() throws Exception {
        var world = new TestWorld(); var c = config(world, new int[]{0, 1});
        var plan = builtinPlan(c, 4);
        var serverField = Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        var scheduler = new Scheduler(world);
        serverField.set(null, scheduler.server);
        try {
            Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                    (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            var allowed = new AtomicBoolean(true);
            var future = RiptideCourseGenerator.schedule(plugin, c, plan, Material.OAK_PLANKS, Material.STONE, allowed::get);
            assertTrue(RiptideCourseGenerator.isGenerating(world.world));
            assertTrue(RiptideCourseGenerator.schedule(plugin, c, plan, Material.OAK_PLANKS, Material.STONE, () -> true).isCompletedExceptionally());
            var completion = future.thenRun(() -> assertFalse(RiptideCourseGenerator.isGenerating(world.world)));
            int iterations = 0;
            while (!future.isDone() && iterations++ < 100) {
                world.writes = 0; scheduler.tick(); assertTrue(world.writes < 3500);
            }
            assertTrue(future.join()); completion.join(); assertFalse(RiptideCourseGenerator.isGenerating(world.world));
            var cancelled = RiptideCourseGenerator.schedule(plugin, c, plan, Material.OAK_PLANKS, Material.STONE, allowed::get);
            scheduler.tick();
            RiptideCourseGenerator.cancel(world.world);
            var snapshot = new HashMap<>(world.blocks); scheduler.tick();
            assertFalse(cancelled.join()); assertEquals(snapshot, world.blocks);
            assertFalse(RiptideCourseGenerator.isGenerating(world.world));
            var invalidated = RiptideCourseGenerator.schedule(plugin, c, plan, Material.OAK_PLANKS, Material.STONE, allowed::get);
            allowed.set(false); scheduler.tick();
            assertFalse(invalidated.join()); assertEquals(snapshot, world.blocks);
        } finally { RiptideCourseGenerator.cancelAll(); serverField.set(null, previousServer); }
    }

    private static RiptideCoursePlan builtinPlan(RiptideRushConfig c, long seed) {
        var levels = new ArrayList<RiptideCoursePlan.Level>();
        var random = new Random(seed);
        for (var t : c.resolvePool()) {
            if (t.blueprint() != null) continue;
            int index = levels.size();
            levels.add(new RiptideCoursePlan.Level(index + 1, 20 + index * 15, t, t.variant(),
                    random.nextInt(5) - 2, random.nextBoolean(), random.nextLong()));
        }
        return new RiptideCoursePlan(seed, RiptideCoursePlanner.VERSION, 0, levels, c.resolveGeometry().totalSteps());
    }

    private static void clear(TestWorld world, RiptideRushConfig c) {
        var g = c.resolveGeometry();
        for (int step = -g.halfLength() - 2; step <= g.totalSteps() + g.halfLength() + 2; step++)
            RiptideCourseGenerator.clearSlice(world.world, g, step, g.halfWidth() + c.getObstacleMargin(), c.getClearHeight());
    }
    private static final class Scheduler {
        private int sequence;
        private final Map<Integer, Runnable> pending = new LinkedHashMap<>();
        final BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "runTaskTimer" -> {
                        int id = ++sequence; pending.put(id, (Runnable) args[1]);
                        yield Proxy.newProxyInstance(BukkitTask.class.getClassLoader(), new Class<?>[]{BukkitTask.class},
                                (task, called, values) -> switch (called.getName()) {
                                    case "getTaskId" -> id;
                                    case "cancel" -> { pending.remove(id); yield null; }
                                    case "isCancelled" -> !pending.containsKey(id);
                                    default -> throw new UnsupportedOperationException(called.getName());
                                });
                    }
                    case "cancelTask" -> { pending.remove((Integer) args[0]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final Server server;
        Scheduler(TestWorld world) {
            server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isPrimaryThread" -> true;
                        case "getScheduler" -> scheduler;
                        case "getWorld" -> world.id.equals(args[0]) ? world.world : null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        void tick() { for (var task : List.copyOf(pending.values())) task.run(); }
    }
}
