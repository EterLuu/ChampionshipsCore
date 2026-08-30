package ink.ziip.championshipscore.worker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerWorldLifecycleContractTest {
    @Test
    void workerFreezesAtLoadAndOnlyStartsSimulationAtRunningTransition() throws IOException {
        String plugin = source("BingoWorkerPlugin.java");
        String session = source("WorkerMatchSession.java");

        assertTrue(plugin.contains("worlds.configureAndFreeze(world)"));
        assertTrue(session.contains("worlds.startMatch()"));
        assertTrue(session.contains("if (!worlds.freeze()) return failPreparation"));
        assertTrue(session.contains("lifecycle.transitionTo(MatchState.RUNNING)"));
        assertTrue(session.indexOf("worlds.startMatch()")
                < session.indexOf("lifecycle.transitionTo(MatchState.RUNNING)"));
        assertTrue(session.contains("worlds.freeze()"));
    }

    @Test
    void waitingPhaseStopsEnvironmentalProgressAndDrops() throws IOException {
        String controller = source("WorkerWorldController.java");
        String rules = Files.readString(Path.of("../championships-platform-bukkit/src/main/java/ink/ziip/"
                + "championshipscore/platform/bukkit/bingo/BingoWorldRules.java"));

        assertTrue(controller.contains("BingoWorldRules.applyPhase(world"));
        assertTrue(rules.contains("GameRules.ADVANCE_TIME, running"));
        assertTrue(rules.contains("GameRules.ADVANCE_WEATHER, running"));
        assertTrue(rules.contains("GameRules.SPAWN_MOBS, running"));
        assertTrue(rules.contains("GameRules.SPAWNER_BLOCKS_WORK, running"));
        assertTrue(rules.contains("GameRules.MOB_GRIEFING, running"));
        assertTrue(rules.contains("GameRules.RANDOM_TICK_SPEED, running ? NORMAL_RANDOM_TICK_SPEED : 0"));
        assertTrue(rules.contains("GameRules.MOB_DROPS, running"));
        assertTrue(rules.contains("GameRules.ENTITY_DROPS, running"));
        assertTrue(rules.contains("GameRules.BLOCK_DROPS, running"));
    }

    @Test
    void spectatorCompassOpensThePlayerMenuWithoutTrackingState() throws IOException {
        String session = source("WorkerMatchSession.java");

        assertTrue(session.contains("spectatorControl(Material.COMPASS, \"teleport\""));
        assertTrue(session.contains("if (rightClick) openSpectatorTargets(player);"));
        assertFalse(session.contains("spectatorTargets"));
        assertFalse(session.contains("openSpectatorTeams"));
    }

    @Test
    void reconnectRestoresTheLiveParticipantInsteadOfScatteringAgain() throws IOException {
        String session = source("WorkerMatchSession.java");

        assertTrue(session.contains("Location quitLocation = player.getLocation().clone()"));
        assertTrue(session.contains("lastQuitLocations.put(playerId, quitLocation)"));
        assertTrue(session.contains("lastQuitLocations.containsKey(player.getUniqueId())"));
        assertTrue(session.contains("(roundPrepared && snapshot.role() == ParticipantRole.PLAYER)"));
        assertTrue(session.contains("restoreQuitLocation(player, lastQuitLocation)"));
        assertTrue(session.contains("player.teleportAsync(location)"));
        assertTrue(session.contains("lastQuitLocations.remove(playerId)"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(Path.of("src/main/java/ink/ziip/championshipscore/worker", name));
    }
}
