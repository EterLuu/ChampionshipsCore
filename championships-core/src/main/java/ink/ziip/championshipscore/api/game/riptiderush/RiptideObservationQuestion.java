package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Collections;

/** The complete clue stays visible with the question; no position-dependent memory advantage. */
public record RiptideObservationQuestion(String variant, List<Integer> clues, int target,
                                         int correctAnswer, int decoyAnswer, boolean correctOnLeft, boolean fromRight)
        implements RiptideQuestion {
    private static final List<String> COLORS = List.of("§c红", "§b蓝", "§e黄", "§a绿", "§d紫");

    public RiptideObservationQuestion { clues = List.copyOf(clues); }

    public static RiptideObservationQuestion generate(String variant, Random random) {
        return generate(variant, random, 0);
    }

    public static RiptideObservationQuestion generate(String variant, Random random, int stage) {
        if (stage < 0 || stage > 4) throw new IllegalArgumentException("invalid observation stage");
        var clues = new ArrayList<Integer>();
        int length = 4 + stage;
        boolean fromRight = false;
        int target;
        int correct;
        int decoy;
        if (variant.equals("OBSERVE_COUNT")) {
            int colors = 3 + stage / 2;
            target = random.nextInt(colors);
            for (int i = 0; i < length; i++) clues.add(random.nextInt(colors));
            clues.set(random.nextInt(clues.size()), target);
            correct = (int) clues.stream().filter(value -> value == target).count();
            decoy = countDecoy(correct, length, random);
        } else {
            // Later stages use similar two-digit numbers, with all options drawn from the clue.
            int base = stage < 2 ? 0 : (1 + random.nextInt(9)) * 10;
            var numbers = new ArrayList<Integer>();
            for (int i = 1; i <= 9; i++) numbers.add(base + i);
            Collections.shuffle(numbers, random);
            if (variant.equals("OBSERVE_UNIQUE")) {
                target = numbers.getFirst();
                clues.add(target);
                for (int i = 1; i <= 2 + stage / 2; i++) {
                    clues.add(numbers.get(i)); clues.add(numbers.get(i));
                }
                Collections.shuffle(clues, random);
                correct = target;
            } else {
                clues.addAll(numbers.subList(0, length));
                switch (variant) {
                    case "OBSERVE_ORDER" -> {
                        target = random.nextInt(length);
                        fromRight = stage > 0 && random.nextBoolean();
                        correct = clues.get(target);
                    }
                    case "OBSERVE_EXTREME" -> {
                        target = random.nextBoolean() ? 1 : 0;
                        correct = target == 1 ? Collections.max(clues) : Collections.min(clues);
                    }
                    case "OBSERVE_PARITY" -> {
                        target = random.nextBoolean() ? 1 : 0;
                        int parity = target;
                        correct = (int) clues.stream().filter(n -> n % 2 == parity).count();
                    }
                    default -> throw new IllegalArgumentException("unknown observation variant " + variant);
                }
            }
            if (variant.equals("OBSERVE_PARITY")) decoy = countDecoy(correct, length, random);
            else {
                var alternatives = clues.stream().distinct().filter(n -> n != correct).toList();
                // A neighboring item or a close numerical value makes a plausible mistake.
                decoy = variant.equals("OBSERVE_ORDER")
                        ? clues.get(Math.floorMod(target + (random.nextBoolean() ? 1 : -1), clues.size()))
                        : alternatives.stream().min(java.util.Comparator.comparingInt(n -> Math.abs(n - correct))).orElseThrow();
            }
        }
        return new RiptideObservationQuestion(variant, clues, target, correct, decoy, random.nextBoolean(), fromRight);
    }

    private static int countDecoy(int correct, int length, Random random) {
        return correct == length ? correct - 1 : correct == 0 ? 1 : correct + (random.nextBoolean() ? 1 : -1);
    }

    @Override public String clue() {
        return clues.stream().map(value -> variant.equals("OBSERVE_COUNT") ? COLORS.get(value) : value.toString())
                .collect(java.util.stream.Collectors.joining(" "));
    }
    @Override public String prompt() {
        return switch (variant) {
            case "OBSERVE_COUNT" -> COLORS.get(target) + "§f有几个？";
            case "OBSERVE_ORDER" -> "§f从" + (fromRight ? "右" : "左") + "数第" + (fromRight ? clues.size() - target : target + 1) + "项？";
            case "OBSERVE_EXTREME" -> target == 1 ? "§f最大的数？" : "§f最小的数？";
            case "OBSERVE_PARITY" -> target == 1 ? "§f奇数有几个？" : "§f偶数有几个？";
            case "OBSERVE_UNIQUE" -> "§f只出现一次的数？";
            default -> throw new IllegalStateException(variant);
        };
    }
    @Override public String expression() { return clue() + " " + prompt(); }
    @Override public int leftAnswer() { return correctOnLeft ? correctAnswer : decoyAnswer; }
    @Override public int rightAnswer() { return correctOnLeft ? decoyAnswer : correctAnswer; }
}
