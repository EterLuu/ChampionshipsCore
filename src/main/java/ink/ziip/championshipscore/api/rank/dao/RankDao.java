package ink.ziip.championshipscore.api.rank.dao;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;

import java.util.List;
import java.util.UUID;

public interface RankDao {

    List<PlayerPointEntry> getPlayerPoints(UUID uuid);

    List<PlayerPointEntry> getTeamPlayerPoints(int teamId);

    void addPlayerPoint(PlayerPointEntry playerPointEntry);

    List<GameStatusEntry> getGameStatusList();

    int getGameStatusOrder(GameTypeEnum gameTypeEnum);

    void addGameStatus(GameStatusEntry gameStatusEntry);

    void deleteGameStatus(int id);
}
