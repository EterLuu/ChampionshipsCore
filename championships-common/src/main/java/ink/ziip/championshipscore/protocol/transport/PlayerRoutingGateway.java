package ink.ziip.championshipscore.protocol.transport;

import ink.ziip.championshipscore.protocol.PlayerRoute;

import java.util.concurrent.CompletionStage;

/** Proxy-neutral player transfer port implemented by Bukkit plugin messages or a native proxy adapter. */
@FunctionalInterface
public interface PlayerRoutingGateway {
    CompletionStage<RouteReceipt> route(PlayerRoute route);
}
