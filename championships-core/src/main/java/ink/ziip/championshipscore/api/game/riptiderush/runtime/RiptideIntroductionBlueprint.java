package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sea, raft camp and rescue pier; the actual obstacles are placed by the race generator. */
final class RiptideIntroductionBlueprint {
    record Cell(int forward, int lateral, int height) {}

    record Sign(int forward, int lateral, String zone) {}

    static final int VERSION = 3;
    static final int MIN_FORWARD = -16;
    static final int MAX_FORWARD = 154;
    static final int MAX_LATERAL = 24;
    static final int MIN_HEIGHT = -5;
    static final int MAX_HEIGHT = 12;
    static final List<Sign> SIGNS =
            List.of(
                    new Sign(-9, 0, "follow"),
                    new Sign(16, 10, "pass"),
                    new Sign(48, -10, "math"),
                    new Sign(64, 10, "color"),
                    new Sign(96, -10, "dodge"),
                    new Sign(120, 10, "rhythm"),
                    new Sign(148, 0, "finish"));

    private RiptideIntroductionBlueprint() {}

    static Map<Cell, Material> blocks() {
        Map<Cell, Material> blocks = new LinkedHashMap<>();
        // The formal map's water is two blocks below the deck, with AIR left behind the raft.
        for (int f = MIN_FORWARD; f <= MAX_FORWARD; f++)
            for (int x = -MAX_LATERAL; x <= MAX_LATERAL; x++) {
                put(blocks, f, x, -4, Material.SAND);
                for (int y = -3; y <= -2; y++)
                    put(
                            blocks,
                            f,
                            x,
                            y,
                            f == MIN_FORWARD || f == MAX_FORWARD || Math.abs(x) == MAX_LATERAL
                                    ? Material.SANDSTONE
                                    : Material.WATER);
            }
        camp(blocks);
        for (int f : List.of(12, 42, 76, 108, 138)) {
            int side = f == 42 || f == 108 ? -1 : 1;
            island(blocks, f, side * 19);
        }
        // Navigation buoys stay outside the full fifteen-block building envelope.
        for (int f = 8; f < 140; f += 16)
            for (int x : List.of(-10, 10)) {
                put(blocks, f, x, -1, Material.DARK_OAK_PLANKS);
                put(blocks, f, x, 0, x > 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
                put(blocks, f, x, 1, Material.SEA_LANTERN);
            }
        for (int f = 145; f <= 152; f++)
            for (int x = -8; x <= 8; x++)
                put(
                        blocks,
                        f,
                        x,
                        0,
                        Math.abs(x) == 8 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS);
        for (int x : List.of(-8, 8)) {
            for (int y = 1; y <= 5; y++) put(blocks, 150, x, y, Material.STRIPPED_SPRUCE_LOG);
            put(blocks, 150, x, 6, Material.SEA_LANTERN);
        }
        for (int f = -4; f <= 4; f++)
            for (int x = -3; x <= 3; x++) put(blocks, f, x, 0, Material.OAK_PLANKS);
        return blocks;
    }

    private static void camp(Map<Cell, Material> blocks) {
        for (int f = -14; f <= -7; f++)
            for (int x = -8; x <= 8; x++) {
                put(blocks, f, x, 0, Material.SPRUCE_PLANKS);
                put(
                        blocks,
                        f,
                        x,
                        5,
                        Math.floorMod(x, 4) < 2 ? Material.WHITE_WOOL : Material.RED_WOOL);
            }
        for (int f : List.of(-14, -7))
            for (int x : List.of(-8, 8))
                for (int y = 1; y <= 4; y++) put(blocks, f, x, y, Material.STRIPPED_SPRUCE_LOG);
        for (int x : List.of(-6, 6)) {
            put(blocks, -12, x, 1, Material.BARREL);
            put(blocks, -11, x, 1, Material.CRAFTING_TABLE);
        }
    }

    private static void island(Map<Cell, Material> blocks, int forward, int lateral) {
        for (int f = -3; f <= 3; f++)
            for (int x = -3; x <= 3; x++) {
                if (f * f + x * x > 10) continue;
                put(blocks, forward + f, lateral + x, -2, Material.SANDSTONE);
                put(blocks, forward + f, lateral + x, -1, Material.SAND);
            }
        for (int y = 0; y <= 5; y++) put(blocks, forward, lateral, y, Material.JUNGLE_LOG);
        for (int d = -3; d <= 3; d++) {
            put(blocks, forward + d, lateral, 6 - Math.abs(d) / 2, Material.JUNGLE_LEAVES);
            put(blocks, forward, lateral + d, 6 - Math.abs(d) / 2, Material.JUNGLE_LEAVES);
        }
    }

    private static void put(Map<Cell, Material> blocks, int f, int x, int y, Material material) {
        blocks.put(new Cell(f, x, y), material);
    }
}
