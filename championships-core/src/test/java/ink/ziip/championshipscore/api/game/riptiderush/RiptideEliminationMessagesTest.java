package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ConfigurationStateExtension.class)
class RiptideEliminationMessagesTest {
    @BeforeEach
    void resetMessages() {
        MessageConfig.RIPTIDE_RUSH_REASON_FELL = null;
        MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND = null;
        MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR = null;
        MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER = null;
        MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE = null;
        MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED = null;
        MessageConfig.RIPTIDE_RUSH_ELIMINATED = null;
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL = null;
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_FLOOR = null;
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_MATH = null;
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED = null;
    }

    @Test
    void eachEliminationCauseChoosesOnlyFromItsConfiguredVariants() {
        MessageConfig.RIPTIDE_RUSH_REASON_FELL = "fell";
        MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND = "left";
        MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR = "floor";
        MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER = "answer";
        MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE = "gate";
        MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED = "disconnected";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED = "fallback";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL = "fall-a\n\nfall-b";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_FLOOR = "floor-a\nfloor-b";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_MATH = "math-a\nmath-b";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED = "disconnect-a\ndisconnect-b";

        assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_FELL, Set.of("fall-a", "fall-b"));
        assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND, Set.of("fall-a", "fall-b"));
        assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR, Set.of("floor-a", "floor-b"));
        assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER, Set.of("math-a", "math-b"));
        assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE, Set.of("math-a", "math-b"));
        assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED, Set.of("disconnect-a", "disconnect-b"));
    }

    @Test
    void missingOrBlankCategoriesFallBackToTheGenericAnnouncement() {
        MessageConfig.RIPTIDE_RUSH_REASON_FELL = "fell";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED = "fallback";
        MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL = " \n ";

        assertEquals("fallback", RiptideEliminationMessages.choose(
                MessageConfig.RIPTIDE_RUSH_REASON_FELL, new Random(1)));
    }

    private static void assertVariants(String reason, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        Random random = new Random(7);
        for (int attempt = 0; attempt < 100 && !actual.equals(expected); attempt++)
            actual.add(RiptideEliminationMessages.choose(reason, random));
        assertEquals(expected, actual);
    }
}
