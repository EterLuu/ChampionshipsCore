package ink.ziip.championshipscore.api.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat reads an immutable snapshot; mutations become visible only after an atomic durable write.
 */
public final class PublicChatMuteStore {
    private final Path file;
    private final Clock clock;
    private volatile Map<UUID, PublicChatMute> entries = Map.of();

    public PublicChatMuteStore(Path file, Clock clock) {
        this.file = file.toAbsolutePath();
        this.clock = clock;
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(file)) {
            entries = Map.of();
            return;
        }
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (InvalidConfigurationException error) {
            throw new IOException("Invalid mute file", error);
        }
        var loaded = new LinkedHashMap<UUID, PublicChatMute>();
        ConfigurationSection root = yaml.getConfigurationSection("mutes");
        if (yaml.contains("mutes") && root == null) throw new IOException("Invalid mutes section");
        if (root != null)
            for (String key : root.getKeys(false)) {
                try {
                    ConfigurationSection row = root.getConfigurationSection(key);
                    if (row == null) throw new IllegalArgumentException("invalid mute entry");
                    var mute =
                            new PublicChatMute(
                                    UUID.fromString(key),
                                    row.getString("name"),
                                    row.getString("reason"),
                                    row.getString("actor"),
                                    number(row, "created-at"),
                                    number(row, "expires-at"));
                    if (mute.activeAt(clock.millis())) loaded.put(mute.playerId(), mute);
                } catch (RuntimeException error) {
                    throw new IOException("Invalid mute entry: " + key, error);
                }
            }
        entries = Map.copyOf(loaded);
    }

    public PublicChatMute activeMute(UUID playerId) {
        PublicChatMute mute = entries.get(playerId);
        return mute != null && mute.activeAt(clock.millis()) ? mute : null;
    }

    public List<PublicChatMute> activeMutes() {
        long now = clock.millis();
        return entries.values().stream().filter(mute -> mute.activeAt(now)).toList();
    }

    public synchronized PublicChatMute mute(
            UUID playerId, String playerName, long durationMillis, String reason, String actor)
            throws IOException {
        if (durationMillis < 0) throw new IllegalArgumentException("negative duration");
        long now = clock.millis();
        long expiry = durationMillis == 0 ? 0 : Math.addExact(now, durationMillis);
        var mute = new PublicChatMute(playerId, playerName, reason, actor, now, expiry);
        var changed = activeEntries();
        changed.put(playerId, mute);
        persist(changed);
        return mute;
    }

    public synchronized boolean unmute(UUID playerId) throws IOException {
        if (activeMute(playerId) == null) return false;
        var changed = activeEntries();
        changed.remove(playerId);
        persist(changed);
        return true;
    }

    private LinkedHashMap<UUID, PublicChatMute> activeEntries() {
        var active = new LinkedHashMap<UUID, PublicChatMute>();
        activeMutes().forEach(mute -> active.put(mute.playerId(), mute));
        return active;
    }

    private void persist(Map<UUID, PublicChatMute> changed) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.createSection("mutes");
        changed.forEach(
                (id, mute) -> {
                    String path = "mutes." + id + ".";
                    yaml.set(path + "name", mute.playerName());
                    yaml.set(path + "reason", mute.reason());
                    yaml.set(path + "actor", mute.actor());
                    yaml.set(path + "created-at", mute.createdAt());
                    yaml.set(path + "expires-at", mute.expiresAt());
                });
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary =
                parent == null
                        ? Files.createTempFile(".mutes-", ".yml")
                        : Files.createTempFile(parent, ".mutes-", ".yml");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            entries = Map.copyOf(changed);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static long number(ConfigurationSection row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Long) && !(value instanceof Integer))
            throw new IllegalArgumentException("invalid or missing " + key);
        return ((Number) value).longValue();
    }
}
