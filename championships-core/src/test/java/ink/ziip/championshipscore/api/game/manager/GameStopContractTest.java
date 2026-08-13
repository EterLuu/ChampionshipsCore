package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStopContractTest {
    @Test
    void onlyLifecycleActiveStagesCanBeSelected() {
        assertFalse(GameManager.isStoppableStage(GameStageEnum.WAITING));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.LOADING));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.PREPARATION));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.COUNTDOWN));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.PROGRESS));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.STOPPING));
        assertFalse(GameManager.isStoppableStage(GameStageEnum.END));
    }

    @Test
    void onlyPlayedStagesUseNormalSettlement() {
        assertFalse(GameManager.settlesOnAdministrativeStop(GameStageEnum.LOADING));
        assertFalse(GameManager.settlesOnAdministrativeStop(GameStageEnum.PREPARATION));
        assertFalse(GameManager.settlesOnAdministrativeStop(GameStageEnum.COUNTDOWN));
        assertTrue(GameManager.settlesOnAdministrativeStop(GameStageEnum.PROGRESS));
        assertTrue(GameManager.settlesOnAdministrativeStop(GameStageEnum.STOPPING));
    }

    @Test
    void commandTargetsOneInstanceAndKeepsNormalEndPath() throws IOException {
        String manager = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/manager/GameManager.java"));
        String command = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/command/game/stop/GameStopSubCommand.java"));

        assertTrue(manager.contains("stopMatch(remote.matchId(), reason, settle)"),
                "Remote Bingo stop must address one match UUID");
        assertTrue(manager.contains("target.endGame();"),
                "A played local game must use its normal settlement entry");
        assertTrue(command.contains("getStoppableMapInstances(game, args[1])"));
        assertTrue(command.contains("equalsIgnoreCase(args[2])"));
        assertTrue(command.contains("args[3].equalsIgnoreCase(\"--confirm\")"));

        String remoteManager = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/bingo/execution/RemoteBingoManager.java"));
        assertTrue(remoteManager.contains("match.markNormalStopRequested()"),
                "Repeated remote stops must share one terminal request instead of publishing twice");
    }
}
