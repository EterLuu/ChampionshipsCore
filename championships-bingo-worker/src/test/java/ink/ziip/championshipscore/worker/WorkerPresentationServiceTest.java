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
