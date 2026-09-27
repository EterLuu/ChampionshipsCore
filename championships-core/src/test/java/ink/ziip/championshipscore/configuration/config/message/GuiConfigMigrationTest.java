package ink.ziip.championshipscore.configuration.config.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuiConfigMigrationTest {
    @Test void stoppedChallengeHierarchyReplacesStockFloorEntryAndPreservesCustomCopy() throws Exception {
        var old = new YamlConfiguration();
        String root = "map-editor.games.riptide-rush.menus.course-editor.";
        old.set(root + "steps.floor.title", "踩色关卡");
        old.set(root + "steps.floor.lore", List.of("编辑踩色变体、每局数量与阶段难度"));
        old.set(root + "steps.dodge.title", "躲避关卡");
        old.set(root + "pauses.title", "停顿关卡 • 子类");
        old.set(root + "items.pause-floor.title", "旧踩色入口");
        old.set(root + "items.floor-timing.title", "自定义时长");
        var defaults = loadGui();
        GuiConfig.preserveCurrentMenus(old, defaults);
        assertEquals("停船挑战", defaults.getString(root + "steps.stopped.title"));
        assertTrue(defaults.getStringList(root + "steps.stopped.lore").getFirst().contains("侧向"));
        assertEquals("自定义时长", defaults.getString(root + "items.floor-timing.title"));
        for (String retired : List.of("steps.floor", "steps.dodge", "pauses", "items.pause-floor"))
            assertFalse(defaults.contains(root + retired), retired);
        for (String screen : List.of("stopped", "side", "side-pool"))
            assertNotNull(defaults.getString(root + screen + ".title"));
    }

    @Test void movingRhythmReplacesStockInstructionsAndKeepsCustomText() throws Exception {
        var old = new YamlConfiguration();
        String items = "map-editor.games.riptide-rush.menus.course-editor.items.";
        old.set(items + "option-shutter.lore", List.of("停船9秒；3秒归位，两次2秒通行窗口", "自定义提示"));
        old.set(items + "option-observe-order.lore", List.of("从左到右找出指定位置的数字", "自定义观察提示"));
        old.set(items + "new-rhythm.lore", List.of("创建节拍闸门，可切换四种节奏变体"));
        var defaults = loadGui();
        GuiConfig.preserveCurrentMenus(old, defaults);
        assertEquals(List.of("木筏持续前进，全门按固定节拍开合", "自定义提示"), defaults.getStringList(items + "option-shutter.lore"));
        assertNotNull(defaults.getString(items + "option-double-beat.title"));
        assertNotNull(defaults.getString(items + "option-center-sides.title"));
        assertEquals(List.of("按题目要求从左或右找出指定位置的数字", "自定义观察提示"), defaults.getStringList(items + "option-observe-order.lore"));
        assertEquals(List.of("创建节拍闸门，可切换十一种节奏变体"), defaults.getStringList(items + "new-rhythm.lore"));
        assertNotNull(defaults.getString(items + "option-observe-unique.title"));
        assertNotNull(defaults.getString(items + "option-cross-beat.title"));
        for (String variant : List.of("horizontal-window", "vertical-window", "window-shutter", "staggered-windows")) {
            assertNotNull(defaults.getString(items + "option-" + variant + ".title"));
            assertFalse(defaults.getStringList(items + "option-" + variant + ".lore").isEmpty());
        }
    }
    @Test void fixedFloorTimingRemovesRetiredConfigurationCopy() throws Exception {
        var old = new YamlConfiguration();
        String items = "map-editor.games.riptide-rush.menus.course-editor.items.";
        String validation = "map-editor.menus.step-list.games.riptide-rush.validation.pause";
        old.set(items + "pause-seconds.title", "obsolete duration");
        old.set(validation + ".title", "obsolete validation");
        old.set("buttons.back.title", "custom back");
        var defaults = loadGui();
        GuiConfig.preserveCurrentMenus(old, defaults);
        assertFalse(defaults.contains(items + "pause-seconds"));
        assertFalse(defaults.contains(validation));
        assertNotNull(defaults.getString(items + "floor-timing.title"));
        assertEquals("custom back", defaults.getString("buttons.back.title"));
    }

    @Test
    void newCourseEditorButtonsAndAllCategoryEntrypointsHaveConfiguredText() throws Exception {
        var gui = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/gui.yml")) {
            assertNotNull(stream);
            gui.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        for (String menu : List.of("category", "entry", "add", "choice", "delete", "course", "preview", "placement", "workshop", "abandon"))
            assertNotNull(gui.getString("map-editor.games.riptide-rush.menus.course-editor." + menu + ".title"));
        for (String step : List.of("pass", "math", "stopped", "rhythm", "course")) {
            assertNotNull(gui.getString("map-editor.games.riptide-rush.menus.course-editor.steps." + step + ".title"));
            assertFalse(gui.getStringList("map-editor.games.riptide-rush.menus.course-editor.steps." + step + ".lore").isEmpty());
        }
        String items = "map-editor.games.riptide-rush.menus.course-editor.items.";
        for (var type : ink.ziip.championshipscore.api.game.riptiderush.RiptideLevelType.values()) {
            for (var variant : ink.ziip.championshipscore.api.game.riptiderush.RiptideLevelTemplate.variants(type)) {
                String option = items + "option-" + variant.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                assertNotNull(gui.getString(option + ".title"), option);
                assertTrue(gui.getBoolean(option + ".states.selected.glint"), option);
            }
        }
        for (int difficulty = 1; difficulty <= 3; difficulty++)
            assertTrue(gui.getBoolean(items + "difficulty-" + difficulty + ".states.selected.glint"));
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/ink/ziip/championshipscore/api/game/area/prepare/gui/RiptideCourseEditorGui.java"));
        var matcher = java.util.regex.Pattern.compile("(?:button|value)\\(h, \\d+, \\\"([^\\\"]+)\\\"").matcher(source);
        while (matcher.find()) {
            String path = "map-editor.games.riptide-rush.menus.course-editor.items." + matcher.group(1);
            assertNotNull(gui.getString(path + ".title"), path);
            assertTrue(gui.isList(path + ".lore"), path);
        }
    }

    @Test
    void v27EditorMigrationPreservesCustomControlsWithoutRestoringRetiredNavigation() throws Exception {
        var old = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/riptiderush/editor-v27.yml")) {
            old.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        old.set("dont-edit-this.version", 27);
        old.set("riptide-editor.buttons.name.title", "我的名称：%value%");
        old.set("buttons.back.title", "我的返回");
        var defaults = loadGui();
        GuiConfig.preserveCurrentMenus(old, defaults);
        String path = "map-editor.games.riptide-rush.menus.course-editor.items.";
        assertEquals("我的名称：%value%", defaults.getString(path + "name.title"));
        assertEquals("我的返回", defaults.getString("buttons.back.title"));
        assertFalse(defaults.contains("riptide-editor"));
        assertTrue(defaults.getStringList(path + "variant.lore").stream().noneMatch(l -> l.contains("循环")));
        assertEquals("EMERALD", defaults.getString(path + "add.material"));
        var once = defaults.saveToString();
        GuiConfig.preserveCurrentMenus(defaults, defaults);
        assertEquals(once, defaults.saveToString());
    }

    @Test
    void v28UpgradeReplacesStockParameterOnlyInstructionsAndKeepsCustomTitles() throws Exception {
        var old = new YamlConfiguration();
        var baseline = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/riptiderush/editor-v28.yml")) {
            baseline.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        for (String key : baseline.getKeys(true)) if (!baseline.isConfigurationSection(key))
            old.set("map-editor.games.riptide-rush.menus." + key, baseline.get(key));
        String path = "map-editor.games.riptide-rush.menus.course-editor.";
        old.set(path + "items.name.title", "我的变体名称：%value%");
        var defaults = loadGui();
        GuiConfig.preserveCurrentMenus(old, defaults);
        assertEquals("我的变体名称：%value%", defaults.getString(path + "items.name.title"));
        assertTrue(defaults.getStringList(path + "items.add.lore").getFirst().contains("施工"));
        assertTrue(defaults.getStringList(path + "items.duplicate.lore").getFirst().contains("建筑"));
        assertNotNull(defaults.getString(path + "workshop.title"));
        assertNotNull(defaults.getString(path + "items.automatic-layout.title"));
    }

    @Test
    void v29WorkspaceHintsUpgradeWithoutReplacingCustomLore() throws Exception {
        var old = new YamlConfiguration(); var defaults = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/gui.yml")) {
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        String path="map-editor.games.riptide-rush.menus.course-editor.items.";
        old.set("dont-edit-this.version",29);
        old.set(path+"pass-building-help.lore",List.of("&#a0a0a0橙色标记之间前后共7格，可建高度6格","我的施工说明"));
        old.set(path+"math-building-help.lore",List.of("我的数学说明"));
        old.set(path+"floor-building-help.lore",List.of("&#a0a0a0甲板逐格布局会保存，七轮使用该布局",
                "&#a0a0a0至少两种方块，目标方块须分散铺设","&#a0a0a0上方三格留空；两侧或上方可搭建装饰"));
        GuiConfig.preserveCurrentMenus(old,defaults);
        assertTrue(defaults.getStringList(path+"pass-building-help.lore").getFirst().contains("%length%"));
        assertEquals("我的施工说明",defaults.getStringList(path+"pass-building-help.lore").getLast());
        assertEquals(List.of("我的解题说明"),defaults.getStringList(path+"math-building-help.lore"));
        assertTrue(defaults.getStringList(path+"floor-building-help.lore").getFirst().contains("%width%"));
    }

    @Test
    void v25UpgradePreservesCustomMenusAndUpdatesOnlyStockRiptideCopy() throws Exception {
        var old = new YamlConfiguration();
        String pause = "map-editor.games.riptide-rush.menus.level-type.items.pause.";
        old.set("dont-edit-this.version", 25);
        old.set("buttons.back.title", "自定义返回");
        old.set(pause + "material", "CLOCK");
        old.set(pause + "title", "&#fff566&l停顿类");
        old.set(pause + "lore", List.of("&#a0a0a0七轮逐轮加速，每轮木筏前移一格", "自定义规则"));
        old.set(pause + "states.selected.title", "自定义选择状态");
        var defaults = loadGui();
        GuiConfig.preserveCurrentMenus(old, defaults);
        assertEquals(loadGui().getInt("dont-edit-this.version"), defaults.getInt("dont-edit-this.version"));
        assertEquals("自定义返回", defaults.getString("buttons.back.title"));
        assertEquals("LIME_CONCRETE", defaults.getString(pause + "material"));
        assertEquals("&#fff566&l踩色", defaults.getString(pause + "title"));
        assertEquals(List.of("&#a0a0a0木筏停留原地，七轮逐轮加速", "自定义规则"), defaults.getStringList(pause + "lore"));
        assertEquals("自定义选择状态", defaults.getString(pause + "states.selected.title"));
    }
    @Test void v30FlatCreationMigratesStockCopyAndPreservesCustomControls() throws Exception {
        var old=new YamlConfiguration();var baseline=new YamlConfiguration();var defaults=new YamlConfiguration();
        try(var in=getClass().getResourceAsStream("/riptiderush/editor-v30.yml")) {
            baseline.load(new InputStreamReader(in,StandardCharsets.UTF_8));
        }
        try(var in=getClass().getResourceAsStream("/gui.yml")) {
            defaults.load(new InputStreamReader(in,StandardCharsets.UTF_8));
        }
        for(String key:baseline.getKeys(true)) if(!baseline.isConfigurationSection(key))
            old.set("map-editor.games.riptide-rush.menus."+key,baseline.get(key));
        String path="map-editor.games.riptide-rush.menus.course-editor.";
        old.set(path+"items.name.title","我的名称");
        old.set(path+"items.pass-building-help.lore",List.of("&#a0a0a0支持1.5格高潜行通道；完成后请试玩","自定义提示"));
        GuiConfig.preserveCurrentMenus(old,defaults);
        assertEquals("我的名称",defaults.getString(path+"items.name.title"));
        assertEquals(List.of("&#a0a0a0不校验连续通路与跳跃高差；完成后请试玩","自定义提示"),defaults.getStringList(path+"items.pass-building-help.lore"));
        assertTrue(defaults.getString(path+"add.title").contains("复制或空白"));
        assertFalse(defaults.getStringList(path+"items.entry.lore").toString().contains("%variant%"));
        assertTrue(defaults.getStringList(path+"items.copy-source.lore").toString().contains("%name%副本"));
        assertTrue(defaults.getStringList(path+"items.try-template.lore").toString().contains("默认F"));
    }

    private static YamlConfiguration loadGui() throws Exception {
        var yaml = new YamlConfiguration();
        try (var stream = GuiConfigMigrationTest.class.getResourceAsStream("/gui.yml")) {
            assertNotNull(stream, "Missing gui.yml");
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return yaml;
    }
}
