package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeRun;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Locale;

@ExtendWith(ConfigurationStateExtension.class)
class RiptidePresentationTest {
    @Test
    void dodgeUsesActualArrivalDirectionAndDecimalSecondsUnderAnyServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            MessageConfig.RIPTIDE_RUSH_DODGE_TITLE = "incoming %direction%";
            MessageConfig.RIPTIDE_RUSH_DODGE_ACTIONBAR = "remaining %seconds%";
            var dodge = new RiptideDodgeRun("ZOMBIE", 5, 7, 9, 0);
            assertEquals(
                    "incoming " + dodge.direction().arrivalSide(),
                    RiptidePresentation.dodgeTitle(dodge));
            assertEquals("remaining 9.0", RiptidePresentation.dodgeActionbar(180));
            assertEquals("remaining 0.3", RiptidePresentation.dodgeActionbar(5));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void shieldFeedbackPreservesTargetComponentAndAccelerationTakesPrecedence() {
        MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK = "&6acceleration";
        MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN = "&cshield";
        Component target = Component.translatable("block.minecraft.red_terracotta");
        assertSame(target, RiptidePresentation.floorActionbar(target, false, false));
        Component shield = RiptidePresentation.floorActionbar(target, false, true);
        Component speed = RiptidePresentation.floorActionbar(target, true, true);
        assertTrue(shield.children().contains(target));
        assertTrue(speed.children().contains(target));
        assertTrue(LegacyText.serialize(shield).contains("shield"));
        assertTrue(LegacyText.serialize(speed).contains("acceleration"));
        assertFalse(LegacyText.serialize(speed).contains("shield"));
    }
}
