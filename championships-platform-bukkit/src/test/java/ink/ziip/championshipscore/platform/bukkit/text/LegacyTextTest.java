package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyTextTest {
    @Test
    void translatesBothHexFormsAndOrdinaryCodes() {
        assertEquals("§x§f§f§6§b§2§6A §x§f§f§f§5§6§6B §fC",
                LegacyText.translateColorCodes("&#ff6b26A #fff566B &fC"));
    }

    @Test
    void createsPlainEquivalentComponent() {
        assertEquals("宾果 规则", PlainTextComponentSerializer.plainText().serialize(
                LegacyText.component("&#ff6b26宾果 &f规则")));
    }

    @Test
    void roundsPointsWithCoreHalfUpSemantics() {
        assertEquals("322", LegacyText.formatPoints(321.5D));
        assertEquals("-2", LegacyText.formatPoints(-1.5D));
    }
}
