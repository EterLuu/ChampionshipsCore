package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ConfigurationStateExtension.class)
class RiptideFinalStageTest {
    @Test void everyFinalPassIsTripleAndEveryFinalMathIsThreeSideQuestions() throws Exception {
        var c=RiptideTestFixtures.config();
        for(int seed=0;seed<100;seed++) {
            var plan=RiptideCoursePlanner.plan(c,seed);
            assertEquals(32,plan.levels().stream().map(RiptideCoursePlan.Level::number).distinct().count());
            for(var l:plan.levels()) if(l.step()>=400) {
                if(l.type()==RiptideLevelType.MATH || l.isSideSweep()) {
                    assertTrue(l.sideMath());assertEquals(3,l.sideWalls().size());
                    for(int beat=1;beat<=3;beat++) assertNotNull(RiptideSideMath.question(l,beat,10,99));
                } else if(l.type()==RiptideLevelType.PASS) {
                    assertEquals("FINAL_TRIPLE",l.rhythm());assertEquals(3,plan.trialLevels(l).size());
                    var group=plan.trialLevels(l);
                    for(int i=1;i<group.size();i++) assertNotEquals(group.get(i-1).template().designKey(group.get(i-1).variant()),
                            group.get(i).template().designKey(group.get(i).variant()));
                }
            }
            assertTrue(plan.estimatedTicks()<6000);
        }
    }

    @Test void exactBlockColumnsMatchPaintForAllAxesIncludingJumpAndCentre() throws Exception {
        var base=RiptideTestFixtures.config().resolveGeometry();
        for(int[] axis:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            var start=base.centerAt(0);
            var g=RiptideCourseGeometry.resolve(start,start.clone().add(500*axis[0],0,500*axis[1]),7,9);
            var floor=RiptideSideMath.floor(g);
            assertEquals(Set.of(Material.RED_CONCRETE, Material.LIGHT_BLUE_CONCRETE), new HashSet<>(floor));
            for(int f=-4;f<=4;f++) for(int x=-3;x<=3;x++) for(double y:new double[]{80,81.2,82}) {
                var feet=new Location(start.getWorld(),g.blockX(400+f,x)+.01,y,g.blockZ(400+f,x)+.99);
                boolean red=floor.get((f+4)*7+x+3)==Material.RED_CONCRETE;
                assertEquals(red,RiptideSideMath.matches(feet,g,400,new RiptideMathQuestion(1,2,3,4,true)));
                assertEquals(!red,RiptideSideMath.matches(feet,g,400,new RiptideMathQuestion(1,2,3,4,false)));
            }
            assertFalse(RiptideSideMath.matches(g.centerAt(400).add(0,-.01,0),g,400,new RiptideMathQuestion(1,2,3,4,true)));
            assertFalse(RiptideSideMath.matches(g.centerAt(410),g,400,new RiptideMathQuestion(1,2,3,4,true)));
        }
    }

    @Test void eachWallProducesExactlyOneBatchAtItsFinalTick() throws Exception {
        var c=RiptideTestFixtures.config();var g=c.resolveGeometry();
        ink.ziip.championshipscore.configuration.config.message.MessageConfig.RIPTIDE_RUSH_SWEEP_TITLE="侧向来墙！";
        var template=RiptideLevelTemplate.create("wall",RiptideLevelType.PASS);
        var walls=List.of(new RiptideCoursePlan.SideWall(template,"GAP",0,1,4),
                new RiptideCoursePlan.SideWall(template,"GAP",0,-1,4),new RiptideCoursePlan.SideWall(template,"GAP",0,1,4));
        var level=new RiptideCoursePlan.Level(1,400,template,"GAP",0,false,123,0,0,"SIDE_MATH",1,walls);
        var run=new RiptidePassRun(g,List.of(level),c);var id=UUID.randomUUID();
        var player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "hashCode" -> 1; case "equals" -> p==a[0]; case "getUniqueId" -> id; case "getLocation" -> g.centerAt(400); default -> null;
        });
        int duration=RiptideSideSweep.beatTicks(3,5.2,walls.getFirst());
        for(int beat=1;beat<=3;beat++) for(int delta:new int[]{-2,-1}) {
            int tick=beat*duration+delta;
            for(var e:Map.<String,Object>of("active",level,"speed",5.2,"tick",tick,
                    "rendered",RiptideSideSweep.frame(tick,walls,3,5.2)).entrySet()) {
                var field=RiptidePassRun.class.getDeclaredField(e.getKey());field.setAccessible(true);field.set(run,e.getValue());
            }
            var answers=run.tick(List.of(player));
            assertEquals(delta==-1?1:0,answers.size());
            if(delta==-1) {
                assertEquals(beat,answers.getFirst().beat());
                assertEquals(RiptideSideMath.question(level,beat,10,99),answers.getFirst().question());
            }
        }
        assertFalse(run.active());assertTrue(run.tick(List.of(player)).isEmpty());run.close();
    }

    @Test void runtimePoolPlansWithoutChangingSavedPublication() throws Exception {
        var yaml=new YamlConfiguration();
        String runtime=System.getProperty("riptide.runtime.config");
        if(runtime!=null) yaml.load(new java.io.File(runtime));
        else try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/riptiderush/area.yml"),java.nio.charset.StandardCharsets.UTF_8)) { yaml.load(reader); }
        var c=RiptideTestFixtures.config();
        c.setTemplates(yaml.getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList());
        c.setPassCount(yaml.getInt("course.counts.pass"));
        c.setMathCount(yaml.getInt("course.counts.math"));
        c.setStoppedCount(yaml.getInt("course.counts.stopped"));
        c.setStoppedAllocationMode(yaml.getString("course.stopped-allocation.mode", "WEIGHTED"));
        c.setColorFloorWeight(yaml.getInt("course.stopped-allocation.weights.color-floor", 3));
        c.setDodgeWeight(yaml.getInt("course.stopped-allocation.weights.dodge", 3));
        c.setSideSweepWeight(yaml.getInt("course.stopped-allocation.weights.side-sweep", 2));
        c.setRhythmCount(yaml.getInt("course.counts.rhythm"));
        c.setMinimumOperand(yaml.getInt("math.minimum-operand"));c.setMaximumOperand(yaml.getInt("math.maximum-operand"));
        String before=yaml.getConfigurationSection("prepare").getValues(true).toString();
        String poolBefore=c.getPool().toString();
        var groups = new HashSet<String>();
        for(int seed=0;seed<30;seed++) {
            var plan=RiptideCoursePlanner.plan(c,seed);
            assertTrue(plan.estimatedTicks() < c.getTimer() * 20, "seed=" + seed);
            assertEquals(30, plan.levels().stream().map(RiptideCoursePlan.Level::number).distinct().count());
            for (var type : RiptideLevelType.values())
                assertEquals(RiptideCoursePlanner.quota(c, type), plan.levels().stream()
                        .filter(l -> l.type() == type).map(RiptideCoursePlan.Level::number).distinct().count());
            assertEquals(3, plan.levels().stream().filter(RiptideCoursePlan.Level::colorFloor).count());
            assertEquals(2, plan.levels().stream().filter(l -> l.type() == RiptideLevelType.COLOR_FLOOR && l.isSideSweep()).count());
            var designs = new HashSet<String>();
            var ids = new HashSet<String>();
            for (var level : plan.levels()) {
                if (!level.isSideSweep() && level.type() == RiptideLevelType.PASS) {
                    assertTrue(ids.add(level.template().id()), "Repeated wall: " + level.template().id());
                    assertTrue(designs.add(level.template().designKey(level.variant())), "Repeated building");
                }
                for (var wall : level.sideWalls()) {
                    assertTrue(ids.add(wall.template().id()), "Repeated side wall: " + wall.template().id());
                    assertTrue(designs.add(wall.template().designKey(wall.variant())), "Repeated side building");
                }
                for (int j = 1; j < level.sideWalls().size(); j++) {
                    var previous = level.sideWalls().get(j - 1);
                    var next = level.sideWalls().get(j);
                    for (var p : RiptideWallGroups.sidePassages(previous))
                        for (var q : RiptideWallGroups.sidePassages(next))
                            assertTrue(Math.abs(p.lateral() - q.lateral()) >= .75, "Repeated side passage");
                }
            }
            for(int i=1;i<plan.levels().size();i++)assertTrue(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),plan.levels().get(i-1),plan.levels().get(i)));
            for(var l:plan.levels()) if(l.rhythm().equals("FINAL_TRIPLE"))
                assertEquals(3,plan.trialLevels(l).stream().map(w -> w.template().designKey(w.variant())).distinct().count());
            assertTrue(plan.levels().getLast().step()+plan.levels().getLast().extent()<496);
            for (var l : plan.levels()) if (l.wallGroup() != 0 && !l.isSideSweep()) {
                var group = plan.trialLevels(l);
                int first = group.getFirst().step();
                int count = group.size();
                assertTrue(first >= (count == 3 ? 200 : 100));
                if (first < (count == 3 ? 300 : 200)) groups.add(l.type() + "_" + count);
            }
        }
        assertEquals(before,yaml.getConfigurationSection("prepare").getValues(true).toString());
        assertEquals(poolBefore,c.getPool().toString());
        assertTrue(groups.containsAll(Set.of("PASS_2", "MATH_2", "MATH_3")), groups.toString());
        if (c.getRhythmCount() > 0) assertTrue(groups.containsAll(Set.of("RHYTHM_2", "RHYTHM_3")), groups.toString());
    }

    @Test void rulesMigrationIsIdempotentAndPreservesCustomRules() {
        var yaml=new YamlConfiguration();yaml.set("rules",List.of(List.of("我的规则")));
        RiptideRushConfig.migrateFinalStageRules(yaml);String once=yaml.saveToString();
        RiptideRushConfig.migrateFinalStageRules(yaml);assertEquals(once,yaml.saveToString());
        assertEquals(List.of("我的规则"),yaml.getList("rules").getFirst());
    }
}
