package ink.ziip.championshipscore.api.game.buildmart;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Build Mart's copper contract: wax is cosmetic and copper never advances oxidation in the arena. */
public final class BuildMartCopperPolicy {
    private static final List<String> OXIDATION_PREFIXES = List.of("exposed_", "weathered_", "oxidized_");
    /** Copper shapes whose aged variants cannot be made immediately from the same-stage full copper block. */
    private static final Set<String> PRISTINE_ONLY_SHAPES = Set.of(
            "copper_bars", "copper_chain", "copper_chest", "copper_door", "copper_golem_statue",
            "copper_lantern", "copper_trapdoor", "lightning_rod");

    private BuildMartCopperPolicy() {
    }

    /** Removes wax while retaining oxidation stage, shape and every BlockData property. */
    public static @NotNull BlockData withoutWax(@NotNull BlockData data) {
        String normalized = withoutWax(data.getAsString());
        return normalized.equals(data.getAsString()) ? data : Bukkit.createBlockData(normalized);
    }

    public static @NotNull Material withoutWax(@NotNull Material material) {
        String normalized = withoutWax(material.getKey().toString());
        Material result = Material.matchMaterial(normalized);
        return result == null ? material : result;
    }

    /** Removes the {@code waxed_} component from a namespaced material or full block-state string. */
    public static @NotNull String withoutWax(@NotNull String blockState) {
        int namespace = blockState.indexOf(':');
        int materialStart = namespace < 0 ? 0 : namespace + 1;
        return blockState.startsWith("waxed_", materialStart)
                ? blockState.substring(0, materialStart) + blockState.substring(materialStart + "waxed_".length())
                : blockState;
    }

    /**
     * Save-time blueprint normalization. All wax is removed; copper shapes that cannot be crafted from the
     * corresponding oxidation-stage full block are additionally collapsed to their pristine variants.
     */
    public static @NotNull BlockData normalizeBlueprint(@NotNull BlockData data) {
        String normalized = normalizeBlueprint(data.getAsString());
        return normalized.equals(data.getAsString()) ? data : Bukkit.createBlockData(normalized);
    }

    public static @NotNull String normalizeBlueprint(@NotNull String blockState) {
        String unwaxed = withoutWax(blockState);
        int properties = unwaxed.indexOf('[');
        String id = properties < 0 ? unwaxed : unwaxed.substring(0, properties);
        String suffix = properties < 0 ? "" : unwaxed.substring(properties);
        int namespace = id.indexOf(':');
        String prefix = namespace < 0 ? "" : id.substring(0, namespace + 1);
        String key = namespace < 0 ? id : id.substring(namespace + 1);
        String pristine = pristineSpecial(key);
        return pristine == null ? unwaxed : prefix + pristine + suffix;
    }

    /** Whether a vanilla block-form transition advances the oxidation stage of the same copper shape. */
    public static boolean isForwardOxidation(@NotNull Material from, @NotNull Material to) {
        CopperIdentity oldIdentity = identity(from);
        CopperIdentity newIdentity = identity(to);
        return oldIdentity != null && newIdentity != null && oldIdentity.shape().equals(newIdentity.shape())
                && newIdentity.stage() > oldIdentity.stage();
    }

    private static String pristineSpecial(String key) {
        String noStage = removeOxidationPrefix(key);
        return PRISTINE_ONLY_SHAPES.contains(noStage) ? noStage : null;
    }

    private static CopperIdentity identity(Material material) {
        String key = material.getKey().getKey().toLowerCase(Locale.ROOT);
        if (key.startsWith("waxed_")) key = key.substring("waxed_".length());
        int stage = oxidationStage(key);
        String shape = removeOxidationPrefix(key);
        if (shape.equals("copper_block")) shape = "copper";
        boolean copper = shape.equals("copper") || shape.startsWith("copper_")
                || shape.startsWith("cut_copper") || shape.startsWith("chiseled_copper")
                || shape.equals("lightning_rod");
        return copper ? new CopperIdentity(shape, stage) : null;
    }

    private static int oxidationStage(String key) {
        if (key.startsWith("exposed_")) return 1;
        if (key.startsWith("weathered_")) return 2;
        if (key.startsWith("oxidized_")) return 3;
        return 0;
    }

    private static String removeOxidationPrefix(String key) {
        for (String prefix : OXIDATION_PREFIXES) {
            if (key.startsWith(prefix)) return key.substring(prefix.length());
        }
        return key;
    }

    private record CopperIdentity(@NotNull String shape, int stage) {
    }
}
