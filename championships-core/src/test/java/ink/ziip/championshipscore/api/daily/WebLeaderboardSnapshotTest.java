package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class WebLeaderboardSnapshotTest {
    @Test
    void newDailyGameCannotBreakAnOlderWebCatalog() {
        CCConfig.WEB_LEADERBOARD_SYNC_ENABLED_GAMES = null;
        for (GameTypeEnum game :
                List.of(
                        GameTypeEnum.Bingo,
                        GameTypeEnum.AceRace,
                        GameTypeEnum.DragonEggCarnival,
                        GameTypeEnum.ParkourWarrior))
            assertTrue(WebLeaderboardSnapshot.canPublish(game));
        assertFalse(WebLeaderboardSnapshot.canPublish(GameTypeEnum.LaserShowdown));
        CCConfig.WEB_LEADERBOARD_SYNC_ENABLED_GAMES = List.of(" Bingo ", "lasershowdown");
        assertTrue(WebLeaderboardSnapshot.canPublish(GameTypeEnum.LaserShowdown));
        assertTrue(WebLeaderboardSnapshot.canPublish(GameTypeEnum.Bingo));
        assertFalse(WebLeaderboardSnapshot.canPublish(GameTypeEnum.AceRace));
    }

    @Test
    void favoriteWeaponIsPersonalTextAndNeverANumericRanking() {
        assertEquals(
                List.of(
                        DailyMetric.LASERSHOWDOWN_WINS,
                        DailyMetric.LASERSHOWDOWN_AVERAGE_KILLS,
                        DailyMetric.LASERSHOWDOWN_HEADSHOT_RATE),
                DailyMetric.rankedForGame(GameTypeEnum.LaserShowdown));
        assertEquals(4, DailyMetric.forGame(GameTypeEnum.LaserShowdown).size());
        assertFalse(DailyMetric.LASERSHOWDOWN_FAVORITE_GUN.rankable());
        assertEquals("2.5", DailyMetric.format(DailyMetric.LASERSHOWDOWN_AVERAGE_KILLS, 2.5));
        assertEquals("25.0%", DailyMetric.format(DailyMetric.LASERSHOWDOWN_HEADSHOT_RATE, 25));
    }
}
