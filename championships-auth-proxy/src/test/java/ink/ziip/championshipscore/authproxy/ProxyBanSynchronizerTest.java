package ink.ziip.championshipscore.authproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class ProxyBanSynchronizerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @TempDir Path tempDirectory;

    @Test
    void bootstrapsCurrentBansThenAppliesOnlyNewBanEvents() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/internal/bridge/proxy-ban-snapshot",
                exchange ->
                        respond(
                                exchange,
                                "{\"maintenance\":false,\"nextCursor\":\"9\",\"profiles\":[{\"username\":\"AllowedPlayer\",\"status\":\"ALLOWED\",\"uuid\":\"11111111-1111-4111-8111-111111111111\"}],\"bans\":[{\"username\":\"ActivePlayer\",\"reason\":\"snapshot\"},"
                                    + "{\"username\":\"ExpiredPlayer\",\"reason\":\"expired\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}]}"));
        server.createContext(
                "/api/internal/bridge/proxy-changes",
                exchange ->
                        respond(
                                exchange,
                                "{\"maintenance\":false,\"nextCursor\":\"11\",\"changes\":[{\"operation\":\"BANNED\",\"authmeUsername\":\"NewPlayer\",\"status\":\"BANNED\",\"reason\":\"event\"},"
                                    + "{\"operation\":\"UNBANNED\",\"authmeUsername\":\"FormerPlayer\",\"status\":\"UNBOUND\"},"
                                    + "{\"operation\":\"REVOKED\",\"authmeUsername\":\"autobot00\",\"status\":\"UNBOUND\",\"bot\":true}]}"));
        server.start();
        try {
            List<String> kicked = new ArrayList<>();
            ProxyAccessState state =
                    new ProxyAccessState(tempDirectory.resolve("ban-state.properties").toFile());
            ProxyBanSynchronizer synchronizer =
                    new ProxyBanSynchronizer(
                            client(server),
                            state,
                            (username, reason, expiresAt) ->
                                    kicked.add(username + ":" + reason + ":" + expiresAt),
                            java.util.logging.Logger.getLogger("test"));

            synchronizer.run();
            assertEquals(List.of("ActivePlayer:snapshot:null"), kicked);
            assertEquals("9", state.cursor());
            assertEquals(
                    "11111111-1111-4111-8111-111111111111",
                    state.cachedProfile("AllowedPlayer", Duration.ZERO, java.time.Instant.now())
                            .uuid);

            synchronizer.run();
            assertEquals(
                    List.of(
                            "ActivePlayer:snapshot:null",
                            "NewPlayer:event:null",
                            "autobot00:Bot account is no longer enabled:null"),
                    kicked);
            assertEquals("11", state.cursor());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reloadsTheSnapshotWhenCcWebRejectsTheCursorAfterADatabaseRestore() throws Exception {
        AtomicInteger snapshots = new AtomicInteger();
        List<String> changeCursors = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/internal/bridge/proxy-ban-snapshot",
                exchange ->
                        respond(
                                exchange,
                                200,
                                snapshots.getAndIncrement() == 0
                                        // Before the restore: a player and a ban added after
                                        // the backup was taken.
                                        ? "{\"maintenance\":false,\"nextCursor\":\"120\",\"profiles\":[{\"username\":\"LatePlayer\",\"status\":\"ALLOWED\",\"uuid\":\"11111111-1111-4111-8111-111111111111\"}],\"bans\":[{\"username\":\"LateBan\",\"reason\":\"late\"}]}"
                                        // The restored website only knows the backup's state.
                                        : "{\"maintenance\":false,\"nextCursor\":\"100\",\"profiles\":[{\"username\":\"KeptPlayer\",\"status\":\"ALLOWED\",\"uuid\":\"22222222-2222-4222-8222-222222222222\"}],\"bans\":[{\"username\":\"RestoredBan\",\"reason\":\"restored\"}]}"));
        server.createContext(
                "/api/internal/bridge/proxy-changes",
                exchange -> {
                    String after =
                            exchange.getRequestURI()
                                    .getQuery()
                                    .replaceAll(".*after=(\\d+).*", "$1");
                    changeCursors.add(after);
                    if ("120".equals(after)) {
                        respond(
                                exchange,
                                409,
                                "{\"statusCode\":409,\"message\":\"CURSOR_AHEAD_OF_OUTBOX\",\"error\":\"Conflict\"}");
                    } else {
                        respond(
                                exchange,
                                200,
                                "{\"maintenance\":false,\"nextCursor\":\""
                                        + after
                                        + "\",\"changes\":[]}");
                    }
                });
        server.start();
        List<LogRecord> records = new ArrayList<>();
        Logger logger = capturingLogger(records);
        try {
            Path file = tempDirectory.resolve("restored.properties");
            List<String> kicked = new ArrayList<>();
            ProxyAccessState state = new ProxyAccessState(file.toFile());
            ProxyBanSynchronizer synchronizer =
                    new ProxyBanSynchronizer(
                            client(server),
                            state,
                            (username, reason, expiresAt) -> kicked.add(username + ":" + reason),
                            logger);

            synchronizer.run();
            assertEquals("120", state.cursor());

            // cc-web was restored from a backup: the cursor is rejected and the snapshot reloaded.
            synchronizer.run();
            java.time.Instant now = java.time.Instant.now();
            assertEquals("100", new ProxyAccessState(file.toFile()).cursor());
            assertNull(state.cachedProfile("LatePlayer", Duration.ZERO, now));
            assertNull(state.cachedProfile("LateBan", Duration.ZERO, now));
            assertEquals("ALLOWED", state.cachedProfile("KeptPlayer", Duration.ZERO, now).status);
            assertEquals("BANNED", state.cachedProfile("RestoredBan", Duration.ZERO, now).status);
            assertEquals(List.of("LateBan:late", "RestoredBan:restored"), kicked);

            // Synchronization then follows the restored event log.
            synchronizer.run();
            assertEquals(List.of("120", "100"), changeCursors);
            assertEquals(2, snapshots.get());
            assertTrue(
                    records.stream()
                            .anyMatch(
                                    record ->
                                            record.getLevel() == Level.WARNING
                                                    && record.getMessage().contains("cursor 120")
                                                    && record.getMessage()
                                                            .contains("CURSOR_AHEAD_OF_OUTBOX")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsTheCursorWhenCcWebRejectsTheRequestForAnotherReason() throws Exception {
        AtomicInteger snapshots = new AtomicInteger();
        AtomicInteger changes = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/internal/bridge/proxy-ban-snapshot",
                exchange -> {
                    snapshots.incrementAndGet();
                    respond(
                            exchange,
                            200,
                            "{\"maintenance\":false,\"nextCursor\":\"9\",\"profiles\":[],\"bans\":[]}");
                });
        server.createContext(
                "/api/internal/bridge/proxy-changes",
                exchange ->
                        respond(
                                exchange,
                                changes.getAndIncrement() == 0 ? 401 : 400,
                                changes.get() == 1
                                        ? "{\"statusCode\":401,\"message\":\"Unauthorized\"}"
                                        : "{\"statusCode\":400,\"message\":\"INVALID_LIMIT\"}"));
        server.start();
        try {
            ProxyAccessState state =
                    new ProxyAccessState(tempDirectory.resolve("kept.properties").toFile());
            ProxyBanSynchronizer synchronizer =
                    new ProxyBanSynchronizer(
                            client(server),
                            state,
                            (username, reason, expiresAt) -> {},
                            capturingLogger(new ArrayList<>()));

            synchronizer.run();
            synchronizer.run();
            synchronizer.run();

            assertEquals(2, changes.get());
            assertEquals(1, snapshots.get());
            assertTrue(state.initialized());
            assertEquals("9", state.cursor());
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
        Handler handler =
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                };
        logger.addHandler(handler);
        try {
            ProxyAccessState state =
                    new ProxyAccessState(tempDirectory.resolve("unavailable.properties").toFile());
            ProxyBanSynchronizer synchronizer =
                    new ProxyBanSynchronizer(
                            client("http://127.0.0.1:" + port),
                            state,
                            (username, reason, expiresAt) -> {},
                            logger);

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
        return new ProxyIdentityClient(
                baseUrl, "cc-core", SECRET, false, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static Logger capturingLogger(List<LogRecord> records) {
        Logger logger = Logger.getLogger("proxy-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                });
        return logger;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws java.io.IOException {
        respond(exchange, 200, body);
    }

    private static void respond(
            com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
