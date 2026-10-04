package ink.ziip.championshipscore.configuration.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;

import lombok.Getter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
public abstract class BaseConfigurationManager extends BaseManager {
    private final List<BaseConfigurationFile> configs = new ArrayList<>();

    public BaseConfigurationManager(@NotNull ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        if (!reload()) throw new IllegalStateException("Configuration initialization failed");
    }

    @Override
    public void unload() {}

    public boolean reload() {
        Map<BaseConfigurationFile, String> snapshots = new IdentityHashMap<>();
        for (BaseConfigurationFile configurationFile : configs)
            snapshots.put(configurationFile, configurationFile.captureRuntimeConfiguration());

        for (BaseConfigurationFile baseConfigurationFile : configs) {
            if (baseConfigurationFile.initializeConfigurationChecked(plugin.getFolder())) continue;
            snapshots.forEach(BaseConfigurationFile::restoreRuntimeConfiguration);
            return false;
        }
        return true;
    }
}
