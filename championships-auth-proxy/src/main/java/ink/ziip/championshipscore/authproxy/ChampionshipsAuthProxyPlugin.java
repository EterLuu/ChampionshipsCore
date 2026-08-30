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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private static final String STATE_FILE_NAME = "state.properties";
    private static final String LEGACY_STATE_FILE_NAME = "ban-state.properties";

    private ProxyIdentityClient identities;
    private ProxyLoginResolver loginResolver;
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
            if (migrateLegacyStateFile(getDataFolder())) {
                getLogger().info("Migrated auth proxy state from " + LEGACY_STATE_FILE_NAME
                        + " to " + STATE_FILE_NAME + ".");
            }
            var accessState = new ProxyAccessState(new File(getDataFolder(), STATE_FILE_NAME));
            long maxStaleHours = config.getLong("offline-cache.max-stale-hours", 0);
            if (maxStaleHours < 0) throw new IllegalArgumentException("offline-cache.max-stale-hours must not be negative");
            loginResolver = new ProxyLoginResolver(
                    identities,
                    accessState,
                    config.getBoolean("offline-cache.enabled", true),
                    maxStaleHours == 0 ? Duration.ZERO : Duration.ofHours(maxStaleHours),
                    getLogger()
            );
            banSynchronizer = new ProxyBanSynchronizer(
                    identities,
                    accessState,
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
            ProxyIdentityClient.LoginProfile profile = loginResolver.lookup(event.getConnection().getName());
            switch (profile.status) {
                case "ALLOWED" -> {
                    event.getConnection().setOnlineMode(false);
                    event.getConnection().setUniqueId(UUID.fromString(profile.uuid));
                }
                case "UNBOUND" -> reject(event, "UNBOUND", "Minecraft account is not bound", notBoundMessage);
                case "BANNED" -> reject(event, "BANNED", banLogReason(profile.reason, profile.expiresAt),
                        bannedMessage(profile.reason, profile.expiresAt));
                case "REVOKED" -> reject(event, "REVOKED", "Server access has been revoked", revokedMessage);
                case "MAINTENANCE" -> reject(event, "MAINTENANCE", "Identity service is in maintenance mode",
                        maintenanceMessage);
                default -> reject(event, "UNAVAILABLE", "Unsupported profile status: " + profile.status,
                        unavailableMessage);
            }
        } catch (Exception exception) {
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            reject(event, "UNAVAILABLE", reason, unavailableMessage);
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

    static boolean migrateLegacyStateFile(File dataDirectory) throws IOException {
        File stateFile = new File(dataDirectory, STATE_FILE_NAME);
        File legacyFile = new File(dataDirectory, LEGACY_STATE_FILE_NAME);
        if (stateFile.exists() || !legacyFile.isFile()) return false;
        Files.createDirectories(dataDirectory.toPath());
        try {
            Files.move(legacyFile.toPath(), stateFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(legacyFile.toPath(), stateFile.toPath());
        }
        return true;
    }

    private void reject(PreLoginEvent event, String status, String reason, String message) {
        getLogger().warning(rejectionLog(event.getConnection().getName(), status, reason));
        event.setCancelReason(TextComponent.fromLegacyText(ProxyText.format(message)));
        event.setCancelled(true);
    }

    static String rejectionLog(String username, String status, String reason) {
        return "Rejected login: player=" + sanitizeLogValue(username, "unknown")
                + ", status=" + sanitizeLogValue(status, "UNKNOWN")
                + ", reason=" + sanitizeLogValue(reason, "not provided");
    }

    private static String banLogReason(String reason, String expiresAt) {
        return "banReason=" + sanitizeLogValue(reason, "not provided")
                + ", expiresAt=" + sanitizeLogValue(expiresAt, "not provided");
    }

    private static String sanitizeLogValue(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), 500));
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length() && sanitized.length() < 500; index++) {
            char character = value.charAt(index);
            boolean whitespace = Character.isWhitespace(character) || Character.isISOControl(character);
            if (whitespace) {
                if (!previousWhitespace && sanitized.length() > 0) sanitized.append(' ');
            } else {
                sanitized.append(character);
            }
            previousWhitespace = whitespace;
        }
        String result = sanitized.toString().trim();
        return result.isEmpty() ? fallback : result;
    }

    private void disconnectBannedPlayer(String username, String reason, String expiresAt) {
        String message = bannedMessage(reason, expiresAt);
        for (ProxiedPlayer player : getProxy().getPlayers()) {
            if (player.isConnected() && player.getName().equalsIgnoreCase(username)) {
                getLogger().warning("Disconnected banned player: player="
                        + sanitizeLogValue(player.getName(), "unknown") + ", reason="
                        + banLogReason(reason, expiresAt));
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
