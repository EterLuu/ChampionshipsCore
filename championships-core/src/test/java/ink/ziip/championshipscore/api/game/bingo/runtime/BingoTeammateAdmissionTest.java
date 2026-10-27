package ink.ziip.championshipscore.api.game.bingo.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

class BingoTeammateAdmissionTest {
    @Test
    void compassRequiresOriginalRoundTeamConnectionAndUnifiedGameplayAdmission() throws Exception {
        Object previous = TestObjects.field(Bukkit.class, "server").get(null);
        try {
            UUID id = UUID.randomUUID();
            var area = TestObjects.allocate(BingoArea.class);
            var round = TestObjects.allocate(BingoRound.class);
            var team = TestObjects.allocate(ChampionshipTeam.class);
            var members = new HashSet<>(Set.of(id));
            TestObjects.set(team, "members", members);
            var mode = new GameMode[] {GameMode.SURVIVAL};
            var worldName = new String[] {"bingo"};
            boolean[] alive = {true}, connected = {true};
            World world =
                    proxy(
                            World.class,
                            (p, m, a) -> {
                                assertEquals("getName", m.getName());
                                return worldName[0];
                            });
            Player player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" -> mode[0];
                                        case "isOnline" -> true;
                                        case "isDead" -> !alive[0];
                                        case "getWorld" -> world;
                                        default -> throw new AssertionError(m.getName());
                                    });
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                assertEquals("getPlayer", m.getName());
                                return connected[0] ? player : null;
                            });
            TestObjects.field(Bukkit.class, "server").set(null, server);
            var plugin = TestObjects.allocate(ChampionshipsCore.class);
            TestObjects.set(plugin, "loaded", true);
            var games = TestObjects.allocate(GameManager.class);
            TestObjects.set(plugin, "gameManager", games);
            TestObjects.set(games, "playerStatus", new HashMap<>(Map.of(id, area)));
            var spectators =
                    new HashMap<
                            UUID, ink.ziip.championshipscore.api.game.instance.BaseGameInstance>();
            TestObjects.set(games, "playerSpectatorStatus", spectators);
            TestObjects.set(games, "spectatorManager", new SpectatorManager(plugin, games));
            var teams = TestObjects.allocate(TeamManager.class);
            TestObjects.set(plugin, "teamManager", teams);
            var assigned = new java.util.concurrent.ConcurrentHashMap<>(Map.of(id, team));
            TestObjects.set(teams, "transientTeamByPlayer", assigned);
            TestObjects.set(teams, "cachedTeams", new java.util.concurrent.ConcurrentHashMap<>());
            TestObjects.set(area, "plugin", plugin);
            TestObjects.set(area, "gamePlayers", List.of(id));
            TestObjects.set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            TestObjects.set(area, "round", round);
            long generation = area.captureGameGeneration();
            assertTrue(area.canUseTeammateCompass(player, team, round, generation));
            mode[0] = GameMode.SPECTATOR;
            assertFalse(area.canUseTeammateCompass(player, team, round, generation));
            mode[0] = GameMode.SURVIVAL;
            spectators.put(id, area);
            assertFalse(area.canUseTeammateCompass(player, team, round, generation));
            spectators.clear();
            for (String reason :
                    List.of(
                            "round",
                            "generation",
                            "team",
                            "membership",
                            "dead",
                            "reconnect",
                            "world",
                            "end",
                            "disposed")) {
                switch (reason) {
                    case "round" ->
                            TestObjects.set(area, "round", TestObjects.allocate(BingoRound.class));
                    case "generation" -> TestObjects.set(area, "startGeneration", generation + 1);
                    case "team" -> assigned.remove(id);
                    case "membership" -> members.remove(id);
                    case "dead" -> alive[0] = false;
                    case "reconnect" -> connected[0] = false;
                    case "world" -> worldName[0] = "lobby";
                    case "end" -> TestObjects.set(area, "gameStageEnum", GameStageEnum.END);
                    case "disposed" -> TestObjects.set(area, "disposed", true);
                }
                assertFalse(area.canUseTeammateCompass(player, team, round, generation), reason);
                TestObjects.set(area, "round", round);
                TestObjects.set(area, "startGeneration", generation);
                TestObjects.set(area, "gameStageEnum", GameStageEnum.PROGRESS);
                TestObjects.set(area, "disposed", false);
                assigned.put(id, team);
                members.add(id);
                alive[0] = true;
                connected[0] = true;
                worldName[0] = "bingo";
            }
        } finally {
            TestObjects.field(Bukkit.class, "server").set(null, previous);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
