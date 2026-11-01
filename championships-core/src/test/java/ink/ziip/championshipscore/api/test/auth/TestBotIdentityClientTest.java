package ink.ziip.championshipscore.api.test.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

final class TestBotIdentityClientTest {
    private static final String SECRET = "test-lease-secret-that-is-at-least-32-bytes";

    @Test
    void signsLeaseRequestAndRejectsAnIncompleteResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/internal/test/bots/leases",
                exchange ->
                        respond(
                                exchange,
                                200,
                                "{\"bots\":[{\"username\":\"autobot00\",\"leaseToken\":\"v1.x.y\"}]}"));
        server.start();
        try {
            TestBotIdentityClient client = client(server);
            assertEquals(
                    "v1.x.y",
                    client.leases("server", UUID.randomUUID(), 2, List.of("autobot00"), 60)
                            .getFirst()
                            .token());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            client.leases(
                                    "server",
                                    UUID.randomUUID(),
                                    2,
                                    List.of("autobot00", "autobot01"),
                                    60));
        } finally {
            server.stop(0);
        }
    }

    private static TestBotIdentityClient client(HttpServer server) {
        return new TestBotIdentityClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "harness",
                SECRET,
                false,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
