package ink.ziip.championshipscore.api.game.area.prepare.tntrun;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

class TNTRunPrepareFlowTest {
    @Test
    void editorRequiresAnExplicitHeightWithoutASeparateBoundarySelection() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        ChampionshipsCore plugin =
                (ChampionshipsCore)
                        ((sun.misc.Unsafe) field.get(null))
                                .allocateInstance(ChampionshipsCore.class);
        TNTRunConfig config = new TNTRunConfig(plugin, "test");
        SetupTarget target =
                (SetupTarget)
                        Proxy.newProxyInstance(
                                SetupTarget.class.getClassLoader(),
                                new Class<?>[] {SetupTarget.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "plugin" -> plugin;
                                            case "config" -> config;
                                            case "name", "worldName" -> "test";
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        PrepareSession session =
                new PrepareSession(
                        plugin, GameTypeEnum.TNTRun, "test", target, new TNTRunPrepareFlow());
        assertNull(session.step("copy_zero_bounds"));
        assertFalse(
                session.getSteps().stream()
                        .anyMatch(step -> step.captureType() == StepCaptureType.WE_SELECTION));
        assertEquals(StepCaptureType.SCHEMATIC, session.step("schematic").captureType());
        var height = session.step("elimination_height");
        assertNotNull(height);
        assertEquals(StepCaptureType.SELECT, height.captureType());
        assertFalse(height.isSet(session));
        config.setEliminationY(-64.5);
        assertTrue(height.isSet(session));
    }
}
