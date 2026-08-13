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

/**
 * Modified under <a href="https://github.com/AlessioDP/ADP-Core">ADP-Core</a>
 * @author AlessioDP
 */
@Getter
public abstract class BaseConfigurationManager extends BaseManager {
    private final List<BaseConfigurationFile> configs = new ArrayList<>();

    public BaseConfigurationManager(@NotNull ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        reload();
    }

    @Override
    public void unload() {
    }

    public boolean reload() {
        Map<BaseConfigurationFile, String> snapshots = new IdentityHashMap<>();
        for (BaseConfigurationFile configurationFile : configs)
            snapshots.put(configurationFile, configurationFile.captureRuntimeConfiguration());

        // Each file is migrated before any of its disk values are validated or exposed to runtime.
        for (BaseConfigurationFile baseConfigurationFile : configs) {
            if (baseConfigurationFile.initializeConfigurationChecked(plugin.getFolder(), isAutoUpgradeEnabled()))
                continue;
            snapshots.forEach(BaseConfigurationFile::restoreRuntimeConfiguration);
            return false;
        }
        return true;
    }

    /**
     * Is the automatic upgrade of configs enabled?
     *
     * @return true if enabled
     */
    protected abstract boolean isAutoUpgradeEnabled();
}
