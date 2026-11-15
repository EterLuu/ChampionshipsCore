package ink.ziip.championshipscore.api.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.MessageConfigurations;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;
import java.util.UUID;

class TeamManagerTest {
    @Test
    void transientRegistryKeepsDifferentRuntimeTeamsThatReuseTheSameDisplayName() {
        ChampionshipTeam first = transientTeam(new UUID(0, 1));
        ChampionshipTeam second = transientTeam(new UUID(0, 2));
        assertFalse(first.equals(second));

        Set<ChampionshipTeam> registry = TeamManager.newTransientTeamRegistry();
        assertTrue(registry.add(first));
        assertTrue(registry.add(second));
        assertEquals(2, registry.size());
        assertEquals(System.identityHashCode(first), first.hashCode());
        assertEquals(System.identityHashCode(second), second.hashCode());
    }

    @Test
    void resolvesAdministratorTeamSelectorsByNameOrNumericId() {
        ChampionshipTeam team =
                new ChampionshipTeam(
                        42, "OrangeOcelots", "ORANGE", "#ffaa00", Set.of(new UUID(0, 3)), null);

        assertSame(team, TeamManager.findTeam(Set.of(team), "OrangeOcelots"));
        assertSame(team, TeamManager.findTeam(Set.of(team), "orangeocelots"));
        assertSame(team, TeamManager.findTeam(Set.of(team), "42"));
        assertNull(TeamManager.findTeam(Set.of(team), "43"));
    }

    private static ChampionshipTeam transientTeam(UUID member) {
        return new ChampionshipTeam(-1, "同游小队 1", "RED", "#ff5555", Set.of(member), null);
    }

    @Nested
    @ExtendWith(ConfigurationStateExtension.class)
    class TeamInfo {
        @Test
        void preservesDefaultLinesAndAllowsIndependentHeaderAndMemberTemplates() throws Exception {
            var config = MessageConfigurations.defaults();
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
                    LegacyText.translateColorCodes(
                            "&r========&cTeam %arg1%&r========\nPlayer%arg1%\n"),
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
}
