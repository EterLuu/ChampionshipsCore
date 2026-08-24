package ink.ziip.championshipscore.api.player.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileUuidResolverTest {
    @Test
    void acceptsCompactYggdrasilUuid() {
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                ProfileUuidResolver.parseUuid("069a79f444e94726a5befca90e38aaf5"));
    }

    @Test
    void acceptsDashedUuidAndRejectsMalformedValues() {
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                ProfileUuidResolver.parseUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertThrows(IllegalArgumentException.class, () -> ProfileUuidResolver.parseUuid("not-a-uuid"));
    }

    @Test
    void resolvesStandardMojangProfileResponse() throws Exception {
        try (TestProfileServer server = new TestProfileServer(200,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}")) {
            assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                    resolver().resolve(server.baseUrl(), "Notch"));
        }
    }

    @Test
    void reportsMissingPlayerAndUnavailableServiceSeparately() throws Exception {
        try (TestProfileServer server = new TestProfileServer(204, "")) {
            PlayerUuidLookupException missing = assertThrows(PlayerUuidLookupException.class,
                    () -> resolver().resolve(server.baseUrl(), "MissingPlayer"));
            assertEquals(PlayerUuidLookupException.Reason.PLAYER_NOT_FOUND, missing.reason());
        }
        try (TestProfileServer server = new TestProfileServer(503, "unavailable")) {
            PlayerUuidLookupException unavailable = assertThrows(PlayerUuidLookupException.class,
                    () -> resolver().resolve(server.baseUrl(), "Notch"));
            assertEquals(PlayerUuidLookupException.Reason.SERVICE_UNAVAILABLE, unavailable.reason());
        }
    }

    @Test
    void rejectsMalformedOrMismatchedProfileResponses() throws Exception {
        try (TestProfileServer server = new TestProfileServer(200,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"OtherPlayer\"}")) {
            PlayerUuidLookupException mismatch = assertThrows(PlayerUuidLookupException.class,
                    () -> resolver().resolve(server.baseUrl(), "Notch"));
            assertEquals(PlayerUuidLookupException.Reason.INVALID_RESPONSE, mismatch.reason());
        }
    }

    private static ProfileUuidResolver resolver() {
        return new ProfileUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static final class TestProfileServer implements AutoCloseable {
        private final HttpServer server;

        private TestProfileServer(int status, String responseBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/users/profiles/minecraft/", exchange -> respond(exchange, status, responseBody));
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
