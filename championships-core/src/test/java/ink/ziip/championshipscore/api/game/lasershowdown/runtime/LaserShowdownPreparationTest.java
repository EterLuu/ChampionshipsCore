package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.GameInstanceHandler;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class LaserShowdownPreparationTest {
    @Test
    void firstPreparationUsesFifteenSecondsAndOnlyOneEquipmentReset() throws Exception {
        var area = allocate(TestArea.class);
        set(area, "gameStageEnum", GameStageEnum.PREPARATION);
        set(area, "arenaSelection", ArenaSelection.all());
        set(area, "areaLocations", new ArrayList<>(List.of(new ArrayList<>())));
        set(area, "gameTeams", List.of());
        set(area, "gamePlayers", List.of());
        set(area, "playerRespawnLocations", new HashMap<>());
        set(area, "playerSpawnLocation", new HashMap<>());
        Map<UUID, LaserGun> guns = new HashMap<>();
        set(area, "selectedGuns", guns);

        area.startGamePreparation();
        assertEquals(15, area.countdownSeconds);
        assertEquals(GameStageEnum.COUNTDOWN, area.getGameStageEnum());
        assertTrue(field(area.getClass(), "firstRoundGunSelectionWindow").getBoolean(area));
        assertEquals(1, area.equipmentResets);
        assertEquals(1, area.modeChanges);

        guns.put(UUID.randomUUID(), LaserGun.SMG);
        area.startGamePreparation();
        assertEquals(5, area.countdownSeconds);
        assertFalse(field(area.getClass(), "firstRoundGunSelectionWindow").getBoolean(area));
        assertEquals(2, area.equipmentResets);
        assertEquals(LaserGun.SMG, guns.values().iterator().next());
    }

    @Test
    void configuredSpawnListsCanBeShuffledForBothLegacyAndGeneratedMaps() throws Exception {
        withFixture(
                fixture -> {
                    var config = fixture.area.getGameConfig();
                    var doc =
                            (YamlConfiguration)
                                    field(config.getClass(), "configuration").get(config);
                    doc.set(
                            "player-spawn-points.area1",
                            List.of("laser:5:20:5:0:0", "laser:6:20:5:0:0"));
                    config.setPlayerSpawnPoints(doc.getConfigurationSection("player-spawn-points"));
                    set(fixture.area, "gameTeams", List.of());
                    set(fixture.area, "playerRespawnLocations", new HashMap<>());
                    set(fixture.area, "playerSpawnLocation", new HashMap<>());
                    set(fixture.area, "areaLocations", new ArrayList<>());
                    set(fixture.area, "arenaSelection", ArenaSelection.all());
                    var refresh =
                            LaserShowdownTeamArea.class.getDeclaredMethod("refreshArenaLocations");
                    refresh.setAccessible(true);
                    var assign =
                            LaserShowdownTeamArea.class.getDeclaredMethod("assignSpawnLocations");
                    assign.setAccessible(true);
                    for (boolean generated : List.of(true, false)) {
                        if (!generated) config.setCopyLayoutOrigin(null);
                        refresh.invoke(fixture.area);
                        assign.invoke(fixture.area);
                        @SuppressWarnings("unchecked")
                        var arenas =
                                (List<List<Location>>)
                                        field(fixture.area.getClass(), "areaLocations")
                                                .get(fixture.area);
                        assertEquals(generated ? 4 : 1, arenas.size());
                        assertTrue(arenas.stream().allMatch(spawns -> spawns.size() == 2));
                        assertEquals(
                                List.of("laser:5:20:5:0:0", "laser:6:20:5:0:0"),
                                config.templateSpawns());
                    }
                });
    }

    @Test
    void countdownAllowsRepeatedFallingAndWalkingWithinTheAssignedArena() throws Exception {
        withFixture(
                fixture -> {
                    fixture.area.setGameStageEnum(GameStageEnum.COUNTDOWN);
                    Location from = new Location(fixture.world, 5, 20, 5);
                    for (int step = 1; step <= 10; step++) {
                        Location to = new Location(fixture.world, 5 + step / 10D, 20 - step, 5);
                        var event = new PlayerMoveEvent(fixture.player, from, to);
                        fixture.common.handleRoutedPlayerMoveLow(event);
                        fixture.game.handleRoutedPlayerMoveHigh(event);
                        assertFalse(
                                event.isCancelled(), "normal landing must not cause a rollback");
                        assertEquals(to, event.getTo());
                        from = to;
                    }
                    assertEquals(0, fixture.area.teleports);
                    assertEquals(0, fixture.area.respawns);
                });
    }

    @Test
    void introductionOutsideTheAssignedArenaDoesNotLoopBackToItsAirborneViewpoint()
            throws Exception {
        withFixture(
                fixture -> {
                    set(fixture.area, "introductionPhase", true);
                    for (int y = 40; y > 25; y--) {
                        var event =
                                new PlayerMoveEvent(
                                        fixture.player,
                                        new Location(fixture.world, 50, y, 50),
                                        new Location(fixture.world, 50, y - 1, 50));
                        fixture.common.handleRoutedPlayerMoveLow(event);
                        fixture.game.handleRoutedPlayerMoveHigh(event);
                        assertFalse(event.isCancelled());
                    }
                    assertEquals(0, fixture.area.teleports);
                    set(fixture.area, "introductionPhase", false);
                    fixture.game.handleRoutedPlayerMoveHigh(
                            new PlayerMoveEvent(
                                    fixture.player,
                                    new Location(fixture.world, 5, 10, 5),
                                    new Location(fixture.world, 50, 10, 50)));
                    assertEquals(1, fixture.area.teleports);
                });
    }

    @Test
    void leavingTheArenaReturnsPlayersWithoutRespawningDuringCombat() throws Exception {
        withFixture(
                fixture -> {
                    var event =
                            new PlayerMoveEvent(
                                    fixture.player,
                                    new Location(fixture.world, 5, 10, 5),
                                    new Location(fixture.world, 50, 10, 50));
                    fixture.area.setGameStageEnum(GameStageEnum.COUNTDOWN);
                    fixture.common.handleRoutedPlayerMoveLow(event);
                    fixture.game.handleRoutedPlayerMoveHigh(event);
                    assertEquals(1, fixture.area.teleports);
                    fixture.area.setGameStageEnum(GameStageEnum.PROGRESS);
                    fixture.game.handleRoutedPlayerMoveHigh(event);
                    assertEquals(1, fixture.area.respawns);
                });
    }

    private record Fixture(
            TestArea area,
            Player player,
            World world,
            GameInstanceHandler common,
            LaserShowdownHandler game) {}

    private static void withFixture(CheckedConsumer action) throws Exception {
        var serverField = field(Bukkit.class, "server");
        Object previous = serverField.get(null);
        try {
            UUID id = UUID.randomUUID();
            Player player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" -> GameMode.ADVENTURE;
                                        default -> throw new AssertionError(m.getName());
                                    });
            World world =
                    proxy(
                            World.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getName" -> "laser";
                                        case "equals" -> p == a[0];
                                        case "hashCode" -> System.identityHashCode(p);
                                        default -> throw new AssertionError(m.getName());
                                    });
            serverField.set(
                    null,
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> player;
                                        case "getWorld" -> world;
                                        default -> throw new AssertionError(m.getName());
                                    }));
            var plugin = allocate(ChampionshipsCore.class);
            var manager = allocate(GameManager.class);
            set(plugin, "gameManager", manager);
            set(manager, "playerSpectatorStatus", new ConcurrentHashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            var area = allocate(TestArea.class);
            set(area, "plugin", plugin);
            set(area, "gamePlayers", List.of(id));
            set(area, "gameStageEnum", GameStageEnum.PREPARATION);
            var config = allocate(LaserShowdownConfig.class);
            set(config, "configuration", new YamlConfiguration());
            config.bindConfiguredWorld("laser");
            config.setArenaMin(new Vector(0, 0, 0));
            config.setArenaMax(new Vector(19, 29, 19));
            config.setCopyLayoutOrigin(new Vector(0, 0, 0));
            config.setCopyLayoutStep(new Vector(100, 0, 100));
            config.setCopySize(new Vector(20, 30, 20));
            set(area, "gameConfig", config);
            var spawn = new Location(world, 5, 20, 5);
            set(area, "areaLocations", List.of(List.of(spawn)));
            set(area, "playerRespawnLocations", Map.of(id, List.of(spawn)));
            var game = new LaserShowdownHandler(plugin);
            game.setLaserShowdownTeamArea(area);
            action.accept(
                    new Fixture(area, player, world, new GameInstanceHandler(plugin, area), game));
        } finally {
            serverField.set(null, previous);
        }
    }

    private interface CheckedConsumer {
        void accept(Fixture fixture) throws Exception;
    }

    private static class TestArea extends LaserShowdownTeamArea {
        int countdownSeconds, equipmentResets, modeChanges, teleports, respawns;

        private TestArea() {
            super(null, null);
        }

        @Override
        public void setGameStageEnum(GameStageEnum stage) {
            gameStageEnum = stage;
        }

        @Override
        protected void startGameIntroduction(Runnable onComplete) {
            onComplete.run();
        }

        @Override
        public void changeGameModelForAllGamePlayers(GameMode mode) {
            modeChanges++;
        }

        @Override
        public void resetPlayerHealthFoodEffectLevelInventory() {
            equipmentResets++;
        }

        @Override
        protected void announceGamePreparation(String message, String title, String subtitle) {}

        @Override
        protected void startFinalCountdown(
                int seconds, String title, String startTitle, String subtitle, Runnable onStart) {
            countdownSeconds = seconds;
            setGameStageEnum(GameStageEnum.COUNTDOWN);
        }

        @Override
        public synchronized void teleportPlayerToSpawnLocation(Player player) {
            teleports++;
        }

        @Override
        public void returnToArena(Player player) {
            respawns++;
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
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

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
