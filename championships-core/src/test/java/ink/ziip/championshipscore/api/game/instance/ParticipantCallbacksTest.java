package ink.ziip.championshipscore.api.game.instance;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ParticipantCallbacksTest {
    @Test
    void validActionRunsOnlyOnce() throws Exception {
        try (var f = new Fixture()) {
            AtomicInteger calls = new AtomicInteger();
            f.area.scheduleGameplayAction(f.player, calls::incrementAndGet, 1);
            f.queue.getFirst().run();
            f.queue.getFirst().run();
            assertEquals(1, calls.get());
        }
    }

    @Test
    void originalConnectionRunAndArenaMustStillOwnThePlayer() throws Exception {
        for (String invalid :
                List.of(
                        "disconnect",
                        "reconnect",
                        "other-area",
                        "new-run",
                        "unload",
                        "dispose",
                        "end",
                        "waiting")) {
            try (var f = new Fixture()) {
                f.area.scheduleParticipantRecovery(f.player, () -> fail(invalid), 1);
                switch (invalid) {
                    case "disconnect" -> f.current = null;
                    case "reconnect" -> f.current = proxy(Player.class, (p, m, a) -> null);
                    case "other-area" -> f.owners.clear();
                    case "new-run" -> set(f.area, "startGeneration", 1L);
                    case "unload" -> set(f.plugin, "loaded", false);
                    case "dispose" -> set(f.area, "disposed", true);
                    case "end" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                    case "waiting" -> set(f.area, "gameStageEnum", GameStageEnum.WAITING);
                }
                f.queue.getFirst().run();
            }
        }
    }

    @Test
    void spectatorCanRecoverButCannotExecuteDelayedGameplay() throws Exception {
        try (var f = new Fixture()) {
            AtomicInteger recoveries = new AtomicInteger();
            f.area.scheduleGameplayAction(f.player, () -> fail("spectator gameplay"), 1);
            f.area.scheduleParticipantRecovery(f.player, recoveries::incrementAndGet, 1);
            f.mode = GameMode.SPECTATOR;
            f.queue.forEach(Runnable::run);
            assertEquals(1, recoveries.get());
        }
    }

    @Test
    void cancelledCallbackCannotConsumeANewRequest() throws Exception {
        try (var f = new Fixture()) {
            f.area.scheduleParticipantRecovery(f.player, () -> fail("cancelled"), 1);
            var callbacks =
                    (ParticipantCallbacks)
                            field(BaseGameInstance.class, "participantCallbacks").get(f.area);
            callbacks.cancel();
            assertEquals(1, f.cancelled);
            AtomicInteger calls = new AtomicInteger();
            f.area.scheduleParticipantRecovery(f.player, calls::incrementAndGet, 1);
            f.queue.forEach(Runnable::run);
            assertEquals(1, calls.get());
        }
    }

    static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        final TNTRunTeamArea area = allocate(TNTRunTeamArea.class);
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final Map<UUID, BaseGameInstance> owners = new HashMap<>();
        final UUID id = UUID.randomUUID();
        final List<Runnable> queue = new ArrayList<>();
        final Player player;
        Player current;
        GameMode mode = GameMode.ADVENTURE;
        int cancelled;

        Fixture() throws Exception {
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" -> mode;
                                        default -> throw new AssertionError(m.getName());
                                    });
            current = player;
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) -> {
                                        if (m.getName().equals("getPlayer")) return current;
                                        throw new AssertionError(m.getName());
                                    }));
            var manager = allocate(GameManager.class);
            set(manager, "playerStatus", owners);
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(plugin, "loaded", true);
            set(plugin, "gameManager", manager);
            set(area, "plugin", plugin);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "gamePlayers", new ArrayList<>(List.of(id)));
            owners.put(id, area);
            set(
                    area,
                    "scheduler",
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                assertEquals("runTaskLater", m.getName());
                                queue.add((Runnable) a[1]);
                                return proxy(
                                        BukkitTask.class,
                                        (t, method, args) ->
                                                switch (method.getName()) {
                                                    case "hashCode" -> System.identityHashCode(t);
                                                    case "equals" -> t == args[0];
                                                    case "cancel" -> {
                                                        cancelled++;
                                                        yield null;
                                                    }
                                                    default ->
                                                            throw new AssertionError(
                                                                    method.getName());
                                                });
                            }));
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }
}
