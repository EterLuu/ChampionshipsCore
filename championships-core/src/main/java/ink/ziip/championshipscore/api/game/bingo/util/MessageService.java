package ink.ziip.championshipscore.api.game.bingo.util;

import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Localised text for the bingo subsystem. Trimmed port of minebingo's MessageService: it keeps the
 * {@code global()} / {@code tr()} / {@code component()} surface the ported task and GUI code calls,
 * but is backed by Bukkit {@link YamlConfiguration} (whose dot-path lookups already resolve nested
 * keys like {@code task.collect}) instead of snakeyaml. Lang files live at
 * {@code <dataFolder>/bingo/lang/<locale>.yml}, seeded from the bundled jar resources on first run.
 */
public final class MessageService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** The live instance, exposed so deeply-nested render code can localize text statically. */
    private static volatile MessageService instance;

    private final Plugin plugin;
    private final Logger log;
    private volatile String prefix = "";
    private volatile String locale = "zh_CN";
    private volatile YamlConfiguration current = new YamlConfiguration();
    private volatile YamlConfiguration fallback = new YamlConfiguration();

    public MessageService(Plugin plugin, String prefix, String locale) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        ensureBundledLangFiles();
        reload(prefix, locale);
        instance = this;
    }

    /** The live message service, for static render code that has no service reference of its own. */
    public static MessageService global() {
        return instance;
    }

    public void reload(String newPrefix, String newLocale) {
        String loadedPrefix = color(newPrefix == null ? "" : newPrefix);
        String loadedLocale = (newLocale == null || newLocale.isBlank()) ? "zh_CN" : newLocale;
        YamlConfiguration loadedCurrent = load(loadedLocale);
        YamlConfiguration loadedFallback = load(loadedLocale.equalsIgnoreCase("zh_CN") ? "en_US" : "zh_CN");
        this.prefix = loadedPrefix;
        this.locale = loadedLocale;
        this.current = loadedCurrent;
        this.fallback = loadedFallback;
    }

    /** Releases the static rendering bridge only when it still points at this manager-owned instance. */
    public void close() {
        if (instance == this) instance = null;
    }

    /** Whether a lang key resolves (current locale or fallback), without logging a miss like {@link #tr}. */
    public boolean has(String key) {
        return getRaw(key) != null;
    }

    public String tr(String key, Object... args) {
        String raw = getRaw(key);
        if (raw == null) {
            log.warning("[BingoLang] Missing key: " + key + " in " + locale);
            return key;
        }
        return format(raw, args);
    }

    /**
     * A lang string as an Adventure {@link Component} for item names and lore. Italics are cleared so
     * the text keeps the lang string's own styling rather than the vanilla default for custom items.
     */
    public Component component(String key, Object... args) {
        return LEGACY.deserialize(tr(key, args)).decoration(TextDecoration.ITALIC, false);
    }

    public void broadcast(String key, Object... args) {
        String text = tr(key, args);
        if (!text.isEmpty()) Bukkit.broadcast(LEGACY.deserialize(prefix + text));
    }

    /** Returns a config list as colored lines, or the single value as a one-element list. Never null. */
    public List<String> lines(String key, Object... args) {
        if (current.isList(key) || fallback.isList(key)) {
            List<String> raw = current.isList(key) ? current.getStringList(key) : fallback.getStringList(key);
            List<String> out = new ArrayList<>(raw.size());
            for (String s : raw) out.add(format(s, args));
            return out;
        }
        return List.of(tr(key, args));
    }

    public static String color(String value) {
        return value == null ? "" : Utils.translateColorCodes(value);
    }

    private String format(String raw, Object... args) {
        String out = color(raw);
        for (int i = 0; args != null && i < args.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return out;
    }

    private String getRaw(String key) {
        Object value = current.get(key);
        if (value == null) value = fallback.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private YamlConfiguration load(String locale) {
        // The bundled jar resource is the base; on-disk values win for any key present in both, and any
        // key that exists only in the jar (e.g. one added in a newer jar that the on-disk file predates,
        // or one the server owner deleted) is filled in. ensureBundled already persists new keys to disk,
        // but this overlay also covers the in-memory case so a missing key never resolves to its raw path.
        YamlConfiguration base = loadResource("bingo/lang/" + locale + ".yml");
        File file = new File(plugin.getDataFolder(), "bingo/lang/" + locale + ".yml");
        if (file.exists()) {
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
            for (String key : disk.getKeys(true)) {
                if (disk.isConfigurationSection(key)) continue;
                base.set(key, disk.get(key));
            }
        }
        return base;
    }

    private void ensureBundledLangFiles() {
        File dir = new File(plugin.getDataFolder(), "bingo/lang");
        if (!dir.exists()) dir.mkdirs();
        ensureBundled("bingo/lang/zh_CN.yml", new File(dir, "zh_CN.yml"));
        ensureBundled("bingo/lang/en_US.yml", new File(dir, "en_US.yml"));
    }

    /**
     * Makes sure {@code dest} exists and carries every bundled key. A language schema bump replaces the
     * previous copy so deliberate terminology/style migrations reach running servers; within the same
     * schema version, administrator overrides win and only missing keys are filled in.
     */
    private void ensureBundled(String resourcePath, File dest) {
        YamlConfiguration bundled = loadResource(resourcePath);
        if (dest.exists()) {
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(dest);
            int bundledVersion = bundled.getInt("dont-edit-this.version", 0);
            int diskVersion = disk.getInt("dont-edit-this.version", -1);
            if (diskVersion < bundledVersion) {
                try {
                    bundled.save(dest);
                } catch (IOException e) {
                    log.warning("[BingoLang] Failed to upgrade " + dest.getName() + ": " + e.getMessage());
                }
                return;
            }
            int before = countLeaves(disk);
            mergeDefaults(bundled, disk);
            if (countLeaves(disk) == before) return; // no new keys - leave the file untouched
            try {
                disk.save(dest);
            } catch (IOException e) {
                log.warning("[BingoLang] Failed to write " + dest.getName() + ": " + e.getMessage());
            }
        } else {
            try {
                dest.getParentFile().mkdirs();
                bundled.save(dest);
            } catch (IOException e) {
                log.warning("[BingoLang] Failed to write " + dest.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Loads a bundled jar resource as a {@link YamlConfiguration}; empty (with a warning) on failure. */
    private YamlConfiguration loadResource(String resourcePath) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return yaml;
            yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warning("[BingoLang] Failed to load bundled " + resourcePath + ": " + e.getMessage());
        }
        return yaml;
    }

    /** Fills {@code dest} with any leaf key missing from it, recursing through nested sections. */
    private static void mergeDefaults(YamlConfiguration defaults, YamlConfiguration dest) {
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!dest.contains(key)) {
                dest.set(key, defaults.get(key));
            }
        }
    }

    private static int countLeaves(YamlConfiguration yaml) {
        int count = 0;
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) count++;
        }
        return count;
    }
}
