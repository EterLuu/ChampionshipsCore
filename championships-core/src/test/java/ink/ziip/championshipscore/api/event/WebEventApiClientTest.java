package ink.ziip.championshipscore.api.event;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebEventApiClientTest {
    private static final String TOKEN = "a".repeat(43);

    private final WebEventApiClient client =
            new WebEventApiClient("https://cc.example.test", false, 1, 1);

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Another origin.
                "https://evil.example/api/internal/championships/team-import/",
                // The configured origin with an unexpected path.
                "https://cc.example.test/not-an-import/"
            })
    void refusesForeignOriginsAndUnexpectedPathsBeforeConnecting(String prefix) {
        assertThrows(IllegalArgumentException.class, () -> client.fetchTeamImport(prefix + TOKEN));
    }
}
