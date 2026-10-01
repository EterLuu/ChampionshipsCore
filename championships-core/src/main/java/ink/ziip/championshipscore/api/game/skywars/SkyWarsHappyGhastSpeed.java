package ink.ziip.championshipscore.api.game.skywars;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.HappyGhast;

/** Leaves ridden flight at its normal speed and slows autonomous flight to five percent. */
final class SkyWarsHappyGhastSpeed {
    private static final NamespacedKey IDLE_SPEED =
            new NamespacedKey("championshipscore", "skywars_happy_ghast_idle_speed");

    private SkyWarsHappyGhastSpeed() {
    }

    static void update(HappyGhast ghast) {
        update(ghast.getAttribute(Attribute.FLYING_SPEED), !ghast.getPassengers().isEmpty());
    }

    static void update(AttributeInstance speed, boolean ridden) {
        if (speed == null) return;
        AttributeModifier modifier = speed.getModifier(IDLE_SPEED);
        if (ridden) {
            if (modifier != null) speed.removeModifier(modifier);
        } else if (modifier == null) {
            speed.addTransientModifier(new AttributeModifier(IDLE_SPEED, -0.95,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
}
