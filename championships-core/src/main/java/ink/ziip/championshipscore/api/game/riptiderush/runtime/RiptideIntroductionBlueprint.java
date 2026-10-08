package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local block coordinates for the reusable teaching canal, relative to the first raft. */
final class RiptideIntroductionBlueprint {
    record Cell(int forward, int lateral, int height) {}

    private RiptideIntroductionBlueprint() {}

    static Map<Cell, Material> blocks() {
        Map<Cell, Material> blocks = new LinkedHashMap<>();
        for (int f = -6; f <= 146; f++) {
            for (int x = -5; x <= 5; x++) {
                put(blocks, f, x, -4, Material.LIGHT_BLUE_CONCRETE);
                put(blocks, f, x, -3, Material.WATER);
                put(blocks, f, x, -2, Material.WATER);
            }
            for (int x : List.of(-6, 6)) {
                for (int y = -4; y < 0; y++) put(blocks, f, x, y, Material.CYAN_CONCRETE);
                put(blocks, f, x, 0, f % 8 == 0 ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ);
                put(blocks, f, x, 1, Material.LIGHT_BLUE_STAINED_GLASS);
            }
            for (int x : List.of(-8, -7, 7, 8)) put(blocks, f, x, 0, Material.SPRUCE_PLANKS);
            for (int x : List.of(-9, 9)) put(blocks, f, x, 0, Material.STRIPPED_SPRUCE_LOG);
        }
        for (int f = -10; f <= -7; f++)
            for (int x = -9; x <= 9; x++) put(blocks, f, x, 0, Material.SPRUCE_PLANKS);
        for (int f = -4; f <= 4; f++)
            for (int x = -3; x <= 3; x++) put(blocks, f, x, 0, Material.OAK_PLANKS);
        gate(blocks, 24, -1, 1);
        gate(blocks, 52, 1, 3);
        for (int f : List.of(0, 24, 52, 70, 111, 140)) {
            for (int x : List.of(-6, 6))
                for (int y = 1; y <= 5; y++) put(blocks, f, x, y, Material.STRIPPED_SPRUCE_LOG);
            for (int x = -6; x <= 6; x++)
                put(blocks, f, x, 6, x % 3 == 0 ? Material.SEA_LANTERN : Material.CYAN_CONCRETE);
        }
        return blocks;
    }

    private static void gate(Map<Cell, Material> blocks, int f, int left, int right) {
        for (int x = -3; x <= 3; x++)
            for (int y = 1; y <= 4; y++)
                if (x < left || x > right || y > 3)
                    put(blocks, f, x, y, Material.STRIPPED_SPRUCE_LOG);
    }

    private static void put(Map<Cell, Material> blocks, int f, int x, int y, Material material) {
        blocks.put(new Cell(f, x, y), material);
    }
}
