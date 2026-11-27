package ink.ziip.championshipscore.presentation.sidebar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

class CoreSidebarManagerTest {
    @Test
    void holderListKeepsOnePlayerPerLineAndFitsTheSidebar() {
        List<UUID> holders = IntStream.rangeClosed(1, 6).mapToObj(i -> new UUID(0, i)).toList();
        UUID viewer = holders.getLast();
        var rows =
                CoreSidebarManager.holderRows(
                        holders, viewer, 11, id -> "P" + id.getLeastSignificantBits());
        assertEquals(
                List.of(
                        "P6",
                        "P1",
                        "P2",
                        "P3",
                        MessageConfig.codeText("hoty-items.more-holders", 2)),
                rows);
        assertEquals(
                List.of(MessageConfig.codeText("hoty-items.more-holders", 6)),
                CoreSidebarManager.holderRows(holders, viewer, 15, UUID::toString));
        assertEquals(
                List.of(MessageConfig.codeText("hoty-items.holders-none")),
                CoreSidebarManager.holderRows(List.of(), viewer, 12, UUID::toString));
    }

    @Test
    void selectsTopEightAndAppendsOutOfRangeViewerLikeBingo() {
        List<Integer> ranked = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 10),
                CoreSidebarManager.selectRankingRows(ranked, 10));
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8), CoreSidebarManager.selectRankingRows(ranked, 3));
    }
}
