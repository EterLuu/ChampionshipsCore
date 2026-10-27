package ink.ziip.championshipscore.api.game.parkourtag.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.parkourtag.geometry.ParkourTagGeometry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.junit.jupiter.api.Test;

import java.util.*;

class ParkourTagEliminationTest {
    @Test
    void quittingOrTaggingTwiceCannotOverwriteFirstSurvivalTimeOrAwardAnotherCatch() {
        UUID runner = UUID.randomUUID(), hunter = UUID.randomUUID();
        var right = new ChampionshipTeam(1, "r", "r", "", Set.of(runner, hunter), null) {};
        var left = new ChampionshipTeam(2, "l", "l", "", Set.of(), null) {};
        var match = new ParkourTagMatch(0, right, left, (ParkourTagGeometry) null);
        match.setRightAreaChaser(hunter);
        assertTrue(match.recordElimination(runner, 12));
        assertFalse(match.recordElimination(runner, 59));
        assertFalse(match.recordElimination(hunter, 60));
        assertFalse(match.recordElimination(UUID.randomUUID(), 60));
        assertEquals(Map.of(runner, 12), match.getPlayerSurviveTimes());
        assertEquals(List.of(right), match.updateTeamSurviveTimes(12));
        assertTrue(match.updateTeamSurviveTimes(59).isEmpty());
        assertEquals(12, match.getRightTeamSurviveTime());
    }
}
