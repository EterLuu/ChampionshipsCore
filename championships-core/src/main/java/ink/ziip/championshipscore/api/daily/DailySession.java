package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Match ownership whose active roster may shrink when a player or Party chooses to leave. */
public final class DailySession {
    private final UUID matchId;
    private final GameTypeEnum game;
    private final String map;
    private final BaseGameInstance instance;
    private final List<ChampionshipTeam> teams;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final long startedAtMillis;

    public DailySession(@NotNull UUID matchId, @NotNull GameTypeEnum game, @NotNull String map,
                        @NotNull BaseGameInstance instance, @NotNull List<ChampionshipTeam> teams,
                        @NotNull Set<UUID> players, long startedAtMillis) {
        this.matchId = matchId;
        this.game = game;
        this.map = map;
        this.instance = instance;
        this.teams = List.copyOf(teams);
        this.players.addAll(players);
        this.startedAtMillis = startedAtMillis;
    }

    public UUID matchId() { return matchId; }
    public GameTypeEnum game() { return game; }
    public String map() { return map; }
    public BaseGameInstance instance() { return instance; }
    public List<ChampionshipTeam> teams() { return teams; }
    public Set<UUID> players() { return Set.copyOf(players); }
    public long startedAtMillis() { return startedAtMillis; }
    public void removePlayers(Set<UUID> removed) { players.removeAll(removed); }
    public boolean isEmpty() { return players.isEmpty(); }
}
