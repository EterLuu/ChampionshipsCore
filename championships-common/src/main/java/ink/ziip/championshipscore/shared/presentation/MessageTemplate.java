package ink.ziip.championshipscore.shared.presentation;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Substitutes named and positional message tokens without interpreting inserted values again. */
public final class MessageTemplate {
    private static final Pattern TOKEN = Pattern.compile("%[\\w.-]+%|\\{[\\w.-]+}");

    private MessageTemplate() {}

    /** Keys include their delimiters, for example {@code %player%} or {@code {0}}. */
    public static String render(String template, Map<String, String> replacements) {
        if (replacements.isEmpty()) return template;
        return TOKEN.matcher(template)
                .replaceAll(
                        match ->
                                Matcher.quoteReplacement(
                                        replacements.getOrDefault(match.group(), match.group())));
    }
}
