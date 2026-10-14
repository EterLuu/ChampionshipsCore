package ink.ziip.championshipscore.api.game.spectate;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.snowball.runtime.SnowballShowdownTeamArea;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class SpectatorDestinationTest {
    @Test
    void allGamesPreferLivePlayersAndFallbackAfterTheLastPlayerStopsPlaying() throws Exception {
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        var modes = new HashMap<UUID, GameMode>();
        Player viewer = player("Viewer", 0, modes);
        Player live = player("Live", 10, modes);
        Player other = player("OtherArena", 20, modes);
        Player eliminated = player("Eliminated", 30, modes);
        Player spectator = player("Spectator", 40, modes);
        List<Player> online = List.of(viewer, live, other, eliminated, spectator);
        try {
            serverField.set(
                    null,
                    Proxy.newProxyInstance(
                            Server.class.getClassLoader(),
                            new Class<?>[] {Server.class},
                            (self, method, args) ->
                                    switch (method.getName()) {
                                        case "getOnlinePlayers" -> online;
                                        case "getPlayer" ->
                                                online.stream()
                                                        .filter(
                                                                p ->
                                                                        p.getUniqueId()
                                                                                .equals(args[0]))
                                                        .findFirst()
                                                        .orElse(null);
                                        default -> null;
                                    }));
            var plugin = allocate(ChampionshipsCore.class);
            var game = allocate(GameManager.class);
            var manager = allocate(SpectatorManager.class);
            set(SpectatorManager.class, manager, "gameManager", game);
            set(SpectatorManager.class, manager, "sessions", new HashMap<>());
            set(ChampionshipsCore.class, plugin, "gameManager", game);
            set(GameManager.class, game, "spectatorManager", manager);
            for (var type : GameTypeEnum.values()) {
                var area = allocate(TestArea.class);
                set(BaseGameInstance.class, area, "plugin", plugin);
                set(BaseGameInstance.class, area, "gameTypeEnum", type);
                area.roster = Set.of(live.getUniqueId(), eliminated.getUniqueId());
                set(
                        GameManager.class,
                        game,
                        "playerStatus",
                        Map.of(
                                live.getUniqueId(),
                                area,
                                eliminated.getUniqueId(),
                                area,
                                other.getUniqueId(),
                                allocate(TestArea.class)));
                set(
                        GameManager.class,
                        game,
                        "playerSpectatorStatus",
                        Map.of(viewer.getUniqueId(), area, spectator.getUniqueId(), area));
                modes.put(live.getUniqueId(), GameMode.ADVENTURE);
                modes.put(eliminated.getUniqueId(), GameMode.SPECTATOR);
                assertEquals(live.getLocation(), manager.defaultDestination(viewer, area));
                modes.put(live.getUniqueId(), GameMode.SPECTATOR);
                assertEquals(
                        area.getSpectatorSpawnLocation(), manager.defaultDestination(viewer, area));
            }
        } finally {
            serverField.set(null, previous);
        }
    }

    private static final class TestArea extends SnowballShowdownTeamArea {
        Set<UUID> roster;

        private TestArea() {
            super(null, null);
        }

        @Override
        public boolean notAreaPlayer(Player player) {
            return !roster.contains(player.getUniqueId());
        }

        @Override
        public Location getSpectatorSpawnLocation() {
            return new Location(null, 99, 0, 0);
        }
    }

    private static Player player(String name, int x, Map<UUID, GameMode> modes) {
        UUID id = UUID.randomUUID();
        modes.put(id, GameMode.ADVENTURE);
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (self, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> id;
                                    case "getName" -> name;
                                    case "getLocation" -> new Location(null, x, 0, 0);
                                    case "getGameMode" -> modes.get(id);
                                    case "isDead" -> false;
                                    default -> null;
                                });
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Class<?> type, Object target, String name, Object value)
            throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
