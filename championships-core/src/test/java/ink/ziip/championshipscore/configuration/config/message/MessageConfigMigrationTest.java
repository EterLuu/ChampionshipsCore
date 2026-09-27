package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MessageConfigMigrationTest {
    @TempDir Path directory;
    @Test void customSideWarningMovesToTitleAndExplicitTitleWins() {
        var old = new YamlConfiguration();
        old.set("riptiderush.sweep-actionbar", "custom warning");
        var defaults = new YamlConfiguration();
        MessageConfig.preserveCurrentMessages(old, defaults);
        assertEquals("custom warning", defaults.getString("riptiderush.sweep-title"));
        assertFalse(defaults.contains("riptiderush.sweep-actionbar"));
        old.set("riptiderush.sweep-title", "custom title");
        MessageConfig.preserveCurrentMessages(old, defaults);
        assertEquals("custom title", defaults.getString("riptiderush.sweep-title"));
    }

    @Test
    void v45UpgradeRemovesMovementInstructionsAndPreservesOtherCustomMessages() throws Exception {
        var old = new YamlConfiguration();
        old.set("dont-edit-this.version", 45);
        old.set("riptiderush.floor-actionbar", "&#fff566彩色地板 %round%/%rounds% &#bababa• &#ededed每轮前移一格，站到手中方块上：&#55ffff%block%");
        old.set("riptiderush.floor-title", "&#fff566&l彩色地板 %round%/5 &#ff6b26%seconds%秒");
        old.set("riptiderush.floor-subtitle", "旧地板规则");
        old.set("riptiderush.sweep-actionbar", "&e侧墙 %beat%/2 &f%side% &7• %time% &b%action%");
        old.set("riptiderush.reason.side-wall", "旧触墙淘汰原因");
        old.set("riptiderush.reason.rhythm", "旧节奏淘汰原因");
        old.set("riptiderush.rhythm.prepare", "先到闸门后方");
        old.set("chat.custom", "自定义聊天");
        old.set("buildmart.submit-incomplete", "自定义匹配提示 %matched%/%total%");
        old.set("no-permission", "自定义权限提示");
        old.set("riptiderush.boss-bar", "自定义木筏计时 %time%");
        old.set("riptiderush.question-bar", "旧数学题");
        old.set("riptiderush.pause-bar", "旧暂停条");
        old.set("map-editor-raft.level-added", "旧固定关卡提示");
        var defaults = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/message.yml")) {
            assertNotNull(stream);
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        int expectedVersion = defaults.getInt("dont-edit-this.version");
        Path destination = directory.resolve("message.yml");
        MessageConfig.preserveCurrentMessages(old, defaults);
        defaults.save(destination.toFile());
        var migrated = YamlConfiguration.loadConfiguration(destination.toFile());
        assertFalse(migrated.getString("riptiderush.floor-actionbar").contains("前移"));
        assertEquals(expectedVersion, migrated.getInt("dont-edit-this.version"));
        assertEquals("&e木筏即将前进！", migrated.getString("riptiderush.departure-actionbar"));
        assertEquals("&e侧向来墙！", migrated.getString("riptiderush.sweep-title"));
        assertFalse(migrated.contains("riptiderush.sweep-actionbar"));
        assertEquals("自定义聊天", migrated.getString("chat.custom"));
        assertEquals("自定义匹配提示 %matched%/%total%", migrated.getString("buildmart.submit-incomplete"));
        assertTrue(migrated.getString("buildmart.submit-differences").contains("%positions%"));
        assertEquals("自定义权限提示", migrated.getString("no-permission"));
        assertEquals("自定义木筏计时 %time%", migrated.getString("riptiderush.boss-bar"));
        assertFalse(migrated.contains("riptiderush.reason.side-wall"));
        assertFalse(migrated.contains("riptiderush.reason.rhythm"));
        assertFalse(migrated.contains("riptiderush.rhythm"));
        assertFalse(migrated.contains("riptiderush.question-bar"));
        assertFalse(migrated.contains("riptiderush.pause-bar"));
        assertFalse(migrated.contains("riptiderush.floor-subtitle"));
        assertFalse(migrated.contains("map-editor-raft"));
        assertEquals("&#ff6b26%seconds%秒", migrated.getString("riptiderush.floor-title"));
        assertEquals("&#55ffff%block%", migrated.getString("riptiderush.floor-actionbar"));
        for (var field : MessageConfig.class.getFields()) {
            var option = field.getAnnotation(ConfigOption.class);
            if (option == null || !(option.path().startsWith("riptiderush.")
                    || option.path().startsWith("map-editor-raft."))) continue;
            assertNotNull(migrated.getString(option.path()), option.path());
            assertFalse(migrated.getString(option.path()).isBlank(), option.path());
        }
    }
}
