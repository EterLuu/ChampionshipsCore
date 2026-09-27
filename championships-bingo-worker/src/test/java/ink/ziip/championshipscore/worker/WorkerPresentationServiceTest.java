package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.MatchState;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        BingoPresentation presentation = new BingoPresentation(Map.of(
                "timer", "&#fff566宾果 &#bababa• &#ededed剩余 &#ff6b26%time%"));
        String plain = PlainTextComponentSerializer.plainText().serialize(
                WorkerPresentationService.message(presentation, "timer", "%time%", "09:59"));
        assertEquals("宾果 • 剩余 09:59", plain);
    }

    @Test
    void dailyPrefixResolvesForParticipantAndSpectatorMessages() {
        BingoPresentation presentation = new BingoPresentation(Map.of(
                "prefix", "&a[游戏大厅] ",
                "participant", "%prefix%欢迎 %player%",
                "spectator", "%prefix%正在旁观 %player%"));
        assertEquals("[游戏大厅] 欢迎 Alex", PlainTextComponentSerializer.plainText().serialize(
                WorkerPresentationService.message(presentation, "participant", "%player%", "Alex")));
        assertEquals("[游戏大厅] 正在旁观 Alex", PlainTextComponentSerializer.plainText().serialize(
                WorkerPresentationService.message(presentation, "spectator", "%player%", "Alex")));
    }

    @Test
    void oldManifestsWithoutPrefixRemainRenderable() {
        BingoPresentation presentation = new BingoPresentation(Map.of("notice", "%prefix%已加入"));
        assertEquals("已加入", PlainTextComponentSerializer.plainText().serialize(
                WorkerPresentationService.message(presentation, "notice")));
    }

    @Test
    void prefixInsideReplacementMessageIsAlsoResolved() {
        BingoPresentation presentation = new BingoPresentation(Map.of(
                "prefix", "&a[大厅] ", "notice", "%message%"));
        assertEquals("[大厅] 已退出", PlainTextComponentSerializer.plainText().serialize(
                WorkerPresentationService.message(presentation, "notice", "%message%", "%prefix%已退出")));
    }

    @Test
    void configuredStatusLineUsesTheSameLabelAndValueLayoutAsCore() {
        String rendered = WorkerPresentationService.sidebarLine(
                "#1da4ad场地状态: #f6ffa8{game.status}", "宾果时速", "进行中", 4);

        assertEquals("#1da4ad场地状态: #f6ffa8进行中", rendered);
        assertEquals("场地状态: 进行中", PlainTextComponentSerializer.plainText()
                .serialize(WorkerPresentationService.component(rendered)));
    }

    @Test
    void runningSidebarStatusDoesNotDuplicateTheBossBarTimer() {
        BingoPresentation presentation = new BingoPresentation(Map.of(
                "sidebar.status.progress", "比赛中"));
        assertEquals("比赛中", WorkerPresentationService.sidebarStatus(presentation, MatchState.RUNNING));
    }

    @Test
    void sidebarStatusRejectsIncompleteManifestPresentation() {
        assertThrows(IllegalArgumentException.class, () -> WorkerPresentationService.sidebarStatus(
                new BingoPresentation(Map.of()), MatchState.RUNNING));
    }

    @Test
    void ordinarySidebarPlaceholdersStillResolveNormally() {
        assertEquals("宾果时速 / 4", WorkerPresentationService.sidebarLine(
                "{game.name} / {viewer.tasks}", "宾果时速", "ignored", 4));
    }
}
