package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class BingoTeamArea extends BaseSingleTeamArea {
    public BingoTeamArea(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void startGamePreparation() {

    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    @Override
    public String getAreaName() {
        return "bingo";
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {

    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {

    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {

    }
}
