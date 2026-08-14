package ink.ziip.championshipscore.api.game.spectate;

import ink.ziip.championshipscore.api.visibility.PlayerVisibilityFilter;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorVisibilityTest {
    @Test
    void usesTheTeamsWoolColorAndFallsBackSafely() {
        assertEquals(Material.RED_WOOL, SpectatorTeamIcon.wool("red"));
        assertEquals(Material.LIGHT_BLUE_WOOL, SpectatorTeamIcon.wool("LIGHT_BLUE"));
        assertEquals(Material.WHITE_WOOL, SpectatorTeamIcon.wool("not-a-color"));
        assertEquals(Material.WHITE_WOOL, SpectatorTeamIcon.wool(null));
    }

    @Test
    void playerFilterAllowsOnlySelectedPlayers() {
        UUID selected = UUID.randomUUID();
        PlayerVisibilityFilter filter = PlayerVisibilityFilter.players(Set.of(selected));

        assertTrue(filter.allows(selected, null));
        assertFalse(filter.allows(UUID.randomUUID(), 1));
    }

    @Test
    void teamFilterAllowsOnlyMembersOfSelectedTeams() {
        PlayerVisibilityFilter filter = PlayerVisibilityFilter.teams(Set.of(2));

        assertTrue(filter.allows(UUID.randomUUID(), 2));
        assertFalse(filter.allows(UUID.randomUUID(), 3));
        assertFalse(filter.allows(UUID.randomUUID(), null));
    }

    @Test
    void filterCannotBeEmptyOrMixPlayersAndTeams() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerVisibilityFilter(Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new PlayerVisibilityFilter(Set.of(UUID.randomUUID()), Set.of(1)));
    }
}
