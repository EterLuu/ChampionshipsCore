package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class CraftingFeastAreaTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void acceptsNonEmptyTeamsWithoutAFourPlayerRequirement(int size) throws Exception {
        assertTrue(CraftingFeastArea.validRoster(List.of(team(size)), 8));
    }

    @Test
    void preservesCapacityAndUniqueNonEmptyRosterRequirements() throws Exception {
        var first = team(2);
        var second = team(3);
        assertTrue(CraftingFeastArea.validRoster(List.of(first, second), 2));
        assertFalse(CraftingFeastArea.validRoster(List.of(first, second), 1));
        assertFalse(CraftingFeastArea.validRoster(List.of(team(0)), 8));
        assertFalse(CraftingFeastArea.validRoster(List.of(), 8));
        set(second, "members", Set.of(first.getMembers().iterator().next(), UUID.randomUUID()));
        assertFalse(CraftingFeastArea.validRoster(List.of(first, second), 8));
    }

    @Test
    void onlyInteractiveFeastIntroductionUsesThreeMinutes() throws Exception {
        var area = allocate(CraftingFeastArea.class);
        var config = allocate(CraftingFeastConfig.class);
        set(area, "gameConfig", config);
        set(config, "tutorialEnabled", true);
        assertEquals(180, area.getIntroductionDurationSeconds());
        set(config, "tutorialEnabled", false);
        assertEquals(90, area.getIntroductionDurationSeconds());
    }

    private static ChampionshipTeam team(int size) throws Exception {
        var team = allocate(ChampionshipTeam.class);
        Set<UUID> members = new LinkedHashSet<>();
        while (members.size() < size) members.add(UUID.randomUUID());
        set(team, "members", members);
        return team;
    }
}
