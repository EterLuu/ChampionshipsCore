package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

class RiptidePlayerStateTest {
    @Nested
    class RiptideRushParticipationCases {
        @Test
        void formalRoundsRequireTwoActualStartersButPracticeAllowsOne() {
            assertFalse(RiptideRushArea.canStartRound(true, 0));
            assertFalse(RiptideRushArea.canStartRound(true, 1));
            assertTrue(RiptideRushArea.canStartRound(true, 2));
            assertFalse(RiptideRushArea.canStartRound(false, 0));
            assertTrue(RiptideRushArea.canStartRound(false, 1));
        }

        @Test
        void eliminatingOfflineTeammatesDoesNotEndSoloRunBeforeMovementStarts() {
            // The reported roster contained one online player and one disconnected teammate.
            int rosterSize = 2;
            int offlinePlayers = 1;
            assertFalse(RiptideRushArea.shouldEndAfterElimination(rosterSize - offlinePlayers, 1));
        }

        @Test
        void soloRunStillEndsWhenItsOnlyPlayerIsEliminated() {
            assertTrue(RiptideRushArea.shouldEndAfterElimination(1, 0));
            assertTrue(RiptideRushArea.shouldEndAfterElimination(0, 0));
        }

        @Test
        void multiplayerKeepsTheLastSurvivorInTheRound() {
            assertFalse(RiptideRushArea.shouldEndAfterElimination(3, 2));
            assertFalse(RiptideRushArea.shouldEndAfterElimination(3, 1));
            assertFalse(RiptideRushArea.shouldEndAfterElimination(2, 1));
            assertTrue(RiptideRushArea.shouldEndAfterElimination(2, 0));
        }
    }

    @Nested
    class RiptideSpectatorMovementCases {
        @Test void eliminatedPlayerCanFlyFarOutsideRaftWithoutTeleportOrCancellation() throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var area = (RiptideRushArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(RiptideRushArea.class);
            UUID id = UUID.randomUUID();
            set(area, "gamePlayers", List.of(id));
            set(area, "eliminatedPlayers", Set.of(id));
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getUniqueId")) return id;
                        if (method.getName().equals("teleport")) fail("Eliminated spectator must fly freely");
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

        @Test void frequentAcceptedMovementDoesNotRunFallChecksOrConsumeGrace() throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var area = (RiptideRushArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(RiptideRushArea.class);
            var g = RiptideTestFixtures.config().resolveGeometry();
            UUID id = UUID.randomUUID();
            set(area, "gamePlayers", List.of(id));
            set(area, "eliminatedPlayers", Set.of());
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "geometry", g);
            set(area, "mathRuns", Map.of(id, new RiptideMathRun(g, List.of(), g.centerAt(0))));
            var checks = new java.util.HashMap<UUID, RiptideFallCheck>();
            set(area, "fallChecks", checks);
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getUniqueId")) return id;
                        throw new AssertionError("Movement must not probe player fall state: " + method.getName());
                    });
            var handler = new RiptideRushHandler(null);
            handler.setArea(area);
            for (int packet = 0; packet < 100; packet++) {
                var event = new PlayerMoveEvent(player, g.centerAt(0), g.centerAt(0).add(10, -1, 0));
                handler.onAcceptedMathMovement(event);
                assertFalse(event.isCancelled());
            }
            assertTrue(checks.isEmpty(), "Only the course tick may create or advance fall confirmations");
        }

        private static void set(Object target, String name, Object value) throws Exception {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(name);
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class RiptideEliminationMessagesCases {
        @BeforeEach
        void resetMessages() {
            MessageConfig.RIPTIDE_RUSH_REASON_FELL = null;
            MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND = null;
            MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR = null;
            MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER = null;
            MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE = null;
            MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED = null;
            MessageConfig.RIPTIDE_RUSH_ELIMINATED = null;
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL = null;
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_FLOOR = null;
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_MATH = null;
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED = null;
        }

        @Test
        void eachEliminationCauseChoosesOnlyFromItsConfiguredVariants() {
            MessageConfig.RIPTIDE_RUSH_REASON_FELL = "fell";
            MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND = "left";
            MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR = "floor";
            MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER = "answer";
            MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE = "gate";
            MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED = "disconnected";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED = "fallback";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL = "fall-a\n\nfall-b";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_FLOOR = "floor-a\nfloor-b";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_MATH = "math-a\nmath-b";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED = "disconnect-a\ndisconnect-b";

            assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_FELL, Set.of("fall-a", "fall-b"));
            assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND, Set.of("fall-a", "fall-b"));
            assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR, Set.of("floor-a", "floor-b"));
            assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER, Set.of("math-a", "math-b"));
            assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE, Set.of("math-a", "math-b"));
            assertVariants(MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED, Set.of("disconnect-a", "disconnect-b"));
        }

        @Test
        void missingOrBlankCategoriesFallBackToTheGenericAnnouncement() {
            MessageConfig.RIPTIDE_RUSH_REASON_FELL = "fell";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED = "fallback";
            MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL = " \n ";

            assertEquals("fallback", RiptideEliminationMessages.choose(
                    MessageConfig.RIPTIDE_RUSH_REASON_FELL, new Random(1)));
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
