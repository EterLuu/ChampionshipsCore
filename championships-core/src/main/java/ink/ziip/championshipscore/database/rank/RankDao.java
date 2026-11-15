package ink.ziip.championshipscore.database.rank;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public interface RankDao {

    /** One authoritative snapshot used by the periodic ranking rebuild. */
    Optional<List<PlayerPointEntry>> getAllValidPlayerPoints();

    boolean addPlayerPoint(PlayerPointEntry playerPointEntry);

    /**
     * Commits one settlement atomically; duplicate transaction ids remain successful/idempotent.
     */
    boolean addPlayerPoints(List<PlayerPointEntry> playerPointEntries);

    /** Empty means the query failed; a present empty list is a valid event with zero rounds. */
    Optional<List<GameStatusEntry>> getGameStatusList();

    /** Empty means no row; database failures throw and must abort the queued operation. */
    OptionalInt getGameStatusOrder(GameTypeEnum gameTypeEnum);

    void addGameStatus(GameStatusEntry gameStatusEntry);

    /** Resets supported games in one statement; throws if the write fails. */
    void resetGameStatus();

    /** Deletes status and invalidates points in one transaction; throws if it cannot commit. */
    void deleteGameRecords(GameTypeEnum gameTypeEnum);

    void deletePlayerPoints(UUID uuid, GameTypeEnum gameTypeEnum);

    void deleteTeamPoints(int teamId, GameTypeEnum gameTypeEnum);
}
