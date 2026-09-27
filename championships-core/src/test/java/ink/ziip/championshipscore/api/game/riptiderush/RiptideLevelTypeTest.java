package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiptideLevelTypeTest {
    @Test
    void exposesOnlySemanticMechanicCategories() {
        assertEquals(List.of(RiptideLevelType.MATH, RiptideLevelType.PASS, RiptideLevelType.COLOR_FLOOR, RiptideLevelType.DODGE, RiptideLevelType.RHYTHM),
                List.of(RiptideLevelType.values()));
    }

    @Test
    void parsesPersistedTypesCaseInsensitivelyAndRejectsObstacleShapes() {
        assertEquals(RiptideLevelType.MATH, RiptideLevelType.parse("math"));
        assertEquals(RiptideLevelType.PASS, RiptideLevelType.parse(" PASS "));
        assertThrows(IllegalArgumentException.class, () -> RiptideLevelType.parse("wall-hole"));
        assertThrows(IllegalArgumentException.class, () -> RiptideLevelType.parse("parkour"));
    }
}
