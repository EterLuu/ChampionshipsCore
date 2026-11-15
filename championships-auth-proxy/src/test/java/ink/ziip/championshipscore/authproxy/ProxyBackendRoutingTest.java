package ink.ziip.championshipscore.authproxy;

import static org.junit.jupiter.api.Assertions.*;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class ProxyBackendRoutingTest {
    @Test
    void discardsStartupIpAndKeepsHostnamePortAndServerMetadata() throws Exception {
        var oldAddress =
                new InetSocketAddress(
                        InetAddress.getByAddress("bingo-container", new byte[] {127, 0, 0, 2}),
                        25566);
        var original = server("bingo", oldAddress, List.of());
        var untouched = server("lobby", new InetSocketAddress("127.0.0.1", 25565), List.of());
        Map<String, ServerInfo> servers =
                new HashMap<>(Map.of("bingo", original, "lobby", untouched));
        AtomicInteger replacements = new AtomicInteger();
        ProxyBackendRouting.configure(
                servers,
                List.of("bingo", "bingo"),
                (previous, address) -> {
                    replacements.incrementAndGet();
                    assertSame(original, previous);
                    assertEquals("configured motd", previous.getMotd());
                    assertTrue(previous.isRestricted());
                    return server(previous.getName(), address, List.of());
                });

        var address = (InetSocketAddress) servers.get("bingo").getSocketAddress();
        assertTrue(address.isUnresolved());
        assertNull(address.getAddress(), "Must not retain the IP from the old container");
        assertEquals("bingo-container", address.getHostString());
        assertEquals(25566, address.getPort());
        assertSame(untouched, servers.get("lobby"));
        assertEquals(1, replacements.get());
        var canonical = servers.get("bingo");
        ProxyBackendRouting.configure(
                servers, List.of("bingo"), (old, next) -> fail("Already configured"));
        assertSame(canonical, servers.get("bingo"));
    }

    @Test
    void invalidSelectionDoesNotPartiallyChangeRegistry() {
        var original = server("bingo", new InetSocketAddress("127.0.0.2", 25565), List.of());
        Map<String, ServerInfo> servers = new HashMap<>(Map.of("bingo", original));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProxyBackendRouting.configure(
                                servers,
                                List.of("bingo", "missing"),
                                (old, address) -> server(old.getName(), address, List.of())));
        assertSame(original, servers.get("bingo"));
    }

    @Test
    void doesNotReplaceServerOwnedByOnlinePlayers() {
        ProxiedPlayer player =
                (ProxiedPlayer)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ProxiedPlayer.class},
                                (proxy, method, args) -> null);
        var original = server("bingo", new InetSocketAddress("127.0.0.2", 25565), List.of(player));
        Map<String, ServerInfo> servers = new HashMap<>(Map.of("bingo", original));
        assertThrows(
                IllegalStateException.class,
                () ->
                        ProxyBackendRouting.configure(
                                servers,
                                List.of("bingo"),
                                (old, address) -> fail("Must preserve live server ownership")));
        assertSame(original, servers.get("bingo"));
    }

    @Test
    void emptyConfigurationPreservesAllBackends() {
        var original = server("bingo", new InetSocketAddress("127.0.0.2", 25565), List.of());
        Map<String, ServerInfo> servers = new HashMap<>(Map.of("bingo", original));
        ProxyBackendRouting.configure(servers, List.of(), (old, address) -> fail("Disabled"));
        assertSame(original, servers.get("bingo"));
    }

    private static ServerInfo server(
            String name, SocketAddress address, List<ProxiedPlayer> players) {
        return (ServerInfo)
                Proxy.newProxyInstance(
                        ProxyBackendRoutingTest.class.getClassLoader(),
                        new Class<?>[] {ServerInfo.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> name;
                                    case "getSocketAddress", "getAddress" -> address;
                                    case "getPlayers" -> players;
                                    case "getMotd" -> "configured motd";
                                    case "isRestricted" -> true;
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
