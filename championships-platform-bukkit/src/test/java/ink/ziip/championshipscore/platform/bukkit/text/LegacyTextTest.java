package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    void rendersConfiguredColourAndBoldBeforeRendererDefaults() {
        Component text = LegacyText.component("&A&L管理员", NamedTextColor.GRAY);
        assertEquals(NamedTextColor.GREEN, text.color());
        assertEquals(TextDecoration.State.TRUE, text.decoration(TextDecoration.BOLD));
        assertEquals("管理员", LegacyText.plainText(LegacyText.serialize(text)));
        assertEquals(NamedTextColor.GRAY, LegacyText.component("普通文字", NamedTextColor.GRAY).color());
    }

    @Test
    void keepsExactHexColoursWhenComponentsEnterTemplates() {
        Component label = LegacyText.component("&#123abc队伍");
        Component rendered = LegacyText.component(LegacyText.serialize(label));
        assertEquals(TextColor.color(0x123abc), rendered.color());
        assertEquals("队伍", PlainTextComponentSerializer.plainText().serialize(rendered));
    }

    @Test
    void anvilTextRemovesAllSupportedFormattingWithoutAddingStyles() {
        for (String input : new String[]{"&e&l地图名称&r", "§e§l地图名称§r",
                "&#123abc地图名称", "#123abc地图名称", "&x&1&2&3&a&b&c地图名称",
                "§x§1§2§3§a§b§c地图名称"}) {
            assertEquals("地图名称", LegacyText.plainText(input), input);
            assertEquals(Component.text("地图名称"), LegacyText.plainComponent(input), input);
        }
    }

    @Test
    void preservesLiteralPunctuationAndHandlesEmptyInput() {
        assertEquals("Tom & Jerry <队伍> 2 < 3", LegacyText.plainText("Tom & Jerry <队伍> 2 < 3"));
        assertEquals("", LegacyText.plainText(null));
        assertEquals(Component.empty(), LegacyText.component(""));
    }

    @Test
    void roundsPointsWithCoreHalfUpSemantics() {
        assertEquals("322", LegacyText.formatPoints(321.5D));
        assertEquals("-2", LegacyText.formatPoints(-1.5D));
    }
}
