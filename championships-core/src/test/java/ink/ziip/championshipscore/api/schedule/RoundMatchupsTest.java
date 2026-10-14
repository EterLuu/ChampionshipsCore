package ink.ziip.championshipscore.api.schedule;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class RoundMatchupsTest {
    @Test
    void eachDuelGameAnnouncesAllCurrentPairsWithTeamColoursAndRoundNumber() {
        var red = new ChampionshipTeam(1, "Red", "RED", "&#ff6b26", null) {};
        var blue = new ChampionshipTeam(2, "Blue", "BLUE", "&#8a70ff", null) {};
        var yellow = new ChampionshipTeam(3, "Yellow", "YELLOW", "&#fff566", null) {};
        var green = new ChampionshipTeam(4, "Green", "GREEN", "&a", null) {};
        for (var game :
                List.of(GameTypeEnum.ParkourTag, GameTypeEnum.BattleBox, GameTypeEnum.LaserBox)) {
            String first =
                    ScheduleManager.roundMatchups(
                            game,
                            1,
                            List.of(
                                    new TwoVTwoVector(red, blue),
                                    new TwoVTwoVector(yellow, green)));
            assertTrue(first.contains(red.getColoredName()));
            assertTrue(first.contains(blue.getColoredName()));
            assertTrue(first.contains(yellow.getColoredName()));
            assertTrue(first.contains(green.getColoredName()));
            assertEquals(5, first.lines().count());
            assertTrue(first.contains(LegacyText.translateColorCodes("&#ff6b261")));
            String next =
                    ScheduleManager.roundMatchups(game, 8, List.of(new TwoVTwoVector(green, red)));
            assertTrue(next.contains(green.getColoredName()));
            assertFalse(next.contains(blue.getColoredName()));
            assertTrue(next.contains(LegacyText.translateColorCodes("&#ff6b268")));
            assertEquals(4, next.lines().count());
        }
    }
}
