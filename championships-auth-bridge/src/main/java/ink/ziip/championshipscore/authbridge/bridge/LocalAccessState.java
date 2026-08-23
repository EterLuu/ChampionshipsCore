package ink.ziip.championshipscore.authbridge.bridge;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalAccessState {
    private final File file;
    private final Map<String, String> whitelist = new ConcurrentHashMap<>();
    private final Map<String, Ban> bans = new ConcurrentHashMap<>();
    private final Map<String, Integer> authVersions = new ConcurrentHashMap<>();
    private volatile String cursor = "0";
    private volatile boolean synchronizedOnce;

    public LocalAccessState(File file) {
        this.file = file;
        load();
    }

    public boolean isWhitelisted(String username) {
        return whitelist.containsKey(normalize(username));
    }

    public Ban activeBan(String username) {
        Ban ban = bans.get(normalize(username));
        if (ban != null && ban.expiresAt() != null && !ban.expiresAt().isAfter(Instant.now())) {
            bans.remove(normalize(username));
            return null;
        }
        return ban;
    }

    public void whitelist(String username, String accountId) {
        whitelist.put(normalize(username), accountId);
    }

    public void revoke(String username) {
        whitelist.remove(normalize(username));
    }

    public void rename(String oldUsername, String newUsername, String accountId) {
        String oldKey = normalize(oldUsername);
        String newKey = normalize(newUsername);
        String existing = whitelist.remove(oldKey);
        if (existing != null) whitelist.put(newKey, accountId == null || accountId.isBlank() ? existing : accountId);
        Ban ban = bans.remove(oldKey);
        if (ban != null) bans.put(newKey, ban);
        Integer version = authVersions.remove(oldKey);
        if (version != null) authVersions.merge(newKey, version, Math::max);
    }

    public void ban(String username, String reason, String expiresAt) {
        bans.put(normalize(username), new Ban(reason == null ? "" : reason, parseInstant(expiresAt)));
    }

    public void unban(String username) {
        bans.remove(normalize(username));
    }

    public int authVersion(String username) {
        return authVersions.getOrDefault(normalize(username), 0);
    }

    public void setAuthVersion(String username, int version) {
        authVersions.merge(normalize(username), version, Math::max);
    }

    public String cursor() {
        return cursor;
    }

    public boolean synchronizedOnce() {
        return synchronizedOnce;
    }

    public synchronized void advance(String cursor) throws IOException {
        this.cursor = cursor;
        this.synchronizedOnce = true;
        save();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        cursor = yaml.getString("cursor", "0");
        synchronizedOnce = yaml.getBoolean("synchronized-once", false);
        var whitelistSection = yaml.getConfigurationSection("whitelist");
        if (whitelistSection != null) for (String key : whitelistSection.getKeys(false)) whitelist.put(key, whitelistSection.getString(key, ""));
        var banSection = yaml.getConfigurationSection("bans");
        if (banSection != null) for (String key : banSection.getKeys(false)) bans.put(key, new Ban(banSection.getString(key + ".reason", ""), parseInstant(banSection.getString(key + ".expires-at"))));
        var versionSection = yaml.getConfigurationSection("auth-versions");
        if (versionSection != null) for (String key : versionSection.getKeys(false)) authVersions.put(key, versionSection.getInt(key));
    }

    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cursor", cursor);
        yaml.set("synchronized-once", synchronizedOnce);
        whitelist.forEach((name, accountId) -> yaml.set("whitelist." + name, accountId));
        bans.forEach((name, ban) -> {
            yaml.set("bans." + name + ".reason", ban.reason());
            yaml.set("bans." + name + ".expires-at", ban.expiresAt() == null ? null : ban.expiresAt().toString());
        });
        authVersions.forEach((name, version) -> yaml.set("auth-versions." + name, version));
        yaml.save(file);
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record Ban(String reason, Instant expiresAt) {
    }
}
