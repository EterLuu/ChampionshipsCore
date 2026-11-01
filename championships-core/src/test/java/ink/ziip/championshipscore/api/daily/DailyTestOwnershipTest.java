package ink.ziip.championshipscore.api.daily;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.model.ServerMode;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.test.*;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.*;

@ExtendWith(ConfigurationStateExtension.class)
class DailyTestOwnershipTest {
    @org.junit.jupiter.api.BeforeEach
    void enableGameTestsForFixture() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = true;
    }

    @Test
    void preparingTestMemberKeepsItsTeamAndCannotReceiveDailyItemOrPartyInvites() throws Exception {
        try (var f = new Fixture()) {
            var sync = DailyManager.class.getDeclaredMethod("syncLobbyIdentity", Player.class);
            sync.setAccessible(true);
            sync.invoke(f.daily, f.bot);
            assertEquals(List.of(f.botId), f.teams.cleared);
            assertTrue(f.teams.applied.isEmpty());
            assertFalse(f.daily.canJoinParty(f.botId));
            assertEquals(
                    DailyManager.PartyUnavailableReason.IN_GAME,
                    f.daily.partyUnavailableReason(f.botId));
            var item = DailyManager.class.getDeclaredMethod("canReceiveLobbyItem", Player.class);
            item.setAccessible(true);
            assertEquals(false, item.invoke(f.daily, f.bot));
            sync.invoke(f.daily, f.ordinary);
            assertEquals(List.of(f.ordinaryId), f.teams.applied);
            assertTrue(f.daily.canJoinParty(f.ordinaryId));
            assertEquals(true, item.invoke(f.daily, f.ordinary));
        }
    }

    @Test
    void preparingTestMemberCannotEnterQueueEvenBeforeBingoVoteOrRulesResolution()
            throws Exception {
        try (var f = new Fixture()) {
            MessageConfig.DAILY_ALREADY_PLAYING = "busy";
            MessageConfig.DAILY_PREFIX = "";
            MessageConfig.DAILY_PREFIXED = "%message%";
            // Adapters/vote are deliberately absent: no DAILY side effect may precede rejection.
            assertFalse(f.daily.selectGame(f.bot, GameTypeEnum.Bingo));
            assertFalse(f.daily.selectGame(f.bot, GameTypeEnum.TNTRun));
            assertEquals(2, f.messages);
            assertFalse(f.daily.isQueued(f.botId));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Server previous = Bukkit.getServer();
        final UUID botId = UUID.randomUUID(), ordinaryId = UUID.randomUUID();
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final DailyManager daily = allocate(DailyManager.class);
        final RecordingTeams teams = allocate(RecordingTeams.class);
        final TestSessionManager tests = new TestSessionManager(null);
        final Player bot = player(botId), ordinary = player(ordinaryId);
        int messages;

        Fixture() throws Exception {
            teams.applied = new ArrayList<>();
            teams.cleared = new ArrayList<>();
            var games = allocate(GameManager.class);
            for (String name :
                    List.of("playerStatus", "playerSpectatorStatus", "roundTransitionHolds"))
                set(games, name, new HashMap<>());
            set(plugin, "gameManager", games);
            set(plugin, "teamManager", teams);
            set(plugin, "testSessionManager", tests);
            set(daily, "plugin", plugin);
            set(daily, "serverMode", ServerMode.DAILY);
            set(daily, "sessionByPlayer", new HashMap<>());
            set(daily, "queueByPlayer", new HashMap<>());
            set(daily, "partyManager", new DailyPartyManager(daily));
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                if (m.getName().equals("getPlayer"))
                                    return botId.equals(a[0]) ? bot : ordinary;
                                throw new AssertionError(m.getName());
                            });
            field(Bukkit.class, "server").set(null, server);
            var roster =
                    TestRoster.fromBotUsernames(
                            java.util.stream.IntStream.range(0, 64)
                                    .mapToObj(i -> "testbot" + i)
                                    .toList());
            tests.createSession(TestSessionMode.SINGLE_GAME, roster);
            Instant now = Instant.now();
            tests.registerLease("testbot0", botId, now.plusSeconds(60), now);
            assertTrue(tests.observePreLogin("testbot0", botId, now).accepted());
        }

        Player player(UUID id) {
            return proxy(
                    Player.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getUniqueId" -> id;
                                case "sendMessage" -> {
                                    messages++;
                                    yield null;
                                }
                                default -> throw new AssertionError(m.getName());
                            });
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previous);
        }
    }

    private static final class RecordingTeams extends TeamManager {
        List<UUID> applied, cleared;

        RecordingTeams() {
            super(null);
        }

        @Override
        public synchronized void applyDailyLobbyIdentity(Player player) {
            applied.add(player.getUniqueId());
        }

        @Override
        public synchronized void clearDailyLobbyIdentity(Player player) {
            cleared.add(player.getUniqueId());
        }
    }
}
