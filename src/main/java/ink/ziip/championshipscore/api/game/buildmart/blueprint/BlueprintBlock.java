package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

/**
 * One block of a blueprint, stored as an integer offset {@code (x, y, z)} from the build anchor plus the
 * exact {@link BlockData} that must occupy that position. Serialized form is {@code "x,y,z=blockdata"},
 * e.g. {@code "1,0,2=minecraft:oak_stairs[facing=east,half=bottom]"}.
 */
@Getter
public class BlueprintBlock {
    private final int x;
    private final int y;
    private final int z;
    private final BlockData blockData;

    public BlueprintBlock(int x, int y, int z, BlockData blockData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockData = blockData;
    }

    /** Parses {@code "x,y,z=blockdata"}; returns {@code null} on malformed input or unknown block data. */
    @Nullable
    public static BlueprintBlock parse(String raw) {
        if (raw == null) return null;
        int eq = raw.indexOf('=');
        if (eq <= 0 || eq == raw.length() - 1) return null;
        String[] coords = raw.substring(0, eq).split(",");
        if (coords.length != 3) return null;
        try {
            int x = Integer.parseInt(coords[0].trim());
            int y = Integer.parseInt(coords[1].trim());
            int z = Integer.parseInt(coords[2].trim());
            BlockData data = Bukkit.createBlockData(raw.substring(eq + 1).trim());
            if (data.getMaterial().isAir()) return null;
            return new BlueprintBlock(x, y, z, data);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String serialize() {
        return x + "," + y + "," + z + "=" + blockData.getAsString();
    }
}
