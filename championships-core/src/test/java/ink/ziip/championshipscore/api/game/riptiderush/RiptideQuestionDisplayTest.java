package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RiptideQuestionDisplayTest {
    @Test void observationClueAndPromptOccupyDifferentTitleLines() {
        String title=MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE,subtitle=MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE;
        try {
            MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE="%question%";
            MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE="left %left% | right %right%";
            for(String variant:RiptideQuestion.variants()) {
                var q=RiptideQuestion.generate(variant,new Random(123),10,99);
                var display=RiptideQuestionDisplay.of(q,1);
                assertEquals(q.clue(),display.title());
                assertTrue(display.subtitle().contains("left "+q.leftAnswer()));
                assertTrue(display.subtitle().contains("right "+q.rightAnswer()));
                if(!q.prompt().isEmpty()) {
                    assertFalse(display.title().contains(q.prompt()));
                    assertTrue(display.subtitle().startsWith(q.prompt()));
                }
                assertFalse(display.title().contains("\n")); assertFalse(display.subtitle().contains("\n"));
            }
        } finally {
            MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE=title;MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE=subtitle;
        }
    }
}
