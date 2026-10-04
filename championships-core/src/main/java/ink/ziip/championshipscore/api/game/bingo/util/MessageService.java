package ink.ziip.championshipscore.api.game.bingo.util;

import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Localised text for the bingo subsystem. Exposes the {@code global()} / {@code tr()} / {@code
 * component()} surface the task and GUI code calls, backed by Bukkit {@link YamlConfiguration}
 * (whose dot-path lookups already resolve nested keys like {@code task.collect}). Lang files live
 * at {@code <dataFolder>/bingo/lang/<locale>.yml}, seeded from the bundled jar resources on first
 * run.
 */
public final class MessageService {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

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

    /**
     * The live message service, for static render code that has no service reference of its own.
     */
    public static MessageService global() {
        return instance;
    }

    public void reload(String newPrefix, String newLocale) {
        String loadedPrefix = color(newPrefix == null ? "" : newPrefix);
        String loadedLocale = (newLocale == null || newLocale.isBlank()) ? "zh_CN" : newLocale;
        YamlConfiguration loadedCurrent = load(loadedLocale);
        YamlConfiguration loadedFallback =
                load(loadedLocale.equalsIgnoreCase("zh_CN") ? "en_US" : "zh_CN");
        this.prefix = loadedPrefix;
        this.locale = loadedLocale;
        this.current = loadedCurrent;
        this.fallback = loadedFallback;
    }

    /**
     * Releases the static rendering bridge only when it still points at this manager-owned
     * instance.
     */
    public void close() {
        if (instance == this) instance = null;
    }

    /**
     * Whether a lang key resolves (current locale or fallback), without logging a miss like {@link
     * #tr}.
     */
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
     * A lang string as an Adventure {@link Component} for item names and lore. Italics are cleared
     * so the text keeps the lang string's own styling rather than the vanilla default for custom
     * items.
     */
    public Component component(String key, Object... args) {
        return LEGACY.deserialize(tr(key, args))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public void broadcast(String key, Object... args) {
        String text = tr(key, args);
        if (!text.isEmpty()) Bukkit.broadcast(LEGACY.deserialize(prefix + text));
    }

    /**
     * Returns a config list as colored lines, or the single value as a one-element list. Never
     * null.
     */
    public List<String> lines(String key, Object... args) {
        if (current.isList(key) || fallback.isList(key)) {
            List<String> raw =
                    current.isList(key) ? current.getStringList(key) : fallback.getStringList(key);
            List<String> out = new ArrayList<>(raw.size());
            for (String s : raw) out.add(format(s, args));
            return out;
        }
        return List.of(tr(key, args));
    }

    public static String color(String value) {
        return value == null ? "" : LegacyText.translateColorCodes(value);
    }

    static String format(String raw, Object... args) {
        String out = raw;
        for (int i = 0; args != null && i < args.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return color(out);
    }

    private String getRaw(String key) {
        Object value = current.get(key);
        if (value == null) value = fallback.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private YamlConfiguration load(String locale) {
        YamlConfiguration base = loadResource("bingo/lang/" + locale + ".yml");
        File file = new File(plugin.getDataFolder(), "bingo/lang/" + locale + ".yml");
        if (file.exists()) {
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
            BaseConfigurationFile.validateVersion(
                    disk.getInt("dont-edit-this.version", -1),
                    base.getInt("dont-edit-this.version", -1),
                    file.getName());
            return disk;
        }
        return base;
    }

    private void ensureBundledLangFiles() {
        File dir = new File(plugin.getDataFolder(), "bingo/lang");
        if (!dir.exists()) dir.mkdirs();
        ensureBundled("bingo/lang/zh_CN.yml", new File(dir, "zh_CN.yml"));
        ensureBundled("bingo/lang/en_US.yml", new File(dir, "en_US.yml"));
    }

    private void ensureBundled(String resourcePath, File dest) {
        if (dest.exists()) return;
        try {
            dest.getParentFile().mkdirs();
            loadResource(resourcePath).save(dest);
        } catch (IOException e) {
            log.warning("[BingoLang] Failed to write " + dest.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Loads a bundled jar resource as a {@link YamlConfiguration}; empty (with a warning) on
     * failure.
     */
    private YamlConfiguration loadResource(String resourcePath) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return yaml;
            yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warning(
                    "[BingoLang] Failed to load bundled " + resourcePath + ": " + e.getMessage());
        }
        return yaml;
    }
}
