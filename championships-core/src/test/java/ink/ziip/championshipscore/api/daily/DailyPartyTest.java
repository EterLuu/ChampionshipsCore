package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyPartyTest {
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
}
