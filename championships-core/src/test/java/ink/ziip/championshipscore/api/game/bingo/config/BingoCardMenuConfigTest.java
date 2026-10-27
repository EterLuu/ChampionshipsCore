package ink.ziip.championshipscore.api.game.bingo.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.shared.presentation.BingoCardPresentation;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class BingoCardMenuConfigTest {
    @Test
    void taskStylesFreezeCompleteTemplatesAndHonorExplicitEmptyStateLore() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
        var original = BingoCardMenuConfig.taskSnapshot();
        String root = "games.bingo.menus.card.items.task";
        document.set(root + ".states.completed.lore", List.of("first", "%player%", "last"));
        document.set(root + ".states.hidden.title", "secret");
        var current = BingoCardMenuConfig.taskSnapshot();
        assertEquals("&8?", original.states().get(BingoTaskPresentation.State.HIDDEN).title());
        assertEquals("secret", current.states().get(BingoTaskPresentation.State.HIDDEN).title());
        assertEquals(
                current, BingoTaskPresentation.from(new BingoPresentation(current.attributes())));
        document.set(root + ".states.completed.lore", List.of());
        assertTrue(
                BingoCardMenuConfig.taskSnapshot()
                        .states()
                        .get(BingoTaskPresentation.State.COMPLETED)
                        .lore()
                        .isEmpty());
        assertEquals(3, current.states().get(BingoTaskPresentation.State.COMPLETED).lore().size());
    }

    @Test
    void reloadChangesNewSnapshotsWhileFrozenCardsKeepOriginalLayoutAndFullLore() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
        var original = BingoCardMenuConfig.snapshot();
        String root = "games.bingo.menus.card";
        document.set(root + ".screens.x3.size", 54);
        document.set(
                root + ".screens.x3.layout.content", List.of(40, 38, 36, 31, 29, 27, 22, 20, 18));
        document.set(root + ".items.info.slot", 53);
        // Other widths inherit the same info slot, so give them enough inventory space.
        document.set(root + ".screens.x4.size", 54);
        document.set(root + ".screens.x5.size", 54);
        document.set(root + ".items.info.lore", List.of("first", "%win-hint%", "last"));
        var current = BingoCardMenuConfig.snapshot();
        assertEquals(27, original.layout(3).size());
        assertEquals(54, current.layout(3).size());
        assertEquals(53, current.layout(5).info());
        assertEquals(3, current.info().lore().size());
        assertEquals(
                current, BingoCardPresentation.from(new BingoPresentation(current.attributes())));
        document.set(root + ".items.info.lore", List.of());
        assertTrue(BingoCardMenuConfig.snapshot().info().lore().isEmpty());
        assertEquals(3, current.info().lore().size());
    }
}
