package ink.ziip.championshipscore.api.game.setup;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** The map/configuration surface editable by setup, independent of a running game instance. */
public interface SetupTarget {
    @NotNull ChampionshipsCore plugin();

    @NotNull GameTypeEnum gameType();

    @NotNull String name();

    @NotNull BaseGameConfig config();

    @NotNull String worldName();

    /** Binds or rebinds this map definition to an already loaded world. */
    boolean bindWorld(@NotNull World world);

    boolean canSaveMap();

    @NotNull CompletableFuture<Boolean> saveMap(@NotNull World.Environment environment);
}
