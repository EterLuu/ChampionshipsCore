package ink.ziip.championshipscore.worker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Language-backed copy used by worker-local menus and spectator controls. */
final class WorkerGuiConfig {
    private static YamlConfiguration configuration = new YamlConfiguration();

    private WorkerGuiConfig() {
    }

    static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (var resource = plugin.getResource("gui.yml")) {
            if (resource == null) throw new IllegalStateException("Missing bundled gui.yml");
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || loaded.contains(key)) continue;
                loaded.set(key, defaults.get(key));
                changed = true;
            }
            if (changed) loaded.save(file);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load worker gui.yml", failure);
        }
        configuration = loaded;
    }

    static String text(String path) {
        return configuration.getString(path, path);
    }

    static String text(String path, Map<String, ?> placeholders) {
        String value = text(path);
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return value;
    }
}
