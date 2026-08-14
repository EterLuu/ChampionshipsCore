package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.api.game.acerace.AceRaceManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.buildmart.BuildMartPrepareFlow;
import ink.ziip.championshipscore.api.game.area.prepare.tntrun.TNTRunPrepareFlow;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedWorldManagerContractTest {
    @Test
    void regionBasedGamesAllowSeveralMapsInOnePhysicalWorld() {
        assertTrue(new AceRaceManager(null).supportsSharedMapWorlds());
        assertTrue(new TGTTOSManager(null).supportsSharedMapWorlds());
        assertTrue(new BattleBoxManager(null).supportsSharedMapWorlds());
        assertTrue(new ParkourTagManager(null).supportsSharedMapWorlds());
        assertTrue(new TNTRunManager(null).supportsSharedMapWorlds());
        assertTrue(new BuildMartManager(null).supportsSharedMapWorlds());
    }

    @Test
    void destructibleSharedWorldGamesPublishWithoutWholeWorldReload() throws Exception {
        assertTrue(PrepareFlowDefinition.class.equals(TNTRunPrepareFlow.class
                .getMethod("publish", PrepareSession.class).getDeclaringClass()));
        assertTrue(PrepareFlowDefinition.class.equals(BuildMartPrepareFlow.class
                .getMethod("publish", PrepareSession.class).getDeclaringClass()));
    }
}
