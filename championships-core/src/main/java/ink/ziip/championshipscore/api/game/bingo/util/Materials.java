package ink.ziip.championshipscore.api.game.bingo.util;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/** Small Material lookup helpers. Paper-only; no cross-version indirection. */
public final class Materials {
    private static final Set<Material> INVALID_COLLECT_OBJECTIVES = EnumSet.of(
            Material.SUSPICIOUS_SAND,
            Material.SUSPICIOUS_GRAVEL);

    private Materials() {
    }

    public static @NotNull Material fromKey(@Nullable Key key) {
        if (key == null) return Material.AIR;
        Material material = Registry.MATERIAL.get(key);
        return material == null ? Material.AIR : material;
    }

    public static @NotNull Material fromKey(@NotNull String key) {
        return fromKey(Key.key(key));
    }

    /** Whether survival players can hold this material to complete an item-collection objective. */
    public static boolean isCollectObjective(Material material) {
        return material.isItem() && !INVALID_COLLECT_OBJECTIVES.contains(material);
    }
}
