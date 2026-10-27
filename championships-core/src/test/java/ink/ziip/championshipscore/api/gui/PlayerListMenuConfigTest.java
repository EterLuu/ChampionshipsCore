package ink.ziip.championshipscore.api.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.shared.presentation.PlayerListPresentation;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class PlayerListMenuConfigTest {
    @Test
    void teammatesFreezeInheritedButtonsAndCompleteLoreForLocalAndRemotePages() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        TestObjects.field(GuiConfig.class, "active").set(null, document);
        var original = PlayerListMenuConfig.teammates();
        assertEquals(3, original.layout().pageCount(101));
        assertEquals(45, original.layout().content().size());
        assertEquals(
                document.getString("buttons.next.title"), original.items().get("next").title());
        String root = MenuId.BINGO_TEAMMATE_TELEPORT.path();
        document.set(root + ".layout.content", List.of(8, 17, 26));
        document.set(root + ".items.teammate.lore", List.of("first", "%player%", "last"));
        document.set(root + ".items.teammate.glint", true);
        document.set(root + ".items.page.lore", List.of("%count%"));
        var current = PlayerListMenuConfig.teammates();
        assertEquals(
                current,
                PlayerListPresentation.teammates(
                        new BingoPresentation(
                                current.attributes(PlayerListPresentation.TEAMMATE_PREFIX))));
        assertEquals(34, current.layout().pageCount(101));
        assertEquals(3, current.items().get("player").lore().size());
        assertTrue(current.items().get("player").glint());
        document.set(root + ".items.teammate.lore", List.of());
        assertTrue(PlayerListMenuConfig.teammates().items().get("player").lore().isEmpty());
        assertEquals(1, original.items().get("player").lore().size());
        assertEquals(3, current.items().get("player").lore().size());
    }

    @Test
    void snapshotUsesTheSameInheritedOverridesForBothServersAndSurvivesReload() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        TestObjects.field(GuiConfig.class, "active").set(null, document);
        var original = PlayerListMenuConfig.spectatorPlayers();
        String root = MenuId.SPECTATOR_PLAYER_TELEPORT.path();
        document.set(root + ".layout.content", null);
        document.set(root + ".layout.content.3", List.of(8, 11, 15));
        document.set(root + ".layout.content.2", List.of(19, 20));
        document.set(root + ".layout.footer.next", 52);
        document.set(root + ".items.player.use", "next");
        document.set(root + ".items.player.material", "DIAMOND");
        document.set(root + ".items.player.glint", true);
        document.set(root + ".items.player.lore", List.of("first", "%player%", "last"));
        var current = PlayerListMenuConfig.spectatorPlayers();
        assertEquals(List.of(8, 11, 15), current.layout().content());
        assertEquals(52, current.layout().control("next"));
        assertEquals(List.of(19, 20), current.layout().pageSlots(1, 5));
        assertEquals("DIAMOND", current.items().get("player").material());
        assertTrue(current.items().get("player").glint());
        assertEquals(3, current.items().get("player").lore().size());
        assertEquals(
                current,
                PlayerListPresentation.spectator(
                        new BingoPresentation(
                                current.attributes(PlayerListPresentation.SPECTATOR_PREFIX))));
        document.set(root + ".items.player.lore", List.of());
        assertTrue(PlayerListMenuConfig.spectatorPlayers().items().get("player").lore().isEmpty());
        assertEquals(45, original.layout().content().size());
        assertEquals(1, original.items().get("player").lore().size());
        assertEquals(3, current.items().get("player").lore().size());
    }
}
