package ink.ziip.championshipscore.api.game.bingo.task.pool;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads the tag layer: one tag per {@code <dataFolder>/bingo/tags/<tag>.yml} file (a {@code values}
 * list of objective-id globs), plus the {@code filters} section of the bingo config (excluded tags and
 * per-tag caps). Installs the result into {@link TagFilters}. A bundled {@code tags/tedious.yml} is
 * written on first run.
 */
public final class TagFilterLoader {
    private static final String DIR = "bingo/tags";
    private static final String RESOURCE_DIR = "bingo/tags";
    private static final String[] BUNDLED = {"tedious", "starter_kit"};

    private TagFilterLoader() {
    }

    public static TagFilters load(JavaPlugin plugin, YamlConfiguration config) {
        ensureBundled(plugin);

        Map<String, List<String>> tagToIds = new LinkedHashMap<>();
        File dir = dir(plugin);
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String tag = file.getName().substring(0, file.getName().length() - ".yml".length())
                        .toLowerCase(Locale.ROOT);
                try {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
                    List<String> values = new ArrayList<>(y.getStringList("values"));
                    if (!values.isEmpty()) {
                        tagToIds.computeIfAbsent(tag, k -> new ArrayList<>()).addAll(values);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("解析标签 bingo/tags/" + file.getName() + " 失败: " + e.getMessage());
                }
            }
        }

        Set<String> excluded = new LinkedHashSet<>(config.getStringList("filters.exclude"));
        Map<String, Integer> caps = new LinkedHashMap<>();
        ConfigurationSection capSec = config.getConfigurationSection("filters.caps");
        if (capSec != null) {
            for (String key : capSec.getKeys(false)) {
                int v = capSec.getInt(key, -1);
                if (v >= 0) caps.put(key.toLowerCase(Locale.ROOT), v);
            }
        }

        TagFilters filters = TagFilters.build(tagToIds, excluded, caps);
        TagFilters.set(filters);
        if (!tagToIds.isEmpty() || !excluded.isEmpty() || !caps.isEmpty()) {
            plugin.getLogger().info("[Bingo] 标签层已载入：标签 " + tagToIds.keySet()
                    + "，排除 " + excluded + "，上限 " + caps);
        }
        return filters;
    }

    private static void ensureBundled(JavaPlugin plugin) {
        File dir = dir(plugin);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建 bingo/tags 目录 " + dir.getPath());
            return;
        }
        for (String name : BUNDLED) {
            File file = new File(dir, name + ".yml");
            if (file.exists()) continue;
            try (InputStream in = plugin.getResource(RESOURCE_DIR + "/" + name + ".yml")) {
                if (in == null) continue;
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("已生成 bingo/tags/" + name + ".yml");
            } catch (IOException e) {
                plugin.getLogger().warning("无法写出 bingo/tags/" + name + ".yml: " + e.getMessage());
            }
        }
    }

    private static File dir(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), DIR);
    }
}
