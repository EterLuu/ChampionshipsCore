package ink.ziip.championshipscore.api.game.bingo.execution;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.bingo.task.*;
import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.platform.bukkit.bingo.FrozenBingoTaskText;
import ink.ziip.championshipscore.support.BingoMessages;

import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class BingoTaskSpecMapperTest {
    @TempDir Path directory;
    private MessageService messages;

    @BeforeEach
    void openMessages() {
        messages = BingoMessages.open(directory, "en_US");
    }

    @AfterEach
    void closeMessages() {
        messages.close();
    }

    @Test
    void missingPresentationServiceFailsInsteadOfPublishingAnIncompleteSnapshot() {
        messages.close();
        assertThrows(
                RuntimeException.class,
                () ->
                        BingoTaskSpecMapper.toSpecs(
                                List.of(
                                        new GameTask(
                                                new PotionTask(
                                                        PotionTask.Form.SPLASH,
                                                        "strength",
                                                        2,
                                                        Dimension.NETHER)))));
    }

    @Test
    void freezesTextAlongsideRegistryNamesCountsAndExecutionUnits() {
        // Material.isBlock and native advancement displays require the separate server matrix.
        var tasks =
                List.of(
                        new GameTask(
                                new PotionTask(
                                        PotionTask.Form.SPLASH, "strength", 2, Dimension.NETHER)),
                        new GameTask(new StatisticTask(new StatisticHandle(Statistic.JUMP), 7)),
                        new GameTask(
                                new StatisticTask(new StatisticHandle(Statistic.SPRINT_ONE_CM), 5)),
                        new GameTask(
                                new OneOfTask(
                                        java.util.Set.of(org.bukkit.Material.WHITE_WOOL),
                                        org.bukkit.Material.WHITE_WOOL,
                                        "wool",
                                        3,
                                        Dimension.OVERWORLD)),
                        new GameTask(new EventTask("die", "MAGIC", 1, Dimension.OVERWORLD)));
        var specs = BingoTaskSpecMapper.toSpecs(tasks);
        for (int index = 0; index < tasks.size(); index++) {
            var spec = specs.get(index);
            assertEquals(index, spec.cellIndex());
            var text = FrozenBingoTaskText.from(spec.attributes()).orElseThrow();
            assertEquals(tasks.get(index).data.getName(), text.name());
            assertEquals(List.of(tasks.get(index).data.getItemDescription()), text.lore());
        }
        assertEquals("potion", specs.get(0).taskType());
        assertEquals("strength", specs.get(0).attributes().get("effect"));
        assertEquals("SPLASH_POTION", specs.get(0).attributes().get("material"));
        assertEquals("2", specs.get(0).attributes().get("count"));
        assertEquals("JUMP", specs.get(1).attributes().get("statistic"));
        assertEquals("7", specs.get(1).attributes().get("target"));
        assertEquals("RABBIT_FOOT", specs.get(1).attributes().get("display.material"));
        assertEquals("true", specs.get(1).attributes().get("display.glow"));
        assertEquals("5000", specs.get(2).attributes().get("target"));
        assertEquals("5", specs.get(2).attributes().get("display.amount"));
        assertEquals("WHITE_WOOL", specs.get(3).attributes().get("materials"));
        assertEquals("3", specs.get(3).attributes().get("display.amount"));
        assertEquals("HARMING", specs.get(4).attributes().get("display.potion-type"));
    }

    @Test
    void localeReloadChangesNewNamesButNeverMutatesAnExistingSnapshot() throws Exception {
        var task =
                new GameTask(
                        new PotionTask(PotionTask.Form.SPLASH, "strength", 2, Dimension.NETHER));
        var before = BingoTaskSpecMapper.toSpecs(List.of(task)).getFirst();
        var originalAttributes = java.util.Map.copyOf(before.attributes());
        var file = directory.resolve("bingo/lang/en_US.yml").toFile();
        var document = YamlConfiguration.loadConfiguration(file);
        document.set("task.presentation.item-name", "&bChanged %name% / %literal%");
        document.set("task.collect_potion", "changed lore");
        document.save(file);
        messages.reload("", "en_US");
        var after = BingoTaskSpecMapper.toSpecs(List.of(task)).getFirst();
        assertNotEquals(
                before.attributes().get("display.name"), after.attributes().get("display.name"));
        assertNotEquals(
                before.attributes().get("display.lore.0"),
                after.attributes().get("display.lore.0"));
        assertEquals(originalAttributes, before.attributes());
        assertEquals(
                task.data.getName(),
                FrozenBingoTaskText.from(after.attributes()).orElseThrow().name());
    }
}
