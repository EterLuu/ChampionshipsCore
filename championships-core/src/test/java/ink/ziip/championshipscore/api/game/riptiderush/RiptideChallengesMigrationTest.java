package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RiptideChallengesMigrationTest {
    @Test void windowMigrationKeepsExistingOverridesQuotasAndPublicationAndIsIdempotent() throws Exception {
        var yaml = new YamlConfiguration();
        var custom = Map.of("id", "rhythm_vertical_window", "type", "RHYTHM", "variant", "VERTICAL_WINDOW",
                "name", "自定义升降", "enabled", false, "weight", 37, "max-uses", 2, "difficulty", 3);
        yaml.set("course.pool", List.of(custom));
        yaml.set("course.counts.rhythm", 2);
        yaml.set("prepare.published", true); yaml.set("prepare.dirty", false); yaml.set("prepare.revision", 7);
        yaml.set("rules", List.of(List.of("自定义规则")));
        RiptideRushConfig.migrateChallenges(yaml, yaml);
        RiptideRushConfig.migrateWindowRules(yaml);
        assertEquals(custom, yaml.getMapList("course.pool").getFirst());
        for (String variant : List.of("HORIZONTAL_WINDOW", "VERTICAL_WINDOW", "WINDOW_SHUTTER", "STAGGERED_WINDOWS"))
            assertEquals(1, yaml.getMapList("course.pool").stream().filter(row -> variant.equals(row.get("variant"))).count());
        assertEquals(2, yaml.getInt("course.counts.rhythm"));
        assertEquals(7, yaml.getInt("prepare.revision"));
        assertTrue(yaml.getBoolean("prepare.published")); assertFalse(yaml.getBoolean("prepare.dirty"));
        assertEquals(List.of("自定义规则"), yaml.getList("rules").getFirst());
        String once = yaml.saveToString();
        RiptideRushConfig.migrateChallenges(yaml, yaml);
        RiptideRushConfig.migrateWindowRules(yaml);
        assertEquals(once, yaml.saveToString());
        var defaults = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/riptiderush/area.yml")) {
            defaults.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(RiptideTestFixtures.config().getLatestVersion(), defaults.getInt("dont-edit-this.version"));
        assertEquals(RiptideRushConfig.defaultPool().stream().filter(row -> "RHYTHM".equals(row.get("type"))).toList(),
                defaults.getMapList("course.pool").stream().filter(row -> "RHYTHM".equals(row.get("type")))
                        .sorted(java.util.Comparator.comparingInt(row -> RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM).indexOf(row.get("variant")))).toList());
    }
    @Test void revealRuleMigrationPreservesCustomRulesAndPublicationAndIsIdempotent() {
        var yaml = new YamlConfiguration();
        yaml.set("rules", List.of(List.of("自定义规则")));
        yaml.set("prepare.published", true); yaml.set("prepare.dirty", false); yaml.set("prepare.revision", 7);
        RiptideRushConfig.migrateRevealRules(yaml);
        String once = yaml.saveToString();
        RiptideRushConfig.migrateRevealRules(yaml);
        assertEquals(once, yaml.saveToString());
        assertEquals(List.of("自定义规则"), yaml.getList("rules").getFirst());
        assertTrue(once.contains("去皮云杉木墙"));
        assertTrue(once.contains("10格"));
        assertTrue(yaml.getBoolean("prepare.published"));
        assertFalse(yaml.getBoolean("prepare.dirty"));
        assertEquals(7, yaml.getInt("prepare.revision"));
    }

    @Test void oldRevealAndGroupRulesAreReplacedWithoutChangingCustomTextOrPublication() {
        var yaml = new YamlConfiguration();
        yaml.set("rules", List.of(List.of("自定义规则"), List.of(
                "&#ededed所有关卡初始为完整去皮云杉木墙，船头靠近至12格时显现原貌；侧墙关卡的占位木墙同时撤去，移动侧墙保持原貌。",
                "穿越在第二次加速后开放双连续，第三次加速后开放三连续；留意下一面墙。",
                "解题初始加法，一次加速加入减法，二次开放双连续，三次加入乘法。")));
        yaml.set("prepare.revision",7); yaml.set("prepare.published",true); yaml.set("prepare.dirty",false);
        RiptideRushConfig.migrateRevealRules(yaml);
        RiptideRushConfig.migrateEarlierGroups(yaml);
        String once = yaml.saveToString();
        RiptideRushConfig.migrateRevealRules(yaml);
        RiptideRushConfig.migrateEarlierGroups(yaml);
        assertEquals(once, yaml.saveToString());
        assertFalse(once.contains("12格"));
        assertTrue(once.contains("10格")); assertTrue(once.contains("统一尺寸"));
        assertTrue(once.contains("第一次加速后开放双连续，第二次加速后开放三连续"));
        assertTrue(once.contains("挤出"));
        assertEquals(List.of("自定义规则"),yaml.getList("rules").getFirst());
        assertEquals(7,yaml.getInt("prepare.revision"));
        assertTrue(yaml.getBoolean("prepare.published")); assertFalse(yaml.getBoolean("prepare.dirty"));
    }

    @Test void migrationPreservesSettingsAndPublicationAndDoesNotDuplicateAdditions() throws Exception {
        var old = new YamlConfiguration();
        old.set("course.counts.math",8); old.set("course.counts.pass",20); old.set("course.counts.floor",4);
        old.set("course.pool",List.of(Map.of("id","custom","type","MATH","variant","ADD","name","我的题目","weight",7)));
        old.set("prepare.published",true); old.set("prepare.dirty",false); old.set("prepare.revision",7);
        old.set("rules",List.of(List.of("我的规则"),List.of("节奏闸门：停船后先到闸门后方，听预告，趁开放向前通过；限时结束仍未通过则出局。")));
        var updated = new YamlConfiguration(); updated.loadFromString(old.saveToString());
        RiptideRushConfig.migrateChallenges(old,updated);
        assertEquals(0,updated.getInt("course.counts.rhythm"));
        assertEquals(8,updated.getInt("course.counts.math"));
        assertEquals(old.getMapList("course.pool").getFirst(),updated.getMapList("course.pool").getFirst());
        assertEquals(old.getConfigurationSection("prepare").getValues(true),updated.getConfigurationSection("prepare").getValues(true));
        assertEquals(17,updated.getMapList("course.pool").size());
        assertTrue(updated.getList("rules").toString().contains("木筏持续前进"));
        assertFalse(updated.getList("rules").toString().contains("停船后先到"));
        String once = updated.saveToString();
        RiptideRushConfig.migrateChallenges(updated,updated);
        assertEquals(once,updated.saveToString());
        updated.set("course.counts.rhythm",3);
        RiptideRushConfig.migrateChallenges(updated,updated);
        assertEquals(3,updated.getInt("course.counts.rhythm"));
    }

    @Test void varietyRulesPreserveCustomTextAndAreIdempotent() {
        var yaml = new YamlConfiguration();
        yaml.set("rules", List.of(List.of("自定义规则", "数颜色、从左到右找第几项",
                "节奏闸门：木筏持续前进，按开合节拍穿过；包含全门、左右交替、长短双拍、中间两侧，离筏或掉落出局。")));
        RiptideRushConfig.migrateChallengeVariety(yaml);
        String once = yaml.saveToString();
        RiptideRushConfig.migrateChallengeVariety(yaml);
        assertEquals(once, yaml.saveToString());
        assertTrue(once.contains("自定义规则"));
        assertTrue(once.contains("观察题随五段航程"));
        assertTrue(once.contains("中间两侧、横移窗口、收放、交错双拍，离筏或掉落出局"));
    }
}
