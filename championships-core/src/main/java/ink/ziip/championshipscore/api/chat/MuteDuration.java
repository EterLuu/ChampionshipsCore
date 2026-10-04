package ink.ziip.championshipscore.api.chat;

import java.util.Locale;
import java.util.regex.Pattern;

/** Parses positive moderation durations without silently accepting partial input or overflow. */
public final class MuteDuration {
    private static final Pattern PART = Pattern.compile("([0-9]+)([smhdw])");

    private MuteDuration() {}

    public static long parseMillis(String input) {
        if (input == null) throw new IllegalArgumentException("missing duration");
        var matcher = PART.matcher(input.toLowerCase(Locale.ROOT));
        long seconds = 0;
        int end = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != end) throw new IllegalArgumentException("invalid duration");
                long unit =
                        switch (matcher.group(2)) {
                            case "s" -> 1;
                            case "m" -> 60;
                            case "h" -> 3_600;
                            case "d" -> 86_400;
                            case "w" -> 604_800;
                            default -> throw new IllegalArgumentException("invalid unit");
                        };
                seconds =
                        Math.addExact(
                                seconds,
                                Math.multiplyExact(Long.parseLong(matcher.group(1)), unit));
                end = matcher.end();
            }
            if (end != input.length() || seconds <= 0)
                throw new IllegalArgumentException("invalid duration");
            return Math.multiplyExact(seconds, 1_000);
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IllegalArgumentException("duration too large", error);
        }
    }

    public static String formatMillis(long millis) {
        long seconds = millis / 1_000 + (millis % 1_000 > 0 ? 1 : 0);
        StringBuilder result = new StringBuilder();
        for (long unit : new long[] {86_400, 3_600, 60, 1}) {
            long amount = seconds / unit;
            if (amount > 0)
                result.append(amount)
                        .append(
                                switch ((int) unit) {
                                    case 86_400 -> "d";
                                    case 3_600 -> "h";
                                    case 60 -> "m";
                                    default -> "s";
                                });
            seconds %= unit;
        }
        return result.isEmpty() ? "0s" : result.toString();
    }
}
