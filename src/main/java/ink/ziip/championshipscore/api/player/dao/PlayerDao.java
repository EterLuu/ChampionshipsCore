package ink.ziip.championshipscore.api.player.dao;

import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface PlayerDao {
    void addPlayer(@NotNull String name, @NotNull UUID uuid);

    @Nullable
    PlayerEntry getPlayer(UUID uuid);

    @Nullable
    PlayerEntry getPlayer(String name);
}
