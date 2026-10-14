package ink.ziip.championshipscore.api.game.bingo.task.pool;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.logging.LogText;

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
 * Reads optional tier lists from {@code <dataFolder>/bingo/tierlists/*.yml}. The active list name
 * is supplied by the caller (blank = none). A list re-ranks objectives by id without touching the
 * catalog.
 */
public final class TierlistLoader {
    private static final String DIR = "bingo/tierlists";
    private static final String RESOURCE_DIR = "bingo/tierlists";
    private static final String[] BUNDLED = {"default"};

    private TierlistLoader() {}

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
            plugin.getLogger()
                    .warning(
                            gameLog(
                                    MessageConfig.codeText("tierlist-loader.load-1"),
                                    MessageConfig.codeText("tierlist-loader.load-2", name)));
            TierlistSource.set(Tierlist.EMPTY, "");
            return Tierlist.EMPTY;
        }
        try {
            Tierlist tierlist =
                    parse(
                            YamlConfiguration.loadConfiguration(file),
                            plugin.getLogger(),
                            name + ".yml");
            TierlistSource.set(tierlist, name);
            plugin.getLogger()
                    .info(
                            gameLog(
                                    MessageConfig.codeText("tierlist-loader.load-1"),
                                    MessageConfig.codeText(
                                            "tierlist-loader.load-5",
                                            name,
                                            (tierlist.isEmpty()
                                                    ? MessageConfig.codeText(
                                                            "tierlist-loader.load-4")
                                                    : ""))));
            return tierlist;
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            gameLog(
                                    MessageConfig.codeText("tierlist-loader.load-1"),
                                    MessageConfig.codeText(
                                            "tierlist-loader.load-3", name, e.getMessage())));
            TierlistSource.set(Tierlist.EMPTY, "");
            return Tierlist.EMPTY;
        }
    }

    private static Tierlist parse(YamlConfiguration y, Logger log, String sourceName) {
        ConfigurationSection tiers = y.getConfigurationSection("tiers");
        if (tiers == null) {
            log.warning(
                    gameLog(
                            MessageConfig.codeText("tierlist-loader.load-1"),
                            MessageConfig.codeText("tierlist-loader.parse-1", sourceName)));
            return Tierlist.EMPTY;
        }
        Map<Difficulty, List<String>> map = new EnumMap<>(Difficulty.class);
        for (String key : tiers.getKeys(false)) {
            Difficulty tier = Tierlist.parseTier(key);
            if (tier == null) {
                log.warning(
                        gameLog(
                                MessageConfig.codeText("tierlist-loader.load-1"),
                                MessageConfig.codeText(
                                        "tierlist-loader.parse-2", sourceName, key)));
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
            plugin.getLogger()
                    .warning(
                            gameLog(
                                    MessageConfig.codeText("tierlist-loader.load-1"),
                                    MessageConfig.codeText(
                                            "tierlist-loader.ensure-bundled-1", dir.getPath())));
            return;
        }
        for (String name : BUNDLED) {
            File file = new File(dir, name + ".yml");
            if (file.exists()) continue;
            try (InputStream in = plugin.getResource(RESOURCE_DIR + "/" + name + ".yml")) {
                if (in == null) continue;
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger()
                        .info(
                                gameLog(
                                        MessageConfig.codeText("tierlist-loader.load-1"),
                                        MessageConfig.codeText(
                                                "tierlist-loader.ensure-bundled-3", name)));
            } catch (IOException e) {
                plugin.getLogger()
                        .warning(
                                gameLog(
                                        MessageConfig.codeText("tierlist-loader.load-1"),
                                        MessageConfig.codeText(
                                                "tierlist-loader.ensure-bundled-2",
                                                name,
                                                e.getMessage())));
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

    private static String gameLog(String event, String message) {
        return LogText.formatGameLog(
                GameTypeEnum.Bingo,
                "-",
                MessageConfig.codeText("tierlist-loader.game-log-1"),
                event,
                message);
    }
}
