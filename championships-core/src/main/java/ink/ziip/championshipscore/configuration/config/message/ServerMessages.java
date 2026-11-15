package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Server domain; persisted by MessageConfig. */
public final class ServerMessages {
    private ServerMessages() {}

    @ConfigOption(path = "server-full")
    public static String SERVER_FULL;

    @ConfigOption(path = "no-permission")
    public static String NO_PERMISSION;

    @ConfigOption(path = "server-mode-switched")
    public static String SERVER_MODE_SWITCHED;
}
