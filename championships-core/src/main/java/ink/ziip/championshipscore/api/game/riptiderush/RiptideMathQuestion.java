package ink.ziip.championshipscore.api.game.riptiderush;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Random;

/** Mixed arithmetic with nearby, same-width decoys that imitate carry/borrow or digit-reading errors. */
public record RiptideMathQuestion(int firstOperand, int secondOperand, int correctAnswer,
                                 int decoyAnswer, boolean correctOnLeft, Operation operation) implements RiptideQuestion {
    public enum Operation {
        ADD("+"), SUBTRACT("−"), MULTIPLY("×");
        private final String symbol;
        Operation(String symbol) { this.symbol = symbol; }
    }

    /** Existing explicit addition fixtures remain valid. Generated questions always specify the operation. */
    public RiptideMathQuestion(int first, int second, int correct, int decoy, boolean left) {
        this(first, second, correct, decoy, left, Operation.ADD);
    }

    public static @NotNull RiptideMathQuestion generate(@NotNull Random random, int minimum, int maximum) {
        return generate(random, minimum, maximum, Operation.values()[random.nextInt(Operation.values().length)]);
    }

    public static @NotNull RiptideMathQuestion generate(@NotNull Random random, int minimum, int maximum,
                                                       @NotNull Operation operation) {
        if (minimum < 0 || maximum < minimum || maximum > Integer.MAX_VALUE / 2)
            throw new IllegalArgumentException("invalid operand range");
        int first;
        int second;
        int correct;
        if (operation == Operation.MULTIPLY) {
            first = nextInclusive(random, 10, 99);
            second = nextInclusive(random, 2, 9); // Avoid trivial multiplication by zero or one.
            if (random.nextBoolean()) { int swap = first; first = second; second = swap; }
            correct = first * second;
        } else {
            first = nextInclusive(random, minimum, maximum);
            second = nextInclusive(random, minimum, maximum);
            if (operation == Operation.SUBTRACT && first < second) {
                int swap = first; first = second; second = swap;
            }
            correct = operation == Operation.ADD ? first + second : first - second;
        }
        return new RiptideMathQuestion(first, second, correct, decoy(random, correct),
                random.nextBoolean(), operation);
    }

    private static int decoy(Random random, int correct) {
        var candidates = new ArrayList<Integer>();
        // Prefer a missed carry/borrow. Keep the units digit, leading digit and answer width
        // whenever possible, so checking only the ends or rough magnitude does not solve the gate.
        for (int delta : new int[]{-100, -20, -10, 10, 20, 100}) {
            long candidate = (long) correct + delta;
            if (candidate >= 0 && candidate <= Integer.MAX_VALUE)
                addCandidate(candidates, correct, (int) candidate);
        }
        String digits = Integer.toString(correct);
        for (int i = 1; i + 1 < digits.length() - 1; i++) {
            char[] swapped = digits.toCharArray();
            char old = swapped[i]; swapped[i] = swapped[i + 1]; swapped[i + 1] = old;
            long candidate = Long.parseLong(new String(swapped));
            if (candidate <= Integer.MAX_VALUE) addCandidate(candidates, correct, (int) candidate);
        }
        var sameLeading = candidates.stream().filter(value -> Integer.toString(value).charAt(0) == digits.charAt(0)).toList();
        if (!sameLeading.isEmpty()) return sameLeading.get(random.nextInt(sameLeading.size()));
        if (!candidates.isEmpty()) return candidates.get(random.nextInt(candidates.size()));
        // Very small custom operands/results cannot have a distinct same-width, same-units decoy.
        return correct < 9 ? correct + 1 : correct - 1;
    }

    private static void addCandidate(ArrayList<Integer> candidates, int correct, int candidate) {
        if (candidate != correct && Integer.toString(candidate).length() == Integer.toString(correct).length()
                && Math.abs((long) candidate - correct) <= Math.max(10, correct / 20)
                && !candidates.contains(candidate)) candidates.add(candidate);
    }

    private static int nextInclusive(Random random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    public int leftAnswer() { return correctOnLeft ? correctAnswer : decoyAnswer; }
    public int rightAnswer() { return correctOnLeft ? decoyAnswer : correctAnswer; }
    public boolean accepts(boolean choseLeft) { return choseLeft == correctOnLeft; }

    /** A centre-line position is not a valid choice for either lane. */
    public boolean acceptsLateralOffset(double lateralOffset) {
        return correctOnLeft ? lateralOffset > 0D : lateralOffset < 0D;
    }

    public @NotNull String expression() {
        return firstOperand + " " + operation.symbol + " " + secondOperand + " = ?";
    }
}
