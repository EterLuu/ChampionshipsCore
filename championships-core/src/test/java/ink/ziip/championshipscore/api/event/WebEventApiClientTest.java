package ink.ziip.championshipscore.api.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebEventApiClientTest {
    private final WebEventApiClient client = new WebEventApiClient(
            "https://cc.example.test", "cc-core", "a".repeat(32), false, 1, 1);

    @Test
    void refusesImportLinksFromAnotherOriginBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,
                () -> client.fetchTeamImport("https://evil.example/api/internal/championships/team-import/" + "a".repeat(43)));
    }

    @Test
    void refusesUnexpectedPathsBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,
                () -> client.fetchTeamImport("https://cc.example.test/not-an-import/" + "a".repeat(43)));
    }
}
