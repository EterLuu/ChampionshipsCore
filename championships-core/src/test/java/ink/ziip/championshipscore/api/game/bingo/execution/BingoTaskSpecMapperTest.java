package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.AllOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.EventSubject;
import ink.ziip.championshipscore.api.game.bingo.task.EventTask;
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
                        5, Dimension.OVERWORLD)),
                new GameTask(new StatisticTask(new StatisticHandle(Statistic.JUMP),
                        7, Dimension.OVERWORLD)),
                new GameTask(new AllOfTask(Set.of(Material.FURNACE, Material.SMOKER),
                        Material.FURNACE, "furnace", 1, Dimension.OVERWORLD)),
                new GameTask(new EventTask("visit_biomes", "NETHER", 3, Dimension.NETHER,
                        Set.of(), Material.NETHERRACK,
                        Set.of(EventSubject.biome("nether_wastes"), EventSubject.biome("crimson_forest")))),
                new GameTask(new EventTask("die", "MAGIC", 1, Dimension.OVERWORLD))));

        assertEquals("item", specs.get(0).taskType());
        assertEquals("IRON_INGOT", specs.get(0).attributes().get("material"));
        assertEquals("potion", specs.get(1).taskType());
        assertEquals("strength", specs.get(1).attributes().get("effect"));
        assertEquals("RED_WOOL,WHITE_WOOL", specs.get(2).attributes().get("materials"));
        assertEquals("WHITE_WOOL", specs.get(2).attributes().get("display.material"));
        assertEquals("3", specs.get(2).attributes().get("display.amount"));
        assertEquals("MINE_BLOCK", specs.get(3).attributes().get("statistic"));
        assertEquals("DIAMOND_ORE", specs.get(3).attributes().get("material"));
        assertEquals("5", specs.get(3).attributes().get("target"));
        assertEquals("DIAMOND_ORE", specs.get(3).attributes().get("display.material"));
        assertEquals("5", specs.get(3).attributes().get("display.amount"));
        assertEquals("RABBIT_FOOT", specs.get(4).attributes().get("display.material"));
        assertEquals("7", specs.get(4).attributes().get("display.amount"));
        assertEquals("all_of", specs.get(5).taskType());
        assertEquals("FURNACE,SMOKER", specs.get(5).attributes().get("materials"));
        assertEquals("event", specs.get(6).taskType());
        assertEquals("visit_biomes", specs.get(6).attributes().get("trigger"));
        assertEquals("BIOME=crimson_forest,BIOME=nether_wastes", specs.get(6).attributes().get("subjects"));
        assertEquals("minecraft:harming_splash_potion", specs.get(7).attributes().get("display.icon-key"));
        assertEquals("HARMING", specs.get(7).attributes().get("display.potion-type"));
    }
}
