package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.acerace.AceRaceManager;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;

import org.junit.jupiter.api.Test;

import java.util.List;

class GameManagerPolicyTest {
    @Test
    void lifecycleStagesControlStoppingSettlementAndSpectatorAccess() {
        record Policy(
                GameStageEnum stage,
                boolean stoppable,
                boolean settles,
                boolean spectator,
                boolean administrator) {}
        var policies =
                List.of(
                        new Policy(GameStageEnum.WAITING, false, false, false, true),
                        new Policy(GameStageEnum.LOADING, true, false, false, true),
                        new Policy(GameStageEnum.PREPARATION, true, false, true, true),
                        new Policy(GameStageEnum.COUNTDOWN, true, false, true, true),
                        new Policy(GameStageEnum.PROGRESS, true, true, true, true),
                        new Policy(GameStageEnum.STOPPING, true, true, false, false),
                        new Policy(GameStageEnum.END, false, false, false, false));
        assertEquals(
                List.of(GameStageEnum.values()), policies.stream().map(Policy::stage).toList());
        for (var policy : policies) {
            String stage = policy.stage().name();
            assertEquals(
                    policy.stoppable(),
                    GameManager.isStoppableStage(policy.stage()),
                    stage + " stop");
            assertEquals(
                    policy.settles(),
                    GameManager.settlesOnAdministrativeStop(policy.stage()),
                    stage + " settle");
            assertEquals(
                    policy.spectator(),
                    GameManager.isSpectatingStageAllowed(policy.stage(), false),
                    stage + " spectator");
            assertEquals(
                    policy.administrator(),
                    GameManager.isSpectatingStageAllowed(policy.stage(), true),
                    stage + " admin");
        }
    }

    @Test
    void strictRosterRestrictionOnlyAppliesOutsideDailyMode() {
        assertFalse(GameManager.shouldEnforceStrictSpectatorRule(false, false));
        assertFalse(GameManager.shouldEnforceStrictSpectatorRule(false, true));
        assertTrue(GameManager.shouldEnforceStrictSpectatorRule(true, false));
        assertFalse(GameManager.shouldEnforceStrictSpectatorRule(true, true));
    }

    @Test
    void regionBasedGamesAllowSeveralMapsInOnePhysicalWorld() {
        assertTrue(new AceRaceManager(null).supportsSharedMapWorlds());
        assertTrue(new TGTTOSManager(null).supportsSharedMapWorlds());
        assertTrue(new BattleBoxManager(null).supportsSharedMapWorlds());
        assertTrue(new ParkourTagManager(null).supportsSharedMapWorlds());
        assertTrue(new TNTRunManager(null).supportsSharedMapWorlds());
        assertTrue(new BuildMartManager(null).supportsSharedMapWorlds());
        assertEquals("buildmart_area", BuildMartManager.worldNameFor("area"));
    }
}
