package ink.ziip.championshipscore.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.util.Map;

class BanPresentationTest {
    @Test
    void usesConfiguredFallbacksForMissingFieldsAndKeepsInsertedTokensLiteral() {
        var text = new BanPresentation("&c%reason% | %expires%", "rule", "account", "raw", "UTC");
        assertEquals("&crule | account", text.render(null, null));
        assertEquals("&crule | account", text.render("  ", " "));
        assertEquals(
                "&creason %expires% | 2026-10-24T09:30:00Z",
                text.render("reason %expires%", "2026-10-24T09:30:00Z"));
    }

    @Test
    void formatsAnInstantInTheConfiguredZoneAndFallsBackForInvalidExpiry() {
        var text =
                new BanPresentation(
                        "%expires%", "rule", "unknown", "yyyy-MM-dd HH:mm", "Asia/Shanghai");
        assertEquals("2026-10-24 17:30", text.render("reason", "2026-10-24T09:30:00Z"));
        assertEquals("unknown", text.render("reason", "invalid"));
        assertEquals("unknown", text.render("reason", null));
    }

    @Test
    void rawExpiryPreservesTheBackendFormatAndExplicitEmptyText() {
        var text = new BanPresentation("%reason%/%expires%", "", "", "raw", "system");
        assertEquals("/", text.render(null, null));
        assertEquals("/legacy value", text.render("", "legacy value"));
        assertEquals("", new BanPresentation("", "", "", "raw", "UTC").render("why", "when"));
    }

    @Test
    void loadsAllPresentationValuesFromTheSameConfiguration() {
        var config =
                Map.of(
                        "messages.banned",
                        "%reason%: %expires%",
                        "ban-presentation.default-reason",
                        "configured reason",
                        "ban-presentation.unknown-expiry",
                        "configured expiry",
                        "ban-presentation.expiry-format",
                        "raw",
                        "ban-presentation.expiry-zone",
                        "UTC");
        assertEquals(
                "configured reason: configured expiry",
                BanPresentation.from(config::get, "messages.banned").render(null, null));
        assertThrows(
                NullPointerException.class, () -> BanPresentation.from(config::get, "missing"));
    }

    @Test
    void rejectsInvalidTimeConfigurationWhenLoaded() {
        assertThrows(
                DateTimeException.class,
                () -> new BanPresentation("", "", "", "raw", "invalid-zone"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BanPresentation("", "", "", "not a pattern", "UTC"));
    }
}
