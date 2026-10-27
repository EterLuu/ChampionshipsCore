package ink.ziip.championshipscore.api.game.bingo.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlayerProgressChecks;

import io.papermc.paper.threadedregions.scheduler.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

class BingoProgressCheckTest {
    @Test
    void inventoryEventsCoalesceAndObsoleteRunsDoNotConsumeTheNewCheck() throws Exception {
        try (var f = new Fixture()) {
            f.area.requestProgressCheck(f.player);
            f.area.requestProgressCheck(f.player);
            assertEquals(1, f.tasks.size());
            set(f.area, "round", allocate(BingoRound.class));
            f.area.requestProgressCheck(f.player);
            f.tasks.getFirst().run();
            assertEquals(0, f.area.scans);
            f.area.requestProgressCheck(f.player);
            assertEquals(2, f.tasks.size());
            f.tasks.getLast().run();
            assertEquals(1, f.area.scans);
        }
    }

    @Test
    void queuedChecksRevalidateRoleConnectionGenerationAndArenaLifecycle() throws Exception {
        for (String change :
                List.of("spectator", "disconnect", "new-generation", "reset", "disposed", "end")) {
            try (var f = new Fixture()) {
                f.area.requestProgressCheck(f.player);
                switch (change) {
                    case "spectator" -> f.mode = GameMode.SPECTATOR;
                    case "disconnect" -> f.connected = false;
                    case "new-generation" -> set(f.area, "startGeneration", 1L);
                    case "reset" -> f.area.resetArea();
                    case "disposed" -> set(f.area, "disposed", true);
                    case "end" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                }
                f.tasks.getFirst().run();
                assertEquals(0, f.area.scans, change);
            }
        }
    }

    private static final class Area extends BingoArea {
        int scans;

        private Area() {
            super(null, null);
        }

        @Override
        public void checkPlayerProgress(Player player) {
            scans++;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        final Area area = allocate(Area.class);
        final List<Runnable> tasks = new ArrayList<>();
        final UUID id = UUID.randomUUID();
        final Player player;
        boolean connected = true;
        GameMode mode = GameMode.SURVIVAL;

        Fixture() throws Exception {
            EntityScheduler scheduler =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                @SuppressWarnings("unchecked")
                                var callback = (Consumer<ScheduledTask>) a[1];
                                tasks.add(() -> callback.accept(null));
                                return proxy(ScheduledTask.class, (t, method, args) -> null);
                            });
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" -> mode;
                                        case "isOnline" -> connected;
                                        case "getScheduler" -> scheduler;
                                        default -> throw new AssertionError(m.getName());
                                    });
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                if (m.getName().equals("getPlayer"))
                                    return connected ? player : null;
                                throw new AssertionError(m.getName());
                            });
            field(Bukkit.class, "server").set(null, server);
            Plugin schedulingPlugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isEnabled" -> true;
                                        case "getServer" -> server;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var plugin = allocate(ChampionshipsCore.class);
            var manager = allocate(GameManager.class);
            set(plugin, "loaded", true);
            set(plugin, "gameManager", manager);
            set(manager, "playerStatus", new HashMap<>(Map.of(id, area)));
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(area, "plugin", plugin);
            set(area, "gamePlayers", List.of(id));
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "round", allocate(BingoRound.class));
            set(area, "lastQuitLocations", new HashMap<>());
            set(area, "progressChecks", new PlayerProgressChecks(schedulingPlugin));
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }
}
