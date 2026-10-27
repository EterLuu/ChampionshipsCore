package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

class SpectatorHotbarConfigTest {
    @Test
    void collisionsIncludeWorkerCardButNotMutuallyExclusiveCoreParticipantControls() {
        var document = new YamlConfiguration();
        document.set("spectator.hotbar.participant-controls.slot", 8);
        document.set("spectator.hotbar.venue-selector.slot", 8);
        assertTrue(GuiConfig.validate(document).isEmpty());
        document.set("spectator.hotbar.leave.slot", 8);
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("share slot 8")));
        document = new YamlConfiguration();
        document.set("games.bingo.hotbar.spectator.card.slot", 4);
        document.set("games.bingo.hotbar.spectator.speed.slot", 4);
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("share slot 4")));
    }

    @Test
    void invalidSlotsAndNonTextFeedbackFailBeforePublicationWhileEmptyTextIsAllowed() {
        for (Object invalid : List.of(-1, 9, "2", 1.5)) {
            var document = new YamlConfiguration();
            document.set("games.bingo.hotbar.spectator.card.slot", invalid);
            assertFalse(GuiConfig.validate(document).isEmpty(), invalid.toString());
        }
        for (String key :
                List.of(
                        "spectator.feedback.speed",
                        "games.bingo.hotbar.spectator.speed.feedback")) {
            var document = new YamlConfiguration();
            document.set(key, false);
            assertFalse(GuiConfig.validate(document).isEmpty());
            document.set(key, "");
            assertTrue(GuiConfig.validate(document).isEmpty());
        }
    }
}
