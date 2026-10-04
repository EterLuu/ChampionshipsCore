package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

class RiptideQuestionTest {
    @Test
    void generatedQuestionsMixAllOperationsAndBothMultiplicationOrders() {
        Random random = new Random(42L);
        var operations = java.util.EnumSet.noneOf(RiptideMathQuestion.Operation.class);
        boolean singleFirst = false, doubleFirst = false;
        for (int index = 0; index < 1000; index++) {
            var question = RiptideMathQuestion.generate(random, 1000, 9999);
            operations.add(question.operation());
            int a = question.firstOperand(), b = question.secondOperand();
            int expected =
                    switch (question.operation()) {
                        case ADD -> a + b;
                        case SUBTRACT -> a - b;
                        case MULTIPLY -> a * b;
                    };
            assertEquals(expected, question.correctAnswer());
            assertTrue(expected >= 0);
            if (question.operation() == RiptideMathQuestion.Operation.MULTIPLY) {
                int single = Math.min(a, b), twoDigits = Math.max(a, b);
                assertTrue(single >= 2 && single <= 9);
                assertTrue(twoDigits >= 10 && twoDigits <= 99);
                singleFirst |= a < 10;
                doubleFirst |= a >= 10;
                assertTrue(question.expression().contains(" × "));
            } else {
                assertTrue(a >= 1000 && a <= 9999 && b >= 1000 && b <= 9999);
                assertTrue(
                        question.expression()
                                .contains(
                                        question.operation() == RiptideMathQuestion.Operation.ADD
                                                ? " + "
                                                : " − "));
            }
            assertNotEquals(question.leftAnswer(), question.rightAnswer());
            assertTrue(question.accepts(question.correctOnLeft()));
            assertFalse(question.accepts(!question.correctOnLeft()));
        }
        assertEquals(3, operations.size());
        assertTrue(singleFirst && doubleFirst);
    }

    @Test
    void decoysPreserveWidthAndUnitsWithoutObviousMagnitudeDifferences() {
        Random random = new Random(18);
        for (int i = 0; i < 2000; i++) {
            var question = RiptideMathQuestion.generate(random, 1000, 9999);
            int correct = question.correctAnswer(), decoy = question.decoyAnswer();
            assertNotEquals(correct, decoy);
            assertTrue(decoy >= 0);
            assertEquals(String.valueOf(correct).length(), String.valueOf(decoy).length());
            assertTrue(Math.abs((long) correct - decoy) <= Math.max(10, correct / 20));
            if (correct >= 10) assertEquals(correct % 10, decoy % 10);
        }
    }

    @Test
    void customTinyAndLargestSupportedOperandsNeverOverflowOrCreateDuplicateAnswers() {
        for (int operand : new int[] {0, 1, 9, 10, Integer.MAX_VALUE / 2}) {
            Random random = new Random(operand);
            for (int i = 0; i < 100; i++) {
                var question = RiptideMathQuestion.generate(random, operand, operand);
                assertTrue(question.correctAnswer() >= 0 && question.decoyAnswer() >= 0);
                assertNotEquals(question.correctAnswer(), question.decoyAnswer());
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> RiptideMathQuestion.generate(new Random(), 0, Integer.MAX_VALUE));
    }

    @Test
    void centreLineIsNeverAcceptedAsEitherAnswer() {
        RiptideMathQuestion left = new RiptideMathQuestion(20, 22, 42, 52, true);
        RiptideMathQuestion right = new RiptideMathQuestion(20, 22, 42, 52, false);

        assertTrue(left.acceptsLateralOffset(0.01D));
        assertFalse(left.acceptsLateralOffset(-0.01D));
        assertFalse(left.acceptsLateralOffset(0D));
        assertTrue(right.acceptsLateralOffset(-0.01D));
        assertFalse(right.acceptsLateralOffset(0.01D));
        assertFalse(right.acceptsLateralOffset(0D));
    }

    @Test
    void observationUsesTheExistingFirstCrossingLock() throws Exception {
        var g = RiptideTestFixtures.config().resolveGeometry();
        var q = RiptideObservationQuestion.generate("OBSERVE_COUNT", new Random(5));
        var run = new RiptideMathRun(g, List.of(new RiptideMathRun.Gate(1, 30, q)), g.centerAt(28));
        var crossing = g.centerAt(31).add(q.correctOnLeft() ? 2 : -2, 0, 0);
        assertEquals(RiptideMathRun.Result.CORRECT, run.sample(crossing).getFirst().result());
        assertTrue(run.sample(g.centerAt(32).add(q.correctOnLeft() ? -2 : 2, 0, 0)).isEmpty());
    }

    @Test
    void everyStageAndObservationVariantHasOneAnswerWithPlausibleOptions() {
        var directions = new java.util.HashSet<Boolean>();
        for (String variant :
                RiptideQuestion.variants().stream().filter(v -> v.startsWith("OBSERVE_")).toList())
            for (int stage = 0; stage < 5; stage++)
                for (int seed = 0; seed < 100; seed++) {
                    var q = RiptideObservationQuestion.generate(variant, new Random(seed), stage);
                    assertEquals(
                            q, RiptideQuestion.generate(variant, new Random(seed), 10, 99, stage));
                    int expected =
                            switch (variant) {
                                case "OBSERVE_COUNT" ->
                                        (int)
                                                q.clues().stream()
                                                        .filter(n -> n == q.target())
                                                        .count();
                                case "OBSERVE_ORDER" -> q.clues().get(q.target());
                                case "OBSERVE_EXTREME" ->
                                        q.target() == 1
                                                ? java.util.Collections.max(q.clues())
                                                : java.util.Collections.min(q.clues());
                                case "OBSERVE_PARITY" ->
                                        (int)
                                                q.clues().stream()
                                                        .filter(n -> n % 2 == q.target())
                                                        .count();
                                case "OBSERVE_UNIQUE" ->
                                        q.clues().stream()
                                                .filter(
                                                        n ->
                                                                java.util.Collections.frequency(
                                                                                q.clues(), n)
                                                                        == 1)
                                                .findFirst()
                                                .orElseThrow();
                                default -> throw new AssertionError(variant);
                            };
                    assertEquals(expected, q.correctAnswer());
                    assertNotEquals(expected, q.decoyAnswer());
                    assertEquals(expected, q.correctOnLeft() ? q.leftAnswer() : q.rightAnswer());
                    assertTrue(q.accepts(q.correctOnLeft()));
                    assertFalse(q.accepts(!q.correctOnLeft()));
                    assertFalse(q.acceptsLateralOffset(0));
                    assertEquals(
                            variant.equals("OBSERVE_UNIQUE") ? 5 + 2 * (stage / 2) : 4 + stage,
                            q.clues().size());
                    if (variant.equals("OBSERVE_COUNT") || variant.equals("OBSERVE_PARITY")) {
                        assertTrue(q.decoyAnswer() >= 0 && q.decoyAnswer() <= q.clues().size());
                    } else {
                        assertTrue(q.clues().contains(q.decoyAnswer()));
                        assertEquals(stage >= 2, q.clues().getFirst() >= 10);
                    }
                    if (variant.equals("OBSERVE_ORDER")) {
                        directions.add(q.fromRight());
                        int index = q.fromRight() ? q.clues().size() - q.target() : q.target() + 1;
                        assertEquals(
                                "§f从" + (q.fromRight() ? "右" : "左") + "数第" + index + "项？",
                                q.prompt());
                    }
                }
        assertEquals(java.util.Set.of(false, true), directions);
    }

    @Test
    void observationClueAndPromptOccupyDifferentTitleLines() {
        String title = MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE,
                subtitle = MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE;
        try {
            MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE = "%question%";
            MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE = "left %left% | right %right%";
            for (String variant : RiptideQuestion.variants()) {
                var q = RiptideQuestion.generate(variant, new Random(123), 10, 99);
                var display = RiptideQuestionDisplay.of(q, 1);
                assertEquals(q.clue(), display.title());
                assertTrue(display.subtitle().contains("left " + q.leftAnswer()));
                assertTrue(display.subtitle().contains("right " + q.rightAnswer()));
                if (!q.prompt().isEmpty()) {
                    assertFalse(display.title().contains(q.prompt()));
                    assertTrue(display.subtitle().startsWith(q.prompt()));
                }
                assertFalse(display.title().contains("\n"));
                assertFalse(display.subtitle().contains("\n"));
            }
        } finally {
            MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE = title;
            MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE = subtitle;
        }
    }
}
