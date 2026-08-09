package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoPresentation;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void longRuleListsAreCompressedIntoIntroductionWindow() {
        for (int section = 0; section < 8; section++) {
            assertEquals(section, WorkerPresentationService.sectionAt(10 + section * 4, 45, 8));
        }
    }

    @Test
    void compactHexAndLegacyColoursAreAccepted() {
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(WorkerPresentationService.component("&#ff6b26宾果 &f规则"));
        assertEquals("宾果 规则", plain);
    }

    @Test
    void coreOwnedTemplateSurvivesPlaceholderResolution() {
        BingoPresentation presentation = new BingoPresentation(Map.of(
                "timer", "&#fff566宾果 &#bababa• &#ededed剩余 &#ff6b26%time%s"));
        String plain = PlainTextComponentSerializer.plainText().serialize(
                WorkerPresentationService.message(presentation, "timer", "%time%", "599"));
        assertEquals("宾果 • 剩余 599s", plain);
    }
}
