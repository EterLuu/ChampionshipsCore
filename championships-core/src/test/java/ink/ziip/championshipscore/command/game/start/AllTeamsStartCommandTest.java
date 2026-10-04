package ink.ziip.championshipscore.command.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.AceRaceManager;
import ink.ziip.championshipscore.api.game.acerace.config.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.frostbite.FrostbiteManager;
import ink.ziip.championshipscore.api.game.frostbite.config.FrostbiteConfig;
import ink.ziip.championshipscore.api.game.frostbite.runtime.FrostbiteArea;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.riptiderush.RiptideRushManager;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ExtendWith(ConfigurationStateExtension.class)
class AllTeamsStartCommandTest {
    private static final String MAP = "frosty_fjord";
    private static final UUID ADMIN = new UUID(0, 1);
    private static final UUID RED_ONLINE = new UUID(1, 1);
    private static final UUID RED_OFFLINE = new UUID(1, 2);
    private static final UUID BLUE_ONLINE = new UUID(2, 1);
    private static final UUID BLUE_OFFLINE = new UUID(2, 2);
    private static final Set<UUID> ROSTER =
            Set.of(RED_ONLINE, RED_OFFLINE, BLUE_ONLINE, BLUE_OFFLINE);

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "RiptideRush", "AceRace"})
    void unteamedAdministratorCanStartTeamsWithOfflineMembers(GameTypeEnum game) throws Exception {
        assertCommandStarts(game, ADMIN, true);
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "RiptideRush", "AceRace"})
    void teamedPlayerCanStartTeamsWithOfflineMembers(GameTypeEnum game) throws Exception {
        assertCommandStarts(game, RED_ONLINE, true);
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "RiptideRush", "AceRace"})
    void consoleUsesTheSameTeamRoster(GameTypeEnum game) throws Exception {
        assertCommandStarts(game, ADMIN, false);
    }

    private static void assertCommandStarts(GameTypeEnum game, UUID senderId, boolean playerSender)
            throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        Field instanceField = ChampionshipsCore.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object previousInstance = instanceField.get(null);
        try {
            List<String> messages = new ArrayList<>();
            Map<UUID, Player> online =
                    Map.of(
                            RED_ONLINE,
                            player(RED_ONLINE, new ArrayList<>()),
                            BLUE_ONLINE,
                            player(BLUE_ONLINE, new ArrayList<>()));
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("runTask")) return task;
                                throw new AssertionError(
                                        "Unexpected scheduler call: " + m.getName());
                            });
            serverField.set(
                    null,
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> online.get(a[0]);
                                        case "isPrimaryThread" -> false;
                                        case "getScheduler" -> scheduler;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected server call: " + m.getName());
                                    }));

            ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
            instanceField.set(null, plugin);
            GameManager manager = allocate(GameManager.class);
            set(manager, "plugin", plugin);
            set(manager, "enabledGames", Set.of(game));
            for (String field :
                    List.of(
                            "teamStatus",
                            "playerStatus",
                            "playerSpectatorStatus",
                            "roundTransitionHolds",
                            "spectatorTransitionHolds"))
                set(manager, field, new ConcurrentHashMap<>());
            set(plugin, "gameManager", manager);
            set(plugin, "visibilityManager", new PlayerVisibilityManager(plugin));

            ChampionshipTeam red = new TestTeam(1, "red", Set.of(RED_ONLINE, RED_OFFLINE));
            ChampionshipTeam blue = new TestTeam(2, "blue", Set.of(BLUE_ONLINE, BLUE_OFFLINE));
            TeamManager teamManager = allocate(TeamManager.class);
            set(
                    teamManager,
                    "cachedTeams",
                    new ConcurrentHashMap<>(Map.of("red", red, "blue", blue)));
            set(teamManager, "pendingMemberTeamIds", Set.of());
            set(teamManager, "pendingTeamDeletions", Set.of());
            set(plugin, "teamManager", teamManager);

            BaseGameConfig config =
                    switch (game) {
                        case FrostbiteFrenzy -> new FrostbiteConfig(plugin, MAP);
                        case RiptideRush -> new RiptideRushConfig(plugin, MAP);
                        case AceRace -> new AceRaceConfig(plugin, MAP);
                        default -> throw new AssertionError(game);
                    };
            set(config, "preparePublished", true);
            set(config, "prepareDirty", false);
            BaseMultiTeamGameInstance area =
                    switch (game) {
                        case FrostbiteFrenzy -> allocate(FrostbiteArea.class);
                        case RiptideRush -> allocate(RiptideRushArea.class);
                        case AceRace -> allocate(AceRaceArea.class);
                        default -> throw new AssertionError(game);
                    };
            if (game == GameTypeEnum.AceRace) set(area, "respawnPoints", List.of());
            set(area, "plugin", plugin);
            set(area, "scheduler", scheduler);
            set(area, "gameConfig", config);
            set(area, "gameTypeEnum", game);
            set(area, "gameStageEnum", GameStageEnum.WAITING);
            set(area, "gameTeams", new ArrayList<ChampionshipTeam>());
            set(area, "gamePlayers", new ArrayList<UUID>());
            set(area, "startChunkTickets", new HashSet<>());
            BaseGameInstanceManager<?> gameAreas =
                    switch (game) {
                        case FrostbiteFrenzy -> new FrostbiteManager(plugin);
                        case RiptideRush -> new RiptideRushManager(plugin);
                        case AceRace -> new AceRaceManager(plugin);
                        default -> throw new AssertionError(game);
                    };
            if (game == GameTypeEnum.AceRace)
                set(
                        gameAreas,
                        "instancesByMap",
                        new ConcurrentHashMap<>(Map.of(MAP, List.of(area))));
            Field areas = BaseGameInstanceManager.class.getDeclaredField("areas");
            areas.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, BaseMultiTeamGameInstance> maps =
                    (Map<String, BaseMultiTeamGameInstance>) areas.get(gameAreas);
            maps.put(MAP, area);
            set(manager, "areaManagers", Map.of(game, gameAreas));
            PrepareSessionManager prepare = allocate(PrepareSessionManager.class);
            set(prepare, "plugin", plugin);
            set(prepare, "mapLocks", Map.of());
            set(plugin, "prepareSessionManager", prepare);

            // Reject outsiders before detaching spectators or changing any match ownership.
            set(manager, "playerSpectatorStatus", new ConcurrentHashMap<>(Map.of(ADMIN, area)));
            assertFalse(manager.joinSingleTeamAreaForAllTeams(game, MAP, List.of(ADMIN)));
            assertFalse(area.tryStartGame(List.of(red, blue), List.of(RED_ONLINE, ADMIN)));
            assertEquals(GameStageEnum.WAITING, area.getGameStageEnum());
            assertTrue(area.getParticipantUniqueIds().isEmpty());
            assertNull(manager.getBasePlayerArea(ADMIN));
            assertSame(area, manager.getPlayerSpectatorStatus(ADMIN));

            MessageConfig.GAME_SINGLE_GAME_START_SUCCESSFUL = "started %game% %area%";
            MessageConfig.GAME_SINGLE_GAME_START_FAILED = "failed %game% %area%";
            MessageConfig.GAME_FROSTBITE = "Frostbite";
            MessageConfig.GAME_RIPTIDE_RUSH = "RiptideRush";
            MessageConfig.GAME_ACE_RACE = "AceRace";
            CommandSender sender =
                    playerSender
                            ? player(senderId, messages)
                            : proxy(
                                    CommandSender.class,
                                    (p, m, a) -> {
                                        if (m.getName().equals("sendMessage")) {
                                            messages.add((String) a[0]);
                                            return null;
                                        }
                                        throw new AssertionError(
                                                "Unexpected sender call: " + m.getName());
                                    });
            var result =
                    new ink.ziip.championshipscore.api.game.start.GameStartService(plugin)
                            .startManual(
                                    ink.ziip.championshipscore.api.game.start.GameStartArguments
                                            .parse(
                                                    new String[] {game.commandName(), MAP, "all"},
                                                    false))
                            .toCompletableFuture()
                            .join();
            assertTrue(result.started(), result.detail());
            assertEquals(GameStageEnum.LOADING, area.getGameStageEnum());
            assertEquals(ROSTER, Set.copyOf(area.getParticipantUniqueIds()));
            assertEquals(Set.of(red, blue), Set.copyOf(area.getGameTeams()));
            assertFalse(area.getParticipantUniqueIds().contains(ADMIN));
            for (UUID id : ROSTER) assertSame(area, manager.getBasePlayerArea(id));
            assertNull(manager.getBasePlayerArea(ADMIN));
            assertSame(area, manager.getPlayerSpectatorStatus(ADMIN));
        } finally {
            serverField.set(null, previousServer);
            instanceField.set(null, previousInstance);
        }
    }

    private static Player player(UUID id, List<String> messages) {
        return proxy(
                Player.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "getUniqueId" -> id;
                            case "sendMessage" -> {
                                messages.add((String) a[0]);
                                yield null;
                            }
                            default ->
                                    throw new AssertionError(
                                            "Unexpected player call: " + m.getName());
                        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id, String name, Set<UUID> members) {
            super(id, name, name, "#FFFFFF", members, null);
        }
    }
}
