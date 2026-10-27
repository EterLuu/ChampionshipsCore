package ink.ziip.championshipscore.api.game.area.prepare.tgttos;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.tgttos.config.TGTTOSConfig;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class TGTTOSAreaTypeStepTest {
    @Test
    void existingOptionsReadCurrentLabelsAndRespectExplicitEmptyValues() throws Exception {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        field(GuiConfig.class, "active").set(null, defaults);
        var options = TGTTOSAreaTypeStep.options();
        assertEquals(
                List.of("BOAT", "ROAD", "NONE", "ELYTRA"),
                options.stream().map(TGTTOSAreaTypeStep.Option::value).toList());
        var overrides = new YamlConfiguration();
        overrides.setDefaults(defaults);
        for (var option : options) {
            String key = option.descriptionKey();
            assertEquals(defaults.getString(key), option.description());
            overrides.set(key, "Current " + option.value());
        }
        field(GuiConfig.class, "active").set(null, overrides);
        for (var option : options) assertEquals("Current " + option.value(), option.description());
        var boat = options.getFirst();
        overrides.set(boat.descriptionKey(), "");
        assertEquals("", boat.description());
        overrides.set(boat.descriptionKey(), null);
        assertEquals(defaults.getString(boat.descriptionKey()), boat.description());
        assertEquals("BOAT", boat.value());
    }

    @Test
    void existingStepStatusUsesReloadedDescriptionWithoutChangingStoredType() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        field(GuiConfig.class, "active").set(null, document);
        var step = new TGTTOSAreaTypeStep();
        var config = allocate(TGTTOSConfig.class);
        config.setAreaType("ROAD");
        var target =
                proxy(
                        SetupTarget.class,
                        (p, m, a) -> {
                            if (m.getName().equals("config")) return config;
                            throw new AssertionError(m.getName());
                        });
        var session = allocate(PrepareSession.class);
        set(session, "target", target);
        document.set(
                "map-editor.menus.step-list.games.tgttos.items.area-type.states.selected.title",
                "%option%: %description%");
        document.set(TGTTOSAreaTypeStep.options().get(1).descriptionKey(), "Updated %option%");
        assertEquals("ROAD: Updated %option%", step.stateText(session));
        assertEquals("ROAD", config.getAreaType());
        assertTrue(step.isSet(session));
    }
}
