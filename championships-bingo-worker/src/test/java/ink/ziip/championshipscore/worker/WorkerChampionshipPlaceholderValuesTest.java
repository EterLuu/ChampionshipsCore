package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import org.junit.jupiter.api.Test;

import java.util.*;

class WorkerChampionshipPlaceholderValuesTest {
    @Test
    void frozenCustomPlayerFormatsApplyToBothDailyAndChampionshipRoles() {
        var format =
                new ink.ziip.championshipscore.shared.presentation.PlayerTextFormat(
                        "<%label%> ", "Game %game%", "&7", "%player%", "%message%", "%message%");
        var values = new HashMap<>(PRESENTATION.messages());
        values.putAll(format.attributes());
        var frozen = new BingoPresentation(values);
        values.put("presentation.player.prefix", "changed");
        assertEquals(
                "<Game 宾果时速> ",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, frozen, "tab_prefix", true));
        assertEquals(
                "<§x§f§f§f§5§6§6金队> ",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, frozen, "tab_prefix", false));
        assertEquals(
                "§7",
                WorkerChampionshipPlaceholderValues.resolve(
                        null, null, frozen, "tab_name_color", true));
        assertEquals(
                "§x§f§f§f§5§6§6",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, frozen, "tab_name_color", true));
    }

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BingoPresentation PRESENTATION =
            new BingoPresentation(
                    Map.of(
                            "game.name",
                            "宾果时速",
                            "papi.none",
                            "无",
                            "papi.spectator",
                            "旁观",
                            "presentation.daily-game",
                            "&6%game%",
                            "presentation.tab.team-footer",
                            "&f队伍: %team% &f| 积分: %points%",
                            "presentation.tab.daily-team-footer",
                            "&f队伍: %team%",
                            "presentation.tab.current-game-footer",
                            "&f当前游戏: &b%game%"));
    private static final TeamSnapshot TEAM =
            new TeamSnapshot(3, "金队", "YELLOW", "&#fff566", List.of(PLAYER_ID), 1234.5D);
    private static final PlayerSnapshot PLAYER =
            new PlayerSnapshot(PLAYER_ID, "Player", ParticipantRole.PLAYER, 3, true, 321.5D);

    private static String resolve(String params) {
        return WorkerChampionshipPlaceholderValues.resolve(PLAYER, TEAM, PRESENTATION, params);
    }

    @Test
    void championshipPlaceholdersKeepCoreIdentityPointsAndTabPrefix() {
        Map<String, String> expected =
                Map.of(
                        "player_team_name_no_color", "金队",
                        "player_team_name", "§x§f§f§f§5§6§6金队",
                        "player_team_color_code", "&#fff566",
                        "player_team_color", "YELLOW",
                        "player_points", "322",
                        "player_team_points", "1235",
                        "tab_name_color", "§x§f§f§f§5§6§6",
                        "tab_footer_status", "§f队伍: §x§f§f§f§5§6§6金队 §f| 积分: 1235",
                        "tab_prefix", "§8[§x§f§f§f§5§6§6金队§8]§r ");
        expected.forEach((key, value) -> assertEquals(value, resolve(key), key));
        assertNull(resolve("player_rank"));
    }

    @Test
    void spectatorUsesCoreFallbackTextAndZeroPoints() {
        PlayerSnapshot spectator =
                new PlayerSnapshot(PLAYER_ID, "Viewer", ParticipantRole.SPECTATOR, null, false, 0D);

        assertEquals(
                "旁观",
                WorkerChampionshipPlaceholderValues.resolve(
                        spectator, null, PRESENTATION, "player_team_name"));
        assertEquals(
                "无",
                WorkerChampionshipPlaceholderValues.resolve(
                        spectator, null, PRESENTATION, "player_team_color"));
        assertEquals(
                "0",
                WorkerChampionshipPlaceholderValues.resolve(
                        spectator, null, PRESENTATION, "player_team_points"));
        assertEquals(
                "§f",
                WorkerChampionshipPlaceholderValues.resolve(
                        spectator, null, PRESENTATION, "tab_name_color"));
    }

    @Test
    void dailyShowsItsTemporaryColorTeamWithoutChampionshipPoints() {
        assertEquals(
                "§x§f§f§f§5§6§6金队",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, PRESENTATION, "player_team_name", true));
        assertEquals(
                "YELLOW",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, PRESENTATION, "player_team_color", true));
        assertEquals(
                "0",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, PRESENTATION, "player_team_points", true));
        assertEquals(
                "§8[§x§f§f§f§5§6§6宾果时速§8]§r ",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, PRESENTATION, "tab_prefix", true));
        assertEquals(
                "§f队伍: §x§f§f§f§5§6§6金队",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, PRESENTATION, "tab_footer_status", true));
        assertEquals(
                "§x§f§f§f§5§6§6",
                WorkerChampionshipPlaceholderValues.resolve(
                        PLAYER, TEAM, PRESENTATION, "tab_name_color", true));
    }

    @Test
    void dailySpectatorUsesTheSameGameIdentityAsCore() {
        PlayerSnapshot spectator =
                new PlayerSnapshot(PLAYER_ID, "Viewer", ParticipantRole.SPECTATOR, null, false, 0D);
        assertEquals(
                "§8[§6宾果时速§8]§r ",
                WorkerChampionshipPlaceholderValues.resolve(
                        spectator, null, PRESENTATION, "tab_prefix", true));
        assertEquals(
                "§f当前游戏: §b宾果时速",
                WorkerChampionshipPlaceholderValues.resolve(
                        spectator, null, PRESENTATION, "tab_footer_status", true));
    }
}
