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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                    client(server), state, (username, reason, expiresAt) -> kicked.add(username + ":" + reason + ":" + expiresAt),
                    java.util.logging.Logger.getLogger("test"));

            synchronizer.run();
            assertEquals(List.of("ActivePlayer:snapshot:null"), kicked);
            assertEquals("9", state.cursor());

            synchronizer.run();
            assertEquals(List.of("ActivePlayer:snapshot:null", "NewPlayer:event:null"), kicked);
            assertEquals("11", state.cursor());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rateLimitsTransientWebFailureLogsWithoutAnExceptionStackTrace() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        server.stop(0);

        Logger logger = Logger.getLogger("proxy-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        logger.addHandler(handler);
        try {
            ProxyBanState state = new ProxyBanState(tempDirectory.resolve("unavailable.properties").toFile());
            ProxyBanSynchronizer synchronizer = new ProxyBanSynchronizer(
                    client("http://127.0.0.1:" + port), state, (username, reason, expiresAt) -> { }, logger);

            synchronizer.run();
            synchronizer.run();

            assertEquals(1, records.size());
            assertEquals(Level.WARNING, records.get(0).getLevel());
            assertTrue(records.get(0).getMessage().contains("attempt 1"));
            assertFalse(records.get(0).getMessage().contains("Could not synchronize"));
            assertNull(records.get(0).getThrown());
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static ProxyIdentityClient client(HttpServer server) {
        return client("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static ProxyIdentityClient client(String baseUrl) {
        return new ProxyIdentityClient(baseUrl, "cc-core", SECRET,
                false, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
