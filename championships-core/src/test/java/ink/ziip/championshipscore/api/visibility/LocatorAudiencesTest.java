package ink.ziip.championshipscore.api.visibility;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.hotycodydusky.runtime.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.support.MessageConfigurations;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class LocatorAudiencesTest {
    @ParameterizedTest
    @EnumSource(value = GameTypeEnum.class, names = "HotyCodyDusky", mode = EnumSource.Mode.EXCLUDE)
    void otherGamesAndTheLobbyHaveNoLocatorAudience(GameTypeEnum game) throws Exception {
        try (var f = new Fixture()) {
            var first = allocate(AceRaceArea.class);
            var second = allocate(AceRaceArea.class);
            set(first, "gameTypeEnum", game);
            set(second, "gameTypeEnum", game);
            f.areas.put(f.a, first);
            f.areas.put(f.b, first);
            f.areas.put(f.c, second);
            var result = f.visibility.locatorAudiences();
            assertTrue(result.isEmpty());
            f.areas.clear();
            assertTrue(f.visibility.locatorAudiences().isEmpty());
        }
    }

    @Test
    void hotCodSubfieldsAndUnallocatedParticipantsRemainIsolated() throws Exception {
        try (var f = new Fixture()) {
            var fish = allocate(HotyCodyDuskyTeamArea.class);
            Map<UUID, Integer> allocation = new HashMap<>(Map.of(f.a, 0, f.b, 0, f.c, 1, f.d, 1));
            set(fish, "playerArenas", allocation);
            for (UUID id : f.ids) f.areas.put(id, fish);
            assertEquals(Set.of(f.b), f.visibility.locatorAudiences().get(f.a));
            assertEquals(Set.of(f.d), f.visibility.locatorAudiences().get(f.c));
            allocation.put(f.b, 1);
            assertTrue(f.visibility.locatorAudiences().get(f.a).isEmpty());
            assertEquals(Set.of(f.b, f.d), f.visibility.locatorAudiences().get(f.c));
        }
    }

    @Test
    void spectatorTargetIsExcludedWhileItsOwnViewerStaysInItsInstance() throws Exception {
        try (var f = new Fixture()) {
            var first = f.fish();
            var second = allocate(AceRaceArea.class);
            f.areas.put(f.a, first);
            f.areas.put(f.b, first);
            f.areas.put(f.c, second);
            f.spectating.put(f.d, first);
            f.modes.put(f.b, GameMode.SPECTATOR);
            f.modes.put(f.d, GameMode.SPECTATOR);
            assertTrue(f.visibility.locatorAudiences().get(f.a).isEmpty());
            assertEquals(Set.of(f.a, f.b), f.visibility.locatorAudiences().get(f.d));
            f.modes.put(f.b, GameMode.ADVENTURE);
            assertEquals(Set.of(f.b), f.visibility.locatorAudiences().get(f.a));
        }
    }

    @Test
    void gameplayVisibilityAndDailySessionRulesAreKept() throws Exception {
        try (var f = new Fixture()) {
            var area = f.fish();
            for (UUID id : f.ids) f.areas.put(id, area);
            f.visibility.seeSelf(f.a, "game", "hidden");
            assertTrue(f.visibility.locatorAudiences().get(f.a).isEmpty());
            f.visibility.seePlayers(f.a, Set.of(f.b), "game", "visible list");
            assertEquals(Set.of(f.b), f.visibility.locatorAudiences().get(f.a));
            f.visibility.seeAll(f.a, "game", "restored");
            UUID session = UUID.randomUUID();
            f.visibility.assignSession(f.a, session);
            f.visibility.assignSession(f.b, session);
            f.visibility.assignSession(f.c, UUID.randomUUID());
            assertEquals(Set.of(f.b), f.visibility.locatorAudiences().get(f.a));
            f.visibility.clearAllSessions();
            assertEquals(Set.of(f.b, f.c, f.d), f.visibility.locatorAudiences().get(f.a));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final UUID a = UUID.randomUUID(),
                b = UUID.randomUUID(),
                c = UUID.randomUUID(),
                d = UUID.randomUUID();
        final List<UUID> ids = List.of(a, b, c, d);
        final Map<UUID, Player> players = new HashMap<>();
        final Map<UUID, GameMode> modes = new HashMap<>();
        final Map<UUID, BaseGameInstance> areas = new ConcurrentHashMap<>(),
                spectating = new ConcurrentHashMap<>();
        final PlayerVisibilityManager visibility;

        Fixture() throws Exception {
            MessageConfigurations.defaults();
            for (UUID id : ids) {
                modes.put(id, GameMode.ADVENTURE);
                players.put(
                        id,
                        proxy(
                                Player.class,
                                (p, m, args) ->
                                        switch (m.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getGameMode" -> modes.get(id);
                                            default -> throw new AssertionError(m.getName());
                                        }));
            }
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, args) ->
                                            switch (m.getName()) {
                                                case "getOnlinePlayers" -> players.values();
                                                case "getPlayer" -> players.get(args[0]);
                                                default -> throw new AssertionError(m.getName());
                                            }));
            var plugin = allocate(ChampionshipsCore.class);
            var games = allocate(GameManager.class);
            set(games, "playerStatus", areas);
            set(games, "playerSpectatorStatus", spectating);
            var spectators = allocate(SpectatorManager.class);
            set(spectators, "gameManager", games);
            set(spectators, "sessions", new ConcurrentHashMap<>());
            set(games, "spectatorManager", spectators);
            set(plugin, "gameManager", games);
            var teams = allocate(TeamManager.class);
            set(teams, "transientTeamByPlayer", new ConcurrentHashMap<>());
            set(teams, "cachedTeams", new ConcurrentHashMap<>());
            set(plugin, "teamManager", teams);
            visibility = new PlayerVisibilityManager(plugin);
        }

        HotyCodyDuskyTeamArea fish() throws Exception {
            var fish = allocate(HotyCodyDuskyTeamArea.class);
            Map<UUID, Integer> allocation = new HashMap<>();
            for (UUID id : ids) allocation.put(id, 0);
            set(fish, "playerArenas", allocation);
            return fish;
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previous);
        }
    }
}
