package ink.ziip.championshipscore.api.game.setup;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A map definition being edited. It owns configuration and world identity; the manager is used only as a
 * transition bridge for idle checks and template persistence, never as the setup target itself.
 */
public record MapSetupTarget(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum gameType,
                             @NotNull String name, @NotNull BaseGameConfig config,
                             @NotNull String worldName,
                             @NotNull BaseGameInstanceManager<?> manager) implements SetupTarget {
    @Override
    public boolean canSaveMap() {
        return manager.canEditMap(name);
    }

    @Override
    public boolean saveMap(@NotNull World.Environment environment) {
        return manager.saveSetupMap(name, environment);
    }
}
