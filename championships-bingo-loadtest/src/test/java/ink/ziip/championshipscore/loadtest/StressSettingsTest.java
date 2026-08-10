package ink.ziip.championshipscore.loadtest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StressSettingsTest {
    @Test
    void acceptsMixedStagesWithHalfStationaryWalkers() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                stage-walkers: [8, 32, 64]
                stage-duration-seconds: [120, 120, 180]
                stage-modes: [mixed, mixed, mixed]
                stage-speed-blocks-per-second: [32.0, 32.0, 32.0]
                stage-target-world-entities: [4000, 8000, 16000]
                """);

        StressSettings settings = StressSettings.load(config);

        assertEquals(3, settings.stages().size());
        assertEquals(32.0, settings.stages().get(2).speedBlocksPerSecond());
        assertEquals(StressSettings.Mode.MIXED, settings.stages().get(2).mode());
        assertEquals(32, settings.stages().get(2).stationaryWalkers());
        assertEquals(32, settings.stages().get(2).flyingWalkers());
        assertEquals(16000, settings.stages().get(2).targetWorldEntities());
        assertEquals(384, settings.stationaryPlayerSeparationBlocks());
        assertEquals(24, settings.entityMinimumSpawnDistanceBlocks());
        assertEquals(128, settings.entityMaximumSpawnDistanceBlocks());
        assertEquals(60, settings.layoutSwitchIntervalSeconds());
        assertEquals(7.0, settings.stationaryDispersalSpeedBlocksPerSecond());
    }

    @Test
    void rejectsMismatchedStageMetadata() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                stage-walkers: [8, 64]
                stage-duration-seconds: [60, 60]
                stage-modes: [flight]
                """);

        assertThrows(IllegalArgumentException.class, () -> StressSettings.load(config));
    }

    @Test
    void rejectsOddMixedWalkerCount() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                stage-walkers: [7]
                stage-duration-seconds: [60]
                stage-modes: [mixed]
                stage-speed-blocks-per-second: [32.0]
                stage-target-world-entities: [4000]
                """);

        assertThrows(IllegalArgumentException.class, () -> StressSettings.load(config));
    }
}
