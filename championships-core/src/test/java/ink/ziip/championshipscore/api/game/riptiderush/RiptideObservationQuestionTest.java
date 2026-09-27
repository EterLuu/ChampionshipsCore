package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RiptideObservationQuestionTest {
    @Test void cluesDetermineTheUniqueAnswerAndSeedReproducesBothVariants() {
        for (String variant : List.of("OBSERVE_COUNT", "OBSERVE_ORDER")) {
            for (int seed = 0; seed < 100; seed++) {
                var question = RiptideObservationQuestion.generate(variant, new Random(seed));
                assertEquals(question, RiptideQuestion.generate(variant, new Random(seed), 10, 99));
                int expected = variant.equals("OBSERVE_COUNT")
                        ? (int) question.clues().stream().filter(value -> value == question.target()).count()
                        : question.clues().get(question.target());
                assertEquals(expected, question.correctAnswer());
                assertNotEquals(expected, question.decoyAnswer());
                assertEquals(expected, question.correctOnLeft() ? question.leftAnswer() : question.rightAnswer());
                assertTrue(question.accepts(question.correctOnLeft()));
                assertFalse(question.accepts(!question.correctOnLeft()));
                assertFalse(question.acceptsLateralOffset(0));
            }
        }
    }

    @Test void observationUsesTheExistingFirstCrossingLock() throws Exception {
        var g = RiptideTestFixtures.config().resolveGeometry();
        var q = RiptideObservationQuestion.generate("OBSERVE_COUNT", new Random(5));
        var run = new RiptideMathRun(g, List.of(new RiptideMathRun.Gate(1, 30, q)), g.centerAt(28));
        var crossing = g.centerAt(31).add(q.correctOnLeft() ? 2 : -2, 0, 0);
        assertEquals(RiptideMathRun.Result.CORRECT, run.sample(crossing).getFirst().result());
        assertTrue(run.sample(g.centerAt(32).add(q.correctOnLeft() ? -2 : 2, 0, 0)).isEmpty());
    }

    @Test void everyStageAndObservationVariantHasOneAnswerWithPlausibleOptions() {
        var directions = new java.util.HashSet<Boolean>();
        for (String variant : RiptideQuestion.variants().stream().filter(v -> v.startsWith("OBSERVE_")).toList())
            for (int stage = 0; stage < 5; stage++) for (int seed = 0; seed < 100; seed++) {
                var q = RiptideObservationQuestion.generate(variant, new Random(seed), stage);
                assertEquals(q, RiptideQuestion.generate(variant, new Random(seed), 10, 99, stage));
                int expected = switch (variant) {
                    case "OBSERVE_COUNT" -> (int) q.clues().stream().filter(n -> n == q.target()).count();
                    case "OBSERVE_ORDER" -> q.clues().get(q.target());
                    case "OBSERVE_EXTREME" -> q.target() == 1 ? java.util.Collections.max(q.clues()) : java.util.Collections.min(q.clues());
                    case "OBSERVE_PARITY" -> (int) q.clues().stream().filter(n -> n % 2 == q.target()).count();
                    case "OBSERVE_UNIQUE" -> q.clues().stream().filter(n -> java.util.Collections.frequency(q.clues(), n) == 1).findFirst().orElseThrow();
                    default -> throw new AssertionError(variant);
                };
                assertEquals(expected, q.correctAnswer());
                assertNotEquals(expected, q.decoyAnswer());
                assertEquals(variant.equals("OBSERVE_UNIQUE") ? 5 + 2 * (stage / 2) : 4 + stage, q.clues().size());
                if (variant.equals("OBSERVE_COUNT") || variant.equals("OBSERVE_PARITY")) {
                    assertTrue(q.decoyAnswer() >= 0 && q.decoyAnswer() <= q.clues().size());
                } else {
                    assertTrue(q.clues().contains(q.decoyAnswer()));
                    assertEquals(stage >= 2, q.clues().getFirst() >= 10);
                }
                if (variant.equals("OBSERVE_ORDER")) {
                    directions.add(q.fromRight());
                    int index = q.fromRight() ? q.clues().size() - q.target() : q.target() + 1;
                    assertEquals("§f从" + (q.fromRight() ? "右" : "左") + "数第" + index + "项？", q.prompt());
                }
            }
        assertEquals(java.util.Set.of(false, true), directions);
    }

}
