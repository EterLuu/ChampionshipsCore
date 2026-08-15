package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        return 11;
    }

    @Override
    protected void loadCustomFileOptions() {
        active = configuration;
    }

    public static @NotNull String text(@NotNull String path) {
        String value = active.getString(path);
        return value == null ? path : value;
    }

    public static @NotNull String text(@NotNull String path, @NotNull Map<String, ?> placeholders) {
        return replace(text(path), placeholders);
    }

    public static @NotNull List<String> lines(@NotNull String path) {
        return active.getStringList(path);
    }

    public static @NotNull List<String> lines(@NotNull String path, @NotNull Map<String, ?> placeholders) {
        return lines(path).stream().map(line -> replace(line, placeholders)).toList();
    }

    public static int integer(@NotNull String path, int fallback) {
        return active.isInt(path) ? active.getInt(path) : fallback;
    }

    public static @NotNull List<Integer> slots(@NotNull String path, @NotNull List<Integer> fallback) {
        List<Integer> configured = active.getIntegerList(path);
        return configured.isEmpty() ? List.copyOf(fallback) : List.copyOf(configured);
    }

    public static @NotNull Material material(@NotNull String path, @NotNull Material fallback) {
        String configured = active.getString(path);
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null || material.isAir() ? fallback : material;
    }

    public static @NotNull MenuSpec menu(@NotNull String path, int fallbackSize, @NotNull String fallbackTitle,
                                         @NotNull List<Integer> fallbackContentSlots) {
        int configuredSize = integer(path + ".size", fallbackSize);
        int size = configuredSize >= 9 && configuredSize <= 54 && configuredSize % 9 == 0
                ? configuredSize : fallbackSize;
        Component title = LegacyText.component(active.getString(path + ".title", fallbackTitle))
                .decoration(TextDecoration.ITALIC, false);
        List<Integer> content = slots(path + ".layout.content", fallbackContentSlots).stream()
                .filter(slot -> slot >= 0 && slot < size).distinct().toList();
        return new MenuSpec(size, title, content.isEmpty() ? List.copyOf(fallbackContentSlots) : content);
    }

    public static @NotNull MenuSpec menu(@NotNull String path, int fallbackSize,
                                         @NotNull Component fallbackTitle,
                                         @NotNull List<Integer> fallbackContentSlots) {
        int configuredSize = integer(path + ".size", fallbackSize);
        int size = configuredSize >= 9 && configuredSize <= 54 && configuredSize % 9 == 0
                ? configuredSize : fallbackSize;
        Component title = active.isString(path + ".title")
                ? LegacyText.component(active.getString(path + ".title", "")) : fallbackTitle;
        title = title.decoration(TextDecoration.ITALIC, false);
        List<Integer> content = slots(path + ".layout.content", fallbackContentSlots).stream()
                .filter(slot -> slot >= 0 && slot < size).distinct().toList();
        return new MenuSpec(size, title, content.isEmpty() ? List.copyOf(fallbackContentSlots) : content);
    }

    public static @NotNull ItemSpec item(@NotNull String path, @NotNull Map<String, ?> placeholders) {
        return item(path, null, placeholders);
    }

    /** A state section overrides only the fields it declares and inherits the rest from the button. */
    public static @NotNull ItemSpec item(@NotNull String path, String state,
                                         @NotNull Map<String, ?> placeholders) {
        return item(path, state, placeholders, new ItemSpec(-1, Material.BARRIER,
                LegacyText.component(path), List.of(), false));
    }

    /** Reads a configured button while retaining the existing implementation as a safe fallback. */
    public static @NotNull ItemSpec item(@NotNull String path, String state,
                                         @NotNull Map<String, ?> placeholders,
                                         @NotNull ItemSpec fallback) {
        String statePath = state == null || state.isBlank() ? null : path + ".states." + state;
        int slot = stateValueInt(statePath, path, "slot", fallback.slot());
        Material material = materialValue(statePath, path, "material", fallback.material());
        String configuredTitle = stateValueString(statePath, path, "title", null);
        List<String> configuredLore = stateValueLines(statePath, path, "lore");
        boolean glint = stateValueBoolean(statePath, path, "glint", fallback.glint());
        return new ItemSpec(slot, material,
                configuredTitle == null ? fallback.title()
                        : LegacyText.component(replace(configuredTitle, placeholders))
                        .decoration(TextDecoration.ITALIC, false),
                configuredLore == null ? fallback.lore() : configuredLore.stream()
                        .map(line -> LegacyText.component(replace(line, placeholders))
                                .decoration(TextDecoration.ITALIC, false)).toList(), glint);
    }

    public static @NotNull Component component(@NotNull String path) {
        return LegacyText.component(text(path)).decoration(TextDecoration.ITALIC, false);
    }

    public static @NotNull Component component(@NotNull String path, @NotNull Map<String, ?> placeholders) {
        return LegacyText.component(text(path, placeholders)).decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull String replace(@NotNull String value, @NotNull Map<String, ?> placeholders) {
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return value;
    }

    private static int stateValueInt(String statePath, String basePath, String leaf, int fallback) {
        if (statePath != null && active.isInt(statePath + "." + leaf))
            return active.getInt(statePath + "." + leaf);
        return active.isInt(basePath + "." + leaf) ? active.getInt(basePath + "." + leaf) : fallback;
    }

    private static boolean stateValueBoolean(String statePath, String basePath, String leaf, boolean fallback) {
        if (statePath != null && active.isBoolean(statePath + "." + leaf))
            return active.getBoolean(statePath + "." + leaf);
        return active.isBoolean(basePath + "." + leaf) ? active.getBoolean(basePath + "." + leaf) : fallback;
    }

    private static @Nullable String stateValueString(String statePath, String basePath, String leaf,
                                                      @Nullable String fallback) {
        if (statePath != null && active.isString(statePath + "." + leaf))
            return active.getString(statePath + "." + leaf, fallback);
        return active.getString(basePath + "." + leaf, fallback);
    }

    private static @Nullable List<String> stateValueLines(String statePath, String basePath, String leaf) {
        if (statePath != null && active.isList(statePath + "." + leaf))
            return active.getStringList(statePath + "." + leaf);
        return active.isList(basePath + "." + leaf) ? active.getStringList(basePath + "." + leaf) : null;
    }

    private static Material materialValue(String statePath, String basePath, String leaf, Material fallback) {
        if (statePath != null && active.isString(statePath + "." + leaf))
            return material(statePath + "." + leaf, fallback);
        return material(basePath + "." + leaf, fallback);
    }

    public record MenuSpec(int size, @NotNull Component title, @NotNull List<Integer> contentSlots) {
    }

    public record ItemSpec(int slot, @NotNull Material material, @NotNull Component title,
                           @NotNull List<Component> lore, boolean glint) {
    }
}
