package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

class WorkerMatchSessionTest {
    @Test
    void spectatorTemplateArgumentsPreserveFrozenPrefixAsLiteralText() throws Exception {
        var session = allocate(WorkerMatchSession.class);
        set(
                session,
                "manifest",
                presentationManifest(
                        MatchRunMode.DAILY,
                        Map.of("prefix", "&dPrefix %speed% "),
                        List.of(),
                        List.of()));
        var player =
                (org.bukkit.entity.Player)
                        java.lang.reflect.Proxy.newProxyInstance(
                                org.bukkit.entity.Player.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.entity.Player.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getFlySpeed")) return .15F;
                                    throw new AssertionError(method.getName());
                                });
        var method =
                WorkerMatchSession.class.getDeclaredMethod(
                        "spectatorControlValues", org.bukkit.entity.Player.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var values = (Map<String, String>) method.invoke(session, player);
        assertEquals(
                "&dPrefix %speed% 15%",
                ink.ziip.championshipscore.shared.presentation.MessageTemplate.render(
                        "%prefix%%speed%", values));
    }

    @Test
    void partialSpectatorControlsFailBeforeWorldAndPlayerSetup() {
        var manifest =
                presentationManifest(
                        MatchRunMode.DAILY,
                        Map.of("gui.spectator-controls.speed.title", "broken"),
                        List.of(),
                        List.of());
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WorkerMatchSession(
                                        null, null, manifest, null, null, null, null, null));
        assertTrue(error.getMessage().contains("gui.spectator-controls.version"));
    }

    @Test
    void legacyRootStylesMatchOldOverridesAndPreserveChildColors() throws Exception {
        var method =
                WorkerMatchSession.class.getDeclaredMethod(
                        "legacyControlStyle",
                        net.kyori.adventure.text.Component.class,
                        String.class);
        method.setAccessible(true);
        var text =
                ink.ziip.championshipscore.platform.bukkit.text.LegacyText.component(
                                "&dRoot &bChild")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        var expected =
                text.color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        assertEquals(expected, method.invoke(null, text, "teleport.title"));
        assertEquals(
                text.color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                method.invoke(null, text, "teleport.hint"));
    }

    @Test
    void sessionAndWaitingRegistryUseFrozenFormatsAndConfiguredSpectatorGameLabel()
            throws Exception {
        var format =
                new ink.ziip.championshipscore.shared.presentation.PlayerTextFormat(
                        "{%label%}", "%game%", "&7", "%player_name%", "%message%", "%message%");
        var messages = new HashMap<>(format.attributes());
        messages.put("game.name", "Bingo %game%");
        messages.put("presentation.daily-game", "watching %game%");
        messages.put("papi.spectator", "Viewers");
        UUID player = UUID.randomUUID(), viewer = UUID.randomUUID();
        var playing = new PlayerSnapshot(player, "Alice", ParticipantRole.PLAYER, 1);
        var watching = new PlayerSnapshot(viewer, "Bob", ParticipantRole.SPECTATOR, null);
        var team = new TeamSnapshot(1, "Red", "red", "&c", List.of(player), 0);
        for (var mode : MatchRunMode.values()) {
            var manifest =
                    presentationManifest(mode, messages, List.of(team), List.of(playing, watching));
            var session = allocate(WorkerMatchSession.class);
            set(session, "manifest", manifest);
            set(session, "participants", Map.of(player, playing, viewer, watching));
            set(session, "teams", Map.of(1, team));
            assertEquals(format, session.playerPresentation(player).format());
            assertTrue(session.playerPresentation(player).activePlayer());
            assertEquals("&cRed", session.playerPresentation(player).label());
            assertFalse(session.playerPresentation(viewer).activePlayer());
            assertEquals(
                    mode == MatchRunMode.DAILY ? "watching Bingo %game%" : "Viewers",
                    session.playerPresentation(viewer).label());
        }
        var registry = allocate(WorkerMatchRegistry.class);
        set(registry, "latestPresentation", new BingoPresentation(messages));
        set(registry, "latestDaily", true);
        assertEquals(format, registry.playerPresentation(viewer).format());
        assertEquals("Viewers", registry.playerPresentation(viewer).label());
    }

    @Test
    void preparationRejectsPartialPlayerTextBeforeWorldOrInventoryWork() {
        var manifest =
                presentationManifest(
                        MatchRunMode.DAILY,
                        Map.of("presentation.player.prefix", "broken"),
                        List.of(),
                        List.of());
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WorkerMatchSession(
                                        null, null, manifest, null, null, null, null, null));
        assertTrue(error.getMessage().contains("presentation.player.version"));
    }

    @Test
    void preparationRejectsCorruptFrozenTaskTextBeforeWorldOrInventoryWork() {
        var manifest =
                presentationManifest(
                        MatchRunMode.DAILY,
                        Map.of(),
                        List.of(),
                        List.of(),
                        Map.of(
                                "material",
                                "STONE",
                                "display.text-version",
                                "1",
                                "display.name",
                                "{",
                                "display.lore-size",
                                "0"));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WorkerMatchSession(
                                        null, null, manifest, null, null, null, null, null));
        assertTrue(error.getMessage().contains("frozen Bingo task text"));
    }

    private static MatchManifest presentationManifest(
            MatchRunMode mode,
            Map<String, String> messages,
            List<TeamSnapshot> teams,
            List<PlayerSnapshot> players) {
        return presentationManifest(mode, messages, teams, players, Map.of("material", "STONE"));
    }

    private static MatchManifest presentationManifest(
            MatchRunMode mode,
            Map<String, String> messages,
            List<TeamSnapshot> teams,
            List<PlayerSnapshot> players,
            Map<String, String> taskAttributes) {
        return new MatchManifest(
                ProtocolVersion.CURRENT,
                UUID.randomUUID(),
                1,
                1,
                "worker",
                mode,
                60,
                1,
                "config",
                new BingoScoringRules(1, List.of(1), 0, 0, 0),
                new BingoRuntimeRules(
                        90,
                        100,
                        10,
                        130,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(messages)),
                List.of(new BingoTaskSpec(0, "stone", "item", taskAttributes)),
                teams,
                players);
    }

    @Test
    void createsStablePortableIdsForProtocolTeams() {
        assertEquals("ccb_0", WorkerMatchSession.nativeTeamId(0));
        assertEquals("ccb_z", WorkerMatchSession.nativeTeamId(35));
        assertTrue(WorkerMatchSession.nativeTeamId(Integer.MAX_VALUE).length() <= 16);
        assertThrows(IllegalArgumentException.class, () -> WorkerMatchSession.nativeTeamId(-1));
    }

    @Test
    void runningBossBarFormatsBothRoundAndProtectionTimersWithTheStrictSixtySecondBoundary()
            throws Exception {
        var messages =
                Map.of(
                        "bingo.timer",
                        "剩余%time%",
                        "bingo.pvp-protection",
                        "PvP保护%time%",
                        "bingo.pvp-active",
                        "PvP已开启");
        var rules =
                new BingoRuntimeRules(
                        90,
                        100,
                        10,
                        130,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(messages));
        var lifecycle = new MatchStateMachine();
        for (MatchState next :
                List.of(
                        MatchState.PREPARING,
                        MatchState.READY,
                        MatchState.ROUTING,
                        MatchState.COUNTDOWN,
                        MatchState.RUNNING)) lifecycle.transitionTo(next);
        var session = allocate(WorkerMatchSession.class);
        set(session, "lifecycle", lifecycle);
        set(session, "participants", Map.of());
        var update = WorkerMatchSession.class.getDeclaredMethod("updateRunningTimer");
        update.setAccessible(true);
        for (var entry : Map.of(700, "11分40秒", 61, "1分1秒", 60, "60秒").entrySet()) {
            var manifest =
                    new MatchManifest(
                            ProtocolVersion.CURRENT,
                            UUID.randomUUID(),
                            1,
                            1,
                            "worker",
                            MatchRunMode.DAILY,
                            entry.getKey(),
                            1,
                            "config",
                            new BingoScoringRules(1, List.of(1), 0, 0, 0),
                            rules,
                            List.of(
                                    new BingoTaskSpec(
                                            0, "stone", "item", Map.of("material", "STONE"))),
                            List.of(),
                            List.of());
            set(session, "manifest", manifest);
            set(session, "startedAtMillis", System.currentTimeMillis());
            set(session, "pvpEnabled", false);
            update.invoke(session);
            BossBar bar = (BossBar) field(WorkerMatchSession.class, "timerBossBar").get(session);
            assertEquals(
                    "剩余" + entry.getValue() + " • PvP保护2分10秒",
                    PlainTextComponentSerializer.plainText().serialize(bar.name()));
            assertEquals(1F, bar.progress());
            set(session, "pvpEnabled", true);
            update.invoke(session);
            assertEquals(
                    "剩余" + entry.getValue() + " • PvP已开启",
                    PlainTextComponentSerializer.plainText().serialize(bar.name()));
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
