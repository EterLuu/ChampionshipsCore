package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Renames formal-event configuration entries with a full in-memory image for rollback. */
final class FormalEventMapRename {
    record State(@NotNull String configuration, boolean changed) {
    }

    private FormalEventMapRename() {
    }

    static @NotNull State rename(@NotNull CCConfig config, @NotNull GameTypeEnum game,
                                 @NotNull String oldName, @NotNull String newName) throws Exception {
        String snapshot = config.captureRuntimeConfiguration();
        if (snapshot == null) throw new IllegalStateException("主配置尚未加载");

        boolean changed = replaceRegistrations(config.getConfiguration(), game, oldName, newName);
        if (changed) {
            config.saveRuntimeConfiguration();
        }
        return new State(snapshot, changed);
    }

    static boolean replaceRegistrations(@NotNull org.bukkit.configuration.file.YamlConfiguration configuration,
                                        @NotNull GameTypeEnum game, @NotNull String oldName,
                                        @NotNull String newName) {
        String path = "formal-events." + game.name() + ".maps";
        List<String> maps = new ArrayList<>(configuration.getStringList(path));
        boolean changed = false;
        for (int index = 0; index < maps.size(); index++) {
            if (!maps.get(index).equalsIgnoreCase(oldName)) continue;
            maps.set(index, newName);
            changed = true;
        }
        if (changed) configuration.set(path, maps);
        return changed;
    }

    static void rollback(@NotNull CCConfig config, @NotNull State state) throws Exception {
        if (!state.changed()) return;
        config.restoreRuntimeConfiguration(state.configuration());
        config.saveRuntimeConfiguration();
    }
}
