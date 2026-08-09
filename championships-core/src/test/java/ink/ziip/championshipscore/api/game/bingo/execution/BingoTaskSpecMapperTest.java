package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.PotionTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BingoTaskSpecMapperTest {
    @Test
    void mapsBukkitObjectivesToStableRegistryNames() {
        List<BingoTaskSpec> specs = BingoTaskSpecMapper.toSpecs(List.of(
                new GameTask(new ItemTask(Material.IRON_INGOT, 4)),
                new GameTask(new PotionTask(PotionTask.Form.SPLASH, "strength", 2, Dimension.NETHER)),
                new GameTask(new OneOfTask(Set.of(Material.WHITE_WOOL, Material.RED_WOOL),
                        Material.WHITE_WOOL, "wool", 3, Dimension.OVERWORLD)),
                new GameTask(new StatisticTask(new StatisticHandle(Statistic.MINE_BLOCK, Material.DIAMOND_ORE),
                        5, Dimension.OVERWORLD))));

        assertEquals("item", specs.get(0).taskType());
        assertEquals("IRON_INGOT", specs.get(0).attributes().get("material"));
        assertEquals("potion", specs.get(1).taskType());
        assertEquals("strength", specs.get(1).attributes().get("effect"));
        assertEquals("RED_WOOL,WHITE_WOOL", specs.get(2).attributes().get("materials"));
        assertEquals("MINE_BLOCK", specs.get(3).attributes().get("statistic"));
        assertEquals("DIAMOND_ORE", specs.get(3).attributes().get("material"));
        assertEquals("5", specs.get(3).attributes().get("target"));
    }
}
