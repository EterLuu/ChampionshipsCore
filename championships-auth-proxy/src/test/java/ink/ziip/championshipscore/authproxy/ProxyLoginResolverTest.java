package ink.ziip.championshipscore.authproxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyLoginResolverTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String UUID = "11111111-1111-4111-8111-111111111111";

    @TempDir
    Path tempDirectory;

    @Test
    void usesPersistedAllowedUuidAfterWebBecomesUnavailableAndAfterProxyRestart() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/bridge/login-profile/Player", exchange ->
                respond(exchange, 200, "{\"status\":\"ALLOWED\",\"uuid\":\"" + UUID + "\"}"));
        server.start();
        Path cache = tempDirectory.resolve("persisted.properties");
        ProxyIdentityClient client = client(server.getAddress().getPort());
        ProxyLoginResolver resolver = resolver(client, new ProxyAccessState(cache.toFile()));

        assertEquals(UUID, resolver.lookup("Player").uuid);
        server.stop(0);

        assertEquals(UUID, resolver.lookup("Player").uuid);
        assertEquals(UUID, resolver(client(server.getAddress().getPort()),
                new ProxyAccessState(cache.toFile())).lookup("Player").uuid);
    }

    @Test
    void neverFallsBackForAuthenticationFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/internal/bridge/login-profile/Player", exchange -> {
            if (requests.getAndIncrement() == 0) {
                respond(exchange, 200, "{\"status\":\"ALLOWED\",\"uuid\":\"" + UUID + "\"}");
            } else {
                respond(exchange, 401, "{}");
            }
        });
        server.start();
        try {
            ProxyLoginResolver resolver = resolver(client(server.getAddress().getPort()),
                    new ProxyAccessState(tempDirectory.resolve("unauthorized.properties").toFile()));
            assertEquals(UUID, resolver.lookup("Player").uuid);
            assertThrows(ProxyIdentityClient.BridgeHttpException.class, () -> resolver.lookup("Player"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnknownPlayersWhileWebIsUnavailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        server.stop(0);

        ProxyLoginResolver resolver = resolver(client(port),
                new ProxyAccessState(tempDirectory.resolve("unknown.properties").toFile()));
        assertThrows(IOException.class, () -> resolver.lookup("UnknownPlayer"));
    }

    private static ProxyLoginResolver resolver(ProxyIdentityClient client, ProxyAccessState state) {
        Logger logger = Logger.getLogger("proxy-login-resolver-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return new ProxyLoginResolver(client, state, true, Duration.ZERO, logger);
    }

    private static ProxyIdentityClient client(int port) {
        return new ProxyIdentityClient("http://127.0.0.1:" + port, "proxy-a", SECRET,
                false, Duration.ofMillis(250), Duration.ofMillis(500));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
