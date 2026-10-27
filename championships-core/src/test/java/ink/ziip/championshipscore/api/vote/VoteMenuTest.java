package ink.ziip.championshipscore.api.vote;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class VoteMenuTest {
    private static final String MENU = "voting.menus.ballot";

    private static YamlConfiguration document() {
        return YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
    }

    private static void activate(YamlConfiguration config) throws Exception {
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, config);
    }

    @Test
    void oldTwelveSlotConfigurationPagesEveryScoringGameWithoutInventingSlots() throws Exception {
        var config = document();
        var configured = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23);
        config.set(MENU + ".layout.content", configured);
        activate(config);
        var games =
                List.of(GameTypeEnum.values()).stream()
                        .filter(game -> !FinaleGameRegistry.isRegistered(game))
                        .toList();
        var layout = VoteMenu.layout(54);
        assertEquals(14, games.size());
        assertEquals(configured, layout.content());
        assertEquals(2, layout.pageCount(games.size()));
        var seen = new ArrayList<GameTypeEnum>();
        for (int page = 0; page < layout.pageCount(games.size()); page++)
            for (int slot : layout.pageSlots(page, games.size()))
                seen.add(games.get(layout.index(page, slot, games.size())));
        assertEquals(games, seen);
        assertEquals(List.of(10, 11), layout.pageSlots(1, games.size()));
    }

    @Test
    void controlOverlapIsRemovedFromCapacityAndOldLayoutSurvivesReload() throws Exception {
        var config = document();
        config.set(MENU + ".layout.content", List.of(49, 1, 20, 4, 10, 7, 40, 45, 48, 53));
        activate(config);
        var old = VoteMenu.layout(54);
        assertEquals(List.of(20, 10), old.content());
        assertFalse(old.border().contains(20));
        config.set(MENU + ".items.close.slot", 44);
        config.set(MENU + ".items.next.slot", 47);
        config.set(MENU + ".layout.content", List.of(11, 12, 13));
        activate(config);
        var current = VoteMenu.layout(54);
        assertEquals(49, old.control("close"));
        assertEquals(53, old.control("next"));
        assertEquals(2, old.index(1, 20));
        assertEquals(44, current.control("close"));
        assertEquals(47, current.control("next"));
        assertEquals(List.of(11, 12, 13), current.content());
    }

    @Test
    void defaultLayoutKeepsAllFourteenCardsOnOnePageAndClampsRemovedCandidates() throws Exception {
        activate(document());
        var layout = VoteMenu.layout(54);
        assertEquals(14, layout.content().size());
        assertEquals(14, new HashSet<>(layout.content()).size());
        assertEquals(1, layout.pageCount(14));
        assertEquals(0, layout.clampPage(5, 0));
        assertEquals(List.of(), layout.pageSlots(0, 0));
        assertTrue(layout.content().stream().noneMatch(layout.controls().values()::contains));
    }

    @Test
    void configuredVoteBarKeepsRoundingAndUsesCustomGlyphs() throws Exception {
        var config = document();
        config.set(MENU + ".presentation.bar-length", 5);
        config.set(MENU + ".presentation.bar-filled", "&a|");
        config.set(MENU + ".presentation.bar-empty", "&7.");
        activate(config);
        var bar = VoteMenu.VoteBar.load();
        assertEquals("&a|&a|&7.&7.&7.", bar.render(1, 3));
        assertEquals("&7.".repeat(5), bar.render(0, 0));
        assertEquals("&a|".repeat(5), bar.render(9, 3));
        assertEquals("&7.".repeat(5), bar.render(-1, 3));
    }
}
