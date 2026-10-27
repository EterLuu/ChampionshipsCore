package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class HotyCodyDuskyCountdownQuitTest {
    @Test
    void countdownQuitExcludesThePlayerOnceWithoutAwardingPreMatchSurvivalPoints()
            throws Exception {
        var area = allocate(HotyCodyDuskyTeamArea.class);
        var plugin = allocate(ChampionshipsCore.class);
        var teams = allocate(TeamManager.class);
        set(teams, "cachedTeams", new ConcurrentHashMap<>());
        set(teams, "transientTeamByPlayer", new ConcurrentHashMap<>());
        set(plugin, "teamManager", teams);
        set(area, "plugin", plugin);
        UUID id = UUID.randomUUID(), other = UUID.randomUUID();
        var team = new ChampionshipTeam(1, "red", "red", "", Set.of(id), null) {};
        set(teams, "cachedTeams", new ConcurrentHashMap<>(Map.of("red", team)));
        set(area, "teamDeathPlayers", new HashMap<>());
        set(area, "gamePlayers", new ArrayList<>(List.of(id, other)));
        set(area, "deathPlayer", new ArrayList<>());
        set(area, "playerDeadTimes", new HashMap<>());
        set(area, "playerPoints", new HashMap<>());
        set(area, "gameStageEnum", GameStageEnum.COUNTDOWN);
        Player player = proxy(Player.class, (p, m, a) -> id);
        var event = new PlayerQuitEvent(player, (String) null);
        area.handlePlayerQuit(event);
        area.handlePlayerQuit(event);
        assertEquals(List.of(id), area.getDeathPlayer());
        assertEquals(1, area.getSurvivedPlayerNums());
        assertEquals(Map.of(team, 1), field(area.getClass(), "teamDeathPlayers").get(area));
        assertTrue(((Map<?, ?>) field(area.getClass(), "playerDeadTimes").get(area)).isEmpty());
        assertTrue(((Map<?, ?>) field(area.getClass(), "playerPoints").get(area)).isEmpty());
    }
}
