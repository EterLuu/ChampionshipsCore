package ink.ziip.championshipscore.api.rank.dao;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankDao {

    /** One authoritative snapshot used by the periodic ranking rebuild. */
    Optional<List<PlayerPointEntry>> getAllValidPlayerPoints();

    boolean addPlayerPoint(PlayerPointEntry playerPointEntry);

    /** Commits one settlement atomically; duplicate transaction ids remain successful/idempotent. */
    boolean addPlayerPoints(List<PlayerPointEntry> playerPointEntries);

    /** Empty means the query failed; a present empty list is a valid event with zero rounds. */
    Optional<List<GameStatusEntry>> getGameStatusList();

    int getGameStatusOrder(GameTypeEnum gameTypeEnum);

    void addGameStatus(GameStatusEntry gameStatusEntry);

    void deleteGameStatus(GameTypeEnum gameTypeEnum);

    void deletePlayerPoints(UUID uuid, GameTypeEnum gameTypeEnum);

    void deleteTeamPoints(int teamId, GameTypeEnum gameTypeEnum);

    void deleteGamePoints(GameTypeEnum gameTypeEnum);
}
