package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.*;

class WorkerTaskDisplayTest {

    @Test
    void frozenFieldsOverrideExecutionValuesWhileLegacyStatisticsKeepTheirDisplayUnits() {
        record Example(BingoTaskSpec task, Material icon, int amount) {}
        List<Example> examples =
                List.of(
                        new Example(
                                new BingoTaskSpec(
                                        0,
                                        "jump",
                                        "statistic",
                                        Map.of(
                                                "statistic",
                                                "JUMP",
                                                "target",
                                                "7000",
                                                "display.material",
                                                "RABBIT_FOOT",
                                                "display.amount",
                                                "7")),
                                Material.RABBIT_FOOT,
                                7),
                        new Example(
                                new BingoTaskSpec(
                                        0,
                                        "jump",
                                        "statistic",
                                        Map.of("statistic", "JUMP", "target", "7")),
                                Material.RABBIT_FOOT,
                                7),
                        new Example(
                                new BingoTaskSpec(
                                        1,
                                        "walk",
                                        "statistic",
                                        Map.of("statistic", "WALK_ONE_CM", "target", "12000")),
                                Material.LEATHER_BOOTS,
                                12));
        for (Example example : examples) {
            assertEquals(
                    example.icon(),
                    WorkerTaskDisplay.icon(example.task()),
                    example.task().attributes().toString());
            assertEquals(
                    example.amount(),
                    WorkerTaskDisplay.amount(example.task()),
                    example.task().attributes().toString());
        }
        assertFalse(
                WorkerTaskDisplay.glows(
                        new BingoTaskSpec(0, "event", "event", Map.of("display.glow", "false"))));
        assertTrue(
                WorkerTaskDisplay.glows(
                        new BingoTaskSpec(0, "item", "item", Map.of("display.glow", "true"))));
        assertTrue(
                WorkerTaskDisplay.glows(
                        new BingoTaskSpec(0, "advancement", "advancement", Map.of())));
        assertEquals(
                "minecraft:rabbit_foot",
                WorkerTaskDisplay.statisticSubject(examples.getFirst().task()).asString());
    }
}
