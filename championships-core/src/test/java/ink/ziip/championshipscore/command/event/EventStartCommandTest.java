package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ConfigurationStateExtension.class)
class EventStartCommandTest {
    @TempDir Path directory;
    private ChampionshipsCore plugin;
    private TestSchedule schedule;
    private Object previousPlugin;
    private CommandSender sender;
    private List<String> messages;

    @BeforeEach void setup() throws Exception {
        previousPlugin = field(ChampionshipsCore.class, "instance").get(null);
        plugin = allocate(ChampionshipsCore.class);
        field(ChampionshipsCore.class, "instance").set(null, plugin);
        set(plugin, "dataFolder", directory.toFile());
        GameManager manager = allocate(GameManager.class);
        set(manager, "enabledGames", Set.of(GameTypeEnum.TNTRun, GameTypeEnum.RiptideRush,
                GameTypeEnum.FrostbiteFrenzy, GameTypeEnum.LaserBox, GameTypeEnum.SulfurSoccer));
        set(plugin, "gameManager", manager);
        schedule = allocate(TestSchedule.class);
        set(plugin, "scheduleManager", schedule);
        messages = new ArrayList<>();
        sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (p, m, a) -> {
                    if (m.getName().equals("sendMessage")) { messages.add((String) a[0]); return null; }
                    throw new AssertionError(m.getName());
                });
        MessageConfig.EVENT_START_STARTED = "started %game%";
        MessageConfig.EVENT_START_GAME_NOT_IN_EVENT = "%game% not in %event%";
        MessageConfig.EVENT_START_NO_EVENT = "no event";
        MessageConfig.EVENT_START_ARCHIVED = "archived";
    }

    @AfterEach void restore() throws Exception {
        field(ChampionshipsCore.class, "instance").set(null, previousPlugin);
    }

    @Test void completionWorksBeforeImportAndIncludesAllNewEnabledGames() {
        assertEquals(Set.of("tntrun", "riptide", "frostbite", "laserbox", "sulfursoccer"),
                Set.copyOf(new EventMainCommand().onTabComplete(sender, null, "cc", new String[]{"start", ""})));
        assertEquals(List.of("laserbox"), new EventMainCommand().onTabComplete(sender, null, "cc",
                new String[]{"start", "La"}));
    }

    @Test void importedOrArchivedEventDoesNotHideSupportedGamesFromCompletion() throws Exception {
        saveEvent(List.of(GameTypeEnum.TNTRun));
        new EventStateStore(plugin).markArchived();
        assertTrue(EventCommandSupport.enabledFormalGames().containsAll(List.of("riptide", "frostbite", "laserbox")));
        assertFalse(EventCommandSupport.enabledFormalGames().contains("bingo"));
    }

    @Test void newScoringGamesDispatchToFormalSchedules() throws Exception {
        saveEvent(List.of(GameTypeEnum.RiptideRush, GameTypeEnum.FrostbiteFrenzy, GameTypeEnum.LaserBox));
        for (GameTypeEnum game : List.of(GameTypeEnum.RiptideRush, GameTypeEnum.FrostbiteFrenzy, GameTypeEnum.LaserBox)) {
            schedule.started = null;
            new EventMainCommand().onCommand(sender, null, "cc", new String[]{"start", game.commandName()});
            assertEquals(game, schedule.started);
            assertNull(schedule.finale);
        }
    }

    @Test void sulfurSoccerUsesFinaleRulesAndCanBeStoppedThroughEvent() throws Exception {
        saveEvent(List.of(GameTypeEnum.LaserBox, GameTypeEnum.SulfurSoccer));
        new EventStartSubCommand().onCommand(sender, null, "cc", new String[]{"sulfursoccer"});
        assertEquals(GameTypeEnum.SulfurSoccer, schedule.finale);
        assertNull(schedule.started);
        assertTrue(schedule.stopFormalEvent(GameTypeEnum.SulfurSoccer));
        assertEquals(GameTypeEnum.SulfurSoccer, schedule.stoppedFinale);
    }

    @ParameterizedTest(name = "[{index}] starts enabled game without importing an event")
    @EnumSource(GameTypeEnum.class)
    void everyGameStartsExistingTeamsWithoutImportingAnEvent(GameTypeEnum game) throws Exception {
        set(plugin.getGameManager(), "enabledGames", EnumSet.allOf(GameTypeEnum.class));
        assertNull(new EventStateStore(plugin).load());
        assertTrue(schedule.supportsFormalEvent(game));
        new EventMainCommand().onCommand(sender, null, "cc", new String[]{"start", game.commandName()});
        if (FinaleGameRegistry.isRegistered(game)) {
            assertEquals(game, schedule.finale);
            assertNull(schedule.started);
        } else {
            assertEquals(game, schedule.started);
            assertNull(schedule.finale);
            assertTrue(messages.getLast().contains("started"));
        }
    }

    @Test void importedEventStillChecksAllowedGamesAndArchivedState() throws Exception {
        var start = new EventStartSubCommand();
        saveEvent(List.of(GameTypeEnum.TNTRun));
        start.onCommand(sender, null, "cc", new String[]{"laserbox"});
        assertNull(schedule.started);
        assertTrue(messages.getLast().contains("not in"));
        new EventStateStore(plugin).markArchived();
        start.onCommand(sender, null, "cc", new String[]{"tntrun"});
        assertNull(schedule.started);
        assertTrue(messages.getLast().contains("archived"));
    }

    @Test void laserBoxFailureShowsTheGameAndActualReasonInsteadOfBingo() {
        MessageConfig.GAME_LASER_BOX = "激光方盒";
        MessageConfig.EVENT_START_UNAVAILABLE = "%game%无法启动：%detail%。";
        schedule.action = ScheduleManager.EventAction.UNAVAILABLE;
        schedule.failure = "需要8个空闲场地副本，当前只有1个";
        new EventStartSubCommand().onCommand(sender, null, "cc", new String[]{"laserbox"});
        assertTrue(messages.getLast().contains("激光方盒"));
        assertTrue(messages.getLast().contains(schedule.failure));
        assertFalse(messages.getLast().contains("宾果"));
    }

    private void saveEvent(List<GameTypeEnum> games) throws Exception {
        var entries = games.stream().map(game -> new EventTeamImport.Game(game.name(), "default", game.name())).toList();
        long scoring = games.stream().filter(game -> game != GameTypeEnum.SulfurSoccer).count();
        new EventStateStore(plugin).save(new EventTeamImport.Event(UUID.randomUUID().toString(), "test-event", "Test",
                "READY", entries, Collections.nCopies((int) scoring, 1D)));
    }

    private static final class TestSchedule extends ScheduleManager {
        GameTypeEnum started, finale, stoppedFinale;
        EventAction action;
        String failure;
        private TestSchedule() { super(null); }
        @Override public EventAction startOrStopFormalEvent(GameTypeEnum game) {
            started = game;
            return action == null ? EventAction.STARTED : action;
        }
        @Override public String getFormalEventStartFailure(GameTypeEnum game) { return failure; }
        @Override public void requestFinale(GameTypeEnum game, String area, ChampionshipTeam right,
                                             ChampionshipTeam left, CommandSender sender, boolean force) {
            assertNull(area); assertNull(right); assertNull(left); assertFalse(force);
            finale = game;
        }
        @Override public boolean stopFinale(GameTypeEnum game) { stoppedFinale = game; return true; }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null)).allocateInstance(type));
    }
}
