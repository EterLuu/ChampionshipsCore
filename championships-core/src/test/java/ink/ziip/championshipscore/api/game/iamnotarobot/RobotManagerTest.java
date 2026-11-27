package ink.ziip.championshipscore.api.game.iamnotarobot;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.config.RobotConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class RobotManagerTest {
    @Test
    void bundledMapUsesTheVersionAcceptedAfterMigration() throws Exception {
        try (var reader =
                new InputStreamReader(
                        Objects.requireNonNull(
                                getClass().getResourceAsStream("/iamnotarobot/area.yml")),
                        StandardCharsets.UTF_8)) {
            var map = new YamlConfiguration();
            map.load(reader);
            int version = map.getInt("dont-edit-this.version");
            assertEquals(RobotConfig.MAP_VERSION, version);
            assertEquals(
                    RobotManager.MapUpgrade.NONE, RobotManager.MapUpgrade.fromVersion(version));
        }
    }

    @Test
    void selectsOnlyChangedRegionsPerMapVersionAndFullMigrationForLegacyMaps() {
        for (int v = 1; v <= 14; v++)
            assertEquals(RobotManager.MapUpgrade.LEGACY, RobotManager.MapUpgrade.fromVersion(v));
        assertEquals(
                RobotManager.MapUpgrade.SLIDERS_AND_JOINT, RobotManager.MapUpgrade.fromVersion(15));
        assertEquals(RobotManager.MapUpgrade.JOINT, RobotManager.MapUpgrade.fromVersion(16));
        assertEquals(RobotManager.MapUpgrade.JOINT, RobotManager.MapUpgrade.fromVersion(17));
        assertEquals(
                RobotManager.MapUpgrade.FORKLIFT_ACCESS, RobotManager.MapUpgrade.fromVersion(18));
        assertEquals(
                RobotManager.MapUpgrade.FORKLIFT_ACCESS, RobotManager.MapUpgrade.fromVersion(19));
        for (int v : new int[] {0, 20, 21})
            assertEquals(RobotManager.MapUpgrade.NONE, RobotManager.MapUpgrade.fromVersion(v));
    }

    @Test
    void reportsTheEffectiveWorldSettingOnceIncludingPositiveLegacyIntervals() {
        String setting =
                "maps.item-frame-cursor-update-interval in"
                        + " world/dimensions/minecraft/robot_verification/paper-world.yml";
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(
                new Handler() {
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    public void flush() {}

                    public void close() {}
                });
        for (int interval : new int[] {-1, 0, 1, 10, 1200}) {
            records.clear();
            RobotManager.logMapInterval(logger, setting, interval);
            assertEquals(1, records.size());
            var record = records.getFirst();
            assertEquals(interval > 0 ? Level.WARNING : Level.INFO, record.getLevel());
            assertTrue(record.getMessage().contains(setting));
            assertTrue(record.getMessage().contains(interval + " ticks"));
            assertTrue(
                    record.getMessage()
                            .contains(interval > 0 ? "recommended: 0" : "poll disabled"));
        }
    }
}
