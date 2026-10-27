package ink.ziip.championshipscore.auth;

import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Configured ban text shared by proxy admission, backend admission and online disconnection. */
public final class BanPresentation {
    private final String template;
    private final String defaultReason;
    private final String unknownExpiry;
    private final DateTimeFormatter expiryFormatter;

    public BanPresentation(
            String template,
            String defaultReason,
            String unknownExpiry,
            String pattern,
            String zone) {
        this.template = Objects.requireNonNull(template, "template");
        this.defaultReason = Objects.requireNonNull(defaultReason, "defaultReason");
        this.unknownExpiry = Objects.requireNonNull(unknownExpiry, "unknownExpiry");
        Objects.requireNonNull(pattern, "pattern");
        ZoneId zoneId = "system".equals(zone) ? ZoneId.systemDefault() : ZoneId.of(zone);
        this.expiryFormatter =
                "raw".equals(pattern)
                        ? null
                        : DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
    }

    public static BanPresentation from(Function<String, String> configuration, String templateKey) {
        return new BanPresentation(
                configuration.apply(templateKey),
                configuration.apply("ban-presentation.default-reason"),
                configuration.apply("ban-presentation.unknown-expiry"),
                configuration.apply("ban-presentation.expiry-format"),
                configuration.apply("ban-presentation.expiry-zone"));
    }

    public String render(String reason, String expiresAt) {
        return MessageTemplate.render(
                template,
                Map.of(
                        "%reason%",
                        reason == null || reason.isBlank() ? defaultReason : reason,
                        "%expires%",
                        formatExpiry(expiresAt)));
    }

    private String formatExpiry(String value) {
        if (value == null || value.isBlank()) return unknownExpiry;
        if (expiryFormatter == null) return value;
        try {
            return expiryFormatter.format(Instant.parse(value));
        } catch (java.time.DateTimeException ignored) {
            return unknownExpiry;
        }
    }
}
