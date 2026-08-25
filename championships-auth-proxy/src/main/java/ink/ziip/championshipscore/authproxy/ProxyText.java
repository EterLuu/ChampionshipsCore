package ink.ziip.championshipscore.authproxy;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Matches the Core configuration convention: &#RRGGBB plus legacy formatting codes. */
final class ProxyText {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private ProxyText() {
    }

    static String format(String text) {
        Matcher matcher = HEX.matcher(text == null ? "" : text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(output);
        return ChatColor.translateAlternateColorCodes('&', output.toString());
    }
}
