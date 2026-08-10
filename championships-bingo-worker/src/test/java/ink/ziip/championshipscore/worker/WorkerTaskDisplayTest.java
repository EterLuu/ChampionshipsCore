package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerTaskDisplayTest {
    @Test
    void frozenDisplayFieldsWinOverExecutionAttributes() {
        BingoTaskSpec task = new BingoTaskSpec(0, "jump", "statistic", Map.of(
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
        BingoTaskSpec jump = new BingoTaskSpec(0, "jump", "statistic", Map.of(
                "statistic", "JUMP", "target", "7"));
        BingoTaskSpec travel = new BingoTaskSpec(1, "walk", "statistic", Map.of(
                "statistic", "WALK_ONE_CM", "target", "12000"));

        assertEquals(Material.RABBIT_FOOT, WorkerTaskDisplay.icon(jump));
        assertEquals(12, WorkerTaskDisplay.amount(travel));
        assertEquals(Material.LEATHER_BOOTS, WorkerTaskDisplay.icon(travel));
    }
}
