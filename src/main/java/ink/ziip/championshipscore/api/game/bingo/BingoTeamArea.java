package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class BingoTeamArea extends BaseSingleTeamArea {

    public BingoTeamArea(ChampionshipsCore plugin, BaseListener gameHandler, BaseGameConfig gameConfig) {
        super(plugin, GameTypeEnum.Bingo, gameHandler, gameConfig);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return CCConfig.BINGO_SPAWN_LOCATION;
    }

    @Override
    public void endGame() {

    }

    @Override
    public void resetArea() {

    }

    @Override
    public BaseGameConfig getGameConfig() {
        return null;
    }

    @Override
    public BaseListener getGameHandler() {
        return null;
    }

    @Override
    public String getWorldName() {
        return "bingo";
    }

    @Override
    public void startGamePreparation() {

    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {

    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {

    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getBingoManager().isStarted()) {
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SURVIVAL);
            });
        } else {
            player.teleport(CCConfig.BINGO_SPAWN_LOCATION);
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SPECTATOR);
            });
        }
    }
}
