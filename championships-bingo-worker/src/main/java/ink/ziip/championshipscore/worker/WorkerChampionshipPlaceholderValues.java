package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure resolver kept separate from PlaceholderAPI so manifest semantics remain unit-testable. */
final class WorkerChampionshipPlaceholderValues {
    private static final Pattern EXPLICIT_HEX_COLOR = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern LEGACY_HEX_COLOR = Pattern.compile("(?<!&)#([a-fA-F0-9]{6})");
    private static final String COLOR_CODE_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private WorkerChampionshipPlaceholderValues() {
    }

    static String resolve(PlayerSnapshot player, TeamSnapshot team, BingoPresentation presentation,
                          String params) {
        String none = presentation.messages().getOrDefault("papi.none", "无");
        String spectator = presentation.messages().getOrDefault("papi.spectator", "旁观");

        if (params.startsWith("player_team_name_no_color")) {
            return team == null ? spectator : team.name();
        }
        if (params.startsWith("player_team_name")) {
            return team == null ? spectator : translateColorCodes(team.colorCode() + team.name());
        }
        if (params.startsWith("player_team_color_code")) {
            return team == null ? none : team.colorCode();
        }
        if (params.startsWith("player_team_color")) {
            return team == null ? none : team.colorName();
        }
        if (params.startsWith("player_points")) {
            return formatPoints(player == null ? 0D : player.points());
        }
        if (params.startsWith("player_team_points")) {
            return formatPoints(team == null ? 0D : team.points());
        }
        return null;
    }

    private static String formatPoints(double points) {
        return BigDecimal.valueOf(points).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String translateColorCodes(String message) {
        message = expandHexColors(message, EXPLICIT_HEX_COLOR);
        message = expandHexColors(message, LEGACY_HEX_COLOR);
        char[] chars = message.toCharArray();
        for (int index = 0; index + 1 < chars.length; index++) {
            if (chars[index] == '&' && COLOR_CODE_CHARS.indexOf(chars[index + 1]) >= 0) {
                chars[index] = '§';
                chars[index + 1] = Character.toLowerCase(chars[index + 1]);
            }
        }
        return new String(chars);
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
