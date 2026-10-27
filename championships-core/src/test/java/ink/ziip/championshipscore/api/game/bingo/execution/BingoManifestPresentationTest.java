package ink.ziip.championshipscore.api.game.bingo.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.shared.presentation.BingoCardPresentation;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.logging.Logger;

@ExtendWith(ConfigurationStateExtension.class)
class BingoManifestPresentationTest {
    @Test
    void eachRunModeFreezesConfiguredTimeUnitsAcrossLaterReloads() throws Exception {
        var plugin = TestObjects.allocate(ChampionshipsCore.class);
        var config = new MessageConfig(plugin);
        var document =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/message.yml").toFile());
        var language =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/bingo/lang/zh_CN.yml").toFile());
        var service = TestObjects.allocate(MessageService.class);
        TestObjects.set(service, "current", language);
        TestObjects.set(service, "fallback", language);
        TestObjects.set(service, "log", Logger.getAnonymousLogger());
        var global = TestObjects.field(MessageService.class, "instance");
        Object previous = global.get(null);
        global.set(null, service);
        try {
            var gui =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/gui.yml").toFile());
            TestObjects.field(GuiConfig.class, "active").set(null, gui);
            var factory = new BingoManifestFactory(plugin);
            var freeze =
                    BingoManifestFactory.class.getDeclaredMethod("presentation", GameRunMode.class);
            freeze.setAccessible(true);
            for (var mode : GameRunMode.values()) {
                document.set("presentation.player.prefix", "custom %label%");
                document.set("bingo.action-bar-count-down-pvp", "PvP %time%");
                document.set("bingo.action-bar-count-down-protection", "%grace% / %time%");
                document.set("code-text.duration.seconds", "%seconds%s");
                document.set("code-text.duration.minutes-seconds", "%minutes%m %seconds%s");
                config.loadFromConfiguration(document);
                gui.set(
                        "games.bingo.menus.card.items.info.lore",
                        java.util.List.of("before", "%win-hint%", "after"));
                gui.set(
                        "spectator.menus.player-teleport-selector.items.player.lore",
                        java.util.List.of("first", "%player%", "last"));
                gui.set(
                        "games.bingo.menus.teammate-teleport.items.teammate.lore",
                        java.util.List.of("first", "%player%", "last"));
                gui.set(
                        "games.bingo.menus.card.items.task.states.completed.lore",
                        java.util.List.of("first", "%player%", "last"));
                var original = (BingoPresentation) freeze.invoke(factory, mode);
                document.set("presentation.player.prefix", "");
                document.set("bingo.action-bar-count-down-pvp", "");
                document.set("bingo.action-bar-count-down-protection", "changed %time%");
                document.set("code-text.duration.seconds", "%seconds% sec");
                config.loadFromConfiguration(document);
                gui.set("games.bingo.menus.card.items.info.lore", java.util.List.of());
                gui.set(
                        "spectator.menus.player-teleport-selector.items.player.lore",
                        java.util.List.of());
                gui.set(
                        "games.bingo.menus.teammate-teleport.items.teammate.lore",
                        java.util.List.of());
                gui.set(
                        "games.bingo.menus.card.items.task.states.completed.lore",
                        java.util.List.of());
                var newer = (BingoPresentation) freeze.invoke(factory, mode);
                assertEquals(
                        3,
                        BingoTaskPresentation.from(original)
                                .states()
                                .get(BingoTaskPresentation.State.COMPLETED)
                                .lore()
                                .size());
                assertTrue(
                        BingoTaskPresentation.from(newer)
                                .states()
                                .get(BingoTaskPresentation.State.COMPLETED)
                                .lore()
                                .isEmpty());
                assertEquals(
                        3,
                        ink.ziip.championshipscore.shared.presentation.PlayerListPresentation
                                .teammates(original)
                                .items()
                                .get("player")
                                .lore()
                                .size());
                assertEquals(
                        0,
                        ink.ziip.championshipscore.shared.presentation.PlayerListPresentation
                                .teammates(newer)
                                .items()
                                .get("player")
                                .lore()
                                .size());
                assertEquals(
                        3,
                        ink.ziip.championshipscore.shared.presentation.PlayerListPresentation
                                .spectator(original)
                                .items()
                                .get("player")
                                .lore()
                                .size());
                assertEquals(
                        0,
                        ink.ziip.championshipscore.shared.presentation.PlayerListPresentation
                                .spectator(newer)
                                .items()
                                .get("player")
                                .lore()
                                .size());
                assertEquals(
                        "custom %label%",
                        ink.ziip.championshipscore.shared.presentation.PlayerTextFormat.from(
                                        original.messages())
                                .prefix());
                assertEquals(
                        "",
                        ink.ziip.championshipscore.shared.presentation.PlayerTextFormat.from(
                                        newer.messages())
                                .prefix());
                assertEquals(3, BingoCardPresentation.from(original).info().lore().size());
                assertEquals(0, BingoCardPresentation.from(newer).info().lore().size());
                assertEquals("PvP %time%", original.message("bingo.timer-pvp"), mode.name());
                assertEquals(
                        "%grace% / %time%",
                        original.message("bingo.timer-protection"), mode.name());
                assertEquals("", newer.message("bingo.timer-pvp"), mode.name());
                assertEquals(
                        "changed %time%", newer.message("bingo.timer-protection"), mode.name());
                assertEquals("%seconds%s", original.message("duration.seconds"), mode.name());
                assertEquals(
                        "%minutes%m %seconds%s",
                        original.message("duration.minutes-seconds"), mode.name());
                assertEquals("%seconds% sec", newer.message("duration.seconds"), mode.name());
            }
        } finally {
            global.set(null, previous);
        }
    }
}
