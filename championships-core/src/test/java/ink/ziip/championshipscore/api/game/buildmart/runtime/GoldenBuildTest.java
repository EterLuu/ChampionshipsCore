package ink.ziip.championshipscore.api.game.buildmart.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.buildmart.mechanics.GoldenBlueprintScheduler;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.GoldenSubmitConfirmation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class GoldenBuildTest {
    @Test
    void finalGoldenBuildSurvivesTheSettlementBoundary() {
        List<Integer> rotations = new ArrayList<>();
        int[] remaining = {900};
        var scheduler = new GoldenBlueprintScheduler(900, 180, () -> rotations.add(remaining[0]));
        for (; remaining[0] >= 0; remaining[0]--) scheduler.tick(remaining[0]);
        assertEquals(List.of(720, 540, 360, 180), rotations);
    }

    @Test
    void shortFinalWindowAndDuplicateTicksDoNotIntroduceExtraRotations() {
        int[] rotations = {0};
        var scheduler = new GoldenBlueprintScheduler(250, 120, () -> rotations[0]++);
        for (int remaining = 250; remaining >= 0; remaining--) {
            scheduler.tick(remaining);
            scheduler.tick(remaining);
        }
        assertEquals(2, rotations[0]);
        scheduler.tick(-1);
        scheduler.tick(250);
        assertEquals(2, rotations[0]);
    }

    @Test
    void roundShorterThanPeriodKeepsInitialOrder() {
        var scheduler = new GoldenBlueprintScheduler(60, 120, () -> fail("unexpected rotation"));
        for (int remaining = 60; remaining >= 0; remaining--) scheduler.tick(remaining);
    }

    private final UUID player = UUID.randomUUID();
    private final Object team = new Object(), slot = new Object();

    @Test
    void confirmationIsConsumedAndExpires() {
        var c = new GoldenSubmitConfirmation();
        assertFalse(c.confirm(player, team, slot, 1, 1000));
        assertTrue(c.confirm(player, team, slot, 1, 1100));
        assertFalse(c.confirm(player, team, slot, 1, 1200));
        assertFalse(c.confirm(player, team, slot, 1, 6200));
        assertTrue(c.confirm(player, team, slot, 1, 6300));
    }

    @Test
    void orderTeamSlotAndPlayerCannotReuseAnotherConfirmation() {
        var c = new GoldenSubmitConfirmation();
        assertFalse(c.confirm(player, team, slot, 1, 100));
        assertFalse(c.confirm(player, team, slot, 2, 200));
        assertFalse(c.confirm(player, new Object(), slot, 2, 300));
        assertFalse(c.confirm(player, team, new Object(), 2, 400));
        assertFalse(c.confirm(UUID.randomUUID(), team, slot, 2, 500));
    }

    @Test
    void quitAndRoundClearInvalidatePendingClicks() {
        var c = new GoldenSubmitConfirmation();
        c.confirm(player, team, slot, 1, 100);
        c.remove(player);
        assertFalse(c.confirm(player, team, slot, 1, 200));
        c.clear();
        assertFalse(c.confirm(player, team, slot, 1, 300));
        assertFalse(c.confirm(player, team, slot, 1, 300));
    }
}
