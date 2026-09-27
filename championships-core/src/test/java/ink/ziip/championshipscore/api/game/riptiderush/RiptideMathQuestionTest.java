package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiptideMathQuestionTest {
    @Test
    void generatedQuestionsMixAllOperationsAndBothMultiplicationOrders() {
        Random random = new Random(42L);
        var operations = java.util.EnumSet.noneOf(RiptideMathQuestion.Operation.class);
        boolean singleFirst = false, doubleFirst = false;
        for (int index = 0; index < 1000; index++) {
            var question = RiptideMathQuestion.generate(random, 1000, 9999);
            operations.add(question.operation());
            int a = question.firstOperand(), b = question.secondOperand();
            int expected = switch (question.operation()) {
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
                assertTrue(question.expression().contains(question.operation() == RiptideMathQuestion.Operation.ADD ? " + " : " − "));
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
        for (int operand : new int[]{0, 1, 9, 10, Integer.MAX_VALUE / 2}) {
            Random random = new Random(operand);
            for (int i = 0; i < 100; i++) {
                var question = RiptideMathQuestion.generate(random, operand, operand);
                assertTrue(question.correctAnswer() >= 0 && question.decoyAnswer() >= 0);
                assertNotEquals(question.correctAnswer(), question.decoyAnswer());
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
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
}
