package ink.ziip.championshipscore.api.game.instance;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.config.ParkourWarriorConfig;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

class GamePreparationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(GameTypeEnum.class)
    void everyGameUsesTheSameFiveSecondEndpoint(GameTypeEnum game) throws Exception {
        try (var f = new Fixture(directory)) {
            set(f.area, "gameTypeEnum", game);
            AtomicInteger starts = new AtomicInteger();
            f.countdown(
                    5,
                    true,
                    () -> {
                        assertEquals(GameStageEnum.PROGRESS, f.area.getGameStageEnum());
                        starts.incrementAndGet();
                    });
            var task = f.last();
            assertEquals(0, task.delay);
            assertEquals(20, task.period);
            f.ticks(task, 5);
            assertEquals(0, starts.get());
            assertEquals(List.of("5", "4", "3", "2", "1"), f.area.titles);
            task.tick.run();
            task.tick.run();
            assertEquals(1, starts.get());
            assertEquals(1, f.area.startAnnouncements);
            assertEquals(6, f.area.notes);
            assertTrue(task.cancelled);
        }
    }

    @Test
    void selectionWindowHandsOffWithoutStartingGameplayOrCancellingNewTask() throws Exception {
        try (var f = new Fixture(directory)) {
            AtomicInteger starts = new AtomicInteger();
            f.countdown(15, false, () -> f.countdown(5, true, starts::incrementAndGet));
            var selection = f.last();
            f.ticks(selection, 16);
            var actual = f.last();
            assertNotSame(selection, actual);
            assertEquals(GameStageEnum.COUNTDOWN, f.area.getGameStageEnum());
            assertEquals(0, starts.get());
            selection.tick.run();
            assertFalse(actual.cancelled);
            f.ticks(actual, 6);
            assertEquals(1, starts.get());
        }
    }

    @Test
    void cancelledCountdownCannotAlterNewCountdown() throws Exception {
        try (var f = new Fixture(directory)) {
            f.countdown(0, true, () -> fail("retired callback"));
            var old = f.last();
            f.area.cancelFinalCountdown();
            f.countdown(2, true, () -> {});
            var current = f.last();
            old.tick.run();
            assertFalse(current.cancelled);
            assertTrue(f.area.titles.isEmpty());
            f.ticks(current, 3);
            assertEquals(1, f.area.startAnnouncements);
        }
    }

    @Test
    void newCountdownStartedInsideOldStartCallbackOwnsAnnouncementsAndFailure() throws Exception {
        try (var f = new Fixture(directory)) {
            f.countdown(
                    0,
                    true,
                    () -> {
                        f.countdown(0, true, () -> {});
                        throw new IllegalStateException("old callback failed after handoff");
                    });
            f.last().tick.run();
            assertEquals(0, f.area.aborts);
            assertEquals(0, f.area.startAnnouncements);
            assertFalse(f.last().cancelled);
            f.last().tick.run();
            assertEquals(1, f.area.startAnnouncements);
        }
    }

    @Test
    void retiredRunStageGenerationPluginAndDisposalCannotStartGameplay() throws Exception {
        for (String invalid : List.of("stage", "generation", "plugin", "dispose")) {
            try (var f = new Fixture(directory)) {
                f.countdown(0, true, () -> fail(invalid));
                switch (invalid) {
                    case "stage" -> f.area.setGameStageEnum(GameStageEnum.END);
                    case "generation" -> f.area.nextGeneration();
                    case "plugin" -> set(f.plugin, "loaded", false);
                    case "dispose" -> f.area.dispose();
                }
                f.last().tick.run();
                assertTrue(f.last().cancelled);
                assertEquals(0, f.area.startAnnouncements);
            }
        }
    }

    @Test
    void stoppedAndIdleAreasCannotBeResurrectedByCountdownRequests() throws Exception {
        for (GameStageEnum stage :
                List.of(GameStageEnum.END, GameStageEnum.WAITING, GameStageEnum.LOADING)) {
            try (var f = new Fixture(directory)) {
                f.area.setGameStageEnum(stage);
                f.countdown(0, true, () -> fail("not an admitted start"));
                assertTrue(f.tasks.isEmpty());
                assertEquals(stage, f.area.getGameStageEnum());
            }
        }
    }

    @Test
    void schedulerRejectionAndCallbackFailureAbortOnlyTheirOwnRun() throws Exception {
        try (var f = new Fixture(directory)) {
            f.reject = true;
            f.countdown(0, true, () -> fail("rejected scheduling"));
            assertEquals(1, f.area.aborts);
            f.reject = false;
            f.area.setGameStageEnum(GameStageEnum.PREPARATION);
            f.countdown(
                    0,
                    true,
                    () -> {
                        throw new IllegalStateException("start failed");
                    });
            f.last().tick.run();
            f.last().tick.run();
            assertEquals(2, f.area.aborts);
            assertTrue(f.last().cancelled);
        }
    }

    @Test
    void synchronousSchedulerCompletionStillCancelsItsReturnedHandle() throws Exception {
        try (var f = new Fixture(directory)) {
            f.immediate = true;
            AtomicInteger starts = new AtomicInteger();
            f.countdown(0, true, starts::incrementAndGet);
            assertEquals(1, starts.get());
            assertTrue(f.last().cancelled);
        }
    }

    @Test
    void introductionUsesFrozenSectionsAndCompletesExactlyOnce() throws Exception {
        try (var f = new Fixture(directory)) {
            AtomicInteger complete = new AtomicInteger();
            f.introduction(complete::incrementAndGet);
            var intro = f.last();
            f.config.rules.clear();
            assertTrue(f.area.isIntroductionPhase());
            f.ticks(intro, 90);
            assertEquals(0, complete.get());
            intro.tick.run();
            intro.tick.run();
            assertEquals(List.of("one", "two", "three"), f.area.sections);
            assertEquals(1, complete.get());
            assertEquals(1, f.area.stops);
            assertFalse(f.area.isIntroductionPhase());
            assertEquals(1, f.area.teleports);
            assertEquals(100, f.area.titleStay);
        }
    }

    @ParameterizedTest
    @EnumSource(GameRunMode.class)
    void introductionsAreArmedOnlyForEventShapedRuns(GameRunMode mode) throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.setGameStageEnum(GameStageEnum.WAITING);
            f.area.prepareRunMode(mode);
            f.area.setGameStageEnum(GameStageEnum.PREPARATION);
            AtomicInteger complete = new AtomicInteger();
            f.area.setIntroductionEnabledForNextStart(true);
            f.area.startGameIntroduction(complete::incrementAndGet);
            boolean expected = mode == GameRunMode.EVENT || mode == GameRunMode.TEST_EVENT;
            assertEquals(expected, f.area.isIntroductionPhase());
            assertEquals(expected ? 0 : 1, complete.get());
            f.area.cancelIntroduction();
            f.area.startGameIntroduction(complete::incrementAndGet);
            assertEquals(expected ? 1 : 2, complete.get());
        }
    }

    @Test
    void interactiveLessonRunsWithoutChatRulesAndReceivesOneStopHook() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.interactive = true;
            f.config.rules.clear();
            f.introduction(() -> {});
            f.ticks(f.last(), 91);
            f.area.cancelIntroduction();
            assertEquals(1, f.area.starts);
            assertEquals(1, f.area.stops);
            assertTrue(f.area.sections.isEmpty());
            assertEquals(0, f.area.titleStay);
        }
    }

    @Test
    void oldIntroductionCannotStopOrCompleteItsReplacement() throws Exception {
        try (var f = new Fixture(directory)) {
            f.introduction(() -> fail("old completion"));
            var old = f.last();
            f.area.cancelIntroduction();
            AtomicInteger completed = new AtomicInteger();
            f.introduction(completed::incrementAndGet);
            var current = f.last();
            f.ticks(old, 100);
            assertFalse(current.cancelled);
            assertTrue(f.area.isIntroductionPhase());
            assertEquals(1, f.area.stops);
            f.ticks(current, 91);
            assertEquals(1, completed.get());
        }
    }

    @Test
    void stopHookCanReplaceIntroductionWithoutOldCompletionOrCleanup() throws Exception {
        try (var f = new Fixture(directory)) {
            f.introduction(() -> fail("replaced completion"));
            var old = f.last();
            f.area.stopHook =
                    () -> {
                        f.area.stopHook = () -> {};
                        f.introduction(() -> {});
                    };
            f.ticks(old, 91);
            assertTrue(f.area.isIntroductionPhase());
            assertFalse(f.last().cancelled);
            assertEquals(0, f.area.barClears);
        }
    }

    @Test
    void cancellingStartHookPreventsModeChangesTeleportAndScheduling() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.startHook = f.area::cancelIntroduction;
            f.introduction(() -> fail("cancelled"));
            assertEquals(1, f.area.stops);
            assertEquals(0, f.area.modeChanges);
            assertEquals(0, f.area.teleports);
            assertTrue(f.tasks.isEmpty());
        }
    }

    @Test
    void cancellingDuringRuleBroadcastCannotRenderCountdownOrComplete() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.ruleHook = f.area::cancelIntroduction;
            f.introduction(() -> fail("cancelled broadcast"));
            f.ticks(f.last(), 10);
            f.area.bars.clear();
            f.last().tick.run();
            assertTrue(f.area.bars.isEmpty());
            assertEquals(1, f.area.stops);
        }
    }

    @Test
    void cancelledBeforeStartDoesNotLeakArmedIntroductionIntoNextRun() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.setIntroductionEnabledForNextStart(true);
            f.area.cancelIntroduction();
            AtomicInteger complete = new AtomicInteger();
            f.area.startGameIntroduction(complete::incrementAndGet);
            assertEquals(1, complete.get());
            assertTrue(f.tasks.isEmpty());
        }
    }

    @Test
    void presentationDurationResetsBetweenPhases() throws Exception {
        try (var f = new Fixture(directory)) {
            f.area.showPreparationCountdown(90);
            f.area.showPreparationCountdown(45);
            assertEquals(List.of("90/90", "45/90"), f.area.bars);
            f.area.clearBossBars();
            f.area.showPreparationCountdown(15);
            assertEquals("15/15", f.area.bars.getLast());
        }
    }

    @Test
    void staleBlockRemovalCannotTouchNewCountdownSnapshots() throws Exception {
        // Paper resolves Material.isAir through its native registry. Supply only this block's
        // external type for the isolated scheduler test, and restore it before other tests run.
        var typeField = field(Material.class, "blockType");
        Object previous = typeField.get(Material.STONE);
        var stone =
                proxy(
                        org.bukkit.block.BlockType.class,
                        (p, m, a) -> {
                            if (m.getName().equals("isAir")) return false;
                            throw new AssertionError(m.getName());
                        });
        typeField.set(
                Material.STONE,
                (java.util.function.Supplier<org.bukkit.block.BlockType>) () -> stone);
        try (var f = new Fixture(directory)) {
            f.area.blocks = true;
            f.area.countdownBlockDisappearance.start(5);
            assertFalse(f.tasks.isEmpty(), f.area.errors.toString());
            var oldBlocks = f.tasks.getFirst();
            f.countdown(5, true, () -> {});
            var newBlocks = f.tasks.get(1);
            oldBlocks.tick.run();
            assertEquals(0, f.removedBlocks);
            assertFalse(newBlocks.cancelled);
            newBlocks.tick.run();
            assertEquals(1, f.removedBlocks);
        } finally {
            typeField.set(Material.STONE, previous);
        }
    }

    @Test
    void introductionFailureIncludesSkippedAndRejectedStarts() throws Exception {
        for (String failure : List.of("skipped", "hook", "scheduler")) {
            try (var f = new Fixture(directory)) {
                if (failure.equals("skipped")) {
                    f.area.startGameIntroduction(
                            () -> {
                                throw new IllegalStateException("preparation failed");
                            });
                } else {
                    if (failure.equals("hook"))
                        f.area.startHook =
                                () -> {
                                    throw new IllegalStateException("lesson failed");
                                };
                    else f.reject = true;
                    f.introduction(() -> fail("failed introduction"));
                    assertEquals(1, f.area.stops);
                }
                assertEquals(1, f.area.aborts);
                assertFalse(f.area.isIntroductionPhase());
            }
        }
    }

    @Test
    void replacementStartedByCancellationHookWinsOverOuterRequest() throws Exception {
        try (var f = new Fixture(directory)) {
            f.introduction(() -> fail("old completion"));
            f.area.stopHook = () -> f.countdown(2, true, () -> {});
            f.introduction(() -> fail("superseded request"));
            assertFalse(f.area.isIntroductionPhase());
            assertFalse(f.last().cancelled);
            f.ticks(f.last(), 3);
            assertEquals(1, f.area.startAnnouncements);
        }
    }

    private static final class Task {
        final Runnable tick;
        final long delay;
        final long period;
        boolean cancelled;
        final BukkitTask handle =
                proxy(
                        BukkitTask.class,
                        (p, m, a) -> {
                            if (!m.getName().equals("cancel"))
                                throw new AssertionError(m.getName());
                            cancelled = true;
                            return null;
                        });

        Task(Runnable tick, long delay, long period) {
            this.tick = tick;
            this.delay = delay;
            this.period = period;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final List<Task> tasks = new ArrayList<>();
        final Map<String, Object> oldMessages = new HashMap<>();
        final Config config;
        final Area area;
        boolean reject;
        boolean immediate;
        int removedBlocks;

        Fixture(Path directory) throws Exception {
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (!m.getName().equals("runTaskTimer"))
                                    throw new AssertionError(m.getName());
                                if (reject) throw new IllegalStateException("scheduler rejected");
                                var task = new Task((Runnable) a[1], (Long) a[2], (Long) a[3]);
                                tasks.add(task);
                                if (immediate) task.tick.run();
                                return task.handle;
                            });
            World[] world = new World[1];
            var state =
                    proxy(
                            BlockState.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getWorld" -> world[0];
                                        case "getType" -> Material.STONE;
                                        case "getX", "getY", "getZ" -> 0;
                                        case "update" -> true;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var block =
                    proxy(
                            Block.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getState" -> state;
                                        case "setType" -> {
                                            removedBlocks++;
                                            yield null;
                                        }
                                        default -> throw new AssertionError(m.getName());
                                    });
            UUID worldId = UUID.randomUUID();
            world[0] =
                    proxy(
                            World.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getBlockAt" -> block;
                                        case "getUID" -> worldId;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var plugins = proxy(PluginManager.class, (p, m, a) -> null);
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getPluginManager" -> plugins;
                                        case "getWorld" -> world[0];
                                        case "isPrimaryThread" -> true;
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(plugin, "server", server);
            set(plugin, "dataFolder", directory.toFile());
            set(plugin, "loaded", true);
            set(plugin, "isEnabled", true);
            field(Bukkit.class, "server").set(null, server);
            for (String key :
                    List.of(
                            "GAME_PREPARATION_COUNT_DOWN",
                            "GAME_INTRODUCTION_TITLE",
                            "GAME_PARKOUR_TAG")) {
                var message =
                        field(
                                ink.ziip.championshipscore.configuration.config.message.GameMessages
                                        .class,
                                key);
                oldMessages.put(key, message.get(null));
                message.set(null, "game %game% %time%");
            }
            config = new Config(plugin);
            config.setSpectatorSpawnPoint(new Location(world[0], 0, 0, 0));
            area = new Area(plugin, config);
            area.setGameStageEnum(GameStageEnum.WAITING);
            area.prepareRunMode(GameRunMode.EVENT);
            area.setGameStageEnum(GameStageEnum.PREPARATION);
        }

        Task last() {
            return tasks.getLast();
        }

        void ticks(Task task, int count) {
            for (int i = 0; i < count; i++) task.tick.run();
        }

        void countdown(int seconds, boolean transition, Runnable start) {
            area.startFinalCountdown(seconds, "game", "start", "", true, transition, start);
        }

        void introduction(Runnable complete) {
            area.setIntroductionEnabledForNextStart(true);
            area.startGameIntroduction(complete);
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
            for (var entry : oldMessages.entrySet())
                field(
                                ink.ziip.championshipscore.configuration.config.message.GameMessages
                                        .class,
                                entry.getKey())
                        .set(null, entry.getValue());
        }
    }

    private static final class Config extends ParkourWarriorConfig {
        final List<List<String>> rules =
                new ArrayList<>(List.of(List.of("one"), List.of("two"), List.of("three")));

        Config(ChampionshipsCore plugin) {
            super(plugin, "fixture");
        }

        @Override
        public List<List<String>> getRules() {
            return rules;
        }

        @Override
        public String getCountdownBlockDisappearanceMode() {
            return "RANDOM";
        }
    }

    private static final class Area extends BaseGameInstance {
        final List<String> errors = new ArrayList<>();
        final List<String> titles = new ArrayList<>();
        final List<String> sections = new ArrayList<>();
        final List<String> bars = new ArrayList<>();
        int starts,
                stops,
                teleports,
                modeChanges,
                titleStay,
                startAnnouncements,
                notes,
                aborts,
                barClears;
        boolean interactive, blocks;
        Runnable startHook = () -> {}, stopHook = () -> {}, ruleHook = () -> {};

        Area(ChampionshipsCore plugin, Config config) {
            super(plugin, GameTypeEnum.ParkourTag, new BaseListener(plugin) {}, config);
        }

        void nextGeneration() {
            invalidateParticipantCallbacks();
        }

        @Override
        public void setGameStageEnum(GameStageEnum stage) {
            gameStageEnum = stage;
        }

        @Override
        protected boolean isInteractiveIntroduction() {
            return interactive;
        }

        @Override
        protected void onIntroductionStarted() {
            starts++;
            startHook.run();
        }

        @Override
        protected void onIntroductionStopped() {
            stops++;
            stopHook.run();
        }

        @Override
        void applyIntroductionGameModeToAllParticipants() {
            modeChanges++;
        }

        @Override
        void broadcastRuleSection(List<String> lines) {
            sections.addAll(lines);
            ruleHook.run();
        }

        @Override
        void sendTimedTitleToAllGamePlayers(String title, String subtitle, int stay) {
            titleStay = stay;
        }

        @Override
        void playCountdownBit(Note note) {
            notes++;
        }

        @Override
        protected String getFinalCountdownTitle(int seconds) {
            return String.valueOf(seconds);
        }

        @Override
        protected String getFinalCountdownSubtitle(String title, int seconds) {
            return title;
        }

        @Override
        protected void updateGameTimerBossBar(String title, int remaining, int duration) {
            bars.add(remaining + "/" + duration);
        }

        @Override
        protected void clearGameTimerBossBar() {
            super.clearGameTimerBossBar();
            barClears++;
        }

        @Override
        protected void announceGameStart(String title, String subtitle) {
            startAnnouncements++;
        }

        @Override
        protected Vector[] getCountdownBlockDisappearanceBounds() {
            return blocks ? new Vector[] {new Vector(), new Vector()} : null;
        }

        @Override
        public CompletableFuture<Boolean> abortAndReset() {
            aborts++;
            setGameStageEnum(GameStageEnum.END);
            return CompletableFuture.completedFuture(true);
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
        protected void logGame(Level level, String event, String message) {
            if (level == Level.SEVERE) errors.add(message);
        }

        @Override
        public void teleportAllSpectators(Location location) {}

        @Override
        public Location getSpectatorSpawnLocation() {
            return gameConfig.getSpectatorSpawnPoint();
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
        public void sendTitleToAllGamePlayers(String title, String subtitle) {
            titles.add(title);
        }

        @Override
        public void changeLevelForAllGamePlayers(int level) {}

        @Override
        public void changeGameModelForAllGamePlayers(GameMode mode) {}

        @Override
        public void setHealthForAllGamePlayers(double health) {}

        @Override
        public void setFoodLevelForAllGamePlayers(int level) {}

        @Override
        public void teleportAllPlayers(Location location) {
            teleports++;
        }

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
