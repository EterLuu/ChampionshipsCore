package ink.ziip.championshipscore.authbridge.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ink.ziip.championshipscore.auth.AuthAdmissionOwner;
import ink.ziip.championshipscore.auth.BanPresentation;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BridgeSynchronizerResynchronizationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Pattern THROUGH = Pattern.compile("\"through\":\"(\\d+)\"");

    @Test
    void restoredWebsiteCursorRebuildsFromTheSnapshotAndReplaysFromTheStart() throws Exception {
        File file = Files.createTempFile("authbridge-state", ".yml").toFile();
        List<String> acknowledgements = Collections.synchronizedList(new ArrayList<>());
        List<String> changeCursors = Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // The website was restored from a backup taken before outbox id 120 existed.
        server.createContext(
                "/api/internal/bridge/ack",
                exchange -> {
                    Matcher through =
                            THROUGH.matcher(
                                    new String(
                                            exchange.getRequestBody().readAllBytes(),
                                            StandardCharsets.UTF_8));
                    String cursor = through.find() ? through.group(1) : "";
                    acknowledgements.add(cursor);
                    if ("120".equals(cursor))
                        json(
                                exchange,
                                409,
                                "{\"message\":\"CURSOR_AHEAD_OF_OUTBOX\",\"statusCode\":409}");
                    else {
                        exchange.sendResponseHeaders(204, -1);
                        exchange.close();
                    }
                });
        server.createContext(
                "/api/internal/bridge/snapshot",
                exchange ->
                        json(
                                exchange,
                                200,
                                "{\"players\":[],\"bans\":[{\"accountId\":\""
                                        + UUID.randomUUID()
                                        + "\",\"username\":\"Banned\",\"reason\":\"cheating\","
                                        + "\"expiresAt\":null}]}"));
        server.createContext(
                "/api/internal/bridge/control-job",
                exchange -> json(exchange, 200, "{\"job\":null}"));
        server.createContext(
                "/api/internal/bridge/changes",
                exchange -> {
                    String after =
                            exchange.getRequestURI()
                                    .getQuery()
                                    .replaceAll("^after=([^&]*).*$", "$1");
                    changeCursors.add(after);
                    json(exchange, 200, "{\"changes\":[],\"nextCursor\":\"" + after + "\"}");
                });
        server.start();
        try {
            LocalAccessState state = new LocalAccessState(file);
            // Created after the backup: the restored website will never revoke these.
            state.recordIdentity(
                    "Stale", UUID.randomUUID().toString(), UUID.randomUUID().toString());
            state.ban("StaleBan", "old", null);
            state.markBindingSnapshotApplied();
            state.advance("120");
            BridgeSynchronizer synchronizer =
                    new BridgeSynchronizer(
                            plugin(),
                            new BridgeApiClient(
                                    "http://127.0.0.1:" + server.getAddress().getPort(),
                                    "cc-core",
                                    SECRET,
                                    false,
                                    Duration.ofSeconds(2),
                                    Duration.ofSeconds(2)),
                            null,
                            state,
                            "",
                            "",
                            new BridgeUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                            AuthAdmissionOwner.PROXY,
                            new BanPresentation("", "", "", "raw", "UTC"));

            synchronizer.run();

            assertTrue(state.resynchronizing());
            assertEquals("0", state.cursor());
            assertNull(state.pendingAcknowledgement());
            assertFalse(state.bindingSnapshotApplied());
            assertTrue(state.hasIdentity("Stale"), "logins keep working until the snapshot");

            synchronizer.run();

            Instant now = Instant.now();
            assertFalse(state.resynchronizing());
            assertTrue(state.bindingSnapshotApplied());
            assertFalse(state.hasIdentity("Stale"));
            assertNull(state.activeBan("StaleBan", now));
            assertNotNull(state.activeBan("Banned", now));
            assertEquals(List.of("120", "0"), acknowledgements);
            assertEquals(List.of("0"), changeCursors);
            assertFalse(new LocalAccessState(file).resynchronizing());
        } finally {
            server.stop(0);
            Files.deleteIfExists(file.toPath());
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Plugin plugin() {
        Logger logger = Logger.getLogger("authbridge-test");
        return (Plugin)
                Proxy.newProxyInstance(
                        Plugin.class.getClassLoader(),
                        new Class<?>[] {Plugin.class},
                        (proxy, method, arguments) ->
                                "getLogger".equals(method.getName()) ? logger : null);
    }
}
