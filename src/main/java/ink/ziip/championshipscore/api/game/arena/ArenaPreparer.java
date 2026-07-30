package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared "stamp out N copies" step behind the per-game {@code prepare} commands. Pastes a single arena
 * schematic at every {@link ArenaGrid#origin(int)} for copies {@code 0..count-1}. Pairs with
 * {@code BaseGameInstance#saveMap} (called by each game afterwards) to persist the stamped world into
 * the static map template, so every later round just loads it — no runtime cloning.
 */
public final class ArenaPreparer {
    private ArenaPreparer() {
    }

    /** Pastes {@code count} copies of {@code schematic} into {@code world} along {@code grid}. Main thread. */
    public static void stampCopies(ChampionshipsCore plugin, World world, File schematic, ArenaGrid grid, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            Vector origin = grid.origin(i);
            plugin.getWorldEditManager().pasteSchematic(world, schematic, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        }
    }

    /**
     * One tight bounding box per copy: copy {@code i} spans {@code [origin(i), origin(i)+size]}, where
     * {@code size} is the schematic's block dimensions ({@code WorldEditManager#getSchematicDimensions}).
     * Unlike a single enclosing box this excludes the empty gaps between copies, so each sub-arena gets its
     * own boundary (a player is in-bounds only inside some copy). Replaces the legacy hand-drawn
     * {@code area-pos}. Empty for {@code count < 1}.
     */
    public static List<BoundingBox> copyBoxes(ArenaGrid grid, int count, Vector size) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vector min = grid.origin(i);
            boxes.add(BoundingBox.of(min, min.clone().add(size)));
        }
        return boxes;
    }
}
