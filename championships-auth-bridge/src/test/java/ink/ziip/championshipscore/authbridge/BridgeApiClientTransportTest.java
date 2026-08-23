package ink.ziip.championshipscore.authbridge;

import ink.ziip.championshipscore.authbridge.bridge.BridgeApiClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeApiClientTransportTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void requiresExplicitOptInForNonLoopbackHttp() {
        assertThrows(IllegalArgumentException.class, () -> client("http://cc-web:3000", false));
        assertDoesNotThrow(() -> client("http://cc-web:3000", true));
    }

    @Test
    void permitsLoopbackHttpAndRemoteHttpsWithoutOptIn() {
        assertDoesNotThrow(() -> client("http://127.0.0.1:3000", false));
        assertDoesNotThrow(() -> client("https://accounts.example.test", false));
    }

    private static BridgeApiClient client(String url, boolean allowInsecurePrivateHttp) {
        return new BridgeApiClient(url, "cc-core", SECRET, allowInsecurePrivateHttp, TIMEOUT, TIMEOUT);
    }
}
