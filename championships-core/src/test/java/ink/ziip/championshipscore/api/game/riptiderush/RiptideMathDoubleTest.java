package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiptideMathDoubleTest {
    private static final RiptideLevelTemplate DOUBLE = new RiptideLevelTemplate(
            "math_double", "连续两道数学门", RiptideLevelType.MATH, "DOUBLE", true, 10, 64, 1);

    @Test void previewIncludesBothIndependentQuestionsAndEitherSelectionTrialsTheWholePair() throws Exception {
        var c = RiptideTestFixtures.config();
        var plan = RiptideCoursePlanner.templatePreview(c, DOUBLE, 123);
        assertEquals(plan, RiptideCoursePlanner.templatePreview(c, DOUBLE, 123));
        assertEquals(2, plan.levels().size());
        var first = plan.levels().getFirst(); var second = plan.levels().getLast();
        assertEquals(12, second.step() - first.step());
        assertNotEquals(first.contentSeed(), second.contentSeed());
        assertTrue(RiptideCoursePlanner.safeTransition(c, c.resolveGeometry(), first, second));
        for (var level : plan.levels()) {
            assertEquals(plan.levels(), plan.trialLevels(level));
            assertNotNull(level.question(10, 99));
        }
        var g = c.resolveGeometry();
        var gates = plan.levels().stream().map(l -> new RiptideMathRun.Gate(l.number(), l.step(), l.question(10, 99))).toList();
        var run = new RiptideMathRun(g, gates, g.centerAt(first.step()-1));
        for (var gate : gates) {
            int lateral = gate.question().acceptsLateralOffset(2) ? 2 : -2;
            var before = g.centerAt(gate.step()-1).add(lateral, 0, 0);
            run.sample(before);
            assertEquals(gate, run.preview(before, 3.7, 18, gate.step(), false));
            var answer = run.sample(g.centerAt(gate.step()+1).add(lateral,0,0));
            assertEquals(1, answer.size());
            assertEquals(RiptideMathRun.Result.CORRECT, answer.getFirst().result());
        }
    }

    @Test void fullCoursesCanSelectPairsAndPreserveQuotaAndSafeTransitions() throws Exception {
        var c = RiptideTestFixtures.config();
        var pool = new ArrayList<>(c.resolvePool()); pool.add(DOUBLE); c.setTemplates(pool);
        boolean sawPair = false;
        for (int seed=0; seed<100; seed++) {
            var plan = RiptideCoursePlanner.plan(c, seed);
            assertEquals(8, plan.levels().stream().filter(l -> l.type()==RiptideLevelType.MATH).map(RiptideCoursePlan.Level::number).distinct().count());
            for (int i=1;i<plan.levels().size();i++)
                assertTrue(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),plan.levels().get(i-1),plan.levels().get(i)), "seed="+seed+" previous="+plan.levels().get(i-1)+" next="+plan.levels().get(i));
            sawPair |= plan.levels().stream().anyMatch(l -> l.rhythm().equals("MATH_DOUBLE"));
        }
        assertTrue(sawPair);
    }

    @Test void migrationRemovesConcreteAddsNewThemesOnceAndPreservesCustomEntriesAndPublication() {
        var yaml = new YamlConfiguration();
        var custom = new RiptideLevelTemplate("custom_wood", "我的木板", RiptideLevelType.COLOR_FLOOR, "WOOD", false, 7, 2, 2).serialize();
        yaml.set("course.pool",List.of(Map.of("id","old_concrete","type","COLOR_FLOOR","variant","CONCRETE"),custom));
        yaml.set("prepare.revision",9);yaml.set("prepare.published",true);yaml.set("prepare.dirty",false);
        RiptideRushConfig.migrateNewVariants(yaml);
        var once = yaml.saveToString();
        RiptideRushConfig.migrateNewVariants(yaml);
        assertEquals(once,yaml.saveToString());
        assertEquals(5,yaml.getMapList("course.pool").size());
        assertTrue(yaml.getMapList("course.pool").contains(custom));
        assertFalse(yaml.getMapList("course.pool").stream().anyMatch(row -> "CONCRETE".equals(row.get("variant"))));
        assertEquals(9,yaml.getInt("prepare.revision"));assertTrue(yaml.getBoolean("prepare.published"));assertFalse(yaml.getBoolean("prepare.dirty"));
    }
}
