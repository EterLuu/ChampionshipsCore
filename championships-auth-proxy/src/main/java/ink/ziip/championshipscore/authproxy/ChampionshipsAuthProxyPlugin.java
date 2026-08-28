package ink.ziip.championshipscore.authproxy;

import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Makes Bungee's offline login path publish the cc-web identity UUID before IP forwarding.
 * AuthMe remains responsible for password verification once the player reaches a backend.
 */
public final class ChampionshipsAuthProxyPlugin extends Plugin implements Listener {
    private ProxyIdentityClient identities;
    private String notBoundMessage;
    private String bannedMessage;
    private String revokedMessage;
    private String unavailableMessage;
    private String maintenanceMessage;
    private ProxyBanSynchronizer banSynchronizer;
    private ScheduledTask banSynchronizerTask;

    @Override
    public void onEnable() {
        try {
            Configuration config = loadConfiguration();
            identities = new ProxyIdentityClient(
                    config.getString("api.base-url"),
                    config.getString("api.key-id"),
                    config.getString("api.hmac-secret"),
                    config.getBoolean("api.allow-insecure-private-http"),
                    Duration.ofSeconds(config.getLong("api.connect-timeout-seconds", 3)),
                    Duration.ofSeconds(config.getLong("api.request-timeout-seconds", 5))
            );
            notBoundMessage = config.getString("messages.not-bound");
            bannedMessage = config.getString("messages.banned");
            revokedMessage = config.getString("messages.revoked");
            unavailableMessage = config.getString("messages.unavailable");
            maintenanceMessage = config.getString("messages.maintenance");
            banSynchronizer = new ProxyBanSynchronizer(
                    identities,
                    new ProxyBanState(new File(getDataFolder(), "ban-state.properties")),
                    this::disconnectBannedPlayer,
                    getLogger()
            );
            getProxy().getPluginManager().registerListener(this, this);
            long pollSeconds = Math.max(5L, config.getLong("api.poll-seconds", 10));
            getProxy().getScheduler().runAsync(this, banSynchronizer);
            banSynchronizerTask = getProxy().getScheduler().schedule(
                    this, banSynchronizer, pollSeconds, pollSeconds, TimeUnit.SECONDS);
            getLogger().info("Enabled cc-web identity UUID forwarding for offline-mode logins.");
        } catch (Exception exception) {
            getLogger().severe("Could not initialize ChampionshipsAuthProxy: " + exception.getMessage());
            getProxy().stop();
        }
    }

    @Override
    public void onDisable() {
        if (banSynchronizerTask != null) getProxy().getScheduler().cancel(banSynchronizerTask);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(PreLoginEvent event) {
        event.registerIntent(this);
        getProxy().getScheduler().runAsync(this, () -> resolveLoginProfile(event));
    }

    private void resolveLoginProfile(PreLoginEvent event) {
        try {
            ProxyIdentityClient.LoginProfile profile = identities.lookup(event.getConnection().getName());
            switch (profile.status) {
                case "ALLOWED" -> {
                    event.getConnection().setOnlineMode(false);
                    event.getConnection().setUniqueId(UUID.fromString(profile.uuid));
                }
                case "UNBOUND" -> reject(event, notBoundMessage);
                case "BANNED" -> reject(event, bannedMessage(profile.reason, profile.expiresAt));
                case "REVOKED" -> reject(event, revokedMessage);
                case "MAINTENANCE" -> reject(event, maintenanceMessage);
                default -> {
                    getLogger().warning("Unexpected login profile status for " + event.getConnection().getName() + ": " + profile.status);
                    reject(event, unavailableMessage);
                }
            }
        } catch (Exception exception) {
            getLogger().warning("Could not resolve cc-web identity for " + event.getConnection().getName() + ": " + exception.getMessage());
            reject(event, unavailableMessage);
        } finally {
            event.completeIntent(this);
        }
    }

    private Configuration loadConfiguration() throws Exception {
        File directory = getDataFolder();
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        File file = new File(directory, "config.yml");
        if (!file.exists()) {
            try (InputStream input = getResourceAsStream("config.yml")) {
                if (input == null) throw new IllegalStateException("Missing bundled config.yml");
                Files.copy(input, file.toPath());
            }
        }
        return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
    }

    private static void reject(PreLoginEvent event, String message) {
        event.setCancelReason(TextComponent.fromLegacyText(ProxyText.format(message)));
        event.setCancelled(true);
    }

    private void disconnectBannedPlayer(String username, String reason, String expiresAt) {
        String message = bannedMessage(reason, expiresAt);
        for (ProxiedPlayer player : getProxy().getPlayers()) {
            if (player.isConnected() && player.getName().equalsIgnoreCase(username)) {
                player.disconnect(TextComponent.fromLegacyText(ProxyText.format(message)));
            }
        }
    }

    private String bannedMessage(String reason, String expiresAt) {
        return applyPlaceholders(bannedMessage, "reason", reason == null || reason.isBlank() ? "违反服务器规则" : reason,
                "expires", formatExpiry(expiresAt));
    }

    private static String applyPlaceholders(String template, String firstKey, String firstValue,
                                            String secondKey, String secondValue) {
        return (template == null ? "" : template)
                .replace("%" + firstKey + "%", firstValue == null ? "" : firstValue)
                .replace("%" + secondKey + "%", secondValue == null ? "" : secondValue);
    }

    private static String formatExpiry(String value) {
        if (value == null || value.isBlank()) return "请查看账号页面";
        try {
            return DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(value));
        } catch (Exception ignored) {
            return "请查看账号页面";
        }
    }
}
