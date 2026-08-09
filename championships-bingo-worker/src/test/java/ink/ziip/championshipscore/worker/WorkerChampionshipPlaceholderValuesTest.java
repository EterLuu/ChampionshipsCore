package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkerChampionshipPlaceholderValuesTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BingoPresentation PRESENTATION = new BingoPresentation(Map.of(
            "papi.none", "无", "papi.spectator", "旁观"));
    private static final TeamSnapshot TEAM = new TeamSnapshot(3, "金队", "YELLOW", "&#fff566",
            List.of(PLAYER_ID), 1234.5D);
    private static final PlayerSnapshot PLAYER = new PlayerSnapshot(PLAYER_ID, "Player",
            ParticipantRole.PLAYER, 3, true, 321.5D);

    @Test
    void matchesCoreTeamAndPointPlaceholders() {
        assertEquals("金队", resolve("player_team_name_no_color"));
        assertEquals("§x§f§f§f§5§6§6金队", resolve("player_team_name"));
        assertEquals("&#fff566", resolve("player_team_color_code"));
        assertEquals("YELLOW", resolve("player_team_color"));
        assertEquals("322", resolve("player_points"));
        assertEquals("1235", resolve("player_team_points"));
        assertNull(resolve("player_rank"));
    }

    @Test
    void spectatorUsesCoreFallbackTextAndZeroPoints() {
        PlayerSnapshot spectator = new PlayerSnapshot(PLAYER_ID, "Viewer",
                ParticipantRole.SPECTATOR, null, false, 0D);

        assertEquals("旁观", WorkerChampionshipPlaceholderValues.resolve(
                spectator, null, PRESENTATION, "player_team_name"));
        assertEquals("无", WorkerChampionshipPlaceholderValues.resolve(
                spectator, null, PRESENTATION, "player_team_color"));
        assertEquals("0", WorkerChampionshipPlaceholderValues.resolve(
                spectator, null, PRESENTATION, "player_team_points"));
    }

    private static String resolve(String params) {
        return WorkerChampionshipPlaceholderValues.resolve(PLAYER, TEAM, PRESENTATION, params);
    }
}
