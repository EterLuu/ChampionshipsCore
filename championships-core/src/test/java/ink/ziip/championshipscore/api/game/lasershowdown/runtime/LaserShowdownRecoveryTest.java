package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponSelection;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponState;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

class LaserShowdownRecoveryTest {
    @Test
    void boundaryReturnPreservesHealthAmmoProtectionAndLife() throws Exception {
        try (Fixture f = new Fixture()) {
            f.area.returnToArena(f.player);
            assertEquals(1, f.area.teleports);
            f.assertPreserved();
        }
    }

    @Test
    void directRespawnCannotRefreshALivingPlayerOrSkipDeathWait() throws Exception {
        try (Fixture f = new Fixture()) {
            f.area.respawnPlayer(f.player);
            assertEquals(0, f.area.teleports);
            f.assertPreserved();
            f.waiting.put(f.id, 180);
            f.mode = GameMode.SPECTATOR;
            f.area.respawnPlayer(f.player);
            assertEquals(180, f.waiting.get(f.id));
            assertEquals(GameMode.SPECTATOR, f.mode);
            assertEquals(0, f.area.teleports);
            f.assertPreserved();
        }
    }

    @Test
    void spectatorsDisposedEndedAndReplacedConnectionsCannotReturn() throws Exception {
        for (String state : List.of("spectator", "disposed", "ended", "connection", "waiting")) {
            try (Fixture f = new Fixture()) {
                switch (state) {
                    case "spectator" -> f.mode = GameMode.SPECTATOR;
                    case "disposed" -> set(f.area, "disposed", true);
                    case "ended" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                    case "connection" -> f.connected = false;
                    case "waiting" -> f.waiting.put(f.id, 180);
                }
                f.area.returnToArena(f.player);
                f.area.respawnPlayer(f.player);
                assertEquals(0, f.area.teleports, state);
                f.assertPreserved();
            }
        }
    }

    @Test
    void menuAdmissionRequiresTheOriginalConnectionRunAndLife() throws Exception {
        var select =
                LaserShowdownTeamArea.class.getDeclaredMethod(
                        "canSelectGun", Player.class, long.class, LaserWeaponSelection.class);
        select.setAccessible(true);
        for (String state :
                List.of(
                        "spectator",
                        "disposed",
                        "ended",
                        "connection",
                        "new-run",
                        "new-life",
                        "introduction")) {
            try (Fixture f = new Fixture()) {
                assertEquals(true, select.invoke(f.area, f.player, 0L, f.life));
                switch (state) {
                    case "spectator" -> f.mode = GameMode.SPECTATOR;
                    case "disposed" -> set(f.area, "disposed", true);
                    case "ended" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                    case "connection" -> f.connected = false;
                    case "new-run" -> set(f.area, "startGeneration", 1L);
                    case "new-life" -> f.lives.put(f.id, new LaserWeaponSelection(100));
                    case "introduction" -> set(f.area, "introductionPhase", true);
                }
                assertEquals(false, select.invoke(f.area, f.player, 0L, f.life), state);
            }
        }
    }

    @Test
    void menuGraceAndOldCloseCannotAffectTheNextLife() throws Exception {
        try (Fixture f = new Fixture()) {
            var select =
                    LaserShowdownTeamArea.class.getDeclaredMethod(
                            "canSelectGun", Player.class, long.class, LaserWeaponSelection.class);
            select.setAccessible(true);
            assertTrue(f.life.openMenu(159));
            set(f.area, "laserTick", 200);
            assertEquals(true, select.invoke(f.area, f.player, 0L, f.life));
            var next = new LaserWeaponSelection(200);
            assertTrue(next.openMenu(201));
            f.lives.put(f.id, next);
            var close =
                    LaserShowdownTeamArea.class.getDeclaredMethod(
                            "closeGunMenu", Player.class, long.class, LaserWeaponSelection.class);
            close.setAccessible(true);
            close.invoke(f.area, f.player, 0L, f.life);
            assertTrue(next.canSelect(400), "old close must not remove the new menu's grace");
            assertEquals(false, select.invoke(f.area, f.player, 0L, f.life));
        }
    }

    private static class Area extends LaserShowdownTeamArea {
        int teleports;

        private Area() {
            super(null, null);
        }

        @Override
        public synchronized void teleportPlayerToSpawnLocation(Player player) {
            teleports++;
        }
    }

    private static class Fixture implements AutoCloseable {
        final Object oldServer;
        final UUID id = UUID.randomUUID();
        final Area area;
        final Player player;
        final Map<UUID, Integer> waiting = new HashMap<>();
        final Map<UUID, Integer> protection = new HashMap<>();
        final Map<UUID, Double> health = new HashMap<>();
        final LaserWeaponSelection life = new LaserWeaponSelection(0);
        final Map<UUID, LaserWeaponSelection> lives = new HashMap<>();
        final LaserWeaponState weapons = new LaserWeaponState();
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
                                                case "setVelocity", "setFallDistance" -> null;
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected recovery mutation: "
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
                                                case "getPlayer" -> connected ? player : null;
                                                default -> throw new AssertionError(m.getName());
                                            }));
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "loaded", true);
            var manager = allocate(GameManager.class);
            set(plugin, "gameManager", manager);
            set(manager, "playerSpectatorStatus", new java.util.concurrent.ConcurrentHashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            area = allocate(Area.class);
            set(area, "plugin", plugin);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "gamePlayers", List.of(id));
            set(
                    manager,
                    "playerStatus",
                    new java.util.concurrent.ConcurrentHashMap<>(Map.of(id, area)));
            set(area, "playerRespawnTick", waiting);
            set(area, "playerProtectionUntil", protection);
            set(area, "playerLaserHealth", health);
            set(area, "playerLives", lives);
            set(area, "weapons", weapons);
            set(area, "laserTick", 100);
            protection.put(id, 140);
            health.put(id, 37D);
            lives.put(id, life);
            weapons.setAmmo(id, 3);
        }

        void assertPreserved() {
            assertEquals(Map.of(id, 140), protection);
            assertEquals(Map.of(id, 37D), health);
            assertEquals(3, weapons.ammo(id, LaserGun.RIFLE));
            assertSame(life, lives.get(id));
        }

        @Override
        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass())
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    private static <T> T allocate(Class<T> t) throws Exception {
        return t.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(t));
    }
}
