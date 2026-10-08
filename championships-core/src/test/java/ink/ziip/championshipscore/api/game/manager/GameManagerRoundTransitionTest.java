package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class GameManagerRoundTransitionTest {
    @Test
    void endingScheduleReleasesOfflineRoundHoldsWithoutTouchingOtherGames() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var manager = (GameManager) unsafe.allocateInstance(GameManager.class);
        var plugin = (ChampionshipsCore) unsafe.allocateInstance(ChampionshipsCore.class);
        set(
                ChampionshipsCore.class,
                plugin,
                "visibilityManager",
                new PlayerVisibilityManager(plugin));
        var spectators = new SpectatorManager(plugin, manager);
        set(GameManager.class, manager, "spectatorManager", spectators);
        set(GameManager.class, manager, "playerSpectatorStatus", new HashMap<>());
        var riptide = (RiptideRushArea) unsafe.allocateInstance(RiptideRushArea.class);
        var other = (RiptideRushArea) unsafe.allocateInstance(RiptideRushArea.class);
        set(BaseGameInstance.class, riptide, "gameTypeEnum", GameTypeEnum.RiptideRush);
        set(BaseGameInstance.class, other, "gameTypeEnum", GameTypeEnum.TNTRun);
        var holdType =
                Arrays.stream(GameManager.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("RoundTransitionHold"))
                        .findFirst()
                        .orElseThrow();
        var constructor =
                holdType.getDeclaredConstructor(BaseGameInstance.class, GameRunMode.class);
        constructor.setAccessible(true);
        UUID participant = UUID.randomUUID(), otherParticipant = UUID.randomUUID();
        Map<UUID, Object> holds = new HashMap<>();
        holds.put(participant, constructor.newInstance(riptide, GameRunMode.EVENT));
        holds.put(otherParticipant, constructor.newInstance(other, GameRunMode.EVENT));
        set(GameManager.class, manager, "roundTransitionHolds", holds);
        var sessionType =
                Arrays.stream(SpectatorManager.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("SpectatorSession"))
                        .findFirst()
                        .orElseThrow();
        var sessionConstructor =
                sessionType.getDeclaredConstructor(
                        UUID.class, BaseGameInstance.class, boolean.class);
        sessionConstructor.setAccessible(true);
        Map<UUID, Object> sessions = new HashMap<>();
        sessions.put(participant, sessionConstructor.newInstance(participant, riptide, false));
        sessions.put(
                otherParticipant, sessionConstructor.newInstance(otherParticipant, other, false));
        set(SpectatorManager.class, spectators, "sessions", sessions);
        Map<UUID, Boolean> originalPhysics = new HashMap<>(Map.of(participant, false));
        set(SpectatorManager.class, spectators, "participantNoPhysics", originalPhysics);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        try {
            serverField.set(
                    null,
                    Proxy.newProxyInstance(
                            Server.class.getClassLoader(),
                            new Class<?>[] {Server.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("getPlayer")) return null;
                                throw new UnsupportedOperationException(method.getName());
                            }));
            assertTrue(manager.isWaitingForNextRound(participant));
            assertSame(riptide, manager.getNextRoundHoldArea(participant));
            assertTrue(spectators.isSpectatorLike(participant));
            manager.releaseRoundTransitionHolds(GameTypeEnum.RiptideRush);
            assertFalse(manager.isWaitingForNextRound(participant));
            assertFalse(spectators.isSpectatorLike(participant));
            assertTrue(spectators.isSpectatorLike(otherParticipant));
            assertEquals(Map.of(participant, false), originalPhysics);
            assertTrue(manager.isWaitingForNextRound(otherParticipant));
            assertDoesNotThrow(() -> manager.releaseRoundTransitionHolds(GameTypeEnum.RiptideRush));
        } finally {
            serverField.set(null, previous);
        }
    }

    private static void set(Class<?> type, Object target, String name, Object value)
            throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
