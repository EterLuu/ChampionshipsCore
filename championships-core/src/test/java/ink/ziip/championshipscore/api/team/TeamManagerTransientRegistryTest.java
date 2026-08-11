package ink.ziip.championshipscore.api.team;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static ChampionshipTeam team(UUID member) {
        return new ChampionshipTeam(-1, "同游小队 1", "RED", "#ff5555", Set.of(member), null);
    }
}
