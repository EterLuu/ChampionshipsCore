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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Configurable text used by inventory menus, hotbar controls and map-preparation screens. */
public final class GuiConfig extends BaseConfigurationFile {
    private static YamlConfiguration active = new YamlConfiguration();
    private static final Pattern LEGACY_GAME_FLOW_SECTION =
            Pattern.compile("^map-editor\\.games\\.([a-z0-9-]+)\\.(?:steps|setup)\\.(.+)$");

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
        active = configuration;
    }

    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration outdated) throws IOException {
        if (outdated.getInt("dont-edit-this.version") >= 23) {
            preserveCurrentMenus(outdated, configuration);
            configuration.save(configurationPath.toFile());
            return;
        }
        // v15 -> v16: shared copy was folded into menu-local text and global button templates.
        for (String key : outdated.getKeys(true)) {
            if (outdated.isConfigurationSection(key)) continue;
            String migrated = migrateLegacyKey(key);
            if (migrated != null && !configuration.isSet(migrated)) {
                configuration.set(migrated, outdated.get(key));
            }
        }
        super.loadFromOutdatedConfiguration(outdated);
    }

    /** Preserve custom menus, migrate the old Riptide editor, and replace only stock mechanic copy. */
    static void preserveCurrentMenus(YamlConfiguration existing, YamlConfiguration defaults) {
        Map<String, String> replacements = Map.ofEntries(
                Map.entry("&#a0a0a0首尾安排穿越，解题和踩色之间隔开穿越", "&#a0a0a0首尾安排穿越，不同特殊类型可相邻"),
                Map.entry("&#a0a0a0相邻穿越玩法不同；踩色按区段分布", "&#a0a0a0连续穿越错开通行位置；停船挑战按区段分布"),
                Map.entry("踩色每局 %quota% 关 • 侧向按航程编排", "停船挑战每局 %quota% 关 • 含踩色与侧向"),
                Map.entry("&#a0a0a0踩色 %floor% 关 • 节奏 %rhythm% 关", "&#a0a0a0停船挑战 %floor% 关 • 节奏 %rhythm% 关"),
                Map.entry("创建节拍闸门，可切换七种节奏变体", "创建节拍闸门，可切换十一种节奏变体"),
                Map.entry("编辑七种动态闸门与每局数量，木筏持续前进", "编辑十一种动态闸门与每局数量，木筏持续前进"),
                Map.entry("编辑节拍闸门、左右交替闸门与每局数量", "编辑十一种动态闸门与每局数量，木筏持续前进"),
                Map.entry("编辑四种动态闸门与每局数量，木筏持续前进", "编辑十一种动态闸门与每局数量，木筏持续前进"),
                Map.entry("从左到右找出指定位置的数字", "按题目要求从左或右找出指定位置的数字"),
                Map.entry("停船9秒；3秒归位，两次2秒通行窗口", "木筏持续前进，全门按固定节拍开合"),
                Map.entry("两次窗口依次开放不同半边，初始侧随机", "木筏持续前进，左右半边循环交替开放"),
                Map.entry("创建节拍闸门，可切换左右交替变体", "创建节拍闸门，可切换十一种节奏变体"),
                Map.entry("创建节拍闸门，可切换四种节奏变体", "创建节拍闸门，可切换十一种节奏变体"),
                Map.entry("线索与答案同时显示，点击选择", "上行线索，下行问题与答案，点击选择"),
                Map.entry("编辑踩色变体、每局数量与停船时长", "编辑踩色变体、每局数量与阶段难度"),
                Map.entry("&#ededed每关停船时长：%value% 秒", "&#ededed阶段停船时长：%value% 秒"),
                Map.entry("&#a0a0a0初始30秒；第二次加速27秒；第三次加速25秒", "&#a0a0a0初始30秒；第二次加速27秒；第四次加速25秒"),
                Map.entry("&#a0a0a0输入1–30秒，可用小数（精度0.05秒）；七轮按比例缩放", "&#a0a0a0初始30秒；第二次加速27秒；第四次加速25秒"),
                Map.entry("&#a0a0a0应用于本类全部变体，木筏全程原地停留", "&#a0a0a0方块种类随阶段为5/6/7/8种；每关七轮，原地停船"),
                Map.entry("连续穿过两道门，每道独立随机加减乘法；两道合计一个数学配额", "第二次加速后开放；各门按阶段出题，两道合计一个数学配额"),

                Map.entry("&#a0a0a0点击输入1–30；七轮按比例缩放", "&#a0a0a0输入1–30秒，可用小数（精度0.05秒）；七轮按比例缩放"),
                Map.entry("&#a0a0a0支持1.5格高潜行通道；完成后请试玩", "&#a0a0a0不校验连续通路与跳跃高差；完成后请试玩"),
                Map.entry("&#a0a0a0橙色标记之间前后共7格，可建高度6格", "&#a0a0a0橙色端线内长%length%格、宽%width%格、高%height%格"),
                Map.entry("&#ededed可在橙色标记之间拆放方块（前后7格，高6格）；踩色也可修改甲板。用准备工具打开保存/放弃菜单。", "&#ededed施工范围：长%length%格、宽%width%格、高%height%格；橙色端线与蓝色边框内可搭建，玻璃柱顶为限高。踩色也可修改甲板，用准备工具打开保存/放弃菜单。"),
                Map.entry("&#a0a0a0七轮逐轮加速，每轮木筏前移一格", "&#a0a0a0木筏停留原地，七轮逐轮加速"),
                Map.entry("&#a0a0a0生成左右答案门，并判定玩家选择", "&#a0a0a0随机加减法、两位数乘一位数，左右选答案"),
                Map.entry("&#fff566&l停顿类", "&#fff566&l彩色地板类"),
                Map.entry("&#55ff55&l停顿类（当前）", "&#55ff55&l彩色地板类（当前）"),
                Map.entry("&#a0a0a0木筏到达此关时停止固定秒数", "&#a0a0a0木筏停留原地，七轮逐轮加速"),
                Map.entry("&#a0a0a0停船后辨认手中方块，共五轮逐轮加速", "&#a0a0a0木筏停留原地，七轮逐轮加速"),
                Map.entry("&#696969倒计时结束后自动继续前行", "&#696969每轮限时站到目标方块上，站错即出局"),
                Map.entry("&#a0a0a0点击切换算术类、通过类或停顿类", "&#a0a0a0点击切换算术类、通过类或彩色地板类"),
                Map.entry("停顿类的固定暂停秒数必须在1到30秒之间。", "彩色地板关总时长必须在1到30秒之间。"),
                Map.entry("彩色地板类的固定暂停秒数必须在1到30秒之间。", "彩色地板关总时长必须在1到30秒之间。"),
                Map.entry("&#a0a0a0随移动木筏通过障碍、算术门与停顿关", "&#a0a0a0随移动木筏通过障碍、算术门与彩色地板关"));
        var legacyEditor = legacyRiptideEditor();
        var flatEditor = legacyRiptideEditor("/riptiderush/editor-v30.yml");
        var previousEditor = legacyRiptideEditor("/riptiderush/editor-v28.yml");
        for (String key : existing.getKeys(true)) {
            if (key.equals("dont-edit-this.version") || existing.isConfigurationSection(key)) continue;
            String editor = "map-editor.games.riptide-rush.menus.course-editor.";
            if (key.startsWith(editor + "steps.floor.") || key.startsWith(editor + "steps.dodge.")
                    || key.startsWith(editor + "pauses.") || key.startsWith(editor + "items.pause-floor.")
                    || key.startsWith(editor + "items.pause-side.")) continue;
            if (key.startsWith("map-editor.games.riptide-rush.menus.course-editor.items.pause-seconds.")
                    || key.startsWith("map-editor.menus.step-list.games.riptide-rush.validation.pause.")) continue;
            Object value = existing.get(key);
            if (key.endsWith("riptide-rush.menus.course-editor.items.course-summary.lore")
                    && value.equals(List.of("&#a0a0a0穿越 %pass% 关 • 数学 %math% 关 • 踩色 %floor% 关", "&#a0a0a0各类数量与设置在对应类别页调整"))) continue;
            String editorPrefix = "map-editor.games.riptide-rush.menus.";
            if (key.startsWith(editorPrefix + "course-editor.")
                    && (java.util.Objects.equals(value, previousEditor.get(key.substring(editorPrefix.length())))
                    || java.util.Objects.equals(value, flatEditor.get(key.substring(editorPrefix.length()))))) continue;
            if (key.startsWith("riptide-editor.")) {
                // Retired navigation/cycling hints must not overwrite the new hierarchy. Carry over
                // user overrides for controls that still have the same meaning at the new location.
                String target = key.replace("riptide-editor.buttons.",
                        "map-editor.games.riptide-rush.menus.course-editor.items.");
                if (!target.equals(key) && defaults.isSet(target)
                        && !java.util.Objects.equals(value, legacyEditor.get(key))) defaults.set(target, value);
                continue;
            }
            if (key.contains("riptide-rush") || key.endsWith("binding.states.level-type.lore")) {
                if (value instanceof String text) {
                    value = replacements.getOrDefault(text, text);
                    if (key.endsWith("level-type.items.pause.material") && text.equals("CLOCK")) value = "LIME_CONCRETE";
                } else if (value instanceof List<?> lines) {
                    value = lines.stream().map(line -> line instanceof String text
                            ? replacements.getOrDefault(text, text) : line).toList();
                }
            }
            if (key.contains("riptide-rush") || key.endsWith("binding.states.level-type.lore")) {
                if (value instanceof String text) value = renameRiptide(text);
                else if (value instanceof List<?> lines) value = lines.stream()
                        .map(line -> line instanceof String text ? renameRiptide(text) : line).toList();
            }
            if (key.endsWith("riptide-rush.menus.course-editor.items.floor-building-help.lore")
                    && value.equals(List.of("&#a0a0a0甲板逐格布局会保存，七轮使用该布局",
                    "&#a0a0a0至少两种方块，目标方块须分散铺设", "&#a0a0a0上方三格留空；两侧或上方可搭建装饰"))) continue;
            defaults.set(key, value);
        }
    }

    private static YamlConfiguration legacyRiptideEditor() {
        return legacyRiptideEditor("/riptiderush/editor-v27.yml");
    }
    private static YamlConfiguration legacyRiptideEditor(String resource) {
        try (var stream = GuiConfig.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing Riptide v27 migration defaults");
            var old = new YamlConfiguration();
            old.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            return old;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot load Riptide v27 migration defaults", error);
        }
    }

    private static String renameRiptide(String text) {
        return text.replace("彩色地板类", "踩色").replace("彩色地板", "踩色")
                .replace("算术类", "解题").replace("数学", "解题").replace("通过类", "穿越");
    }

    private static @Nullable String migrateLegacyKey(@NotNull String key) {
        return switch (key) {
            case "common.copy.previous-page" -> "buttons.previous.title";
            case "common.copy.next-page" -> "buttons.next.title";
            case "common.copy.page" -> "buttons.page.title";
            case "common.copy.back" -> "buttons.back.title";
            case "common.copy.close" -> "buttons.close.title";
            case "common.copy.refresh" -> "buttons.refresh.title";
            case "common.copy.confirm" -> "buttons.confirm.title";
            case "common.copy.cancel" -> "buttons.cancel.title";
            default -> null;
        };
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

    public static @NotNull String line(@NotNull String path, int index, @NotNull Map<String, ?> placeholders) {
        return replace(line(path, index), placeholders);
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

    /** Reads a configured item, inheriting unset fields from its shared button template. */
    public static @NotNull ItemSpec item(@NotNull String path, String state,
                                         @NotNull Map<String, ?> placeholders,
                                         @NotNull ItemSpec fallback) {
        String use = active.isString(path + ".use") ? active.getString(path + ".use", "") : "";
        ItemSpec template = use.isBlank() ? fallback : button(use, state, placeholders, fallback);
        return item(path, state, placeholders, fallback, template);
    }

    /** Reads a shared button by stable identifier. */
    public static @NotNull ItemSpec button(@NotNull ButtonId button,
                                           @NotNull Map<String, ?> placeholders,
                                           @NotNull ItemSpec fallback) {
        return button(button.id(), null, placeholders, fallback);
    }

    /** Reads a shared button template; menu items may override any individual field. */
    public static @NotNull ItemSpec button(@NotNull String id,
                                           @NotNull Map<String, ?> placeholders,
                                           @NotNull ItemSpec fallback) {
        return button(id, null, placeholders, fallback);
    }

    private static @NotNull ItemSpec button(@NotNull String id, @Nullable String state,
                                            @NotNull Map<String, ?> placeholders,
                                            @NotNull ItemSpec fallback) {
        return item("buttons." + id, state, placeholders, fallback, fallback);
    }

    private static @NotNull ItemSpec item(@NotNull String path, @Nullable String state,
                                          @NotNull Map<String, ?> placeholders,
                                          @NotNull ItemSpec fallback,
                                          @NotNull ItemSpec template) {
        String statePath = state == null || state.isBlank() ? null : path + ".states." + state;
        int slot = stateValueInt(statePath, path, "slot", template.slot());
        Material material = materialValue(statePath, path, "material", template.material());
        String configuredTitle = stateValueString(statePath, path, "title", null);
        List<String> configuredLore = stateValueLines(statePath, path, "lore");
        boolean glint = stateValueBoolean(statePath, path, "glint", template.glint());
        return new ItemSpec(slot, material,
                configuredTitle == null ? template.title()
                        : LegacyText.component(replace(configuredTitle, placeholders))
                        .decoration(TextDecoration.ITALIC, false),
                configuredLore == null ? template.lore() : configuredLore.stream()
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
