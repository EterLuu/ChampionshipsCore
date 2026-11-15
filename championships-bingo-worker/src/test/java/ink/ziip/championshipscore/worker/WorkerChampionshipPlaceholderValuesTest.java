package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

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

    private static final PlayerSnapshot SPECTATOR =
            new PlayerSnapshot(PLAYER_ID, "Viewer", ParticipantRole.SPECTATOR, null, false, 0D);

    static Stream<Arguments> placeholders() {
        return Stream.of(
                // Championship player: Core identity, rounded points and team tab prefix.
                Arguments.of(PLAYER, TEAM, false, "player_team_name_no_color", "金队"),
                Arguments.of(PLAYER, TEAM, false, "player_team_name", "§x§f§f§f§5§6§6金队"),
                Arguments.of(PLAYER, TEAM, false, "player_team_color_code", "&#fff566"),
                Arguments.of(PLAYER, TEAM, false, "player_team_color", "YELLOW"),
                Arguments.of(PLAYER, TEAM, false, "player_points", "322"),
                Arguments.of(PLAYER, TEAM, false, "player_team_points", "1235"),
                Arguments.of(PLAYER, TEAM, false, "tab_name_color", "§x§f§f§f§5§6§6"),
                Arguments.of(
                        PLAYER,
                        TEAM,
                        false,
                        "tab_footer_status",
                        "§f队伍: §x§f§f§f§5§6§6金队 §f| 积分: 1235"),
                Arguments.of(PLAYER, TEAM, false, "tab_prefix", "§8[§x§f§f§f§5§6§6金队§8]§r "),
                Arguments.of(PLAYER, TEAM, false, "player_rank", null),
                // Championship spectator: Core fallback text and zero points.
                Arguments.of(SPECTATOR, null, false, "player_team_name", "旁观"),
                Arguments.of(SPECTATOR, null, false, "player_team_color", "无"),
                Arguments.of(SPECTATOR, null, false, "player_team_points", "0"),
                Arguments.of(SPECTATOR, null, false, "tab_name_color", "§f"),
                // DAILY player: temporary color team without championship points.
                Arguments.of(PLAYER, TEAM, true, "player_team_name", "§x§f§f§f§5§6§6金队"),
                Arguments.of(PLAYER, TEAM, true, "player_team_color", "YELLOW"),
                Arguments.of(PLAYER, TEAM, true, "player_team_points", "0"),
                Arguments.of(PLAYER, TEAM, true, "tab_prefix", "§8[§x§f§f§f§5§6§6宾果时速§8]§r "),
                Arguments.of(PLAYER, TEAM, true, "tab_footer_status", "§f队伍: §x§f§f§f§5§6§6金队"),
                Arguments.of(PLAYER, TEAM, true, "tab_name_color", "§x§f§f§f§5§6§6"),
                // DAILY spectator: the same game identity as Core.
                Arguments.of(SPECTATOR, null, true, "tab_prefix", "§8[§6宾果时速§8]§r "),
                Arguments.of(SPECTATOR, null, true, "tab_footer_status", "§f当前游戏: §b宾果时速"));
    }

    @ParameterizedTest(name = "{0} daily={2} {3}")
    @MethodSource("placeholders")
    void resolvesCoreIdentityPointsAndTabTextForEveryRoleAndMode(
            PlayerSnapshot player,
            TeamSnapshot team,
            boolean daily,
            String params,
            String expected) {
        assertEquals(
                expected,
                WorkerChampionshipPlaceholderValues.resolve(
                        player, team, PRESENTATION, params, daily));
    }
}
