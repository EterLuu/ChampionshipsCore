package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class NativeTeamServiceTest {
    @Test
    void repeatedProjectionDoesNotRebuildUnchangedTeamMetadata() {
        var state = new TeamState();
        var service = state.service();
        service.getOrCreateTeam("lobby", "DAILY", "white", "#ffffff", Team.OptionStatus.ALWAYS);
        var initial = Map.copyOf(state.writes);
        for (int i = 0; i < 64; i++)
            service.getOrCreateTeam("lobby", "DAILY", "white", "#ffffff", Team.OptionStatus.ALWAYS);
        assertEquals(initial, state.writes);
        assertEquals(Map.of("displayName", 1, "color", 1, "setOption", 1), initial);
    }

    @Test
    void projectionStillRepairsEveryExternallyChangedField() {
        var state = new TeamState();
        var service = state.service();
        service.configure(state.team, "DAILY", "white", "#ffffff", Team.OptionStatus.ALWAYS);
        state.name = Component.text("external");
        state.color = NamedTextColor.RED;
        state.collision = Team.OptionStatus.NEVER;
        service.configure(state.team, "DAILY", "white", "#ffffff", Team.OptionStatus.ALWAYS);
        assertEquals(Map.of("displayName", 2, "color", 2, "setOption", 2), state.writes);
        assertEquals(NamedTextColor.WHITE, state.color);
        assertEquals(Team.OptionStatus.ALWAYS, state.collision);
    }

    private static final class TeamState {
        Component name = Component.empty();
        NamedTextColor color;
        Team.OptionStatus collision = Team.OptionStatus.NEVER;
        final Map<String, Integer> writes = new HashMap<>();
        final Team team =
                (Team)
                        Proxy.newProxyInstance(
                                Team.class.getClassLoader(),
                                new Class<?>[] {Team.class},
                                (p, m, a) -> {
                                    if (m.getName().equals("hasColor")) return color != null;
                                    if (m.getName().equals("displayName")) {
                                        if (a == null || a.length == 0) return name;
                                        name = (Component) a[0];
                                    } else if (m.getName().equals("color")) {
                                        if (a == null || a.length == 0) {
                                            if (color == null)
                                                throw new IllegalStateException(
                                                        "Team does not have a color!");
                                            return color;
                                        }
                                        color = (NamedTextColor) a[0];
                                    } else if (m.getName().equals("getOption")) return collision;
                                    else if (m.getName().equals("setOption"))
                                        collision = (Team.OptionStatus) a[1];
                                    else throw new AssertionError(m.getName());
                                    writes.merge(m.getName(), 1, Integer::sum);
                                    return null;
                                });

        NativeTeamService service() {
            return new NativeTeamService(
                    (Scoreboard)
                            Proxy.newProxyInstance(
                                    Scoreboard.class.getClassLoader(),
                                    new Class<?>[] {Scoreboard.class},
                                    (p, m, a) -> {
                                        if (m.getName().equals("getTeam")) return team;
                                        throw new AssertionError(m.getName());
                                    }));
        }
    }

    @Test
    void resolvesConfiguredExactNamedColorBeforeDyeFallback() {
        assertEquals(NamedTextColor.RED, NativeTeamService.resolveNamedColor("green", "#ff5555"));
        assertEquals(
                NamedTextColor.DARK_GREEN, NativeTeamService.resolveNamedColor("green", "#123456"));
        assertEquals(NamedTextColor.WHITE, NativeTeamService.resolveNamedColor("unknown", null));
    }

    @Test
    void normalizesDuplicateEntriesAndRejectsBlankNames() {
        assertEquals(
                Set.of("Alice", "Bob"),
                NativeTeamService.normalizedEntries(List.of("Alice", "Bob", "Alice")));
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeTeamService.normalizedEntries(List.of("Alice", " ")));
    }

    @Test
    void validatesPortableNativeTeamIds() {
        NativeTeamService.validateScoreboardId("ccb_1z");
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeTeamService.validateScoreboardId("team-name"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeTeamService.validateScoreboardId("12345678901234567"));
    }

    @Test
    void remembersUnsupportedMutationForOptionalCallers() {
        Scoreboard scoreboard =
                (Scoreboard)
                        Proxy.newProxyInstance(
                                Scoreboard.class.getClassLoader(),
                                new Class<?>[] {Scoreboard.class},
                                (proxy, method, args) -> null);
        NativeTeamService service = new NativeTeamService(scoreboard);
        assertTrue(service.mutationSupported());
        service.markMutationUnsupported();
        assertFalse(service.mutationSupported());
    }
}
