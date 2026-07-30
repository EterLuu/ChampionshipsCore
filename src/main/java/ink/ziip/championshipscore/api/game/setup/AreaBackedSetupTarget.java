package ink.ziip.championshipscore.api.game.setup;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/** Compatibility adapter until map definitions are fully removed from the legacy Area managers. */
public record AreaBackedSetupTarget(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum gameType,
                                    @NotNull String name, @NotNull BaseGameInstance area) implements SetupTarget {
    @Override
    public @NotNull BaseGameConfig config() {
        return area.getGameConfig();
    }

    @Override
    public @NotNull String worldName() {
        return area.getWorldName();
    }

    @Override
    public boolean canSaveMap() {
        return area.canSaveMap();
    }

    @Override
    public boolean saveMap(@NotNull World.Environment environment) {
        return area.saveMap(environment);
    }
}
