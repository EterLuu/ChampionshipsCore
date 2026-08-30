package ink.ziip.championshipscore.auth;

/** Selects which platform owns player-facing admission decisions. */
public enum AuthAdmissionOwner {
    PROXY,
    BRIDGE;

    public static AuthAdmissionOwner parse(String value, AuthAdmissionOwner fallback) {
        if (value == null || value.isBlank()) return fallback;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PROXY" -> PROXY;
            case "BRIDGE" -> BRIDGE;
            default -> throw new IllegalArgumentException("Unsupported admission owner: " + value);
        };
    }
}
