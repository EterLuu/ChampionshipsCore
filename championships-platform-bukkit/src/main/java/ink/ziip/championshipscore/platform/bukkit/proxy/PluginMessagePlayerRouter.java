package ink.ziip.championshipscore.platform.bukkit.proxy;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.PlayerRoute;
import ink.ziip.championshipscore.protocol.transport.PlayerRoutingGateway;
import ink.ziip.championshipscore.protocol.transport.RouteReceipt;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** BungeeCord-compatible Connect message; Velocity supports it when its Bungee channel is enabled. */
public final class PluginMessagePlayerRouter implements PlayerRoutingGateway, AutoCloseable {
    public static final String LEGACY_CHANNEL = "BungeeCord";
    public static final String NAMESPACED_CHANNEL = "bungeecord:main";

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final String channel;

    public PluginMessagePlayerRouter(Plugin plugin, String channel) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = new PlatformScheduler(plugin);
        this.channel = requireChannel(channel);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, this.channel);
    }

    @Override
    public CompletionStage<RouteReceipt> route(PlayerRoute route) {
        Objects.requireNonNull(route, "route");
        long now = System.currentTimeMillis();
        if (route.expiredAt(now)) {
            return CompletableFuture.completedFuture(new RouteReceipt(
                    route.playerId(), route.serverName(), false, "route-expired"));
        }
        Player player = plugin.getServer().getPlayer(route.playerId());
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new RouteReceipt(
                    route.playerId(), route.serverName(), false, "player-offline"));
        }
        return route(player, route.serverName());
    }

    /** Direct server transfer for worker admission fallbacks which do not belong to a live match. */
    public CompletionStage<RouteReceipt> route(Player player, String serverName) {
        Objects.requireNonNull(player, "player");
        String target = Objects.requireNonNull(serverName, "serverName");
        if (target.isBlank()) throw new IllegalArgumentException("serverName must not be blank");
        if (!player.isOnline()) {
            return CompletableFuture.completedFuture(new RouteReceipt(
                    player.getUniqueId(), target, false, "player-offline"));
        }
        CompletableFuture<RouteReceipt> result = new CompletableFuture<>();
        scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                result.complete(new RouteReceipt(player.getUniqueId(), target, false, "player-offline"));
                return;
            }
            try {
                player.sendPluginMessage(plugin, channel, connectPayload(target));
                result.complete(new RouteReceipt(player.getUniqueId(), target, true, "plugin-message-sent"));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static byte[] connectPayload(String serverName) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to create in-memory plugin message", impossible);
        }
    }

    private static String requireChannel(String channel) {
        if (!LEGACY_CHANNEL.equals(channel) && !NAMESPACED_CHANNEL.equals(channel)) {
            throw new IllegalArgumentException("Unsupported proxy plugin-message channel " + channel);
        }
        return channel;
    }

    @Override
    public void close() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
    }
}
