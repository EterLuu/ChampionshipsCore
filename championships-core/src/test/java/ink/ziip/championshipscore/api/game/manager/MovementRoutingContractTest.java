package ink.ziip.championshipscore.api.game.manager;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementRoutingContractTest {
    @Test
    void perAreaListenersDoNotSubscribeToPlayerMoveDirectly() throws Exception {
        Path gameRoot = Path.of("src/main/java/ink/ziip/championshipscore/api/game");
        try (var sources = Files.walk(gameRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith("Handler.java")).toList()) {
                if (source.endsWith("GameManagerHandler.java")) continue;
                String java = Files.readString(source);
                assertFalse(java.matches("(?s).*@EventHandler[^\\n]*\\n\\s*public void \\w+\\(PlayerMoveEvent.*"),
                        "PlayerMoveEvent must be routed by GameManager: " + source);
            }
        }
    }

    @Test
    void centralRouterKeepsAllThreePriorityPhases() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/manager/GameManagerHandler.java"));
        assertTrue(source.contains("routePlayerMoveLow(event)"));
        assertTrue(source.contains("routePlayerMoveNormal(event)"));
        assertTrue(source.contains("routePlayerMoveHigh(event)"));
        assertTrue(source.contains("if (!positionChanged(event)) return"));
    }

    @Test
    void tntRunKeepsProvenAsyncFootProbeWithoutDuplicatingRemovalTasks() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/tntrun/TNTRunTeamArea.java"));
        String handler = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/tntrun/TNTRunHandler.java"));
        assertTrue(source.contains("handlePlayerMoveTask = scheduler.runTaskTimerAsynchronously"));
        assertTrue(source.contains("pendingBlockRemovals.putIfAbsent"));
        assertTrue(source.contains("handlePlayerMoveTask.cancel()"));
        assertFalse(handler.contains("            tntRunTeamArea.handlePlayerMove(player);"));
    }

    @Test
    void skyWarsKeepsHighFanoutParticlesAsyncButAuthoritativeBorderChecksSync() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/skywars/SkyWarsTeamArea.java"));
        int setParticles = source.indexOf("private void setParticles");
        int setHeightParticles = source.indexOf("private void setHeightParticles");
        assertTrue(source.contains("borderCheckTask = scheduler.runTaskTimer(plugin"));
        assertTrue(setParticles > 0 && setHeightParticles > setParticles);
        assertTrue(source.indexOf("scheduler.runTaskAsynchronously", setParticles) < setHeightParticles);
        assertTrue(source.indexOf("scheduler.runTaskAsynchronously", setHeightParticles) > setHeightParticles);
    }
}
