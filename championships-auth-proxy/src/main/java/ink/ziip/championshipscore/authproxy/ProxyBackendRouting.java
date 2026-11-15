package ink.ziip.championshipscore.authproxy;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Keeps dynamic backend hostnames unresolved until Netty opens each connection. */
final class ProxyBackendRouting {
    private ProxyBackendRouting() {}

    static void configure(ProxyServer proxy, List<String> names) {
        configure(
                proxy.getServers(),
                names,
                (original, address) ->
                        proxy.constructServerInfo(
                                original.getName(),
                                address,
                                original.getMotd(),
                                original.isRestricted()));
        for (String name : names)
            proxy.getLogger()
                    .info(
                            "Backend DNS will resolve on connection: server="
                                    + name
                                    + " address="
                                    + proxy.getServerInfo(name).getSocketAddress());
    }

    static void configure(
            Map<String, ServerInfo> servers,
            List<String> names,
            BiFunction<ServerInfo, InetSocketAddress, ServerInfo> factory) {
        // Only install at proxy startup, before players, queued messages or backend connections
        // can own these objects. Keep a single canonical ServerInfo throughout that lifetime.
        Map<String, ServerInfo> replacements = new LinkedHashMap<>();
        for (String name : names) {
            ServerInfo original = servers.get(name);
            if (original == null)
                throw new IllegalArgumentException("Unknown dynamic backend server: " + name);
            if (!(original.getSocketAddress() instanceof InetSocketAddress address))
                throw new IllegalArgumentException("Dynamic backend must use TCP: " + name);
            if (address.isUnresolved() || replacements.containsKey(name)) continue;
            if (!original.getPlayers().isEmpty())
                throw new IllegalStateException(
                        "Backend DNS setup requires a full proxy restart: " + name);
            replacements.put(
                    name,
                    factory.apply(
                            original,
                            InetSocketAddress.createUnresolved(
                                    address.getHostString(), address.getPort())));
        }
        // Validate the complete selection before changing the registry.
        servers.putAll(replacements);
    }
}
