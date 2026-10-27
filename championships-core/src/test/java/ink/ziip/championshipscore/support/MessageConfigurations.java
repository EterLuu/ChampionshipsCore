package ink.ziip.championshipscore.support;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

/** Loads resource defaults through the same entry point as plugin configuration startup. */
public final class MessageConfigurations {
    private MessageConfigurations() {}

    public static MessageConfig defaults() throws ReflectiveOperationException {
        var plugin = TestObjects.allocate(ChampionshipsCore.class);
        TestObjects.set(plugin, "classLoader", MessageConfig.class.getClassLoader());
        TestObjects.set(plugin, "logger", java.util.logging.Logger.getAnonymousLogger());
        var config = new MessageConfig(plugin);
        config.loadDefaultOptions();
        return config;
    }
}
