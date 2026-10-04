package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;

import java.util.Random;

/** Shared two-choice contract for arithmetic and observation, including side gates. */
public interface RiptideQuestion {
    String expression();

    default String clue() {
        return expression();
    }

    default String prompt() {
        return "";
    }

    int leftAnswer();

    int rightAnswer();

    boolean correctOnLeft();

    default boolean accepts(boolean choseLeft) {
        return choseLeft == correctOnLeft();
    }

    default boolean acceptsLateralOffset(double lateral) {
        return correctOnLeft() ? lateral > 0 : lateral < 0;
    }

    static RiptideQuestion generate(String variant, Random random, int minimum, int maximum) {
        return generate(variant, random, minimum, maximum, 0);
    }

    static java.util.List<String> variants() {
        return RiptideLevelTemplate.variants(RiptideLevelType.MATH).stream()
                .filter(v -> !v.equals("AUTO") && !v.equals("DOUBLE"))
                .toList();
    }

    static RiptideQuestion generate(
            String variant, Random random, int minimum, int maximum, int stage) {
        return variant.startsWith("OBSERVE_")
                ? RiptideObservationQuestion.generate(variant, random, stage)
                : RiptideMathQuestion.generate(
                        random, minimum, maximum, RiptideMathQuestion.Operation.valueOf(variant));
    }
}
