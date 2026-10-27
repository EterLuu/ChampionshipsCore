package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.daily.DailyBingoVoteController.Phase;
import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@ExtendWith(ConfigurationStateExtension.class)
class DailyBingoVoteMenuTest {
    private YamlConfiguration document;

    @BeforeEach
    void setup() throws Exception {
        document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, document);
    }

    @Test
    void everyPhaseKeepsSparseOptionAndPhaseNavigationSeparateAcrossReload() {
        for (Phase phase : Phase.values()) {
            String path =
                    DailyBingoVoteMenu.FAMILY
                            + ".screens."
                            + phase.name().toLowerCase(java.util.Locale.ROOT);
            document.set(path + ".layout.content", List.of(16, 10));
            document.set(path + ".layout.footer.options-next", 1);
            var layout = DailyBingoVoteMenu.layout(phase);
            assertEquals(
                    phase == Phase.GENESIS ? 5 : 3,
                    layout.pageCount(phase == Phase.GENESIS ? 9 : 5));
            assertEquals(List.of(16), layout.pageSlots(2, 5));
            assertEquals(1, layout.control("options-next"));
            if (phase != Phase.GENESIS) assertEquals(26, layout.control("next"));
            document.set(path + ".layout.footer.options-next", 2);
            document.set(path + ".layout.content", List.of(11));
            assertEquals(1, layout.control("options-next"));
            assertEquals(List.of(16, 10), layout.content());
            assertEquals(2, DailyBingoVoteMenu.layout(phase).control("options-next"));
        }
    }

    @Test
    void fullLoreInsertsLocalizedDescriptionsBeforeSinglePassParameters() {
        String item = DailyBingoVoteMenu.FAMILY + ".items.modes.domination";
        document.set(
                item + ".states.selected.lore",
                List.of("before", "%description%", "%name%", "after"));
        var values = Map.of("name", "Literal %description% $\\");
        var lines =
                GuiItemLore.expand(
                        GuiConfig.itemLoreTemplate(item, "selected"),
                        values,
                        "%description%",
                        List.of(Component.text("first"), Component.text("second")));
        assertEquals(
                List.of("before", "first", "second", "Literal %description% $\\", "after"),
                lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList());
        document.set(item + ".states.selected.lore", List.of());
        assertTrue(GuiConfig.itemLoreTemplate(item, "selected").isEmpty());
        assertTrue(GuiConfig.itemLoreTemplate(item, "locked").contains("%locked-text%"));
        assertFalse(GuiConfig.itemLoreTemplate(item, "locked").contains("%count-text%"));
    }
}
