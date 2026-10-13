package ink.ziip.championshipscore.api.game.buildmart.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

@ExtendWith(ConfigurationStateExtension.class)
class BuildMartBossBarTest {
    @Test
    void actualRoundAndGoldenWindowTitlesBothUseMinutesWithoutDuplicatingUnits() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(
                        new InputStreamReader(
                                getClass().getResourceAsStream("/message.yml"),
                                StandardCharsets.UTF_8));
        MessageConfig.BUILD_MART_BOSSBAR_TITLE = document.getString("buildmart.bossbar.title");
        MessageConfig.BUILD_MART_BOSSBAR_GOLDEN_NONE =
                document.getString("buildmart.bossbar.golden-none");
        MessageConfig.BUILD_MART_BOSSBAR_GOLDEN_ACTIVE =
                document.getString("buildmart.bossbar.golden-active");
        var config = new BuildMartConfig(allocate(ChampionshipsCore.class), "test");
        config.setTimer(900);
        config.setGoldenRefreshSeconds(180);
        var area = allocate(BuildMartArea.class);
        set(area, "gameConfig", config);
        set(area, "timer", 700);
        var blueprint = allocate(BuildMartBlueprint.class);
        set(blueprint, "displayName", "桥");
        set(area, "currentGolden", blueprint);
        var title = BuildMartArea.class.getDeclaredMethod("bossBarTitle");
        title.setAccessible(true);
        assertEquals(
                "&#fff566匹配赛建 &#696969|&#ededed 剩余&#fff56611分40秒 &#696969|&#ededed"
                        + " &#fff566黄金：&#ededed桥 &#696969（2分40秒）",
                title.invoke(area));
        set(area, "timer", 60);
        assertEquals(
                "&#fff566匹配赛建 &#696969|&#ededed 剩余&#fff56660秒 &#696969|&#ededed"
                        + " &#fff566黄金：&#ededed桥 &#696969（60秒）",
                title.invoke(area));
        set(area, "currentGolden", null);
        assertEquals(
                "&#fff566匹配赛建 &#696969|&#ededed 剩余&#fff56660秒 &#696969|&#ededed"
                        + " &#696969黄金：&#ededed无",
                title.invoke(area));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
