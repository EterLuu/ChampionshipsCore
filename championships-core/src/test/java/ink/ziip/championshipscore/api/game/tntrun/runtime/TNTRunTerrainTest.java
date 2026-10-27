package ink.ziip.championshipscore.api.game.tntrun.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;

import org.bukkit.*;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.IntConsumer;

class TNTRunTerrainTest {
    @Test
    void areaUsesOneSamplerAndBatchedEightTickRemoval() throws Exception {
        try (var f = new Fixture()) {
            f.start();
            f.floor.step();
            assertEquals(3, f.floor.reads);
            assertEquals(1, f.floor.schedules);
            f.floor.advance(7);
            assertTrue(f.floor.changes.isEmpty());
            f.floor.step();
            assertEquals(2, f.floor.changes.size());
        }
    }

    @Test
    void stoppedDisposedOrReusedRunCannotReadTheOldPlayersLocation() throws Exception {
        for (String state : List.of("stop", "disposed", "generation", "terrain")) {
            try (var f = new Fixture()) {
                f.start();
                switch (state) {
                    case "stop" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                    case "disposed" -> set(f.area, "disposed", true);
                    case "generation" -> set(f.area, "startGeneration", 2L);
                    case "terrain" -> set(f.area, "terrainGeneration", 2);
                }
                f.floor.advance(9);
                assertEquals(0, f.floor.locationReads, state);
                assertTrue(f.floor.changes.isEmpty(), state);
            }
        }
    }

    @Test
    void terrainCancellationInvalidatesQueuedRemovalBeforeNewRun() throws Exception {
        try (var f = new Fixture()) {
            f.start();
            f.floor.step();
            f.cancel();
            f.start();
            f.floor.advance(8);
            assertTrue(f.floor.changes.isEmpty());
            f.floor.step();
            assertEquals(2, f.floor.changes.size());
        }
    }

    @Test
    void eliminatedAndSpectatingPlayersCannotSampleOrCommitTerrain() throws Exception {
        for (boolean spectator : List.of(false, true)) {
            for (boolean alreadySampled : List.of(false, true)) {
                try (var f = new Fixture()) {
                    f.start();
                    if (alreadySampled) f.floor.advance(1);
                    if (spectator) f.person.mode = GameMode.SPECTATOR;
                    else f.area.getDeathPlayer().add(f.person.id);
                    f.floor.advance(9);
                    assertEquals(alreadySampled ? 1 : 0, f.floor.locationReads);
                    assertTrue(f.floor.changes.isEmpty());
                }
            }
        }
    }

    private static final class Area extends TNTRunTeamArea {
        private Area() {
            super(null, null, false, "fixture");
        }

        @Override
        protected BukkitTask startRemainingTimer(int duration, IntConsumer tick, Runnable end) {
            return null;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final TNTRunFloorTrackerTest.Fixture floor = new TNTRunFloorTrackerTest.Fixture();
        final TNTRunFloorTrackerTest.Person person = floor.add(.5, 70, .5);
        final Area area = allocate(Area.class);

        Fixture() throws Exception {
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "server", floor.server);
            set(plugin, "isEnabled", true);
            var manager = allocate(GameManager.class);
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(plugin, "gameManager", manager);
            field(Bukkit.class, "server").set(null, floor.server);
            set(area, "plugin", plugin);
            set(area, "scheduler", floor.scheduler);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "gamePlayers", new ArrayList<>(List.of(person.id)));
            set(area, "deathPlayer", new ArrayList<>());
            set(area, "rainTnt", new HashSet<>());
            set(area, "gameConfig", allocate(TNTRunConfig.class));
        }

        void start() throws Exception {
            invoke("beginGameProgress");
        }

        void cancel() throws Exception {
            invoke("cancelTerrainTasks");
        }

        void invoke(String name) throws Exception {
            var method = TNTRunTeamArea.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(area);
        }

        public void close() throws Exception {
            cancel();
            field(Bukkit.class, "server").set(null, previous);
        }
    }
}
