package ink.ziip.championshipscore.api.game.setup;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A map definition being edited. It owns configuration and world identity while its manager resolves the
 * current representative instance used for idle checks and template persistence.
 */
public record MapSetupTarget(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum gameType,
                             @NotNull String name, @NotNull BaseGameConfig config,
                             @NotNull BaseGameInstanceManager<?> manager) implements SetupTarget {
    @Override
    public @NotNull String worldName() {
        return config.getConfiguredWorld();
    }

    @Override
    public boolean bindWorld(@NotNull World world) {
        return manager.bindMapWorld(name, world);
    }

    @Override
    public boolean canSaveMap() {
        return manager.canEditMap(name);
    }

    @Override
    public boolean saveMap(@NotNull World.Environment environment) {
        ink.ziip.championshipscore.api.game.instance.BaseGameInstance representative = manager.getArea(name);
        return representative != null && representative.saveMap(environment);
    }
}
