package ink.ziip.championshipscore.authproxy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyBanSynchronizerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @TempDir
    Path tempDirectory;

    @Test
    void bootstrapsCurrentBansThenAppliesOnlyNewBanEvents() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/bridge/proxy-ban-snapshot", exchange -> respond(exchange,
                "{\"nextCursor\":\"9\",\"bans\":[{\"username\":\"ActivePlayer\",\"reason\":\"snapshot\"},"
                        + "{\"username\":\"ExpiredPlayer\",\"reason\":\"expired\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}]}"));
        server.createContext("/api/internal/bridge/changes", exchange -> respond(exchange,
                "{\"nextCursor\":\"11\",\"changes\":[{\"operation\":\"BANNED\",\"authmeUsername\":\"NewPlayer\",\"reason\":\"event\"},"
                        + "{\"operation\":\"UNBANNED\",\"authmeUsername\":\"FormerPlayer\"}]}"));
        server.start();
        try {
            List<String> kicked = new ArrayList<>();
            ProxyBanState state = new ProxyBanState(tempDirectory.resolve("ban-state.properties").toFile());
            ProxyBanSynchronizer synchronizer = new ProxyBanSynchronizer(
                    client(server), state, (username, reason) -> kicked.add(username + ":" + reason),
                    java.util.logging.Logger.getLogger("test"));

            synchronizer.run();
            assertEquals(List.of("ActivePlayer:snapshot"), kicked);
            assertEquals("9", state.cursor());

            synchronizer.run();
            assertEquals(List.of("ActivePlayer:snapshot", "NewPlayer:event"), kicked);
            assertEquals("11", state.cursor());
        } finally {
            server.stop(0);
        }
    }

    private static ProxyIdentityClient client(HttpServer server) {
        return new ProxyIdentityClient("http://127.0.0.1:" + server.getAddress().getPort(), "cc-core", SECRET,
                false, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
