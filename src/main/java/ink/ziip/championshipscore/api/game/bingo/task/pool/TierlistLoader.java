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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads optional tier lists from {@code <dataFolder>/bingo/tierlists/*.yml}. The active list name is
 * supplied by the caller (blank = none). A list re-ranks objectives by id without touching the catalog.
 */
public final class TierlistLoader {
    private static final String DIR = "bingo/tierlists";
    private static final String RESOURCE_DIR = "bingo/tierlists";
    private static final String[] BUNDLED = {"default"};

    private TierlistLoader() {
    }

    /** Loads the named tier list, or {@link Tierlist#EMPTY} when blank/missing. */
    public static Tierlist load(JavaPlugin plugin, String selected) {
        ensureBundled(plugin);
        if (selected == null || selected.isBlank()) {
            TierlistSource.set(Tierlist.EMPTY, "");
            return Tierlist.EMPTY;
        }
        String name = normalizeName(selected);
        File file = new File(dir(plugin), name + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("[Bingo] tier list '" + name + "' 不存在，使用卡池内联难度。");
            TierlistSource.set(Tierlist.EMPTY, "");
            return Tierlist.EMPTY;
        }
        try {
            Tierlist tierlist = parse(YamlConfiguration.loadConfiguration(file), plugin.getLogger(), name + ".yml");
            TierlistSource.set(tierlist, name);
            plugin.getLogger().info("[Bingo] 已载入 tier list bingo/tierlists/" + name + ".yml"
                    + (tierlist.isEmpty() ? "（无有效规则）" : ""));
            return tierlist;
        } catch (Exception e) {
            plugin.getLogger().warning("[Bingo] 解析 tier list bingo/tierlists/" + name + ".yml 失败: " + e.getMessage());
            TierlistSource.set(Tierlist.EMPTY, "");
            return Tierlist.EMPTY;
        }
    }

    private static Tierlist parse(YamlConfiguration y, Logger log, String sourceName) {
        ConfigurationSection tiers = y.getConfigurationSection("tiers");
        if (tiers == null) {
            log.warning(sourceName + ": 缺少 tiers 段。");
            return Tierlist.EMPTY;
        }
        Map<Difficulty, List<String>> map = new EnumMap<>(Difficulty.class);
        for (String key : tiers.getKeys(false)) {
            Difficulty tier = Tierlist.parseTier(key);
            if (tier == null) {
                log.warning(sourceName + ": 无效难度键 '" + key + "'，跳过。");
                continue;
            }
            List<String> tokens = new ArrayList<>(tiers.getStringList(key));
            map.computeIfAbsent(tier, k -> new ArrayList<>()).addAll(tokens);
        }
        return Tierlist.of(map);
    }

    private static void ensureBundled(JavaPlugin plugin) {
        File dir = dir(plugin);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Bingo] 无法创建 tierlists 目录 " + dir.getPath());
            return;
        }
        for (String name : BUNDLED) {
            File file = new File(dir, name + ".yml");
            if (file.exists()) continue;
            try (InputStream in = plugin.getResource(RESOURCE_DIR + "/" + name + ".yml")) {
                if (in == null) continue;
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("[Bingo] 已生成 bingo/tierlists/" + name + ".yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Bingo] 无法写出 bingo/tierlists/" + name + ".yml: " + e.getMessage());
            }
        }
    }

    private static File dir(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), DIR);
    }

    private static String normalizeName(String name) {
        String trimmed = name.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            trimmed = trimmed.substring(0, trimmed.length() - ".yml".length());
        }
        return trimmed.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
