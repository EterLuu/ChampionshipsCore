package ink.ziip.championshipscore.api.game.frostbite.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.frostbite.mechanics.FrostbiteSupplyHints;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

class FrostbiteRecoveryTest {
    @Test
    void nonFormalDisconnectStillSitsOutTheRound() throws Exception {
        for (var stage :
                List.of(
                        GameStageEnum.PREPARATION,
                        GameStageEnum.COUNTDOWN,
                        GameStageEnum.PROGRESS)) {
            try (var f = new Fixture()) {
                set(f.area, "gameStageEnum", stage);
                f.connected = false;
                f.area.handlePlayerQuit(new PlayerQuitEvent(f.player, (String) null));
                f.area.handlePlayerQuit(new PlayerQuitEvent(f.player, (String) null));
                assertFalse(f.round.active(f.id), stage.name());
                assertFalse(f.round.freeze(f.other, f.id, 0, 100), stage.name());
                assertFalse(f.round.freeze(f.id, f.other, 0, 100), stage.name());
                assertTrue(f.round.kills().isEmpty());
                assertTrue(new FrostbiteRound(f.teams, 1).active(f.id));
            }
        }
    }

    @Test
    void formalDisconnectKeepsSeatAndAllowsSameRoundRecoveryInEveryPreparedPhase()
            throws Exception {
        for (var stage :
                List.of(
                        GameStageEnum.PREPARATION,
                        GameStageEnum.COUNTDOWN,
                        GameStageEnum.PROGRESS)) {
            try (var f = new Fixture()) {
                set(f.area, "runMode", GameRunMode.EVENT);
                set(f.area, "gameStageEnum", stage);
                f.round.heat(f.id, 60);
                f.connected = false;
                f.area.handlePlayerQuit(new PlayerQuitEvent(f.player, (String) null));
                f.area.handlePlayerQuit(new PlayerQuitEvent(f.player, (String) null));
                assertFalse(f.round.active(f.id));
                assertTrue(f.round.reconnect(f.id), stage.name());
                assertTrue(f.round.active(f.id));
                assertTrue(f.round.heated(f.id, 59));
                assertFalse(f.round.heated(f.id, 60));
            }
        }
    }

    @Test
    void formalRoundContinuesWhenEveryoneIsTemporarilyDisconnected() throws Exception {
        try (var f = new Fixture()) {
            set(f.area, "runMode", GameRunMode.EVENT);
            f.connected = false;
            Method tickRound = FrostbiteArea.class.getDeclaredMethod("tickRound");
            tickRound.setAccessible(true);
            tickRound.invoke(f.area);
            assertEquals(GameStageEnum.PROGRESS, f.area.getGameStageEnum());
            assertFalse(f.round.active(f.id));
            assertFalse(f.round.active(f.other));
            assertTrue(f.round.reconnect(f.id));
            assertTrue(f.round.reconnect(f.other));
        }
    }

    @Test
    void sharedSpectatorIdentityBlocksGameplayEvenWithAdventureMode() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.area.playing(f.player));
            f.external.put(f.id, f.area);
            assertFalse(f.area.playing(f.player));
            f.external.clear();
            f.mode = GameMode.SPECTATOR;
            assertFalse(f.area.playing(f.player));
            f.mode = GameMode.ADVENTURE;
            assertTrue(f.area.playing(f.player));
            f.round.leave(f.id);
            assertFalse(f.area.playing(f.player));
        }
    }

    @Test
    void obsoleteJoinCannotTouchOldRoundOrPlayer() throws Exception {
        for (String state :
                List.of("disconnected", "disposed", "ended", "waiting", "unloaded", "new-area")) {
            try (var f = new Fixture()) {
                switch (state) {
                    case "disconnected" -> f.connected = false;
                    case "disposed" -> set(f.area, "disposed", true);
                    case "ended" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                    case "waiting" -> set(f.area, "gameStageEnum", GameStageEnum.WAITING);
                    case "unloaded" -> set(f.plugin, "loaded", false);
                    case "new-area" -> f.owners.clear();
                }
                f.area.handlePlayerJoin(new PlayerJoinEvent(f.player, (String) null));
                assertTrue(f.round.active(f.id), state);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object oldServer;
        final UUID id = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        final List<List<UUID>> teams = List.of(List.of(id), List.of(other));
        final FrostbiteRound round = new FrostbiteRound(teams, 0);
        final FrostbiteArea area = allocate(FrostbiteArea.class);
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final Map<UUID, FrostbiteArea> owners = new HashMap<>();
        final Map<UUID, FrostbiteArea> external = new HashMap<>();
        final Player player;
        GameMode mode = GameMode.ADVENTURE;
        boolean connected = true;

        Fixture() throws Exception {
            oldServer = field(Bukkit.class, "server").get(null);
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (p, m, a) ->
                                            switch (m.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getGameMode" -> mode;
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected player mutation: "
                                                                        + m.getName());
                                            });
            field(Bukkit.class, "server")
                    .set(
                            null,
                            Proxy.newProxyInstance(
                                    Server.class.getClassLoader(),
                                    new Class<?>[] {Server.class},
                                    (p, m, a) ->
                                            switch (m.getName()) {
                                                case "getPlayer" ->
                                                        connected && id.equals(a[0])
                                                                ? player
                                                                : null;
                                                default -> throw new AssertionError(m.getName());
                                            }));
            set(plugin, "loaded", true);
            var manager = allocate(GameManager.class);
            set(plugin, "gameManager", manager);
            set(manager, "playerStatus", owners);
            set(manager, "playerSpectatorStatus", external);
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(area, "plugin", plugin);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "runMode", GameRunMode.GAME);
            set(area, "gamePlayers", List.of(id, other));
            set(area, "round", round);
            owners.put(id, area);
            for (String name :
                    List.of(
                            "freezeLocations",
                            "invisibleUntil",
                            "collisionBefore",
                            "meleeCooldown",
                            "useCooldown",
                            "fullPickupHintAt",
                            "avalancheClouds")) set(area, name, new HashMap<>());
            set(area, "props", new ArrayList<>());
            set(area, "shots", new HashMap<>());
            set(area, "zones", new ArrayList<>());
            set(area, "supplyHints", new FrostbiteSupplyHints());
        }

        @Override
        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        field(object.getClass(), name).set(object, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
