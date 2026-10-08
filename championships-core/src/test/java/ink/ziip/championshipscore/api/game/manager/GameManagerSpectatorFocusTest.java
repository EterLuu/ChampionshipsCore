package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.laserbox.runtime.LaserBoxArea;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

class GameManagerSpectatorFocusTest {
    @ParameterizedTest
    @EnumSource(GameTypeEnum.class)
    void parallelPreparationsKeepTheActiveFocus(GameTypeEnum game) throws Exception {
        withManager(
                manager -> {
                    for (var stage :
                            List.of(
                                    GameStageEnum.PREPARATION,
                                    GameStageEnum.COUNTDOWN,
                                    GameStageEnum.PROGRESS)) {
                        var current = area(game, stage, GameRunMode.EVENT);
                        set(manager, "spectatorFocus", current);
                        for (int copy = 0; copy < 9; copy++) {
                            manager.onInstancePreparationStarted(
                                    area(game, GameStageEnum.PREPARATION, GameRunMode.EVENT));
                            assertSame(
                                    current,
                                    field(GameManager.class, "spectatorFocus").get(manager));
                        }
                    }
                });
    }

    @Test
    void firstPreparationAdoptsOfflineRoundSpectatorsOnlyOnce() throws Exception {
        withManager(
                manager -> {
                    var old = area(GameTypeEnum.LaserBox, GameStageEnum.WAITING, GameRunMode.EVENT);
                    var first =
                            area(
                                    GameTypeEnum.LaserBox,
                                    GameStageEnum.PREPARATION,
                                    GameRunMode.EVENT);
                    var second =
                            area(
                                    GameTypeEnum.LaserBox,
                                    GameStageEnum.PREPARATION,
                                    GameRunMode.EVENT);
                    UUID spectator = UUID.randomUUID();
                    old.addSpectatorWithoutTeleport(spectator);
                    set(manager, "playerSpectatorStatus", new HashMap<>(Map.of(spectator, old)));
                    set(manager, "spectatorFocus", old);
                    manager.holdSpectatorsForNextRound(old);

                    manager.onInstancePreparationStarted(first);
                    manager.onInstancePreparationStarted(second);

                    assertSame(first, field(GameManager.class, "spectatorFocus").get(manager));
                    assertEquals(Set.of(spectator), first.getSpectatorUniqueIds());
                    assertTrue(old.getSpectatorUniqueIds().isEmpty());
                    assertTrue(second.getSpectatorUniqueIds().isEmpty());
                    assertEquals(
                            Map.of(spectator, first),
                            field(GameManager.class, "playerSpectatorStatus").get(manager));
                    assertEquals(
                            Map.of(),
                            field(GameManager.class, "spectatorTransitionHolds").get(manager));
                });
    }

    @Test
    void endedFocusAndDifferentEventsCanSelectANewFocusButStandaloneGamesCannot() throws Exception {
        withManager(
                manager -> {
                    var ended = area(GameTypeEnum.LaserBox, GameStageEnum.END, GameRunMode.EVENT);
                    var next =
                            area(
                                    GameTypeEnum.LaserBox,
                                    GameStageEnum.PREPARATION,
                                    GameRunMode.EVENT);
                    set(manager, "spectatorFocus", ended);
                    manager.onInstancePreparationStarted(next);
                    assertSame(next, field(GameManager.class, "spectatorFocus").get(manager));

                    var other =
                            area(
                                    GameTypeEnum.BattleBox,
                                    GameStageEnum.PREPARATION,
                                    GameRunMode.EVENT);
                    manager.onInstancePreparationStarted(other);
                    assertSame(other, field(GameManager.class, "spectatorFocus").get(manager));
                    manager.onInstancePreparationStarted(
                            area(
                                    GameTypeEnum.LaserBox,
                                    GameStageEnum.PREPARATION,
                                    GameRunMode.GAME));
                    assertSame(other, field(GameManager.class, "spectatorFocus").get(manager));
                });
    }

    private static BaseGameInstance area(GameTypeEnum game, GameStageEnum stage, GameRunMode mode)
            throws Exception {
        var area = allocate(LaserBoxArea.class);
        set(area, "gameTypeEnum", game);
        set(area, "gameStageEnum", stage);
        set(area, "runMode", mode);
        set(area, "spectators", new HashSet<UUID>());
        return area;
    }

    private interface Action {
        void run(GameManager manager) throws Exception;
    }

    private static void withManager(Action action) throws Exception {
        Field serverField = field(Bukkit.class, "server");
        Object previous = serverField.get(null);
        try {
            Server server =
                    (Server)
                            Proxy.newProxyInstance(
                                    Server.class.getClassLoader(),
                                    new Class<?>[] {Server.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getPlayer" -> null;
                                                case "getOnlinePlayers" -> List.of();
                                                case "isPrimaryThread" -> true;
                                                default ->
                                                        throw new AssertionError(method.getName());
                                            });
            serverField.set(null, server);
            var plugin = allocate(ChampionshipsCore.class);
            var manager = allocate(GameManager.class);
            set(manager, "plugin", plugin);
            for (String name :
                    List.of("areaManagers", "spectatorTransitionHolds", "pendingFinaleAudience"))
                set(manager, name, new HashMap<>());
            set(plugin, "visibilityManager", new PlayerVisibilityManager(plugin));
            action.run(manager);
        } finally {
            serverField.set(null, previous);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
