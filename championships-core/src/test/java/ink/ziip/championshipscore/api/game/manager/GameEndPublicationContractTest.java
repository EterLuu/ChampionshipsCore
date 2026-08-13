package ink.ziip.championshipscore.api.game.manager;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEndPublicationContractTest {
    @Test
    void gameImplementationsPublishEndEventsThroughSettlementGuard() throws Exception {
        Path gameRoot = Path.of("src/main/java/ink/ziip/championshipscore/api/game");
        try (var sources = Files.walk(gameRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String java = Files.readString(source);
                assertFalse(java.contains("callEvent(new SingleGameEndEvent")
                                || java.contains("callEvent(new TeamGameEndEvent"),
                        "Game end events must use BaseGameInstance.publishGameEndEvent: " + source);
            }
        }
    }

    @Test
    void baseInstanceKeepsAbortSettlementSuppression() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/instance/BaseGameInstance.java"));
        assertTrue(source.contains("settlementSuppressed = true"));
        assertTrue(source.contains("if (settlementSuppressed)"));
        assertTrue(source.contains("if (!settlementSuppressed) Bukkit.getPluginManager().callEvent(event)"));
    }
}
