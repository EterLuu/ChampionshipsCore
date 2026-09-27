package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Headless configuration fixtures. Each call returns a fresh mutable configuration. */
final class RiptideTestFixtures {
    private RiptideTestFixtures() {}

    private static final World WORLD = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "raft-test";
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    default -> throw new UnsupportedOperationException(method.getName());
                });

    static RiptideRushConfig config() throws Exception {
        // Only configuration parsing is exercised; avoid JavaPlugin's server-only constructor.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var plugin = (ink.ziip.championshipscore.ChampionshipsCore) unsafe.allocateInstance(
                ink.ziip.championshipscore.ChampionshipsCore.class);
        var c = new RiptideRushConfig(plugin, "raft-test");
        var yaml = defaults();
        c.loadFromConfiguration(yaml);
        c.setPassCount(20); c.setMathCount(8); c.setStoppedCount(4); c.setRhythmCount(0);
        c.setColorFloorWeight(2); c.setDodgeWeight(0); c.setSideSweepWeight(2);
        c.setTemplates(c.resolvePool().stream().filter(t -> !t.variant().equals("DOUBLE")).toList());
        c.setStartPoint(new Location(WORLD, .5, 80, -110.5));
        c.setFinishPoint(new Location(WORLD, .5, 80, 389.5));
        c.setMinimumOperand(10); c.setMaximumOperand(99);
        return c;
    }

    static YamlConfiguration defaults() throws Exception {
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(RiptideTestFixtures.class
                .getResourceAsStream("/riptiderush/area.yml")), StandardCharsets.UTF_8)) { yaml.load(reader); }
        return yaml;
    }

    static RiptideRushConfig config(TestWorld world, int[] direction) throws Exception {
        var c = RiptideTestFixtures.config();
        c.setStartPoint(new Location(world.world, .5, 80, .5));
        c.setFinishPoint(new Location(world.world, .5 + direction[0] * 500, 80, .5 + direction[1] * 500));
        return c;
    }

    record Pos(int x, int y, int z) { }
    private static BlockData data(Material material) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "clone" -> proxy;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    static final class TestWorld {
        final Map<Pos, Material> blocks = new HashMap<>();
        final UUID id = UUID.randomUUID();
        int writes;
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "generator-test";
                    case "getUID" -> id;
                    case "equals" -> proxy == args[0];
                    case "getBlockAt" -> block(new Pos((int) args[0], (int) args[1], (int) args[2]));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Material at(RiptideCourseGeometry g, int step, int lateral, int height) {
            return blocks.getOrDefault(new Pos(g.blockX(step, lateral), g.floorY() + height, g.blockZ(step, lateral)), Material.AIR);
        }
        Block block(Pos pos) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getType" -> blocks.getOrDefault(pos, Material.AIR);
                        case "isEmpty" -> blocks.getOrDefault(pos, Material.AIR) == Material.AIR;
                        case "getState" -> {
                            var saved = blocks.getOrDefault(pos, Material.AIR);
                            yield Proxy.newProxyInstance(org.bukkit.block.BlockState.class.getClassLoader(),
                                    new Class<?>[]{org.bukkit.block.BlockState.class}, (state, called, values) -> {
                                        if (!called.getName().equals("update")) throw new UnsupportedOperationException(called.getName());
                                        assertEquals(true, values[0]); assertEquals(false, values[1]);
                                        if (saved == Material.AIR) blocks.remove(pos); else blocks.put(pos, saved);
                                        writes++;
                                        return true;
                                    });
                        }
                        case "getBlockData" -> data(blocks.getOrDefault(pos, Material.AIR));
                        case "setType", "setBlockData" -> {
                            Material material = args[0] instanceof Material m ? m : ((BlockData) args[0]).getMaterial();
                            writes++;
                            if (material == Material.AIR) blocks.remove(pos); else blocks.put(pos, material);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        void set(RiptideCourseGeometry g, int step, int lateral, int height, Material material) {
            block(new Pos(g.blockX(step, lateral), g.floorY() + height, g.blockZ(step, lateral))).setType(material, false);
        }
    }
}
