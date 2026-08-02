package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import ink.ziip.championshipscore.ChampionshipsCore;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable build order: a named, star-rated set of {@link BlueprintBlock}s placed relative to a build
 * anchor. Stars drive both the pool it belongs to (1–5 normal, 7 golden) and the points a completion is
 * worth. The block count is the denominator for the completion ratio used when scoring partial builds.
 */
@Getter
public class BuildMartBlueprint {
    private final String id;
    private final String displayName;
    private final int stars;
    private final List<BlueprintBlock> blocks;

    public BuildMartBlueprint(String id, String displayName, int stars, List<BlueprintBlock> blocks) {
        this.id = id;
        this.displayName = displayName;
        this.stars = stars;
        this.blocks = List.copyOf(blocks);
    }

    public int blockCount() {
        return blocks.size();
    }

    /** Loads a blueprint from a YAML file; returns {@code null} when the file is missing/invalid. */
    @Nullable
    public static BuildMartBlueprint load(ChampionshipsCore plugin, File file) {
        if (file == null || !file.isFile()) return null;
        String id = file.getName().toLowerCase().endsWith(".yml")
                ? file.getName().substring(0, file.getName().length() - 4)
                : file.getName();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String displayName = yaml.getString("name", id);
        int stars = yaml.getInt("stars", 1);
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (String raw : yaml.getStringList("blocks")) {
            BlueprintBlock block = BlueprintBlock.parse(raw);
            if (block != null) blocks.add(block);
        }
        if (blocks.isEmpty()) {
            plugin.getLogger().warning("[BuildMart] 蓝图 " + id + " 没有有效方块，已跳过。");
            return null;
        }
        return new BuildMartBlueprint(id, displayName, stars, blocks);
    }

    /** Convenience: the literal star glyphs for display, e.g. {@code ★★★}. */
    public String starString() {
        return "★".repeat(Math.max(0, stars));
    }
}
