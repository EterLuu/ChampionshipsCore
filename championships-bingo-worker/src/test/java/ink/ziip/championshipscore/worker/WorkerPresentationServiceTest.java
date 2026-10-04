package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class WorkerPresentationServiceTest {
    @Test
    void standardThreeSectionsKeepCoreTiming() {
        assertEquals(-1, WorkerPresentationService.sectionAt(9, 45, 3));
        assertEquals(0, WorkerPresentationService.sectionAt(10, 45, 3));
        assertEquals(1, WorkerPresentationService.sectionAt(20, 45, 3));
        assertEquals(2, WorkerPresentationService.sectionAt(30, 45, 3));
        assertEquals(-1, WorkerPresentationService.sectionAt(40, 45, 3));
    }

    @Test
    void coreOwnedTemplateSurvivesPlaceholderResolution() {
        BingoPresentation presentation =
                new BingoPresentation(
                        Map.of("timer", "&#fff566宾果 &#bababa• &#ededed剩余 &#ff6b26%time%"));
        String plain =
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                WorkerPresentationService.message(
                                        presentation, "timer", "%time%", "09:59"));
        assertEquals("宾果 • 剩余 09:59", plain);
    }

    @Test
    void dailyPrefixResolvesForParticipantAndSpectatorMessages() {
        BingoPresentation presentation =
                new BingoPresentation(
                        Map.of(
                                "prefix", "&a[游戏大厅] ",
                                "participant", "%prefix%欢迎 %player%",
                                "spectator", "%prefix%正在旁观 %player%"));
        assertEquals(
                "[游戏大厅] 欢迎 Alex",
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                WorkerPresentationService.message(
                                        presentation, "participant", "%player%", "Alex")));
        assertEquals(
                "[游戏大厅] 正在旁观 Alex",
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                WorkerPresentationService.message(
                                        presentation, "spectator", "%player%", "Alex")));
    }

    @Test
    void oldManifestsWithoutPrefixRemainRenderable() {
        BingoPresentation presentation = new BingoPresentation(Map.of("notice", "%prefix%已加入"));
        assertEquals(
                "已加入",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.message(presentation, "notice")));
    }

    @Test
    void prefixInsideReplacementMessageIsAlsoResolved() {
        BingoPresentation presentation =
                new BingoPresentation(Map.of("prefix", "&a[大厅] ", "notice", "%message%"));
        assertEquals(
                "[大厅] 已退出",
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                WorkerPresentationService.message(
                                        presentation, "notice", "%message%", "%prefix%已退出")));
    }

    @Test
    void configuredStatusLineUsesTheSameLabelAndValueLayoutAsCore() {
        String rendered =
                WorkerPresentationService.sidebarLine(
                        "#1da4ad场地状态: #f6ffa8{game.status}", "宾果时速", "进行中", 4);

        assertEquals("#1da4ad场地状态: #f6ffa8进行中", rendered);
        assertEquals(
                "场地状态: 进行中",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.component(rendered)));
    }

    @Test
    void runningSidebarStatusDoesNotDuplicateTheBossBarTimer() {
        BingoPresentation presentation =
                new BingoPresentation(Map.of("sidebar.status.progress", "比赛中"));
        assertEquals(
                "比赛中", WorkerPresentationService.sidebarStatus(presentation, MatchState.RUNNING));
    }

    @Test
    void sidebarStatusRejectsIncompleteManifestPresentation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WorkerPresentationService.sidebarStatus(
                                new BingoPresentation(Map.of()), MatchState.RUNNING));
    }

    @Test
    void ordinarySidebarPlaceholdersStillResolveNormally() {
        assertEquals(
                "宾果时速 / 4",
                WorkerPresentationService.sidebarLine(
                        "{game.name} / {viewer.tasks}", "宾果时速", "ignored", 4));
    }

    @Test
    void participantSeesTopEightAndTheirOwnOutOfRangeTeam() {
        Map<Integer, TeamSnapshot> teams = teams(10);
        BingoResult result = result(10);

        List<WorkerSidebarRanking.Entry> rows = WorkerSidebarRanking.select(result, teams, 10);

        assertEquals(9, rows.size());
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 10),
                rows.stream().map(row -> row.team().id()).toList());
        assertEquals(10, rows.getLast().rank());
        assertTrue(rows.getLast().viewerTeam());
    }

    @Test
    void participantTeamInsideTopEightIsHighlightedWithoutDuplication() {
        List<WorkerSidebarRanking.Entry> rows =
                WorkerSidebarRanking.select(result(10), teams(10), 3);

        assertEquals(8, rows.size());
        assertTrue(rows.get(2).viewerTeam());
        assertEquals(1, rows.stream().filter(WorkerSidebarRanking.Entry::viewerTeam).count());
    }

    @Test
    void spectatorSeesOnlyTopEightWithoutHighlight() {
        List<WorkerSidebarRanking.Entry> rows =
                WorkerSidebarRanking.select(result(10), teams(10), null);

        assertEquals(8, rows.size());
        assertTrue(rows.stream().noneMatch(WorkerSidebarRanking.Entry::viewerTeam));
    }

    private static Map<Integer, TeamSnapshot> teams(int count) {
        Map<Integer, TeamSnapshot> teams = new LinkedHashMap<>();
        for (int id = 1; id <= count; id++) {
            teams.put(id, new TeamSnapshot(id, "Team " + id, "WHITE", "&f", List.of()));
        }
        return teams;
    }

    private static BingoResult result(int count) {
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        Map<Integer, Integer> cells = new LinkedHashMap<>();
        Map<Integer, Long> ticks = new LinkedHashMap<>();
        for (int id = 1; id <= count; id++) {
            scores.put(id, (count - id + 1) * 100);
            cells.put(id, count - id);
            ticks.put(id, (long) id);
        }
        return new BingoResult(0, false, scores, cells, ticks, "test-result");
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

    @Test
    void matchesCoreTeamAndPointPlaceholders() {
        assertEquals("金队", resolve("player_team_name_no_color"));
        assertEquals("§x§f§f§f§5§6§6金队", resolve("player_team_name"));
        assertEquals("&#fff566", resolve("player_team_color_code"));
        assertEquals("YELLOW", resolve("player_team_color"));
        assertEquals("322", resolve("player_points"));
        assertEquals("1235", resolve("player_team_points"));
        assertEquals("§x§f§f§f§5§6§6", resolve("tab_name_color"));
        assertEquals("§f队伍: §x§f§f§f§5§6§6金队 §f| 积分: 1235", resolve("tab_footer_status"));
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
    void championshipPlayerKeepsTeamPrefixAndTeamColourForName() {
        assertEquals("§8[§x§f§f§f§5§6§6金队§8]§r ", resolve("tab_prefix"));
        assertEquals("§x§f§f§f§5§6§6", resolve("tab_name_color"));
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

    private static String resolve(String params) {
        return WorkerChampionshipPlaceholderValues.resolve(PLAYER, TEAM, PRESENTATION, params);
    }

    @Test
    void frozenDisplayFieldsWinOverExecutionAttributes() {
        BingoTaskSpec task =
                new BingoTaskSpec(
                        0,
                        "jump",
                        "statistic",
                        Map.of(
                                "statistic", "JUMP",
                                "target", "7000",
                                "display.material", "RABBIT_FOOT",
                                "display.amount", "7"));

        assertEquals(Material.RABBIT_FOOT, WorkerTaskDisplay.icon(task));
        assertEquals(7, WorkerTaskDisplay.amount(task));
        assertEquals("minecraft:rabbit_foot", WorkerTaskDisplay.statisticSubject(task).asString());
    }

    @Test
    void oldManifestStatisticsRetainOriginalIconsAndTravelUnits() {
        BingoTaskSpec jump =
                new BingoTaskSpec(
                        0, "jump", "statistic", Map.of("statistic", "JUMP", "target", "7"));
        BingoTaskSpec travel =
                new BingoTaskSpec(
                        1,
                        "walk",
                        "statistic",
                        Map.of("statistic", "WALK_ONE_CM", "target", "12000"));

        assertEquals(Material.RABBIT_FOOT, WorkerTaskDisplay.icon(jump));
        assertEquals(12, WorkerTaskDisplay.amount(travel));
        assertEquals(Material.LEATHER_BOOTS, WorkerTaskDisplay.icon(travel));
    }

    @Test
    void createsStablePortableIdsForProtocolTeams() {
        assertEquals("ccb_0", WorkerMatchSession.nativeTeamId(0));
        assertEquals("ccb_z", WorkerMatchSession.nativeTeamId(35));
        assertTrue(WorkerMatchSession.nativeTeamId(Integer.MAX_VALUE).length() <= 16);
        assertThrows(IllegalArgumentException.class, () -> WorkerMatchSession.nativeTeamId(-1));
    }
}
