package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.MatchState;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class WorkerPresentationServiceTest {
    @Test
    void fullTimersUseFrozenCoreTemplatesAndLegacySnapshotsKeepTheirOriginalComposition() {
        var fields = new java.util.HashMap<String, String>();
        fields.put("duration.seconds", "%seconds%s");
        fields.put("duration.minutes-seconds", "%minutes%m %seconds%s");
        fields.put("bingo.timer", "&eTime %time%");
        fields.put("bingo.pvp-active", "&cPvP");
        fields.put("bingo.pvp-protection", "&aSafe %time%");
        var old = new BingoPresentation(fields);
        assertEquals(
                "Time 1m 1s • Safe 5s",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.timerTitle(old, false, 61, 5)));
        assertEquals(
                "Time 1m 1s • PvP",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.timerTitle(old, true, 61, 0)));

        fields.put("bingo.timer-pvp", "&bPvP %time%");
        fields.put("bingo.timer-protection", "&d%grace% / %time%");
        var frozen = new BingoPresentation(fields);
        fields.put("bingo.timer-protection", "changed");
        assertEquals(
                "5s / 1m 1s",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.timerTitle(frozen, false, 61, 5)));
        assertEquals(
                "PvP 1m 1s",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.timerTitle(frozen, true, 61, 0)));
        assertEquals(5, old.messages().size());
        fields.remove("bingo.timer-pvp");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WorkerPresentationService.timerTitle(
                                new BingoPresentation(fields), false, 61, 5));
    }

    @Test
    void durationsUseFrozenCoreTemplatesAndOldManifestsKeepTheirOriginalRendering() {
        var configured =
                new BingoPresentation(
                        Map.of(
                                "duration.seconds",
                                "%seconds%s",
                                "duration.minutes-seconds",
                                "%minutes%m %seconds%s"));
        assertEquals("60s", WorkerPresentationService.bossBarTime(configured, 60));
        assertEquals("1m 1s", WorkerPresentationService.bossBarTime(configured, 61));
        var old = new BingoPresentation(Map.of("game.name", "Bingo"));
        assertEquals("60秒", WorkerPresentationService.bossBarTime(old, 60));
        assertEquals("1分1秒", WorkerPresentationService.bossBarTime(old, 61));
        assertEquals(Map.of("game.name", "Bingo"), old.messages());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WorkerPresentationService.bossBarTime(
                                new BingoPresentation(Map.of("duration.seconds", "%seconds%s")),
                                61));
    }

    @Test
    void standardThreeSectionsKeepCoreTiming() {
        assertEquals(-1, WorkerPresentationService.sectionAt(9, 45, 3));
        assertEquals(0, WorkerPresentationService.sectionAt(10, 45, 3));
        assertEquals(1, WorkerPresentationService.sectionAt(20, 45, 3));
        assertEquals(2, WorkerPresentationService.sectionAt(30, 45, 3));
        assertEquals(-1, WorkerPresentationService.sectionAt(40, 45, 3));
    }

    @Test
    void messagesResolveCoreTemplatesDailyPrefixesAndLegacyOrNestedReplacements() {
        record Example(
                Map<String, String> fields, String key, String expected, String... replacements) {}
        var daily =
                Map.of(
                        "prefix",
                        "&a[游戏大厅] ",
                        "participant",
                        "%prefix%欢迎 %player%",
                        "spectator",
                        "%prefix%正在旁观 %player%");
        var examples =
                List.of(
                        new Example(
                                Map.of("timer", "&#fff566宾果 &#bababa• &#ededed剩余 &#ff6b26%time%"),
                                "timer",
                                "宾果 • 剩余 09:59",
                                "%time%",
                                "09:59"),
                        new Example(daily, "participant", "[游戏大厅] 欢迎 Alex", "%player%", "Alex"),
                        new Example(daily, "spectator", "[游戏大厅] 正在旁观 Alex", "%player%", "Alex"),
                        new Example(Map.of("notice", "%prefix%已加入"), "notice", "已加入"),
                        new Example(
                                Map.of("prefix", "&a[大厅] ", "notice", "%message%"),
                                "notice",
                                "[大厅] 已退出",
                                "%message%",
                                "%prefix%已退出"));
        for (Example example : examples) {
            assertEquals(
                    example.expected(),
                    PlainTextComponentSerializer.plainText()
                            .serialize(
                                    WorkerPresentationService.message(
                                            new BingoPresentation(example.fields()),
                                            example.key(),
                                            example.replacements())),
                    example.expected());
        }
    }

    @Test
    void sidebarLinesResolveStatusAndOrdinaryFieldsWithoutChangingFormatting() {
        String rendered =
                WorkerPresentationService.sidebarLine(
                        "#1da4ad场地状态: #f6ffa8{game.status}", "宾果时速", "进行中", 4);
        assertEquals("#1da4ad场地状态: #f6ffa8进行中", rendered);
        assertEquals(
                "场地状态: 进行中",
                PlainTextComponentSerializer.plainText()
                        .serialize(WorkerPresentationService.component(rendered)));
        assertEquals(
                "宾果时速 / 4",
                WorkerPresentationService.sidebarLine(
                        "{game.name} / {viewer.tasks}", "宾果时速", "ignored", 4));
        assertEquals(
                "{game.status} / 4",
                WorkerPresentationService.sidebarLine(
                        "{game.name} / {viewer.tasks}", "{game.status}", "running", 4));
    }

    @Test
    void ordinaryMessageArgumentsDoNotExpandOtherArguments() {
        var presentation = new BingoPresentation(Map.of("notice", "%player% / {0}"));
        assertEquals(
                "{0} $1\\name / ready",
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                WorkerPresentationService.message(
                                        presentation,
                                        "notice",
                                        "%player%",
                                        "{0} $1\\name",
                                        "{0}",
                                        "ready")));
    }

    @Test
    void sidebarStatusRequiresTheConfiguredLabelAndDoesNotAppendATimer() {
        var presentation = new BingoPresentation(Map.of("sidebar.status.progress", "比赛中"));
        assertEquals(
                "比赛中", WorkerPresentationService.sidebarStatus(presentation, MatchState.RUNNING));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WorkerPresentationService.sidebarStatus(
                                new BingoPresentation(Map.of()), MatchState.RUNNING));
    }
}
