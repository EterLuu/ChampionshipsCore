package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyModelTest {
    @Test
    void selectionIsPartyWideAndLeadershipTransfersOnLeave() {
        UUID creator = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        DailyParty party = new DailyParty(creator);
        assertTrue(party.add(member));

        long revision = party.select(GameTypeEnum.Bingo);
        assertEquals(GameTypeEnum.Bingo, party.selectedGame());
        assertEquals(revision, party.revision());

        assertTrue(party.remove(creator));
        assertEquals(member, party.leader());
    }

    @Test
    void rulesClampInvalidConfigurationToUsableCapacity() {
        DailyRules rules = new DailyRules(99, 99, 2, 3, 0);

        assertEquals(6, rules.maxPlayers());
        assertEquals(6, rules.minPlayers());
        assertEquals(3, rules.countdownSeconds());
    }

    @Test
    void statsAccumulateProgressAndKeepTheLargestSingleMatchCount() {
        DailyStatSnapshot snapshot = DailyStatSnapshot.EMPTY
                .add(true, 3, 25)
                .add(false, 1, 14);

        assertEquals(2, snapshot.gamesPlayed());
        assertEquals(1, snapshot.wins());
        assertEquals(4, snapshot.lineCount());
        assertEquals(39, snapshot.completedTasks());
        assertEquals(25, snapshot.maxCompletedTasks());
    }
}
