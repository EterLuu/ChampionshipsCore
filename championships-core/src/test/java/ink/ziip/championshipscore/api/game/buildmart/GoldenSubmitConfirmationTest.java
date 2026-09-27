package ink.ziip.championshipscore.api.game.buildmart;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class GoldenSubmitConfirmationTest {
    private final UUID player = UUID.randomUUID();
    private final Object team = new Object(), slot = new Object();
    @Test void confirmationIsConsumedAndExpires() {
        var c = new GoldenSubmitConfirmation();
        assertFalse(c.confirm(player, team, slot, 1, 1000));
        assertTrue(c.confirm(player, team, slot, 1, 1100));
        assertFalse(c.confirm(player, team, slot, 1, 1200));
        assertFalse(c.confirm(player, team, slot, 1, 6200));
        assertTrue(c.confirm(player, team, slot, 1, 6300));
    }
    @Test void orderTeamSlotAndPlayerCannotReuseAnotherConfirmation() {
        var c = new GoldenSubmitConfirmation();
        assertFalse(c.confirm(player, team, slot, 1, 100));
        assertFalse(c.confirm(player, team, slot, 2, 200));
        assertFalse(c.confirm(player, new Object(), slot, 2, 300));
        assertFalse(c.confirm(player, team, new Object(), 2, 400));
        assertFalse(c.confirm(UUID.randomUUID(), team, slot, 2, 500));
    }
    @Test void quitAndRoundClearInvalidatePendingClicks() {
        var c = new GoldenSubmitConfirmation();
        c.confirm(player, team, slot, 1, 100); c.remove(player);
        assertFalse(c.confirm(player, team, slot, 1, 200));
        c.clear();
        assertFalse(c.confirm(player, team, slot, 1, 300));
        assertFalse(c.confirm(player, team, slot, 1, 300));
    }
}
