package ink.ziip.championshipscore.api.game.bingo.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;

class StatisticHandleTest {
    @Test
    void supportsEveryUnqualifiedServerStatistic() {
        for (Statistic statistic : Statistic.values()) {
            if (statistic.isSubstatistic()) continue;
            StatisticHandle handle = new StatisticHandle(statistic);
            assertNotEquals(Material.AIR, handle.icon(), statistic.name());
            assertNotEquals(statistic.name(), handle.translationKey(), statistic.name());
        }
    }

    @Test
    void distinguishesStrawBedSleepFromOrdinaryBedSleep() {
        StatisticHandle strawBed = new StatisticHandle(Statistic.SLEEP_IN_STRAW_BED);
        StatisticHandle bed = new StatisticHandle(Statistic.SLEEP_IN_BED);
        assertEquals(Material.STRAW_BED, strawBed.icon());
        assertEquals("sleep_in_straw_bed", strawBed.translationKey());
        assertNotEquals(bed.icon(), strawBed.icon());
        assertNotEquals(bed.translationKey(), strawBed.translationKey());
    }
}
