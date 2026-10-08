package ink.ziip.championshipscore.api.game.riptiderush.course;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class RiptideLevelTemplateTest {

    @Test
    void templateSerializationValidatesIdentityVariantsAndBounds() {
        for (var type : RiptideLevelType.values()) {
            var t = RiptideLevelTemplate.create(type.name().toLowerCase(Locale.ROOT), type);
            assertEquals(t, RiptideLevelTemplate.parse(t.serialize()));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiptideLevelTemplate(
                                "../bad", "x", RiptideLevelType.PASS, "AUTO", true, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiptideLevelTemplate(
                                "bad", "x", RiptideLevelType.MATH, "WEAVE", true, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RiptideLevelTemplate(
                                "bad", "x", RiptideLevelType.PASS, "AUTO", true, 0, 1, 1));
    }
}
