package ink.ziip.championshipscore.api.game.riptiderush;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RiptideFloorProtectionTest {
    @Test void consumedShieldCoversAllRemainingRoundsOnlyForThatPlayerAndStage() {
        var protection = new RiptideFloorProtection();
        var player = UUID.randomUUID(); var other = UUID.randomUUID();
        assertFalse(protection.protects(player));
        protection.grant(player);
        for (int round=2; round<=7; round++) {
            assertTrue(protection.protects(player));
            assertFalse(protection.protects(other));
        }
        protection.clear();
        assertFalse(protection.protects(player));
    }
}
