package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Configurable text used by inventory menus, hotbar controls and map-preparation screens. */
public final class GuiConfig extends BaseConfigurationFile {
    private static YamlConfiguration active = new YamlConfiguration();

    public GuiConfig(@NotNull ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public String getFileName() {
        return "gui.yml";
    }

    @Override
    public String getResourceName() {
        return "gui.yml";
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    @Override
    protected void loadCustomFileOptions() {
        active = configuration;
    }

    /** Preserve administrator overrides while filling new GUI keys from future bundled versions. */
    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration previous) throws IOException {
        for (String key : previous.getKeys(true)) {
            if (!previous.isConfigurationSection(key) && !key.equals("dont-edit-this.version"))
                configuration.set(key, previous.get(key));
        }
        super.loadFromOutdatedConfiguration(new YamlConfiguration());
    }

    public static @NotNull String text(@NotNull String path) {
        String value = active.getString(path);
        return value == null ? path : value;
    }

    public static @NotNull String text(@NotNull String path, @NotNull Map<String, ?> placeholders) {
        String value = text(path);
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return value;
    }

    public static @NotNull List<String> lines(@NotNull String path) {
        return active.getStringList(path);
    }

    public static @NotNull Component component(@NotNull String path) {
        return LegacyText.component(text(path)).decoration(TextDecoration.ITALIC, false);
    }
}
