package ink.ziip.championshipscore.api.game.laserbox.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponSelection;
import ink.ziip.championshipscore.api.game.laserbox.config.LaserBoxConfig;
import ink.ziip.championshipscore.api.game.laserbox.geometry.LaserBoxGeometry;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.*;

class LaserBoxSelectionTest {
    @Test
    void eightSecondAdmissionAndOpenMenuGraceUseTheCurrentLife() throws Exception {
        try (var f = new Fixture()) {
            set(f.round, "tick", 159);
            assertTrue(f.canSelect());
            assertTrue(f.life.openMenu(159));
            set(f.round, "tick", 200);
            assertTrue(f.canSelect());
            f.life.closeMenu(200);
            assertFalse(f.canSelect());
        }
        try (var f = new Fixture()) {
            set(f.round, "tick", 160);
            assertFalse(f.canSelect());
        }
    }

    @Test
    void preparationAllowsSelectionOnLaterRoundsWithoutACombatLife() throws Exception {
        try (var f = new Fixture()) {
            set(f.area, "round", null);
            for (var stage : List.of(GameStageEnum.PREPARATION, GameStageEnum.COUNTDOWN)) {
                set(f.area, "gameStageEnum", stage);
                assertTrue(f.area.isGunSelectionStage());
                assertTrue(f.canSelect(null, null));
            }
        }
    }

    @Test
    void staleConnectionRunRoundLifeAndSpectatorCannotUseAnOldMenu() throws Exception {
        for (String state :
                List.of(
                        "spectator",
                        "disconnect",
                        "new-run",
                        "new-round",
                        "new-life",
                        "disposed",
                        "end",
                        "introduction")) {
            try (var f = new Fixture()) {
                assertTrue(f.canSelect());
                switch (state) {
                    case "spectator" -> f.mode = GameMode.SPECTATOR;
                    case "disconnect" -> f.connected = false;
                    case "new-run" -> set(f.area, "startGeneration", 1L);
                    case "new-round" -> set(f.area, "round", new LaserBoxRound());
                    case "new-life" -> {
                        f.round.disconnect(f.id);
                        set(f.round, "tick", 80);
                        assertTrue(f.round.respawn(f.id));
                    }
                    case "disposed" -> set(f.area, "disposed", true);
                    case "end" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                    case "introduction" -> set(f.area, "introductionPhase", true);
                }
                assertFalse(f.canSelect(), state);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        final LaserBoxArea area = allocate(LaserBoxArea.class);
        final LaserBoxRound round = new LaserBoxRound();
        final UUID id = UUID.randomUUID();
        final LaserWeaponSelection life;
        final Player player;
        GameMode mode = GameMode.ADVENTURE;
        boolean connected = true;

        Fixture() throws Exception {
            World world =
                    proxy(
                            World.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getName" -> "laser";
                                        default -> throw new AssertionError(m.getName());
                                    });
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" -> mode;
                                        case "getLocation" -> new Location(world, 5, 5, 5);
                                        default -> throw new AssertionError(m.getName());
                                    });
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) -> {
                                        if (m.getName().equals("getPlayer"))
                                            return connected ? player : null;
                                        throw new AssertionError(m.getName());
                                    }));
            var plugin = allocate(ChampionshipsCore.class);
            var manager = allocate(GameManager.class);
            set(plugin, "loaded", true);
            set(plugin, "gameManager", manager);
            set(manager, "playerStatus", new HashMap<>(Map.of(id, area)));
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(area, "plugin", plugin);
            var team = allocate(ink.ziip.championshipscore.api.team.ChampionshipTeam.class);
            set(team, "members", Set.of(id));
            set(area, "rightChampionshipTeam", team);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "round", round);
            var config = allocate(LaserBoxConfig.class);
            var configuration = new org.bukkit.configuration.file.YamlConfiguration();
            configuration.set("world-name", "laser");
            set(config, "configuration", configuration);
            set(area, "gameConfig", config);
            var geometry = allocate(LaserBoxGeometry.class);
            set(geometry, "boundaryMin", new Vector(0, 0, 0));
            set(geometry, "boundaryMax", new Vector(10, 10, 10));
            set(area, "geometry", geometry);
            round.beginLife(id);
            life = round.selection(id);
        }

        boolean canSelect() throws Exception {
            return canSelect(round, life);
        }

        boolean canSelect(LaserBoxRound originalRound, LaserWeaponSelection originalLife)
                throws Exception {
            var method =
                    LaserBoxArea.class.getDeclaredMethod(
                            "canSelectGun",
                            Player.class,
                            long.class,
                            LaserBoxRound.class,
                            LaserWeaponSelection.class);
            method.setAccessible(true);
            return (boolean) method.invoke(area, player, 0L, originalRound, originalLife);
        }

        @Override
        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }
}
