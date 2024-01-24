package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class BingoArea extends BaseTeamArea {
    public BingoArea(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return null;
    }

    @Override
    public String getAreaName() {
        return null;
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
