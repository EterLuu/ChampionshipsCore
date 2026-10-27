package ink.ziip.championshipscore.command.team;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.support.MessageConfigurations;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class TeamListSubCommandTest {
    @Test
    void wholeDefaultRowsRetainLabelsCountsColorsAndEmptyNames() throws Exception {
        MessageConfigurations.defaults().loadFromConfiguration(new YamlConfiguration());
        assertEquals(
                "\n&r&cRed &#696969(2/3 在线)\n  &#55ff55在线: &fAlice&7, &fBob\n  &#ff6b6b离线: &fCleo",
                TeamListSubCommand.formatTeam("&cRed", List.of("Alice", "Bob"), List.of("Cleo")));
        assertEquals(
                "\n&r&cRed &#696969(0/0 在线)\n  &#55ff55在线: &f&#696969无\n  &#ff6b6b离线: &f&#696969无",
                TeamListSubCommand.formatTeam("&cRed", List.of(), List.of()));
        assertEquals("\n&#696969共 2 支队伍，2/3 名成员在线", TeamListSubCommand.formatSummary(2, 2, 3));
    }

    @Test
    void configuredOrderSeparatorsAndEmptyTemplatesAreNotReinterpreted() throws Exception {
        var config = MessageConfigurations.defaults();
        var document = new YamlConfiguration();
        document.set(
                "code-text.team-list-sub-command.row",
                "%offline-names% | %team% | %online-names% (%online-count%/%member-count%)");
        document.set("code-text.team-list-sub-command.names-separator", " / ");
        document.set("code-text.team-list-sub-command.empty", "empty %team%");
        document.set("code-text.team-list-sub-command.summary", "%members%/%online% in %teams%");
        config.loadFromConfiguration(document);
        assertEquals(
                "empty %team% | Team%online-count% | A%team% / B (2/2)",
                TeamListSubCommand.formatTeam(
                        "Team%online-count%", List.of("A%team%", "B"), List.of()));
        assertEquals("8/3 in 4", TeamListSubCommand.formatSummary(4, 3, 8));
        document.set("code-text.team-list-sub-command.row", "");
        document.set("code-text.team-list-sub-command.summary", "");
        config.loadFromConfiguration(document);
        assertEquals("", TeamListSubCommand.formatTeam("team", List.of("a"), List.of()));
        assertEquals("", TeamListSubCommand.formatSummary(1, 1, 1));
        document.set("code-text.team-list-sub-command.row", 42);
        assertThrows(IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
        assertEquals("", TeamListSubCommand.formatTeam("team", List.of("a"), List.of()));
    }
}
