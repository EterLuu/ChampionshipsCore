package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleMessageConfigTest {
    @TempDir Path directory;
    @Test void updatingRiptideRulesPreservesOtherCustomizedPanels() throws Exception {
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var plugin=(ChampionshipsCore)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(ChampionshipsCore.class);
        var config=new ScheduleMessageConfig(plugin);
        YamlConfiguration defaults;
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/schedule-message.yml")),StandardCharsets.UTF_8)) {
            defaults=YamlConfiguration.loadConfiguration(reader);
        }
        var old=new YamlConfiguration();
        old.set("dont-edit-this.version",12);
        old.set("bingo",List.of("custom Bingo rules"));
        old.set("riptide-rush-points",List.of("old scores"));
        set(config,"configuration",defaults);
        Path file=directory.resolve("schedule-message.yml");set(config,"configurationPath",file);
        config.loadFromOutdatedConfiguration(old);
        var saved=YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(List.of("custom Bingo rules"),saved.getStringList("bingo"));
        assertEquals(config.getLatestVersion(),saved.getInt("dont-edit-this.version"));
        String rules=saved.getStringList("riptide-rush-points").toString();
        assertTrue(rules.contains("同批同分并按人数跳位"));
        assertFalse(rules.contains("最后两名出局者另得"));
    }
    private void set(Object target,String name,Object value)throws Exception {
        var field=BaseConfigurationFile.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
