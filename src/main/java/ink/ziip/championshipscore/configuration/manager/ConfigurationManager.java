package ink.ziip.championshipscore.configuration.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;

/**
 * Modified under <a href="https://github.com/AlessioDP/ADP-Core">ADP-Core</a>
 *
 * @author AlessioDP
 */
public class ConfigurationManager extends BaseConfigurationManager {

    public ConfigurationManager(ChampionshipsCore plugin) {
        super(plugin);
        getConfigs().add(new CCConfig(plugin));
        getConfigs().add(new MessageConfig(plugin));
        getConfigs().add(new ScheduleMessageConfig(plugin));
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

    public ScheduleMessageConfig getScheduleMessageConfig() {
        for (BaseConfigurationFile configurationFile : getConfigs()) {
            if (configurationFile instanceof ScheduleMessageConfig)
                return (ScheduleMessageConfig) configurationFile;
        }
        throw new IllegalStateException("No configuration file found.");
    }
}
