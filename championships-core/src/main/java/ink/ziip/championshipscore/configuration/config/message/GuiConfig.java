package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Configurable text used by inventory menus, hotbar controls and map-preparation screens. */
public final class GuiConfig extends BaseConfigurationFile {
    private static volatile YamlConfiguration active = new YamlConfiguration();

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
        return 45;
    }

    @Override
    protected void loadCustomFileOptions() {
        try (var stream = plugin.getResource(getResourceName())) {
            if (stream == null) throw new IllegalStateException("Missing gui.yml resource");
            var defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<String> errors = validate(configuration);
            if (!errors.isEmpty())
                throw new IllegalArgumentException("Invalid gui.yml: " + String.join("; ", errors));
            configuration.setDefaults(defaults);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot load gui.yml defaults", error);
        }
        active = configuration;
    }

    /** Reports invalid menu structure before a reload exposes it to live inventories. */
    static List<String> validate(YamlConfiguration document) {
        List<String> errors = new ArrayList<>();
        for (String path : document.getKeys(true)) {
            if (document.isConfigurationSection(path)) continue;
            Object value = document.get(path);
            if (path.endsWith(".size")) {
                if (!(value instanceof Integer size) || size < 9 || size > 54 || size % 9 != 0)
                    errors.add(path + " must be 9, 18, 27, 36, 45 or 54");
            } else if (path.endsWith(".slot")) {
                int limit = path.contains(".hotbar.") ? 9 : documentMenuSize(document, path);
                if (!(value instanceof Integer slot) || slot < 0 || slot >= limit)
                    errors.add(path + " must be between 0 and " + (limit - 1));
            } else if (path.endsWith(".layout.content") || path.endsWith(".layout.border")) {
                int limit = documentMenuSize(document, path);
                if (!(value instanceof List<?> entries)) {
                    errors.add(path + " must be a list of slots");
                    continue;
                }
                HashSet<Integer> seen = new HashSet<>();
                for (Object entry : entries) {
                    if (!(entry instanceof Integer slot)
                            || slot < 0
                            || slot >= limit
                            || !seen.add(slot)) {
                        errors.add(path + " contains a duplicate or invalid slot: " + entry);
                        break;
                    }
                }
            } else if (path.endsWith(".material")) {
                Material material =
                        value instanceof String name ? Material.matchMaterial(name) : null;
                if (material == null
                        || material == Material.AIR
                        || material == Material.CAVE_AIR
                        || material == Material.VOID_AIR)
                    errors.add(path + " is not a usable material");
            } else if (path.endsWith(".use")) {
                if (!(value instanceof String id)
                        || !document.isConfigurationSection("buttons." + id))
                    errors.add(path + " references an unknown button");
            } else if (path.endsWith(".title") && !(value instanceof String)) {
                errors.add(path + " must be text");
            } else if (path.endsWith(".lore")
                    && (!(value instanceof List<?> lines)
                            || lines.stream().anyMatch(line -> !(line instanceof String)))) {
                errors.add(path + " must be a list of text lines");
            }
        }
        return errors;
    }

    private static int documentMenuSize(YamlConfiguration document, String path) {
        int layout = path.indexOf(".layout.");
        int items = path.indexOf(".items.");
        int boundary = layout >= 0 ? layout : items;
        return boundary < 0 ? 54 : document.getInt(path.substring(0, boundary) + ".size", 54);
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

    public static @NotNull String line(@NotNull String path, int index) {
        List<String> lines = active.getStringList(path);
        return index >= 0 && index < lines.size() ? lines.get(index) : "";
    }

    public static @NotNull String line(
            @NotNull String path, int index, @NotNull Map<String, ?> placeholders) {
        return replace(line(path, index), placeholders);
    }

    public static @NotNull List<String> lines(
            @NotNull String path, @NotNull Map<String, ?> placeholders) {
        return lines(path).stream().map(line -> replace(line, placeholders)).toList();
    }

    public static int integer(@NotNull String path, int fallback) {
        return active.isInt(path) ? active.getInt(path) : fallback;
    }

    public static @NotNull List<Integer> slots(
            @NotNull String path, @NotNull List<Integer> fallback) {
        return slots(path, fallback, layoutSize(path));
    }

    private static @NotNull List<Integer> slots(
            @NotNull String path, @NotNull List<Integer> fallback, int size) {
        List<Integer> configured = validSlots(active.getList(path), size);
        return configured.isEmpty() ? validSlots(fallback, size) : configured;
    }

    private static @NotNull List<Integer> validSlots(@Nullable List<?> values, int size) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .filter(slot -> slot >= 0 && slot < size)
                .distinct()
                .toList();
    }

    /** Resolves one fixed control slot using the same bounds as the menu layout. */
    public static int slot(@NotNull String path, int fallback) {
        int configured = integer(path, fallback);
        int size = layoutSize(path);
        return configured >= 0 && configured < size ? configured : fallback;
    }

    private static int layoutSize(String path) {
        int layout = path.indexOf(".layout.");
        int items = path.indexOf(".items.");
        int boundary = layout >= 0 ? layout : items;
        if (boundary < 0) return 54;
        int size = integer(path.substring(0, boundary) + ".size", 54);
        return size >= 9 && size <= 54 && size % 9 == 0 ? size : 54;
    }

    public static @NotNull Material material(@NotNull String path, @NotNull Material fallback) {
        String configured = active.getString(path);
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null || material.isAir() ? fallback : material;
    }

    public static @NotNull MenuSpec menu(
            @NotNull String path,
            int fallbackSize,
            @NotNull String fallbackTitle,
            @NotNull List<Integer> fallbackContentSlots) {
        int size = menuSize(path, fallbackSize);
        Component title =
                LegacyText.component(active.getString(path + ".title", fallbackTitle))
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return new MenuSpec(
                size, title, slots(path + ".layout.content", fallbackContentSlots, size));
    }

    public static @NotNull MenuSpec menu(
            @NotNull String path,
            int fallbackSize,
            @NotNull Component fallbackTitle,
            @NotNull List<Integer> fallbackContentSlots) {
        int size = menuSize(path, fallbackSize);
        Component title =
                active.isString(path + ".title")
                        ? LegacyText.component(active.getString(path + ".title", ""))
                        : fallbackTitle;
        title = title.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return new MenuSpec(
                size, title, slots(path + ".layout.content", fallbackContentSlots, size));
    }

    private static int menuSize(String path, int fallbackSize) {
        int configured = integer(path + ".size", fallbackSize);
        return configured >= fallbackSize && configured <= 54 && configured % 9 == 0
                ? configured
                : fallbackSize;
    }

    public static @NotNull ItemSpec item(
            @NotNull String path, @NotNull Map<String, ?> placeholders) {
        return item(path, null, placeholders);
    }

    /**
     * A state section overrides only the fields it declares and inherits the rest from the button.
     */
    public static @NotNull ItemSpec item(
            @NotNull String path, String state, @NotNull Map<String, ?> placeholders) {
        return item(
                path,
                state,
                placeholders,
                new ItemSpec(-1, Material.BARRIER, LegacyText.component(path), List.of(), false));
    }

    /** Reads a configured item, inheriting unset fields from its shared button template. */
    public static @NotNull ItemSpec item(
            @NotNull String path,
            String state,
            @NotNull Map<String, ?> placeholders,
            @NotNull ItemSpec fallback) {
        String use = active.isString(path + ".use") ? active.getString(path + ".use", "") : "";
        ItemSpec template = use.isBlank() ? fallback : button(use, state, placeholders, fallback);
        return item(path, state, placeholders, fallback, template);
    }

    /** Reads a shared button by stable identifier. */
    public static @NotNull ItemSpec button(
            @NotNull ButtonId button,
            @NotNull Map<String, ?> placeholders,
            @NotNull ItemSpec fallback) {
        return button(button.id(), null, placeholders, fallback);
    }

    /** Reads a shared button template; menu items may override any individual field. */
    public static @NotNull ItemSpec button(
            @NotNull String id, @NotNull Map<String, ?> placeholders, @NotNull ItemSpec fallback) {
        return button(id, null, placeholders, fallback);
    }

    private static @NotNull ItemSpec button(
            @NotNull String id,
            @Nullable String state,
            @NotNull Map<String, ?> placeholders,
            @NotNull ItemSpec fallback) {
        return item("buttons." + id, state, placeholders, fallback, fallback);
    }

    private static @NotNull ItemSpec item(
            @NotNull String path,
            @Nullable String state,
            @NotNull Map<String, ?> placeholders,
            @NotNull ItemSpec fallback,
            @NotNull ItemSpec template) {
        String statePath = state == null || state.isBlank() ? null : path + ".states." + state;
        int slot = stateValueInt(statePath, path, "slot", template.slot());
        Material material = materialValue(statePath, path, "material", template.material());
        String configuredTitle = stateValueString(statePath, path, "title", null);
        List<String> configuredLore = stateValueLines(statePath, path, "lore");
        boolean glint = stateValueBoolean(statePath, path, "glint", template.glint());
        return new ItemSpec(
                slot,
                material,
                configuredTitle == null
                        ? template.title()
                        : LegacyText.component(replace(configuredTitle, placeholders))
                                .decorationIfAbsent(
                                        TextDecoration.ITALIC, TextDecoration.State.FALSE),
                configuredLore == null
                        ? template.lore()
                        : configuredLore.stream()
                                .map(
                                        line ->
                                                LegacyText.component(replace(line, placeholders))
                                                        .decorationIfAbsent(
                                                                TextDecoration.ITALIC,
                                                                TextDecoration.State.FALSE))
                                .toList(),
                glint);
    }

    public static @NotNull Component component(@NotNull String path) {
        return LegacyText.component(text(path))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static @NotNull Component component(
            @NotNull String path, @NotNull Map<String, ?> placeholders) {
        return LegacyText.component(text(path, placeholders))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static @NotNull String replace(
            @NotNull String value, @NotNull Map<String, ?> placeholders) {
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            value =
                    value.replace(
                            "%" + entry.getKey() + "%",
                            entry.getValue() instanceof Component component
                                    ? LegacyText.serialize(component)
                                    : String.valueOf(entry.getValue()));
        return value;
    }

    private static int stateValueInt(String statePath, String basePath, String leaf, int fallback) {
        if (statePath != null && active.isInt(statePath + "." + leaf))
            return active.getInt(statePath + "." + leaf);
        return active.isInt(basePath + "." + leaf)
                ? active.getInt(basePath + "." + leaf)
                : fallback;
    }

    private static boolean stateValueBoolean(
            String statePath, String basePath, String leaf, boolean fallback) {
        if (statePath != null && active.isBoolean(statePath + "." + leaf))
            return active.getBoolean(statePath + "." + leaf);
        return active.isBoolean(basePath + "." + leaf)
                ? active.getBoolean(basePath + "." + leaf)
                : fallback;
    }

    private static @Nullable String stateValueString(
            String statePath, String basePath, String leaf, @Nullable String fallback) {
        if (statePath != null && active.isString(statePath + "." + leaf))
            return active.getString(statePath + "." + leaf, fallback);
        return active.getString(basePath + "." + leaf, fallback);
    }

    private static @Nullable List<String> stateValueLines(
            String statePath, String basePath, String leaf) {
        if (statePath != null && active.isList(statePath + "." + leaf))
            return active.getStringList(statePath + "." + leaf);
        return active.isList(basePath + "." + leaf)
                ? active.getStringList(basePath + "." + leaf)
                : null;
    }

    private static Material materialValue(
            String statePath, String basePath, String leaf, Material fallback) {
        if (statePath != null && active.isString(statePath + "." + leaf))
            return material(statePath + "." + leaf, fallback);
        return material(basePath + "." + leaf, fallback);
    }

    public record MenuSpec(
            int size, @NotNull Component title, @NotNull List<Integer> contentSlots) {}

    public record ItemSpec(
            int slot,
            @NotNull Material material,
            @NotNull Component title,
            @NotNull List<Component> lore,
            boolean glint) {}
}
