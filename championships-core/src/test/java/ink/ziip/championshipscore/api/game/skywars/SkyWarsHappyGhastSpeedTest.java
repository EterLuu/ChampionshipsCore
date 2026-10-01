package ink.ziip.championshipscore.api.game.skywars;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkyWarsHappyGhastSpeedTest {
    @Test
    void unriddenSpeedIsFivePercentAndRepeatedTicksDoNotCompoundTheReduction() {
        var speed = new SpeedAttribute(0.05);
        for (int tick = 0; tick < 100; tick++) SkyWarsHappyGhastSpeed.update(speed.instance, false);

        assertEquals(0.0025, speed.value(), 1.0e-12);
        assertEquals(0.05, speed.base);
        assertEquals(1, speed.modifiers.size());
    }

    @Test
    void mountingAndDismountingRepeatedlyRestoresNormalSpeed() {
        var speed = new SpeedAttribute(0.05);
        for (int ride = 0; ride < 10; ride++) {
            SkyWarsHappyGhastSpeed.update(speed.instance, false);
            assertEquals(0.0025, speed.value(), 1.0e-12);
            SkyWarsHappyGhastSpeed.update(speed.instance, true);
            assertEquals(0.05, speed.value(), 1.0e-12);
            assertTrue(speed.modifiers.isEmpty());
        }
    }

    @Test
    void otherSpeedModifiersAndLaterBaseSpeedChangesArePreserved() {
        var speed = new SpeedAttribute(0.05);
        var boost = new AttributeModifier(new NamespacedKey("test", "flight_boost"), 0.5,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        speed.modifiers.put(boost.getKey(), boost);

        SkyWarsHappyGhastSpeed.update(speed.instance, false);
        assertEquals(0.075 * 0.05, speed.value(), 1.0e-12);
        speed.base = 0.1;
        assertEquals(0.15 * 0.05, speed.value(), 1.0e-12);
        SkyWarsHappyGhastSpeed.update(speed.instance, true);
        assertEquals(0.15, speed.value(), 1.0e-12);
        assertEquals(Map.of(boost.getKey(), boost), speed.modifiers);
    }

    @Test
    void missingFlightAttributeDoesNotBreakTheRound() {
        assertDoesNotThrow(() -> SkyWarsHappyGhastSpeed.update(null, false));
        assertDoesNotThrow(() -> SkyWarsHappyGhastSpeed.update(null, true));
    }

    private static final class SpeedAttribute {
        double base;
        final Map<NamespacedKey, AttributeModifier> modifiers = new LinkedHashMap<>();
        final AttributeInstance instance;

        SpeedAttribute(double base) {
            this.base = base;
            instance = (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                    new Class<?>[]{AttributeInstance.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getModifier" -> modifiers.get(arguments[0]);
                        case "addTransientModifier" -> {
                            var modifier = (AttributeModifier) arguments[0];
                            assertNull(modifiers.putIfAbsent(modifier.getKey(), modifier));
                            yield null;
                        }
                        case "removeModifier" -> {
                            modifiers.remove(((AttributeModifier) arguments[0]).getKey());
                            yield null;
                        }
                        default -> throw new AssertionError("Unexpected attribute mutation: " + method.getName());
                    });
        }

        double value() {
            double value = base;
            for (AttributeModifier modifier : modifiers.values()) {
                assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1, modifier.getOperation());
                value *= 1.0 + modifier.getAmount();
            }
            return value;
        }
    }
}
