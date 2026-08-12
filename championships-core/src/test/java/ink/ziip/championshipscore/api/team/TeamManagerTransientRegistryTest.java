package ink.ziip.championshipscore.api.team;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamManagerTransientRegistryTest {
    @Test
    void keepsDifferentRuntimeTeamsThatReuseTheSameDisplayName() {
        ChampionshipTeam first = team(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ChampionshipTeam second = team(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertEquals(first, second, "the domain object intentionally compares teams by display name");

        Set<ChampionshipTeam> registry = TeamManager.newTransientTeamRegistry();
        assertTrue(registry.add(first));
        assertTrue(registry.add(second));
        assertEquals(2, registry.size());
    }

    @Test
    void resolvesAdministratorTeamSelectorsByNameOrNumericId() {
        ChampionshipTeam team = new ChampionshipTeam(42, "OrangeOcelots", "ORANGE", "#ffaa00",
                Set.of(UUID.fromString("00000000-0000-0000-0000-000000000003")), null);

        assertSame(team, TeamManager.findTeam(Set.of(team), "OrangeOcelots"));
        assertSame(team, TeamManager.findTeam(Set.of(team), "orangeocelots"));
        assertSame(team, TeamManager.findTeam(Set.of(team), "42"));
        assertNull(TeamManager.findTeam(Set.of(team), "43"));
    }

    private static ChampionshipTeam team(UUID member) {
        return new ChampionshipTeam(-1, "同游小队 1", "RED", "#ff5555", Set.of(member), null);
    }
}
