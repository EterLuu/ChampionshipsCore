package ink.ziip.championshipscore.authbridge.bridge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeUuidResolverTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolvesOfflineUuidWithTheVanillaAlgorithm() {
        BridgeUuidResolver resolver = new BridgeUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(StandardCharsets.UTF_8)),
                resolver.resolve("Notch", "OFFLINE", null));
    }

    @Test
    void resolvesOnlineUuidOnlyFromTheDeclaredMojangProfile() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/profiles/minecraft/Notch", exchange -> {
            byte[] body = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        BridgeUuidResolver resolver = new BridgeUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/users/profiles/minecraft/");

        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                resolver.resolve("Notch", "ONLINE", null));
    }

    @Test
    void neverFallsBackToOfflineWhenOnlineLookupFails() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/profiles/minecraft/Unknown", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        BridgeUuidResolver resolver = new BridgeUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/users/profiles/minecraft/");

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("Unknown", "ONLINE", null));
    }

    @Test
    void requiresAnExplicitUuidForUuidSource() {
        BridgeUuidResolver resolver = new BridgeUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("Notch", "UUID", null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("Notch", "UUID", "not-a-uuid"));
    }
}
