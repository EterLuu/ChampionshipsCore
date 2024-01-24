package ink.ziip.championshipscore.configuration.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

/*
 * Modified under https://github.com/AlessioDP/ADP-Core
 * Author: AlessioDP
 */
public class ConfigurationManager extends BaseConfigurationManager {

    public ConfigurationManager(ChampionshipsCore plugin) {
        super(plugin);
        getConfigs().add(new CCConfig(plugin));
        getConfigs().add(new MessageConfig(plugin));
    }

    @Override
    protected boolean isAutoUpgradeEnabled() {
        return true;
    }

    public CCConfig getCCConfig() {
        for (BaseConfigurationFile configurationFile : getConfigs()) {
            if (configurationFile instanceof CCConfig)
                return (CCConfig) configurationFile;
        }
        throw new IllegalStateException("No configuration file found.");
    }

    public MessageConfig getMessageConfig() {
        for (BaseConfigurationFile configurationFile : getConfigs()) {
            if (configurationFile instanceof MessageConfig)
                return (MessageConfig) configurationFile;
        }
        throw new IllegalStateException("No configuration file found.");
    }
}
