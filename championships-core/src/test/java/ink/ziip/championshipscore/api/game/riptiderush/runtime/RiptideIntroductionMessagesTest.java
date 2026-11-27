package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Locale;

class RiptideIntroductionMessagesTest {
    @Test
    void everyStageZoneAndGuideTextIsBundled() {
        for (var stage : RiptideIntroductionLesson.Stage.values()) {
            String key = stage.name().toLowerCase(Locale.ROOT);
            assertFalse(RiptideIntroductionRun.text(key).isBlank());
            assertFalse(RiptideIntroductionRun.text("stage." + key + ".name").isBlank());
            assertFalse(RiptideIntroductionRun.text("stage." + key + ".tip").isBlank());
        }
        for (var arch : RiptideIntroductionBlueprint.SIGNS)
            assertFalse(RiptideIntroductionRun.text("zone." + arch.zone()).isBlank());
        String coach = RiptideIntroductionRun.text("coach", "A", 2, 7, "B");
        assertTrue(
                coach.contains("A")
                        && coach.contains("2/7")
                        && coach.contains("\n")
                        && coach.endsWith("B"));
        assertTrue(RiptideIntroductionRun.text("departure", 3).contains("3"));
    }
}
