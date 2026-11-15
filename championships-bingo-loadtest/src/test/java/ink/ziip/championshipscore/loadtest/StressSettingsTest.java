package ink.ziip.championshipscore.loadtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class StressSettingsTest {
    @Test
    void acceptsMixedStagesWithHalfStationaryWalkers() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(
                """
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStages")
    void rejectsInvalidStageConfiguration(String reason, String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);

        assertThrows(IllegalArgumentException.class, () -> StressSettings.load(config), reason);
    }

    static Stream<Arguments> invalidStages() {
        return Stream.of(
                Arguments.of(
                        "mismatched stage metadata",
                        """
                        stage-walkers: [8, 64]
                        stage-duration-seconds: [60, 60]
                        stage-modes: [flight]
                        """),
                Arguments.of(
                        "legacy top-level speed without per-stage values",
                        """
                        stage-walkers: [8]
                        stage-duration-seconds: [60]
                        stage-modes: [flight]
                        speed-blocks-per-second: 32.0
                        """),
                Arguments.of(
                        "odd mixed walker count",
                        """
                        stage-walkers: [7]
                        stage-duration-seconds: [60]
                        stage-modes: [mixed]
                        stage-speed-blocks-per-second: [32.0]
                        stage-target-world-entities: [4000]
                        """));
    }
}
