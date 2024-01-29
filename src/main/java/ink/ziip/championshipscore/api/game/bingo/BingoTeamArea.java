package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class BingoTeamArea extends BaseSingleTeamArea {

    public BingoTeamArea(ChampionshipsCore plugin, BaseListener gameHandler, BaseGameConfig gameConfig) {
        super(plugin, GameTypeEnum.Bingo, gameHandler, gameConfig);
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

    }
}
