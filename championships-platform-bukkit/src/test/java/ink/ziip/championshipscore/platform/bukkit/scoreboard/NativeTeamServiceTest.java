package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeTeamServiceTest {
    @Test
    void resolvesConfiguredExactNamedColorBeforeDyeFallback() {
        assertEquals(NamedTextColor.RED, NativeTeamService.resolveNamedColor("green", "#ff5555"));
        assertEquals(NamedTextColor.DARK_GREEN, NativeTeamService.resolveNamedColor("green", "#123456"));
        assertEquals(NamedTextColor.WHITE, NativeTeamService.resolveNamedColor("unknown", null));
    }

    @Test
    void normalizesDuplicateEntriesAndRejectsBlankNames() {
        assertEquals(Set.of("Alice", "Bob"),
                NativeTeamService.normalizedEntries(List.of("Alice", "Bob", "Alice")));
        assertThrows(IllegalArgumentException.class,
                () -> NativeTeamService.normalizedEntries(List.of("Alice", " ")));
    }

    @Test
    void validatesPortableNativeTeamIds() {
        NativeTeamService.validateScoreboardId("ccb_1z");
        assertThrows(IllegalArgumentException.class,
                () -> NativeTeamService.validateScoreboardId("team-name"));
        assertThrows(IllegalArgumentException.class,
                () -> NativeTeamService.validateScoreboardId("12345678901234567"));
    }

    @Test
    void remembersUnsupportedMutationForOptionalCallers() {
        Scoreboard scoreboard = (Scoreboard) Proxy.newProxyInstance(
                Scoreboard.class.getClassLoader(), new Class<?>[]{Scoreboard.class},
                (proxy, method, args) -> null);
        NativeTeamService service = new NativeTeamService(scoreboard);
        assertTrue(service.mutationSupported());
        service.markMutationUnsupported();
        assertFalse(service.mutationSupported());
    }
}
