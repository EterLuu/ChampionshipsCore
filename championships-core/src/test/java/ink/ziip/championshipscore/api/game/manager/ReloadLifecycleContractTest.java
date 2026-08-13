package ink.ziip.championshipscore.api.game.manager;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadLifecycleContractTest {
    @Test
    void hotReloadWaitsForResetsBeforeUnloadingDisabledManagers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/manager/GameManager.java"));
        int waitForOperations = source.indexOf("CompletableFuture.allOf(operations).whenComplete");
        int unloadDisabledManager = source.indexOf("manager.unload();", waitForOperations);
        assertTrue(waitForOperations >= 0);
        assertTrue(unloadDisabledManager > waitForOperations,
                "Disabled managers must not dispose instances before abort/reset futures complete");
    }

    @Test
    void remoteForceEndWaitsForTerminalAcknowledgement() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/bingo/execution/RemoteBingoManager.java"));
        String match = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/bingo/execution/RemoteBingoMatch.java"));
        assertTrue(manager.matches("(?s).*match\\.terminalFuture\\(\\)\\s*\\.orTimeout.*"));
        assertTrue(match.contains("terminal.complete(null)"));
    }
}
