package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastTutorial;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

class FeastCoachMessagesTest {
    @Test
    void everyGoalAndLiveInstructionHasABundledMessage() throws Exception {
        try (var stream = getClass().getResourceAsStream("/message.yml")) {
            assertNotNull(stream);
            var messages =
                    YamlConfiguration.loadConfiguration(
                                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getConfigurationSection("code-text.crafting-feast.tutorial");
            assertNotNull(messages);
            for (var task : FeastTutorial.Task.values())
                assertTrue(
                        messages.isString("task." + task.name().toLowerCase(Locale.ROOT)),
                        task.name());
            for (String key :
                    new String[] {
                        "welcome-title",
                        "welcome-subtitle",
                        "board-header",
                        "board-task",
                        "board-action",
                        "board-note",
                        "hint",
                        "next-guest",
                        "order-request",
                        "order-note",
                        "order-seating",
                        "seat-note",
                        "serve-now",
                        "serve-note",
                        "vip-note",
                        "failure-dish",
                        "iron-dish",
                        "failure-wait",
                        "failure-watch",
                        "failure-note",
                        "failure-result",
                        "delivery-wait",
                        "delivery-ready",
                        "delivery-needed",
                        "delivery-wrong",
                        "delivery-note",
                        "delivery-correct",
                        "delivery-place",
                        "delivery-finish",
                        "delivery-on-way",
                        "delivery-pickup",
                        "finished-title",
                        "practice",
                        "practice-note",
                        "run-failed"
                    })
                assertTrue(messages.isString(key) && !messages.getString(key).isBlank(), key);
        }
        assertTrue(FeastCoach.text("serve-now", "DISH", 2).contains("#2"));
        assertTrue(FeastCoach.text("delivery-correct", 4, "IRON").contains("W4"));
        assertTrue(FeastCoach.text("delivery-place", 3, "POTATO").contains("POTATO"));
    }
}
