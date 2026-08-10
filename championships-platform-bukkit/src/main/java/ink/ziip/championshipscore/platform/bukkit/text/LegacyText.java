package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared formatting rules for Core and Folia workers that render the same configured text. */
public final class LegacyText {
    private static final Pattern EXPLICIT_HEX_COLOR = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern LEGACY_HEX_COLOR = Pattern.compile("(?<!&)#([a-fA-F0-9]{6})");
    private static final String COLOR_CODE_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final char SECTION = '§';
    private static final LegacyComponentSerializer SECTION_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private LegacyText() {
    }

    /** Translates preferred {@code &#RRGGBB}, legacy {@code #RRGGBB}, and ordinary ampersand codes. */
    public static String translateColorCodes(String message) {
        message = expandHexColors(message, EXPLICIT_HEX_COLOR);
        message = expandHexColors(message, LEGACY_HEX_COLOR);
        char[] chars = message.toCharArray();
        for (int index = 0; index + 1 < chars.length; index++) {
            if (chars[index] == '&' && COLOR_CODE_CHARS.indexOf(chars[index + 1]) >= 0) {
                chars[index] = SECTION;
                chars[index + 1] = Character.toLowerCase(chars[index + 1]);
            }
        }
        return new String(chars);
    }

    /** Converts configured legacy text into an Adventure component. */
    public static Component component(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return SECTION_SERIALIZER.deserialize(translateColorCodes(text));
    }

    /** Rounds scoreboard points exactly as the Core placeholders do. */
    public static String formatPoints(double points) {
        return BigDecimal.valueOf(points).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String expandHexColors(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("&x");
            for (char digit : matcher.group(1).toCharArray()) replacement.append('&').append(digit);
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }
}
