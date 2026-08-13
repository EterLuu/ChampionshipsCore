package ink.ziip.championshipscore.worker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerGuiLanguageTest {
    @Test
    void workerGuiKeysUseTheSameEnglishHierarchyConvention() {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/gui.yml").toFile());

        assertTrue(gui.getKeys(true).stream()
                .filter(key -> !gui.isConfigurationSection(key))
                .allMatch(key -> key.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")));
    }

    @Test
    void spectatorControlsUseNaturalCopyAndKeepPlaceholders() throws IOException {
        String gui = Files.readString(Path.of("src/main/resources/gui.yml"));

        assertFalse(gui.contains("已停止玩家追踪"));
        assertFalse(gui.contains("当前没有可追踪玩家"));
        assertTrue(gui.contains("正在追踪：%player%"));
        assertTrue(gui.contains("飞行速度：%speed%"));
        assertTrue(gui.contains("左键：提高速度"));
        assertTrue(gui.contains("右键：降低速度"));
    }
}
