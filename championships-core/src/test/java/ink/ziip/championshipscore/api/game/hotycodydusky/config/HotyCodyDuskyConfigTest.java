package ink.ziip.championshipscore.api.game.hotycodydusky.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class HotyCodyDuskyConfigTest {
    @Test
    void existingSquareOriginsAndTheirDistinctHeightBoundsRemainOneMap() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var plugin =
                (ChampionshipsCore)
                        ((sun.misc.Unsafe) unsafeField.get(null))
                                .allocateInstance(ChampionshipsCore.class);
        var config = new HotyCodyDuskyConfig(plugin, "area");
        var yaml = new YamlConfiguration();
        var layout = yaml.createSection("copy-layout");
        layout.set(
                "origins",
                List.of(
                        new Vector(-32, 60, -36),
                        new Vector(-32, 60, -182),
                        new Vector(115, 60, -182),
                        new Vector(115, 60, -36)));
        config.setCopyLayout(layout);
        config.setCopies(4);
        config.setCopySize(new Vector(62, 25, 66));
        config.setCopyBounds(
                List.of(
                        bounds(-32, -36, 84),
                        bounds(-32, -182, 83),
                        bounds(115, -182, 80),
                        bounds(115, -36, 81)));
        assertEquals(new Vector(147, 0, -146), config.getCopyGrid().delta(2));
        assertEquals(
                List.of(85D, 84D, 81D, 82D),
                config.getCopyBoxes().stream().map(box -> box.getMaxY()).toList());
        assertEquals(4, config.getCopyBoxes().size());
    }

    @Test
    void incompleteCopyDefinitionsRemainDrafts() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var plugin =
                (ChampionshipsCore)
                        ((sun.misc.Unsafe) unsafeField.get(null))
                                .allocateInstance(ChampionshipsCore.class);
        var config = new HotyCodyDuskyConfig(plugin, "area");
        assertFalse(config.isPrepareReady());
        assertNull(config.getPlayerSpawnPoint());
        config.setCopies(4);
        config.setCopySize(new Vector(62, 25, 66));
        config.setCopyBounds(List.of(bounds(-32, -36, 84)));
        assertThrows(IllegalArgumentException.class, config::getCopyBoxes);
    }

    private static Map<String, Object> bounds(int x, int z, int y) {
        return Map.of("pos1", new Vector(x, 60, z), "pos2", new Vector(x + 61, y, z + 65));
    }
}
