package ink.ziip.championshipscore.api.game.arena;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class GamePickupTest {
    @Test
    void heldNameIncludesLiteralUseDescriptionAndHonorsAnEmptyDescription() {
        Component title = Component.text("道具 %description%");
        var name = GamePickup.heldName(title, Component.text("右键释放 %title%"));
        assertEquals(
                "道具 %description% · 右键释放 %title%",
                LegacyText.plainText(LegacyText.serialize(name)));
        assertEquals(title, GamePickup.heldName(title, Component.empty()));
    }

    @Test
    void hintSurvivesHudRefreshesAndExpiresAtFiveSeconds() {
        var hints = new GamePickup.Hints();
        UUID player = UUID.randomUUID();
        hints.add(player, Component.text("冰雪狙击弓：三发冰箭"), 23);
        for (int tick = 23; tick < 123; tick += 5)
            assertEquals(Component.text("冰雪狙击弓：三发冰箭"), hints.current(player, tick));
        assertNull(hints.current(player, 123));
    }

    @Test
    void twoIcicleRewardsAreShownInOrderWithoutLosingEitherDescription() {
        var hints = new GamePickup.Hints();
        UUID player = UUID.randomUUID();
        hints.add(player, Component.text("营火"), 20);
        hints.add(player, Component.text("雪崩"), 20);
        assertEquals(Component.text("营火"), hints.current(player, 119));
        assertEquals(Component.text("雪崩"), hints.current(player, 120));
        assertEquals(Component.text("雪崩"), hints.current(player, 219));
        assertNull(hints.current(player, 220));
    }

    @Test
    void freezeAndQuitClearOnlyTheirPlayerAndRoundResetClearsEveryone() {
        var hints = new GamePickup.Hints();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        hints.add(a, Component.text("旧道具"), 0);
        hints.add(b, Component.text("另一玩家"), 0);
        hints.clear(a);
        assertNull(hints.current(a, 1));
        assertEquals(Component.text("另一玩家"), hints.current(b, 1));
        hints.add(a, Component.text("重生后的道具"), 5);
        assertEquals(Component.text("重生后的道具"), hints.current(a, 5));
        hints.clear();
        assertNull(hints.current(a, 6));
        assertNull(hints.current(b, 6));
    }
}
