package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.platform.bukkit.text.CrossServerChatText;
import ink.ziip.championshipscore.platform.bukkit.text.PlayerPresentation;
import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import ink.ziip.championshipscore.redis.RedisChatTransport;
import ink.ziip.championshipscore.redis.RedisConnectionConfig;
import ink.ziip.championshipscore.redis.RedisConsumerConfig;
import ink.ziip.championshipscore.redis.RedisGroupNames;
import ink.ziip.championshipscore.redis.RedisTransportConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/** Worker-owned global chat bridge; match team chat remains local and private. */
final class WorkerChatService implements AutoCloseable {
    private final Plugin plugin;
    private final WorkerConfig config;
    private final WorkerMatchRegistry registry;
    private final PlatformScheduler scheduler;
    private final RedisChatTransport transport;

    WorkerChatService(Plugin plugin, WorkerConfig config, WorkerMatchRegistry registry) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.scheduler = new PlatformScheduler(plugin);
        RedisTransportConfig redis = config.redis();
        RedisConnectionConfig connection = new RedisConnectionConfig(redis.uri(), redis.namespace(),
                config.workerId(), redis.approximateMaxStreamLength(), redis.commandTimeout());
        RedisConsumerConfig matchConsumer = config.consumer();
        RedisConsumerConfig chatConsumer = new RedisConsumerConfig(
                RedisGroupNames.chat(matchConsumer.group(), config.workerId()),
                config.workerId() + "-chat", 64, matchConsumer.blockTimeout(),
                matchConsumer.reclaimIdle(), matchConsumer.maxDeliveries());
        transport = new RedisChatTransport(connection, chatConsumer, this::receive,
                failure -> plugin.getLogger().log(Level.WARNING,
                        "Redis cross-server chat consumer failure", failure));
    }

    CompletionStage<Void> start() {
        return transport.start();
    }

    void publish(Player player, Component content) {
        PlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        CrossServerChatMessage message = CrossServerChatText.message(config.workerId(), player.getUniqueId(),
                player.getName(), presentation, content, System.currentTimeMillis());
        transport.publish(message).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to publish cross-server chat message " + message.messageId(), failure);
            return null;
        });
    }

    private void receive(CrossServerChatMessage message) {
        final Component line;
        try {
            line = CrossServerChatText.render(message);
        } catch (RuntimeException malformed) {
            plugin.getLogger().log(Level.WARNING,
                    "Rejected malformed cross-server chat component " + message.messageId(), malformed);
            return;
        }
        scheduler.runGlobal(() -> {
            List<Player> players = List.copyOf(plugin.getServer().getOnlinePlayers());
            plugin.getServer().getConsoleSender().sendMessage(line);
            for (Player player : players) scheduler.runEntity(player, () -> player.sendMessage(line));
        });
    }

    @Override
    public void close() {
        transport.close();
    }
}
