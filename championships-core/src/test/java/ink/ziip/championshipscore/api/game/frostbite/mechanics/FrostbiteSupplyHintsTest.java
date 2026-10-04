package ink.ziip.championshipscore.api.game.frostbite.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class FrostbiteSupplyHintsTest {
    @Test
    void hintSurvivesHudRefreshesAndExpiresAtFiveSeconds() {
        var hints = new FrostbiteSupplyHints();
        UUID player = UUID.randomUUID();
        hints.add(player, "冰雪狙击弓：三发冰箭", 23);
        for (int tick = 23; tick < 123; tick += 5)
            assertEquals("冰雪狙击弓：三发冰箭", hints.current(player, tick));
        assertNull(hints.current(player, 123));
    }

    @Test
    void twoIcicleRewardsAreShownInOrderWithoutLosingEitherDescription() {
        var hints = new FrostbiteSupplyHints();
        UUID player = UUID.randomUUID();
        hints.add(player, "营火", 20);
        hints.add(player, "雪崩", 20);
        assertEquals("营火", hints.current(player, 119));
        assertEquals("雪崩", hints.current(player, 120));
        assertEquals("雪崩", hints.current(player, 219));
        assertNull(hints.current(player, 220));
    }

    @Test
    void freezeAndQuitClearOnlyTheirPlayerAndRoundResetClearsEveryone() {
        var hints = new FrostbiteSupplyHints();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        hints.add(a, "旧道具", 0);
        hints.add(b, "另一玩家", 0);
        hints.clear(a);
        assertNull(hints.current(a, 1));
        assertEquals("另一玩家", hints.current(b, 1));
        hints.add(a, "重生后的道具", 5);
        assertEquals("重生后的道具", hints.current(a, 5));
        hints.clear();
        assertNull(hints.current(a, 6));
        assertNull(hints.current(b, 6));
    }
}
