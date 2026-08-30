package ink.ziip.championshipscore.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shared validation and deterministic identity helpers for AuthBridge and AuthProxy. */
public final class AuthIdentity {
    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern COMPACT_UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern DASHED_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private AuthIdentity() {
    }

    public static boolean isMinecraftUsername(String username) {
        return username != null && MINECRAFT_USERNAME.matcher(username).matches();
    }

    public static String requireUsername(String username) {
        if (!isMinecraftUsername(username)) throw new IllegalArgumentException("Invalid Minecraft username");
        return username;
    }

    public static String normalizeUsername(String username) {
        return requireUsername(username).toLowerCase(Locale.ROOT);
    }

    public static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
        String compact;
        if (COMPACT_UUID.matcher(value).matches()) compact = value;
        else if (DASHED_UUID.matcher(value).matches()) compact = value.replace("-", "");
        else throw new IllegalArgumentException("Invalid " + field);
        return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
    }

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + requireUsername(username)).getBytes(StandardCharsets.UTF_8));
    }
}
