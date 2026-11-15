package ink.ziip.championshipscore.api.game.instance;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.util.world.WorldManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

class GameMapLifecycleTest {
    @TempDir Path directory;

    @Test
    void concurrentLoadsShareCompletionAndPublishOnlyAfterReturningToMain() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.writeWorld("old");
            var first = f.area.loadMap(World.Environment.NORMAL);
            assertSame(first, f.area.loadMap(World.Environment.NORMAL));
            assertEquals(1, f.async.size());
            assertFalse(first.isDone());
            f.runAsync();
            assertEquals("published", f.worldContents());
            assertFalse(first.isDone());
            assertEquals(0, f.listener.registrations);
            f.runMain();
            assertTrue(first.join());
            assertEquals(1, f.listener.registrations);
            assertEquals(GameStageEnum.WAITING, f.area.getGameStageEnum());
            assertEquals(1, f.worlds.loads);
        }
    }

    @Test
    void unpublishedDraftIsLoadedWithoutDeletingItsFiles() throws Exception {
        try (var f = new Fixture(directory)) {
            f.config.ready = false;
            f.writeWorld("draft");
            assertTrue(f.area.loadPublishedMapOrDraft(World.Environment.NORMAL).join());
            assertEquals("draft", f.worldContents());
            assertFalse(Files.exists(f.template));
            assertTrue(f.async.isEmpty());
        }
    }

    @Test
    void missingPublishedTemplateFallsBackToDraftAndClearsReadiness() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeWorld("draft");
            assertTrue(f.area.loadPublishedMapOrDraft(World.Environment.NORMAL).join());
            assertFalse(f.config.ready);
            assertEquals("draft", f.worldContents());
        }
    }

    @Test
    void unresolvedWorldBindingDoesNotTouchWorldFiles() throws Exception {
        try (var f = new Fixture(directory)) {
            f.config.bindingPending = true;
            assertTrue(f.area.loadPublishedMapOrDraft(World.Environment.NORMAL).join());
            assertEquals(0, f.worlds.loads);
            assertEquals(1, f.listener.registrations);
            assertTrue(f.async.isEmpty());
        }
    }

    @Test
    void publicationStagesReloadsAndRemovesPreviousTemplateAfterSuccess() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("old-published");
            f.writeWorld("new-draft");
            var save = f.area.saveMap(World.Environment.NORMAL);
            f.drain();
            assertTrue(save.join());
            assertEquals("new-draft", f.templateContents());
            assertEquals("new-draft", f.worldContents());
            assertEquals(List.of("arena"), f.templateEntries());
            assertEquals(GameStageEnum.WAITING, f.area.getGameStageEnum());
            assertEquals(List.of(true), f.worlds.unloadSaves);
        }
    }

    @Test
    void failedStagingPreservesPublishedTemplateAndEditableDraft() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("old-published");
            f.writeWorld("new-draft");
            f.worlds.failStaging = true;
            var save = f.area.saveMap(World.Environment.NORMAL);
            f.drain();
            assertFalse(save.join());
            assertEquals("old-published", f.templateContents());
            assertEquals("new-draft", f.worldContents());
            assertEquals(List.of("arena"), f.templateEntries());
            assertEquals(GameStageEnum.WAITING, f.area.getGameStageEnum());
        }
    }

    @Test
    void failedPublishedReloadRestoresPreviousTemplateAndReturnsFailure() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("old-published");
            f.writeWorld("new-draft");
            f.worlds.failedLoads = 1;
            var save = f.area.saveMap(World.Environment.NORMAL);
            f.drain();
            assertFalse(save.join());
            assertEquals("old-published", f.templateContents());
            assertEquals("old-published", f.worldContents());
            assertEquals(List.of("arena"), f.templateEntries());
            assertEquals(GameStageEnum.WAITING, f.area.getGameStageEnum());
        }
    }

    @Test
    void configFailureDoesNotRegisterTheRestoredMap() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.config.reloadsSucceed = false;
            var load = f.area.loadMap(World.Environment.NORMAL);
            f.drain();
            assertFalse(load.join());
            assertEquals(0, f.listener.registrations);
            assertEquals(GameStageEnum.END, f.area.getGameStageEnum());
        }
    }

    @Test
    void unloadFailureLeavesWorldAndTemplateUntouched() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.writeWorld("draft");
            f.worlds.unloadsSucceed = false;
            assertFalse(f.area.saveMap(World.Environment.NORMAL).join());
            assertEquals("published", f.templateContents());
            assertEquals("draft", f.worldContents());
            assertTrue(f.async.isEmpty());
        }
    }

    @Test
    void disposalCompletesQueuedLoadAndPreventsOldFileDeletion() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.writeWorld("draft");
            var load = f.area.loadMap(World.Environment.NORMAL);
            f.area.dispose();
            assertFalse(load.join());
            f.drain();
            assertEquals("draft", f.worldContents());
            assertEquals(0, f.worlds.loads);
            assertEquals(0, f.listener.registrations);
        }
    }

    @Test
    void disposalAfterCopyPreventsOldMainCallbackFromLoadingWorld() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            var load = f.area.loadMap(World.Environment.NORMAL);
            f.runAsync();
            assertFalse(f.main.isEmpty());
            f.area.dispose();
            assertFalse(load.join());
            f.drain();
            assertEquals(0, f.worlds.loads);
            assertEquals(0, f.listener.registrations);
            assertEquals(GameStageEnum.END, f.area.getGameStageEnum());
        }
    }

    @Test
    void disposalDuringPublicationCannotReloadDraftOrLeaveFuturePending() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.writeWorld("draft");
            var save = f.area.saveMap(World.Environment.NORMAL);
            f.area.dispose();
            assertTrue(save.isDone());
            assertFalse(save.join());
            f.drain();
            assertEquals("draft", f.worldContents());
            assertEquals("published", f.templateContents());
            assertEquals(0, f.worlds.loads);
            assertEquals(0, f.listener.registrations);
        }
    }

    @Test
    void queuedMainRequestIsRejectedAfterDisposal() throws Exception {
        try (var f = new Fixture(directory)) {
            f.primary = false;
            var load = f.area.loadPublishedMapOrDraft(World.Environment.NORMAL);
            f.primary = true;
            f.area.dispose();
            assertTrue(load.isDone());
            assertFalse(load.join());
            f.drain();
            assertEquals(0, f.config.reloads);
            assertEquals(0, f.listener.registrations);
        }
    }

    @Test
    void schedulerRejectionAndPluginShutdownResolveFailure() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.rejectScheduling = true;
            assertFalse(f.area.loadMap(World.Environment.NORMAL).join());
            f.rejectScheduling = false;
            var load = f.area.loadMap(World.Environment.NORMAL);
            set(f.plugin, "loaded", false);
            f.drain();
            assertFalse(load.join());
            assertEquals(0, f.worlds.loads);
        }
    }

    @Test
    void abortWaitsForTheOwnedLoadBeforeReportingReadiness() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            var load = f.area.loadMap(World.Environment.NORMAL);
            var abort = f.area.abortAndReset();
            assertFalse(abort.isDone());
            f.drain();
            assertTrue(load.join());
            assertTrue(abort.join());
        }
    }

    @Test
    void publicationOwnsTheWholeRequestIncludingStagingAndFinalCleanup() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.writeWorld("draft");
            var save = f.area.saveMap(World.Environment.NORMAL);
            var abort = f.area.abortAndReset();
            assertSame(save, f.area.loadMap(World.Environment.NORMAL));
            assertFalse(f.area.saveMap(World.Environment.NORMAL).join());
            assertEquals(1, f.async.size());
            assertFalse(abort.isDone());
            f.runAsync();
            f.runMain();
            f.runAsync();
            f.runMain();
            assertEquals(GameStageEnum.WAITING, f.area.getGameStageEnum());
            var cleanupWait = f.area.abortAndReset();
            assertFalse(cleanupWait.isDone());
            f.drain();
            assertTrue(cleanupWait.join());
            assertTrue(save.join());
            assertTrue(abort.join());
            assertEquals("draft", f.templateContents());
            assertEquals("draft", f.worldContents());
        }
    }

    @Test
    void disposalAfterStagingCannotUnloadOrDeleteAReplacementWorld() throws Exception {
        try (var f = new Fixture(directory)) {
            f.writeTemplate("published");
            f.writeWorld("draft");
            var save = f.area.saveMap(World.Environment.NORMAL);
            f.runAsync();
            f.runMain();
            f.writeWorld("replacement-owner");
            f.area.dispose();
            assertFalse(save.join());
            f.drain();
            assertTrue(f.worlds.loaded);
            assertEquals(List.of(true), f.worlds.unloadSaves);
            assertEquals("replacement-owner", f.worldContents());
            assertEquals(0, f.listener.registrations);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        // Only the JavaPlugin loader shell is bypassed; all objects under test are constructed.
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final ArrayDeque<Runnable> async = new ArrayDeque<>();
        final ArrayDeque<Runnable> main = new ArrayDeque<>();
        final Path template;
        final Path world;
        final Worlds worlds;
        final Config config;
        final Listener listener;
        final Area area;
        boolean primary = true;
        boolean rejectScheduling;

        Fixture(Path root) throws Exception {
            template = root.resolve("maps/arena");
            world = root.resolve("worlds/arena");
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (rejectScheduling)
                                    throw new IllegalStateException("scheduler stopped");
                                switch (m.getName()) {
                                    case "runTaskAsynchronously" -> async.add((Runnable) a[1]);
                                    case "runTask" -> main.add((Runnable) a[1]);
                                    default -> throw new AssertionError(m.getName());
                                }
                                return null;
                            });
            var manager = proxy(PluginManager.class, (p, m, a) -> null);
            worlds = new Worlds(plugin, world);
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isPrimaryThread" -> primary;
                                        case "getScheduler" -> scheduler;
                                        case "getPluginManager" -> manager;
                                        case "getWorld" -> worlds.loaded ? worlds.world : null;
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(plugin, "server", server);
            set(plugin, "dataFolder", root.toFile());
            set(plugin, "isEnabled", true);
            set(plugin, "loaded", true);
            set(plugin, "worldManager", worlds);
            field(Bukkit.class, "server").set(null, server);
            config = new Config(plugin);
            listener = new Listener(plugin);
            area = new Area(plugin, listener, config);
            area.setGameStageEnum(GameStageEnum.WAITING);
        }

        void writeTemplate(String text) throws Exception {
            write(template, text);
        }

        void writeWorld(String text) throws Exception {
            write(world, text);
            worlds.loaded = true;
        }

        String templateContents() throws Exception {
            return Files.readString(template.resolve("marker"));
        }

        String worldContents() throws Exception {
            return Files.readString(world.resolve("marker"));
        }

        static void write(Path folder, String text) throws Exception {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("marker"), text);
        }

        List<String> templateEntries() throws Exception {
            try (var files = Files.list(template.getParent())) {
                return files.map(path -> path.getFileName().toString()).sorted().toList();
            }
        }

        void runAsync() {
            primary = false;
            try {
                async.removeFirst().run();
            } finally {
                primary = true;
            }
        }

        void runMain() {
            main.removeFirst().run();
        }

        void drain() {
            for (int i = 0; !async.isEmpty() || !main.isEmpty(); i++) {
                assertTrue(i < 30, "map workflow must terminate");
                if (!async.isEmpty()) runAsync();
                if (!main.isEmpty()) runMain();
            }
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }

    private static final class Worlds extends WorldManager {
        final Path folder;
        final World world =
                proxy(
                        World.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getPlayers" -> List.of();
                                    default -> throw new AssertionError(m.getName());
                                });
        final List<Boolean> unloadSaves = new ArrayList<>();
        boolean loaded;
        boolean unloadsSucceed = true;
        boolean failStaging;
        int failedLoads;
        int loads;

        Worlds(ChampionshipsCore plugin, Path folder) {
            super(plugin);
            this.folder = folder;
        }

        @Override
        public File getWorldFolder(String name) {
            return folder.toFile();
        }

        @Override
        public boolean prepareWorldForFirstLoad(String name) {
            return true;
        }

        @Override
        public boolean unloadWorld(String name, boolean save) {
            unloadSaves.add(save);
            if (!unloadsSucceed) return false;
            loaded = false;
            return true;
        }

        @Override
        public boolean loadWorld(String name, World.Environment environment, boolean readOnly) {
            loads++;
            if (failedLoads-- > 0) return false;
            loaded = true;
            return true;
        }

        @Override
        public boolean copyWorldFiles(File source, File target) {
            if (failStaging && target.getName().endsWith("-staging")) {
                assertTrue(target.mkdirs());
                return false;
            }
            return super.copyWorldFiles(source, target);
        }
    }

    private static final class Config extends BaseGameConfig {
        boolean ready = true;
        boolean bindingPending;
        boolean reloadsSucceed = true;
        int reloads;

        Config(ChampionshipsCore plugin) {
            super(plugin, "arena");
        }

        @Override
        public boolean reloadConfigurationChecked(Path folder) {
            reloads++;
            return reloadsSucceed;
        }

        @Override
        public boolean isPrepareReady() {
            return ready;
        }

        @Override
        public boolean isWorldBindingPending() {
            return bindingPending;
        }

        @Override
        public void beginPrepareDraft() {
            ready = false;
        }

        @Override
        public String getAreaName() {
            return "arena";
        }

        @Override
        public String getFolderName() {
            return "test/";
        }

        @Override
        public String getResourceName() {
            return "test.yml";
        }

        @Override
        public int getLatestVersion() {
            return 1;
        }

        @Override
        public Vector getAreaPos1() {
            return null;
        }

        @Override
        public Vector getAreaPos2() {
            return null;
        }

        @Override
        public Location getSpectatorSpawnPoint() {
            return null;
        }
    }

    private static final class Listener extends BaseListener {
        int registrations;

        Listener(ChampionshipsCore plugin) {
            super(plugin);
        }

        @Override
        public void register() {
            registrations++;
        }

        @Override
        public void unRegister() {}
    }

    private static final class Area extends BaseGameInstance {
        Area(ChampionshipsCore plugin, Listener listener, Config config) {
            super(plugin, GameTypeEnum.ParkourTag, listener, config);
        }

        @Override
        public BaseGameConfig getGameConfig() {
            return gameConfig;
        }

        @Override
        public BaseListener getGameHandler() {
            return gameHandler;
        }

        @Override
        public String getWorldName() {
            return "arena";
        }

        @Override
        public boolean canSaveMap() {
            return getGameStageEnum() == GameStageEnum.WAITING;
        }

        @Override
        protected void logGame(Level level, String event, String message) {}

        @Override
        public void teleportAllSpectators(Location location) {}

        @Override
        public Location getSpectatorSpawnLocation() {
            return null;
        }

        @Override
        public int getTimer() {
            return 0;
        }

        @Override
        public void endGame() {}

        @Override
        public void resetBaseArea() {}

        @Override
        public void resetArea() {}

        @Override
        public void removeAllPlayers() {}

        @Override
        public Collection<UUID> getParticipantUniqueIds() {
            return List.of();
        }

        @Override
        public void startGamePreparation() {}

        @Override
        public void sendMessageToAllGamePlayers(String message) {}

        @Override
        public void sendActionBarToAllGamePlayers(String message) {}

        @Override
        protected Collection<Player> getOnlineParticipantSpectators() {
            return List.of();
        }

        @Override
        public void sendTitleToAllGamePlayers(String title, String subtitle) {}

        @Override
        public void changeLevelForAllGamePlayers(int level) {}

        @Override
        public void changeGameModelForAllGamePlayers(GameMode mode) {}

        @Override
        public void setHealthForAllGamePlayers(double health) {}

        @Override
        public void setFoodLevelForAllGamePlayers(int level) {}

        @Override
        public void teleportAllPlayers(Location location) {}

        @Override
        public void clearEffectsForAllGamePlayers() {}

        @Override
        public void cleanInventoryForAllGamePlayers() {}

        @Override
        public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {}

        @Override
        public void playNoteToAllGamePlayers(Instrument instrument, Note note) {}

        @Override
        public boolean notAreaPlayer(Player player) {
            return true;
        }

        @Override
        public void handlePlayerDeath(PlayerDeathEvent event) {}

        @Override
        public void handlePlayerQuit(PlayerQuitEvent event) {}

        @Override
        public void handlePlayerJoin(PlayerJoinEvent event) {}
    }
}
