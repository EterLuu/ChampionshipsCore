package ink.ziip.championshipscore.api.game.sulfursoccer.support;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.sulfursoccer.config.SulfurSoccerConfig;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.util.List;

public final class SulfurSoccerTestFixtures {
    private SulfurSoccerTestFixtures() {}

    public static SulfurSoccerConfig config() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var plugin =
                (ink.ziip.championshipscore.ChampionshipsCore)
                        unsafe.allocateInstance(ink.ziip.championshipscore.ChampionshipsCore.class);
        var config = new SulfurSoccerConfig(plugin, "soccer");
        var yaml = new YamlConfiguration();
        yaml.set("world-name", "soccer");
        var field = BaseConfigurationFile.class.getDeclaredField("configuration");
        field.setAccessible(true);
        field.set(config, yaml);
        config.setAreaPos1(new Vector(-20, 0, -10));
        config.setAreaPos2(new Vector(20, 8, 10));
        config.setRightAreaPos1(new Vector(0, 0, -10));
        config.setRightAreaPos2(new Vector(20, 8, 10));
        config.setLeftAreaPos1(new Vector(-20, 0, -10));
        config.setLeftAreaPos2(new Vector(-1, 8, 10));
        config.setRightGoalPos1(new Vector(18, 0, -2));
        config.setRightGoalPos2(new Vector(20, 3, 2));
        config.setLeftGoalPos1(new Vector(-20, 0, -2));
        config.setLeftGoalPos2(new Vector(-18, 3, 2));
        config.setRightSpawnPoints(
                List.of(
                        "soccer:4:1:0:90:0",
                        "soccer:6:1:0:90:0",
                        "soccer:8:1:0:90:0",
                        "soccer:10:1:0:90:0"));
        config.setLeftSpawnPoints(
                List.of(
                        "soccer:-4:1:0:-90:0",
                        "soccer:-6:1:0:-90:0",
                        "soccer:-8:1:0:-90:0",
                        "soccer:-10:1:0:-90:0"));
        config.setBallSpawnPoint(new Location(null, 0, 1, 0));
        config.setSpectatorSpawnPoint(new Location(null, 0, 10, 0));
        return config;
    }
}
