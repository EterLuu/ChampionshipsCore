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
    private static MessageService instance;

    private final Plugin plugin;
    private final Logger log;
    private String prefix = "";
    private String locale = "zh_CN";
    private YamlConfiguration current = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

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
        this.prefix = color(newPrefix == null ? "" : newPrefix);
        this.locale = (newLocale == null || newLocale.isBlank()) ? "zh_CN" : newLocale;
        this.current = load(this.locale);
        this.fallback = load(this.locale.equalsIgnoreCase("zh_CN") ? "en_US" : "zh_CN");
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
        File file = new File(plugin.getDataFolder(), "bingo/lang/" + locale + ".yml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        try (InputStream in = plugin.getResource("bingo/lang/" + locale + ".yml")) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warning("[BingoLang] Failed to load bundled lang " + locale + ": " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    private void ensureBundledLangFiles() {
        File dir = new File(plugin.getDataFolder(), "bingo/lang");
        if (!dir.exists()) dir.mkdirs();
        ensureBundled("bingo/lang/zh_CN.yml", new File(dir, "zh_CN.yml"));
        ensureBundled("bingo/lang/en_US.yml", new File(dir, "en_US.yml"));
    }

    private void ensureBundled(String resourcePath, File dest) {
        if (dest.exists()) return;
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return;
            Files.copy(in, dest.toPath());
        } catch (IOException e) {
            log.warning("[BingoLang] Failed to write " + dest.getName() + ": " + e.getMessage());
        }
    }
}
