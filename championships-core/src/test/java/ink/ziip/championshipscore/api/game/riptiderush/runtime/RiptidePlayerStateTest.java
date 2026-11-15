package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideMathRun;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.RiptideRushMessages;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

class RiptidePlayerStateTest {
    @Nested
    class RiptideRushParticipationCases {
        @Test
        void eliminationEndsOnlyAfterTheCurrentRosterHasNoSurvivors() {
            int[][] cases = {
                {1, 1, 0}, {1, 0, 1}, {0, 0, 1}, {3, 2, 0}, {3, 1, 0}, {2, 1, 0}, {2, 0, 1}
            };
            for (int[] example : cases) {
                assertEquals(
                        example[2] == 1,
                        RiptideRushArea.shouldEndAfterElimination(example[0], example[1]),
                        "roster=" + example[0] + " survivors=" + example[1]);
            }
        }
    }

    @Nested
    class RiptideSpectatorMovementCases {
        @Test
        void eliminatedPlayerCanFlyFarOutsideRaftWithoutTeleportOrCancellation() throws Exception {

            var area = allocate(RiptideRushArea.class);
            UUID id = UUID.randomUUID();
            set(area, "gamePlayers", List.of(id));
            set(area, "eliminatedPlayers", Set.of(id));
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            Player player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class[] {Player.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("getUniqueId")) return id;
                                        if (method.getName().equals("teleport"))
                                            fail("Eliminated spectator must fly freely");
                                        return null;
                                    });
            var handler = new RiptideRushHandler(null);
            handler.setArea(area);
            var destination = new Location(null, 1000, 200, 1000);
            var event = new PlayerMoveEvent(player, new Location(null, 0, 80, 0), destination);
            assertDoesNotThrow(() -> handler.handleRoutedPlayerMoveLow(event));
            assertFalse(event.isCancelled());
            assertEquals(destination, event.getTo());
        }

        @Test
        void frequentAcceptedMovementDoesNotRunFallChecksOrConsumeGrace() throws Exception {

            var area = allocate(RiptideRushArea.class);
            var g = RiptideTestFixtures.config().resolveGeometry();
            UUID id = UUID.randomUUID();
            var plugin =
                    ink.ziip.championshipscore.support.TestObjects.allocate(
                            ink.ziip.championshipscore.ChampionshipsCore.class);
            var manager =
                    ink.ziip.championshipscore.support.TestObjects.allocate(
                            ink.ziip.championshipscore.api.game.manager.GameManager.class);
            set(manager, "playerSpectatorStatus", new java.util.HashMap<>());
            set(
                    manager,
                    "spectatorManager",
                    new ink.ziip.championshipscore.api.game.spectate.SpectatorManager(
                            plugin, manager));
            set(plugin, "gameManager", manager);
            set(area, "plugin", plugin);
            set(area, "gamePlayers", List.of(id));
            set(area, "eliminatedPlayers", Set.of());
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "geometry", g);
            set(area, "mathRuns", Map.of(id, new RiptideMathRun(g, List.of(), g.centerAt(0))));
            var checks = new java.util.HashMap<UUID, RiptideFallCheck>();
            set(area, "fallChecks", checks);
            Player player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class[] {Player.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("getUniqueId")) return id;
                                        if (method.getName().equals("getGameMode"))
                                            return org.bukkit.GameMode.ADVENTURE;
                                        throw new AssertionError(
                                                "Movement must not probe player fall state: "
                                                        + method.getName());
                                    });
            var serverField =
                    ink.ziip.championshipscore.support.TestObjects.field(
                            org.bukkit.Bukkit.class, "server");
            Object previousServer = serverField.get(null);
            serverField.set(
                    null,
                    ink.ziip.championshipscore.support.TestObjects.proxy(
                            org.bukkit.Server.class,
                            (server, method, args) ->
                                    method.getName().equals("getPlayer") ? player : null));
            try {
                var handler = new RiptideRushHandler(null);
                handler.setArea(area);
                for (int packet = 0; packet < 100; packet++) {
                    var event =
                            new PlayerMoveEvent(
                                    player, g.centerAt(0), g.centerAt(0).add(10, -1, 0));
                    handler.onAcceptedMathMovement(event);
                    assertFalse(event.isCancelled());
                }
                assertTrue(
                        checks.isEmpty(),
                        "Only the course tick may create or advance fall confirmations");
            } finally {
                serverField.set(null, previousServer);
            }
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class RiptideEliminationMessagesCases {
        @BeforeEach
        void resetMessages() {
            RiptideRushMessages.RIPTIDE_RUSH_REASON_FELL = null;
            RiptideRushMessages.RIPTIDE_RUSH_REASON_LEFT_BEHIND = null;
            RiptideRushMessages.RIPTIDE_RUSH_REASON_WRONG_FLOOR = null;
            RiptideRushMessages.RIPTIDE_RUSH_REASON_WRONG_ANSWER = null;
            RiptideRushMessages.RIPTIDE_RUSH_REASON_MISSED_GATE = null;
            RiptideRushMessages.RIPTIDE_RUSH_REASON_DISCONNECTED = null;
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED = null;
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_FALL = null;
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_FLOOR = null;
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_MATH = null;
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED = null;
        }

        @Test
        void eachEliminationCauseChoosesOnlyFromItsConfiguredVariants() {
            RiptideRushMessages.RIPTIDE_RUSH_REASON_FELL = "fell";
            RiptideRushMessages.RIPTIDE_RUSH_REASON_LEFT_BEHIND = "left";
            RiptideRushMessages.RIPTIDE_RUSH_REASON_WRONG_FLOOR = "floor";
            RiptideRushMessages.RIPTIDE_RUSH_REASON_WRONG_ANSWER = "answer";
            RiptideRushMessages.RIPTIDE_RUSH_REASON_MISSED_GATE = "gate";
            RiptideRushMessages.RIPTIDE_RUSH_REASON_DISCONNECTED = "disconnected";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED = "fallback";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_FALL = "fall-a\n\nfall-b";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_FLOOR = "floor-a\nfloor-b";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_MATH = "math-a\nmath-b";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED = "disconnect-a\ndisconnect-b";

            assertVariants(
                    RiptideRushMessages.RIPTIDE_RUSH_REASON_FELL, Set.of("fall-a", "fall-b"));
            assertVariants(
                    RiptideRushMessages.RIPTIDE_RUSH_REASON_LEFT_BEHIND,
                    Set.of("fall-a", "fall-b"));
            assertVariants(
                    RiptideRushMessages.RIPTIDE_RUSH_REASON_WRONG_FLOOR,
                    Set.of("floor-a", "floor-b"));
            assertVariants(
                    RiptideRushMessages.RIPTIDE_RUSH_REASON_WRONG_ANSWER,
                    Set.of("math-a", "math-b"));
            assertVariants(
                    RiptideRushMessages.RIPTIDE_RUSH_REASON_MISSED_GATE,
                    Set.of("math-a", "math-b"));
            assertVariants(
                    RiptideRushMessages.RIPTIDE_RUSH_REASON_DISCONNECTED,
                    Set.of("disconnect-a", "disconnect-b"));
        }

        @Test
        void missingOrBlankCategoriesFallBackToTheGenericAnnouncement() {
            RiptideRushMessages.RIPTIDE_RUSH_REASON_FELL = "fell";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED = "fallback";
            RiptideRushMessages.RIPTIDE_RUSH_ELIMINATED_FALL = " \n ";

            assertEquals(
                    "fallback",
                    RiptideEliminationMessages.choose(
                            RiptideRushMessages.RIPTIDE_RUSH_REASON_FELL, new Random(1)));
        }

        private static void assertVariants(String reason, Set<String> expected) {
            Set<String> actual = new HashSet<>();
            Random random = new Random(7);
            for (int attempt = 0; attempt < 100 && !actual.equals(expected); attempt++)
                actual.add(RiptideEliminationMessages.choose(reason, random));
            assertEquals(expected, actual);
        }
    }
}
