package ink.ziip.championshipscore.api.game.parkourwarrior.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyManager;
import ink.ziip.championshipscore.api.daily.DailyStatsManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.config.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.model.*;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

class ParkourWarriorMovementTest {
    @TempDir Path directory;

    @Test
    void missingPreparationCannotCreateProgressOrThrow() throws Exception {
        try (var f = new Fixture(directory)) {
            ((Map<?, ?>)
                            field(ParkourWarriorTeamArea.class, "playerCheckpointProgress")
                                    .get(f.area))
                    .clear();
            f.move(0);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertTrue(f.progress.isEmpty());
            assertTrue(f.runner.messages.isEmpty());
            assertEquals(0, f.area.backTools);
        }
    }

    @Test
    void ordinaryMovementDoesNotResolveNamesOrTeams() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(-100);
            f.move(-99);
            assertEquals(0, f.runner.nameReads);
            assertEquals(0, f.teams.lookups);
            assertTrue(f.runner.messages.isEmpty());
        }
    }

    @Test
    void mainCheckpointAdvancesOnceAndUsesOneMessageForRunnerAndAudience() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(0);
            f.move(0);
            assertEquals(-1, f.progress.get(f.main));
            assertEquals(1, f.runner.messages.size());
            f.move(1);
            f.move(1);
            assertEquals(0, f.progress.get(f.main));
            f.move(2);
            f.move(2);
            assertEquals(
                    1, f.area.getPlayerCheckpoints(f.runner.id, PKWCheckPointTypeEnum.main, ""));
            assertEquals(
                    2, f.area.getPlayerSubCheckpoints(f.runner.id, PKWCheckPointTypeEnum.main, 0));
            assertEquals(f.runner.messages, f.area.audience);
            assertEquals(4, f.runner.messages.size());
            assertTrue(
                    f.runner.messages.stream()
                            .allMatch(message -> message.contains("runner%checkpoint%")));
            assertEquals(List.of(false, false, false, true), f.area.soundEvents);
            assertEquals(0, f.runner.teleports.size());
            assertEquals(0, f.area.ends);
        }
    }

    @Test
    void subCheckpointCompletionReturnsToBranchSpawnOnlyOnce() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(10);
            assertEquals(1, f.area.backTools);
            f.move(11);
            f.move(12);
            f.move(13);
            f.move(13);
            assertEquals(
                    3,
                    f.area.getPlayerCheckpoints(f.runner.id, PKWCheckPointTypeEnum.sub, "branch"));
            assertEquals(List.of(f.branch.getSpawn()), f.runner.teleports);
            int messages = f.runner.messages.size();
            f.move(0);
            f.move(10);
            assertEquals(messages + 1, f.runner.messages.size());
            assertEquals(2, f.progress.get(f.branch));
        }
    }

    @Test
    void returningFromAnUnfinishedBranchPreservesScoreUntilReentry() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(10);
            f.move(11);
            f.area.backToMainSpawnPoint(f.runner.player);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertEquals(List.of(f.branch.getSpawn()), f.runner.teleports);
            assertEquals(0, f.progress.get(f.branch));
            f.move(11);
            assertEquals(0, f.progress.get(f.branch));
            f.move(10);
            assertEquals(-1, f.progress.get(f.branch));
            assertEquals(2, f.area.backTools);
        }
    }

    @Test
    void overlappingEntranceTakesPriorityAndOnlyOneSectionAdvancesPerMove() throws Exception {
        try (var f = new Fixture(directory)) {
            f.main.getSubCheckpoints().set(0, f.branch.getEnter());
            f.move(0);
            f.move(10);
            assertEquals(-1, f.progress.get(f.main));
            assertEquals(-1, f.progress.get(f.branch));
            f.branch.getSubCheckpoints().set(1, f.branch.getSubCheckpoints().getFirst());
            f.move(11);
            assertEquals(0, f.progress.get(f.branch));
            f.move(11);
            assertEquals(1, f.progress.get(f.branch));
        }
    }

    @Test
    void checkpointsWithSameDisplayNameRemainDistinct() throws Exception {
        try (var f = new Fixture(directory)) {
            var second = f.checkpoint("main", "other", PKWCheckPointTypeEnum.main, 30, 1);
            f.checkpoints.add(second);
            f.move(0);
            f.move(30);
            f.move(31);
            assertEquals(-1, f.progress.get(f.main));
            assertEquals(0, f.progress.get(second));
        }
    }

    @Test
    void matchingCoordinatesInAnotherWorldCannotAdvance() throws Exception {
        try (var f = new Fixture(directory)) {
            f.runner.location = new Location(world(), 0, 0, 0);
            f.area.handlePlayerMove(f.runner.player);
            assertTrue(f.progress.isEmpty());
            f.move(0);
            f.runner.location = new Location(world(), 1, 0, 0);
            f.area.handlePlayerMove(f.runner.player);
            assertEquals(-1, f.progress.get(f.main));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = GameStageEnum.class,
            names = {"WAITING", "LOADING", "PREPARATION", "COUNTDOWN", "STOPPING", "END"})
    void directCallsOutsideProgressCannotAdvanceOrReturn(GameStageEnum stage) throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(10);
            set(f.area, "gameStageEnum", stage);
            f.move(11);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertEquals(-1, f.progress.get(f.branch));
            assertTrue(f.runner.teleports.isEmpty());
        }
    }

    @Test
    void spectatorAndNonParticipantCannotAdvanceOrUseReturnTool() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(10);
            f.runner.mode = GameMode.SPECTATOR;
            f.move(11);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertEquals(-1, f.progress.get(f.branch));
            assertTrue(f.runner.teleports.isEmpty());
            f.runner.mode = GameMode.ADVENTURE;
            f.area.getGamePlayers().clear();
            f.move(11);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertEquals(-1, f.progress.get(f.branch));
            assertTrue(f.runner.teleports.isEmpty());
        }
    }

    @Test
    void finalCompletionKeepsFormalOfflineRosterAndAwardsMultiplierOnce() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.getGamePlayers().add(UUID.randomUUID());
            ChampionshipTeam originalTeam = f.teams.team;
            f.area.onFinishVisibility = () -> f.teams.team = null;
            f.move(0);
            f.move(2);
            f.move(20);
            f.move(21);
            assertTrue(f.area.isFinishedPlayer(f.runner.id));
            assertEquals(0, f.area.ends);
            int messages = f.runner.messages.size();
            // Even an incorrectly restored mode cannot award this runner a second multiplier.
            f.runner.mode = GameMode.ADVENTURE;
            f.move(21);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertEquals(messages, f.runner.messages.size());
            f.teams.team = originalTeam;
            f.area.calculatePoints();
            assertEquals(30D, f.area.getPlayerPointsSnapshot().get(f.runner.id));
        }
    }

    @Test
    void dailyCompletionIgnoresOfflineRunnerAndUsesPersonalMultiplier() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.setGameStageEnum(GameStageEnum.WAITING);
            f.area.prepareRunMode(GameRunMode.DAILY);
            f.area.setGameStageEnum(GameStageEnum.PROGRESS);
            f.area.getGamePlayers().add(UUID.randomUUID());
            f.move(0);
            f.move(2);
            f.move(20);
            f.move(21);
            assertEquals(1, f.area.ends);
            f.move(21);
            assertEquals(1, f.area.ends);
            f.area.calculatePoints();
            assertEquals(50D, f.area.getPlayerPointsSnapshot().get(f.runner.id));
        }
    }

    @Test
    void formalRunEndsOnceWhenItsWholeRosterFinishes() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(20);
            f.move(21);
            f.move(21);
            assertEquals(1, f.area.ends);
            assertTrue(f.area.isFinishedPlayer(f.runner.id));
        }
    }

    @Test
    void disposedAreaCannotAdvanceRetainedProgress() throws Exception {
        try (var f = new Fixture(directory)) {
            f.move(10);
            f.area.dispose();
            f.move(11);
            f.area.backToMainSpawnPoint(f.runner.player);
            assertEquals(-1, f.progress.get(f.branch));
            assertTrue(f.runner.teleports.isEmpty());
        }
    }

    private static World world() {
        return proxy(
                World.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "equals" -> p == a[0];
                            case "hashCode" -> System.identityHashCode(p);
                            default -> throw new AssertionError(m.getName());
                        });
    }

    private static final class Runner {
        final UUID id = UUID.randomUUID();
        final List<String> messages = new ArrayList<>();
        final List<Location> teleports = new ArrayList<>();
        Location location;
        GameMode mode = GameMode.ADVENTURE;
        int nameReads;
        final PlayerInventory inventory =
                proxy(
                        PlayerInventory.class,
                        (p, m, a) -> {
                            if (!m.getName().equals("remove"))
                                throw new AssertionError(m.getName());
                            return null;
                        });
        final Player player =
                proxy(
                        Player.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getUniqueId" -> id;
                                    case "getLocation" -> location;
                                    case "getGameMode" -> mode;
                                    case "getName" -> {
                                        nameReads++;
                                        yield "runner%checkpoint%";
                                    }
                                    case "getInventory" -> inventory;
                                    case "sendMessage" -> {
                                        messages.add((String) a[0]);
                                        yield null;
                                    }
                                    case "playSound" -> null;
                                    case "teleport" -> {
                                        teleports.add(((Location) a[0]).clone());
                                        yield true;
                                    }
                                    default -> throw new AssertionError(m.getName());
                                });
    }

    private static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        final Map<String, Object> oldMessages = new HashMap<>();
        // Bukkit's loader and unrelated managers are fixtures; the area runs its real constructor.
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final GameManager manager = allocate(GameManager.class);
        final Teams teams = allocate(Teams.class);
        final Runner runner = new Runner();
        final World world = world();
        final SpectatorManager spectators;
        final Area area;
        final List<PKWCheckpoint> checkpoints;
        final Map<PKWCheckpoint, Integer> progress = new HashMap<>();
        final PKWCheckpoint main;
        final PKWCheckpoint branch;

        @SuppressWarnings("unchecked")
        Fixture(Path root) throws Exception {
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                throw new AssertionError(m.getName());
                            });
            var plugins = proxy(PluginManager.class, (p, m, a) -> null);
            var loader =
                    proxy(
                            PluginLoader.class,
                            (p, m, a) -> {
                                if (m.getName().equals("createRegisteredListeners"))
                                    return Map.of();
                                throw new AssertionError(m.getName());
                            });
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getPluginManager" -> plugins;
                                        case "isPrimaryThread" -> true;
                                        case "getPlayer" ->
                                                runner.id.equals(a[0]) ? runner.player : null;
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(plugin, "server", server);
            set(plugin, "loader", loader);
            set(plugin, "dataFolder", root.toFile());
            set(plugin, "loaded", true);
            set(plugin, "isEnabled", true);
            set(plugin, "gameManager", manager);
            teams.team = new Team();
            set(plugin, "teamManager", teams);
            // Native spectator presentation is covered by the spectator contract tests.
            spectators = new SpectatorManager(plugin, manager);
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", spectators);
            var daily = new DailyManager(plugin, new DailyStatsManager(plugin));
            set(plugin, "dailyManager", daily);
            field(Bukkit.class, "server").set(null, server);
            for (String key :
                    List.of(
                            "PARKOUR_WARRIOR_SUB_CHECKPOINT_ENTERED",
                            "PARKOUR_WARRIOR_SUB_CHECKPOINT_ARRIVED",
                            "PARKOUR_WARRIOR_SUB_CHECKPOINT_COMPLETED",
                            "PARKOUR_WARRIOR_END_CHECKPOINT_COMPLETED")) {
                var message =
                        field(
                                ink.ziip.championshipscore.configuration.config.message
                                        .ParkourWarriorMessages.class,
                                key);
                oldMessages.put(key, message.get(null));
                message.set(null, key + ": %player% %checkpoint% %sub-checkpoint%");
            }
            var config = new ParkourWarriorConfig(plugin, "test");
            config.setSpectatorSpawnPoint(new Location(world, -50, 0, 0));
            config.setPlayerSpawnPoint(new Location(world, -100, 0, 0));
            config.setPoints2(10);
            config.setFinalPointsHard(0.5D);
            area = new Area(plugin, config);
            area.getGamePlayers().add(runner.id);
            area.setGameStageEnum(GameStageEnum.PROGRESS);
            ((Map<UUID, Map<PKWCheckpoint, Integer>>)
                            field(ParkourWarriorTeamArea.class, "playerCheckpointProgress")
                                    .get(area))
                    .put(runner.id, progress);
            checkpoints =
                    (List<PKWCheckpoint>)
                            field(ParkourWarriorTeamArea.class, "checkpoints").get(area);
            main = checkpoint("main", "main", PKWCheckPointTypeEnum.main, 0, 2);
            branch = checkpoint("branch", "branch", PKWCheckPointTypeEnum.sub, 10, 3);
            checkpoints.addAll(
                    List.of(
                            main,
                            branch,
                            checkpoint("final", "final", PKWCheckPointTypeEnum.fin, 20, 1)));
        }

        PKWCheckpoint checkpoint(
                String name, String key, PKWCheckPointTypeEnum type, int x, int count) {
            List<CCSelection> sections = new ArrayList<>();
            for (int i = 1; i <= count; i++) sections.add(selection(x + i));
            return new PKWCheckpoint(
                    null,
                    name,
                    key,
                    type,
                    sections,
                    selection(x),
                    new Location(world, x - 1, 2, 0),
                    PKWFinalCheckPointTypeEnum.hard);
        }

        CCSelection selection(int x) {
            return new CCSelection(new Vector(x, 0, 0), new Vector(x, 0, 0), world);
        }

        void move(int x) {
            runner.location = new Location(world, x, 0, 0);
            area.handlePlayerMove(runner.player);
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
            for (var entry : oldMessages.entrySet())
                field(
                                ink.ziip.championshipscore.configuration.config.message
                                        .ParkourWarriorMessages.class,
                                entry.getKey())
                        .set(null, entry.getValue());
        }
    }

    private static final class Teams extends TeamManager {
        ChampionshipTeam team;
        int lookups;

        private Teams() {
            super(null);
        }

        @Override
        public ChampionshipTeam getTeamByPlayer(Player player) {
            lookups++;
            return team;
        }

        @Override
        public ChampionshipTeam getTeamByPlayer(UUID id) {
            return team;
        }
    }

    private static final class Team extends ChampionshipTeam {
        Team() {
            super(1, "test", "RED", "#ff5555", null);
        }
    }

    private static final class Area extends ParkourWarriorTeamArea {
        final List<String> audience = new ArrayList<>();
        int backTools;
        int ends;
        Runnable onFinishVisibility = () -> {};
        final List<Boolean> soundEvents;

        Area(ChampionshipsCore plugin, ParkourWarriorConfig config) {
            this(plugin, config, new ArrayList<>());
        }

        private Area(ChampionshipsCore plugin, ParkourWarriorConfig config, List<Boolean> sounds) {
            super(plugin, config, 0, false, (player, completed) -> sounds.add(completed));
            soundEvents = sounds;
        }

        @Override
        protected void logGame(Level level, String event, String message) {}

        @Override
        public void sendMessageToAllSpectators(String message) {
            audience.add(message);
        }

        @Override
        public void giveBackToolToPlayer(Player player) {
            backTools++;
        }

        @Override
        public void hideAndShowPlayer(Player player) {
            onFinishVisibility.run();
        }

        @Override
        public void endGame() {
            ends++;
            setGameStageEnum(GameStageEnum.END);
        }

        @Override
        public void addPlayerPoints(UUID id, double points) {
            playerPoints.merge(id, points, Double::sum);
        }
    }
}
