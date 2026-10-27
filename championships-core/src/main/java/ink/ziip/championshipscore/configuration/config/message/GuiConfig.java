package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;
import ink.ziip.championshipscore.shared.presentation.InventoryItemTemplate;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
        return 63;
    }

    @Override
    protected void loadCustomFileOptions() {
        try (var stream = plugin.getResource(getResourceName())) {
            if (stream == null) throw new IllegalStateException("Missing gui.yml resource");
            var defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            configuration.setDefaults(defaults);
            List<String> errors = validate(configuration, Material::isItem);
            if (!errors.isEmpty())
                throw new IllegalArgumentException("Invalid gui.yml: " + String.join("; ", errors));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot load gui.yml defaults", error);
        }
        active = configuration;
    }

    /** Reports invalid menu structure before a reload exposes it to live inventories. */
    static List<String> validate(YamlConfiguration document) {
        return validate(document, material -> true);
    }

    /** The live loader supplies the platform item registry; structural tests need no server. */
    static List<String> validate(YamlConfiguration document, Predicate<Material> isItem) {
        List<String> errors = new ArrayList<>();
        var paths = new HashSet<>(document.getKeys(true));
        if (document.getDefaults() != null) paths.addAll(document.getDefaults().getKeys(true));
        for (String path : paths) {
            if (path.contains(".layout.content.")) continue;
            if (path.endsWith(".layout.content") && document.isConfigurationSection(path)) {
                String menu = path.substring(0, path.length() - ".layout.content".length());
                if (!MenuId.supportsContentArrangements(menu))
                    errors.add(path + " does not support count-specific placements");
                List<?> largest = List.of();
                for (String key : sectionKeys(document, path)) {
                    List<?> slots = document.getList(path + "." + key);
                    int count;
                    try {
                        count = Integer.parseInt(key);
                    } catch (NumberFormatException invalid) {
                        count = -1;
                    }
                    if (count < 1 || count > 54 || slots == null || slots.size() != count) {
                        errors.add(path + "." + key + " must name its positive slot count");
                        continue;
                    }
                    validateSlots(
                            errors, path + "." + key, slots, documentMenuSize(document, path));
                    if (slots.size() > largest.size()) largest = slots;
                }
                validateContentCapacity(errors, document, path, largest, paths);
                continue;
            }
            if (document.isConfigurationSection(path)) continue;
            Object value = document.get(path);
            if (path.endsWith(".size")) {
                if (!(value instanceof Integer size) || size < 9 || size > 54 || size % 9 != 0)
                    errors.add(path + " must be 9, 18, 27, 36, 45 or 54");
                else if (document.getDefaults() != null
                        && document.getDefaults().isInt(path)
                        && size < document.getDefaults().getInt(path))
                    errors.add(path + " must be at least " + document.getDefaults().getInt(path));
            } else if (path.endsWith(".slot")
                    || path.contains(".layout.footer.")
                    || path.contains(".layout.overlay.")) {
                int limit = documentMenuSize(document, path);
                if (!(value instanceof Integer slot) || slot < 0 || slot >= limit)
                    errors.add(path + " must be between 0 and " + (limit - 1));
            } else if (path.endsWith(".layout.content") || path.endsWith(".layout.border")) {
                int limit = documentMenuSize(document, path);
                if (!(value instanceof List<?> entries)) {
                    errors.add(path + " must be a list of slots");
                    continue;
                }
                validateSlots(errors, path, entries, limit);
                if (path.endsWith(".layout.content")) {
                    validateContentCapacity(errors, document, path, entries, paths);
                }
            } else if (path.endsWith(".presentation.bar-length")) {
                if (!(value instanceof Integer length) || length < 1 || length > 64)
                    errors.add(path + " must be between 1 and 64");
            } else if (path.endsWith(".presentation.bar-filled")
                    || path.endsWith(".presentation.bar-empty")
                    || path.equals(
                            MenuId.MAP_EDITOR_LIST_EDITOR.path() + ".presentation.detail-row")
                    || path.equals(
                            MenuId.DAILY_BINGO_VOTE.path() + ".presentation.description-row")) {
                if (!(value instanceof String)) errors.add(path + " must be text");
            } else if (path.endsWith(".presentation.map-preview-limit")) {
                if (!(value instanceof Integer limit) || limit < 0)
                    errors.add(path + " must be a non-negative integer");
            } else if (path.endsWith(".material") || path.endsWith(".fallback-material")) {
                String taskPath = MenuId.BINGO_CARD.path() + ".items.task";
                if ("TASK".equals(value)
                        && (path.equals(taskPath + ".material")
                                || path.matches(
                                        java.util.regex.Pattern.quote(taskPath)
                                                + "\\.states\\.(normal|completed|occupied)\\.material")))
                    continue;
                Material material =
                        value instanceof String name ? Material.matchMaterial(name) : null;
                if (material == null
                        || material == Material.AIR
                        || material == Material.CAVE_AIR
                        || material == Material.VOID_AIR
                        || !isItem.test(material)) errors.add(path + " is not a usable material");
            } else if (path.endsWith(".use")) {
                if (!(value instanceof String id)
                        || !document.isConfigurationSection("buttons." + id))
                    errors.add(path + " references an unknown button");
            } else if ((path.endsWith(".title")
                            || path.startsWith("spectator.feedback.")
                            || path.equals("games.bingo.hotbar.spectator.speed.feedback"))
                    && !(value instanceof String)) {
                errors.add(path + " must be text");
            } else if (path.endsWith(".glint") && !(value instanceof Boolean)) {
                errors.add(path + " must be true or false");
            } else if ((path.endsWith(".lore") || path.endsWith(".completion-summary"))
                    && (!(value instanceof List<?> lines)
                            || lines.stream().anyMatch(line -> !(line instanceof String)))) {
                errors.add(path + " must be a list of text lines");
            } else if (path.startsWith(MenuId.BINGO_CARD.path() + ".task-presentation.")
                    && !path.endsWith(".completion-summary")
                    && !(value instanceof String)) {
                errors.add(path + " must be text");
            }
        }
        validateBingoCardLayouts(errors, document);
        validateLaserEquipment(errors, document);
        validateHotbarSlots(
                errors,
                document,
                "spectator.hotbar",
                List.of(
                        "night-vision",
                        "player-teleport",
                        "leave",
                        "flight-speed",
                        "venue-selector"));
        validateHotbarSlots(
                errors,
                document,
                "games.bingo.hotbar.spectator",
                List.of("card", "teleport", "speed"));
        String taskRoot = MenuId.BINGO_CARD.path() + ".items.task";
        if (document.isConfigurationSection(taskRoot)) {
            for (String state : List.of("hidden", "locked")) {
                try {
                    BingoTaskPresentation.validateMasked(itemTemplate(document, taskRoot, state));
                } catch (IllegalArgumentException invalid) {
                    errors.add(taskRoot + ".states." + state + ": " + invalid.getMessage());
                }
            }
        }
        for (MenuId menu : MenuId.values()) {
            if (!document.isInt(menu.path() + ".size")
                    && !document.isConfigurationSection(menu.path() + ".layout")
                    && menu != MenuId.MAP_EDITOR_PREPARE_TOOLBAR) continue;
            int size = documentMenuSize(document, menu.path() + ".layout.footer.control");
            if ((menu.path().startsWith("daily.")
                            && MenuId.supportsContentArrangements(menu.path()))
                    || menu == MenuId.VOTING_BALLOT
                    || menu == MenuId.SPECTATOR_VENUE_SELECTOR
                    || menu.path().startsWith("teams.menus.")
                            && document.isConfigurationSection(menu.item("empty"))) {
                String item = MenuId.itemFamilyFor(menu.path()) + ".items.empty";
                String empty =
                        item + (menu.path().endsWith(".detail") ? ".states.detail" : "") + ".slot";
                String overlay = menu.path() + ".layout.overlay.empty";
                if (document.isInt(overlay)) empty = overlay;
                int slot =
                        document.isInt(empty)
                                ? document.getInt(empty)
                                : document.getInt(item + ".slot", 22);
                if (slot < 0
                        || slot >= size
                        || resolvedControls(document, menu.path(), paths).containsValue(slot))
                    errors.add(empty + " must fit outside this screen's fixed controls");
            }
            var occupied = new HashMap<Integer, String>();
            var controls = resolvedControls(document, menu.path(), paths);
            for (String path : paths)
                if (path.startsWith(menu.path() + ".layout.overlay.")
                        && document.isInt(path)
                        && controls.containsValue(document.getInt(path)))
                    errors.add(path + " overlaps a fixed control");
            controls.forEach(
                    (id, slot) -> {
                        if (!MenuId.itemFamilyFor(menu.path()).equals(menu.path())
                                && (slot < 0 || slot >= size))
                            errors.add(
                                    menu.path()
                                            + ".layout.footer."
                                            + id
                                            + " resolves outside its inventory: "
                                            + slot);
                        String previous = occupied.putIfAbsent(slot, id);
                        if (previous != null
                                && !MenuId.mutuallyExclusiveControls(menu.path(), previous, id))
                            errors.add(
                                    menu.path()
                                            + " controls "
                                            + previous
                                            + " and "
                                            + id
                                            + " share slot "
                                            + slot);
                    });
        }
        return errors;
    }

    private static void validateHotbarSlots(
            List<String> errors, YamlConfiguration document, String root, List<String> controls) {
        if (!document.isConfigurationSection(root)) return;
        var occupied = new HashMap<Integer, String>();
        for (String control : controls) {
            String path = root + "." + control + ".slot";
            if (!document.isInt(path))
                continue; // The generic leaf validator reports invalid types.
            int slot = document.getInt(path);
            String previous = occupied.putIfAbsent(slot, control);
            if (previous != null)
                errors.add(
                        root + " controls " + previous + " and " + control + " share slot " + slot);
        }
    }

    private static void validateLaserEquipment(List<String> errors, YamlConfiguration document) {
        for (LaserGun gun : LaserGun.values()) {
            String path = "laser.equipment.guns." + gun.id();
            if (!document.isConfigurationSection(path)) continue;
            for (String state : new String[] {null, "idle", "selected"}) {
                if (!itemTemplate(document, path, state).material().equals(gun.material().name()))
                    errors.add(path + " must keep " + gun.material() + " for weapon mechanics");
            }
        }
        String ammo = "laser.equipment.ammo";
        if (document.isConfigurationSection(ammo)
                && !itemTemplate(document, ammo, null).material().equals("SPECTRAL_ARROW"))
            errors.add(ammo + " must keep SPECTRAL_ARROW for ammunition mechanics");
    }

    private static void validateSlots(
            List<String> errors, String path, List<?> entries, int limit) {
        var seen = new HashSet<Integer>();
        for (Object entry : entries)
            if (!(entry instanceof Integer slot) || slot < 0 || slot >= limit || !seen.add(slot)) {
                errors.add(path + " contains a duplicate or invalid slot: " + entry);
                break;
            }
    }

    private static void validateBingoCardLayouts(List<String> errors, YamlConfiguration document) {
        String root = MenuId.BINGO_CARD.path();
        if (!document.isConfigurationSection(root)) return;
        for (int width :
                ink.ziip.championshipscore.shared.presentation.BingoCardPresentation.WIDTHS) {
            String path = root + ".screens.x" + width;
            if (!document.isConfigurationSection(path)) {
                errors.add(path + " must define a complete card layout");
                continue;
            }
            var slots = document.getIntegerList(path + ".layout.content");
            int info = controlSlot(document, path, root, "info", 0);
            int size = document.getInt(path + ".size", width * 9);
            if (slots.size() != width * width)
                errors.add(
                        path
                                + ".layout.content must contain exactly "
                                + width * width
                                + " task slots");
            if (slots.contains(info) || info < 0 || info >= size)
                errors.add(path + " info must fit outside card task slots");
        }
    }

    private static void validateContentCapacity(
            List<String> errors,
            YamlConfiguration document,
            String path,
            List<?> entries,
            java.util.Set<String> paths) {
        String menu = path.substring(0, path.length() - ".layout.content".length());
        var reserved = resolvedControls(document, menu, paths).values();
        if (MenuId.isPagedMenu(menu)
                && (entries.isEmpty() || entries.stream().allMatch(reserved::contains)))
            errors.add(path + " must leave at least one content slot outside fixed controls");
    }

    private static Map<String, Integer> resolvedControls(
            YamlConfiguration document, String menu, java.util.Set<String> paths) {
        String family = MenuId.itemFamilyFor(menu);
        var ids = new HashSet<String>();
        for (String path : paths) {
            String footer = menu + ".layout.footer.";
            String item = menu + ".items.";
            if (path.startsWith(footer) && document.isInt(path))
                ids.add(path.substring(footer.length()));
            else if (family.equals(menu)
                    && path.startsWith(item)
                    && path.endsWith(".slot")
                    && document.isInt(path)) {
                String id = path.substring(item.length(), path.length() - ".slot".length());
                if (!id.contains(".")) ids.add(id);
            }
        }
        if ((menu.equals(MenuId.VOTING_BALLOT.path())
                || menu.equals(MenuId.SPECTATOR_VENUE_SELECTOR.path()))) ids.remove("empty");
        var result = new HashMap<String, Integer>();
        for (String id : ids) result.put(id, controlSlot(document, menu, family, id, -1));
        return result;
    }

    private static int documentMenuSize(YamlConfiguration document, String path) {
        if (path.contains(".hotbar.")
                || path.startsWith(MenuId.MAP_EDITOR_PREPARE_TOOLBAR.path() + ".")) return 9;
        int layout = path.indexOf(".layout.");
        int items = path.indexOf(".items.");
        int boundary = layout >= 0 ? layout : items;
        String sizePath = boundary < 0 ? "" : path.substring(0, boundary) + ".size";
        return document.isInt(sizePath) ? document.getInt(sizePath) : 54;
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
        if (active.isConfigurationSection(path))
            return slotArrangements(path).values().stream()
                    .max(java.util.Comparator.comparingInt(List::size))
                    .orElse(fallback);
        List<Integer> configured = validSlots(active.getList(path), size);
        if (path.endsWith(".layout.border") && active.isList(path)) return configured;
        return configured.isEmpty() ? validSlots(fallback, size) : configured;
    }

    /** A content list may instead declare one placement for each supported visible count. */
    public static Map<Integer, List<Integer>> slotArrangements(String path) {
        var section = active.getConfigurationSection(path);
        if (section == null) return Map.of();
        var result = new HashMap<Integer, List<Integer>>();
        for (String key : sectionKeys(active, path)) {
            try {
                int count = Integer.parseInt(key);
                var slots = validSlots(active.getList(path + "." + key), layoutSize(path));
                if (count > 0 && slots.size() == count) result.put(count, slots);
            } catch (NumberFormatException ignored) {
                // The loader reports invalid counts before installing this configuration.
            }
        }
        return Map.copyOf(result);
    }

    private static java.util.Set<String> sectionKeys(YamlConfiguration document, String path) {
        var keys = new HashSet<String>();
        var section = document.getConfigurationSection(path);
        if (section != null) keys.addAll(section.getKeys(false));
        if (document.getDefaults() != null) {
            var defaults = document.getDefaults().getConfigurationSection(path);
            if (defaults != null) keys.addAll(defaults.getKeys(false));
        }
        return keys;
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

    /** Explicit window overrides, then existing family overrides, then bundled defaults. */
    public static int controlSlot(String path, String itemPath, String id, int fallback) {
        return controlSlot(active, path, itemPath, id, fallback);
    }

    private static int controlSlot(
            YamlConfiguration document, String path, String itemPath, String id, int fallback) {
        List<String> candidates =
                List.of(
                        path + ".layout.footer." + id,
                        path + ".items." + id + ".slot",
                        itemPath + ".items." + id + ".slot");
        for (String candidate : candidates)
            if (document.isSet(candidate) && document.isInt(candidate))
                return document.getInt(candidate);
        for (String candidate : candidates)
            if (document.isInt(candidate)) return document.getInt(candidate);
        return fallback;
    }

    private static int layoutSize(String path) {
        int size = documentMenuSize(active, path);
        return size >= 9 && size <= 54 && size % 9 == 0 ? size : 54;
    }

    public static @NotNull Material material(@NotNull String path, @NotNull Material fallback) {
        String configured = active.getString(path);
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null || material.isAir() || !material.isItem() ? fallback : material;
    }

    public static @NotNull MenuSpec menu(
            @NotNull String path,
            int fallbackSize,
            @NotNull String fallbackTitle,
            @NotNull List<Integer> fallbackContentSlots) {
        int size = menuSize(path, fallbackSize);
        Component title =
                LegacyText.component(stringValue(path + ".title", fallbackTitle))
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
                        ? LegacyText.component(active.getString(path + ".title"))
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

    /** Raw inherited lore for renderers which insert computed rows before parameter expansion. */
    public static List<String> itemLoreTemplate(String path, String state) {
        String statePath = state == null ? null : path + ".states." + state;
        var lines = stateValueLines(statePath, path, "lore");
        if (lines != null) return lines;
        String use = stringValue(path + ".use", "");
        if (use.isBlank()) return List.of();
        String template = "buttons." + use;
        lines =
                stateValueLines(
                        state == null ? null : template + ".states." + state, template, "lore");
        return lines == null ? List.of() : lines;
    }

    /** Reads a configured item, inheriting unset fields from its shared button template. */
    public static @NotNull ItemSpec item(
            @NotNull String path,
            String state,
            @NotNull Map<String, ?> placeholders,
            @NotNull ItemSpec fallback) {
        String use = stringValue(path + ".use", "");
        ItemSpec template = use.isBlank() ? fallback : button(use, state, placeholders, fallback);
        return item(path, state, placeholders, fallback, template);
    }

    /** Freeze effective item inheritance without requiring the native Bukkit material registry. */
    public static InventoryItemTemplate itemTemplate(String path, String state) {
        return itemTemplate(active, path, state);
    }

    /** The same effective inheritance when validating a candidate document before activation. */
    public static InventoryItemTemplate itemTemplate(
            YamlConfiguration document, String path, String state) {
        var fallback = new InventoryItemTemplate("PAPER", "", List.of(), false);
        String configuredUse = document.getString(path + ".use");
        String use = configuredUse == null ? "" : configuredUse;
        var template =
                use.isBlank()
                        ? fallback
                        : itemTemplateValues(document, "buttons." + use, state, fallback);
        return itemTemplateValues(document, path, state, template);
    }

    private static InventoryItemTemplate itemTemplateValues(
            YamlConfiguration document, String path, String state, InventoryItemTemplate fallback) {
        String statePath = state == null || state.isBlank() ? null : path + ".states." + state;
        var lore = stateValueLines(document, statePath, path, "lore");
        return new InventoryItemTemplate(
                stateValueString(document, statePath, path, "material", fallback.material())
                        .toUpperCase(java.util.Locale.ROOT),
                stateValueString(document, statePath, path, "title", fallback.title()),
                lore == null ? fallback.lore() : lore,
                stateValueBoolean(document, statePath, path, "glint", fallback.glint()));
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
        Map<String, String> replacements = new HashMap<>();
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            replacements.put(
                    "%" + entry.getKey() + "%",
                    entry.getValue() instanceof Component component
                            ? LegacyText.serialize(component)
                            : String.valueOf(entry.getValue()));
        return MessageTemplate.render(value, replacements);
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
        return stateValueBoolean(active, statePath, basePath, leaf, fallback);
    }

    private static boolean stateValueBoolean(
            YamlConfiguration document,
            String statePath,
            String basePath,
            String leaf,
            boolean fallback) {
        if (statePath != null && document.isBoolean(statePath + "." + leaf))
            return document.getBoolean(statePath + "." + leaf);
        return document.isBoolean(basePath + "." + leaf)
                ? document.getBoolean(basePath + "." + leaf)
                : fallback;
    }

    private static @Nullable String stateValueString(
            String statePath, String basePath, String leaf, @Nullable String fallback) {
        return stateValueString(active, statePath, basePath, leaf, fallback);
    }

    private static @Nullable String stateValueString(
            YamlConfiguration document,
            String statePath,
            String basePath,
            String leaf,
            @Nullable String fallback) {
        if (statePath != null && document.isString(statePath + "." + leaf))
            return document.getString(statePath + "." + leaf);
        return document.isString(basePath + "." + leaf)
                ? document.getString(basePath + "." + leaf)
                : fallback;
    }

    private static @Nullable String stringValue(String path, @Nullable String fallback) {
        return active.isString(path) ? active.getString(path) : fallback;
    }

    private static @Nullable List<String> stateValueLines(
            String statePath, String basePath, String leaf) {
        return stateValueLines(active, statePath, basePath, leaf);
    }

    private static @Nullable List<String> stateValueLines(
            YamlConfiguration document, String statePath, String basePath, String leaf) {
        if (statePath != null && document.isList(statePath + "." + leaf))
            return document.getStringList(statePath + "." + leaf);
        return document.isList(basePath + "." + leaf)
                ? document.getStringList(basePath + "." + leaf)
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
