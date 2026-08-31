package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveEventScoringTest {
    @Test
    void resolvesAllowedGamesAndExplicitGameMultipliers() {
        EventStateStore.ActiveEvent event = new EventStateStore.ActiveEvent("id", "s4cc", "赛事", false,
                List.of(new EventStateStore.EventGame(GameTypeEnum.Bingo, "default", "宾果"),
                        new EventStateStore.EventGame(GameTypeEnum.BuildMart, "default", "建造市场")),
                List.of(1D, 1.5D));

        assertTrue(event.allows(GameTypeEnum.Bingo));
        assertFalse(event.allows(GameTypeEnum.TGTTOS));
    }
}
