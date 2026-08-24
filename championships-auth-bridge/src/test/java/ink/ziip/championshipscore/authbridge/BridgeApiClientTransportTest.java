package ink.ziip.championshipscore.authbridge;

import com.sun.net.httpserver.HttpServer;
import ink.ziip.championshipscore.authbridge.bridge.BridgeApiClient;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void acknowledgesCursorWithTheExactSignedBody() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedSignature = new AtomicReference<>();
        AtomicReference<String> capturedTimestamp = new AtomicReference<>();
        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/bridge/ack", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            capturedSignature.set(exchange.getRequestHeaders().getFirst("X-CC-Signature"));
            capturedTimestamp.set(exchange.getRequestHeaders().getFirst("X-CC-Timestamp"));
            capturedRequestId.set(exchange.getRequestHeaders().getFirst("X-CC-Request-Id"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            BridgeApiClient client = client("http://127.0.0.1:" + server.getAddress().getPort(), false);

            client.acknowledge("42");

            String expectedBody = "{\"through\":\"42\"}";
            assertEquals(expectedBody, capturedBody.get());
            assertEquals(signature("POST", "/api/internal/bridge/ack", capturedTimestamp.get(),
                    capturedRequestId.get(), expectedBody), capturedSignature.get());
        } finally {
            server.stop(0);
        }
    }

    private static BridgeApiClient client(String url, boolean allowInsecurePrivateHttp) {
        return new BridgeApiClient(url, "cc-core", SECRET, allowInsecurePrivateHttp, TIMEOUT, TIMEOUT);
    }

    private static String signature(String method, String path, String timestamp, String requestId, String body) throws Exception {
        String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((method + "\n" + path + "\n" + timestamp + "\n"
                + requestId + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8)));
    }
}
