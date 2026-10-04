package ink.ziip.championshipscore.configuration.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

class ConfigurationValidationTest {
    private static final class Fields {
        long timeout;
        double multiplier;
        List<Double> weights;
        List<List<String>> rules;
    }

    @Test
    void numericScalarsAndNestedListsUseDeclaredFieldTypes() throws Exception {
        var document = new YamlConfiguration();
        document.set("timeout", 12);
        document.set("multiplier", 2);
        document.set("weights", List.of(1, 1.5));
        document.set("rules", List.of(List.of("first"), List.of("second")));

        assertEquals(
                12L,
                ConfigurationValueReader.read(
                        document, "timeout", Fields.class.getDeclaredField("timeout")));
        assertEquals(
                2D,
                ConfigurationValueReader.read(
                        document, "multiplier", Fields.class.getDeclaredField("multiplier")));
        assertEquals(
                List.of(1D, 1.5D),
                ConfigurationValueReader.read(
                        document, "weights", Fields.class.getDeclaredField("weights")));
        assertEquals(
                List.of(List.of("first"), List.of("second")),
                ConfigurationValueReader.read(
                        document, "rules", Fields.class.getDeclaredField("rules")));
    }

    @Test
    void invalidListEntriesCannotDisappearDuringTypedConversion() throws Exception {
        var document = new YamlConfiguration();
        document.set("weights", List.of(1, "invalid", 2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ConfigurationValueReader.read(
                                document, "weights", Fields.class.getDeclaredField("weights")));
        document.set("rules", List.of(List.of("first"), List.of(42)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ConfigurationValueReader.read(
                                document, "rules", Fields.class.getDeclaredField("rules")));
    }

    @Test
    void integerOptionsRejectFractionsAndOverflow() throws Exception {
        var document = new YamlConfiguration();
        for (Number value : List.of(1.5D, Double.POSITIVE_INFINITY, 1E100D)) {
            document.set("timeout", value);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ConfigurationValueReader.read(
                                    document, "timeout", Fields.class.getDeclaredField("timeout")));
        }
    }

    @Test
    void emptyFormalMapListsUseRegisteredMapSelectionWithoutAPresetName() {
        var document = new YamlConfiguration();
        for (var game : ink.ziip.championshipscore.api.game.model.GameTypeEnum.values()) {
            assertEquals(List.of(), CCConfig.formalEventMaps(document, game));
            document.set("formal-events." + game.name() + ".maps", List.of());
            assertEquals(List.of(), CCConfig.formalEventMaps(document, game));
        }
        document.set("formal-events.Bingo.maps", List.of(" custom ", "", "second"));
        assertEquals(
                List.of("custom", "second"),
                CCConfig.formalEventMaps(
                        document, ink.ziip.championshipscore.api.game.model.GameTypeEnum.Bingo));
    }

    @Test
    void acceptsOnlyTheCurrentVersion() {
        assertDoesNotThrow(() -> BaseConfigurationFile.validateVersion(45, 45, "gui.yml"));
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> BaseConfigurationFile.validateVersion(44, 45, "gui.yml"));
        assertTrue(error.getMessage().contains("gui.yml"));
        assertTrue(error.getMessage().contains("44"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BaseConfigurationFile.validateVersion(46, 45, "gui.yml"));
    }

    @Test
    void rejectsInvalidRoundMultipliers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CCConfig.validateRoundMultipliers(java.util.List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CCConfig.validateRoundMultipliers(java.util.List.of(1D, -0.1D)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CCConfig.validateRoundMultipliers(java.util.List.of(1D, Double.NaN)));
        assertEquals(
                java.util.List.of(1D, 2.5D),
                CCConfig.validateRoundMultipliers(java.util.List.of(1D, 2.5D)));
    }
}
