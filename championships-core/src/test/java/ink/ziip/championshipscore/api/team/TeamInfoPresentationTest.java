package ink.ziip.championshipscore.api.team;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

@ExtendWith(ConfigurationStateExtension.class)
class TeamInfoPresentationTest {
    @Test
    void preservesDefaultLinesAndAllowsIndependentHeaderAndMemberTemplates() throws Exception {
        var config = ink.ziip.championshipscore.support.MessageConfigurations.defaults();
        var document = new YamlConfiguration();
        config.loadFromConfiguration(document);
        var team =
                new ChampionshipTeam(1, "Team %arg1%", "red", "&c", null) {
                    @Override
                    public List<TeamMemberEntry> getTeamMemberEntries() {
                        return List.of(
                                new TeamMemberEntry(1, UUID.randomUUID(), "Player%arg1%", 1));
                    }
                };
        var manager = TestObjects.allocate(TeamManager.class);
        assertEquals(
                LegacyText.translateColorCodes("&r========&cTeam %arg1%&r========\nPlayer%arg1%\n"),
                manager.getTeamInfo(team));
        document.set("code-text.team-manager.info-header", "[%arg2%]");
        document.set("code-text.team-manager.info-member", "- %arg1%");
        config.loadFromConfiguration(document);
        assertEquals("[Team %arg1%]\n- Player%arg1%\n", manager.getTeamInfo(team));
        document.set("code-text.team-manager.info-header", "");
        config.loadFromConfiguration(document);
        assertEquals("\n- Player%arg1%\n", manager.getTeamInfo(team));
    }
}
