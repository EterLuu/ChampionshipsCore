package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaLayoutPlanner;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.util.Vector;

/** One source arena and three generated arenas sharing a single match. */
@Getter
@Setter
public abstract class FourArenaConfig extends BaseGameConfig {
    public static final int ARENA_COUNT = 4;

    @ConfigOption(path = "arena-min", nullable = true)
    private Vector arenaMin;

    @ConfigOption(path = "arena-max", nullable = true)
    private Vector arenaMax;

    @ConfigOption(path = "copy-layout.origin", nullable = true)
    private Vector copyLayoutOrigin;

    @ConfigOption(path = "copy-layout.step", nullable = true)
    private Vector copyLayoutStep;

    @ConfigOption(path = "copy-layout.size", nullable = true)
    private Vector copySize;

    @ConfigOption(path = "copy-layout.generated", nullable = true)
    private boolean copiesGenerated;

    @ConfigOption(path = "copy-layout.template-captured", nullable = true)
    private boolean templateCaptured;

    protected FourArenaConfig(ChampionshipsCore plugin, String name) {
        super(plugin, name);
    }

    public boolean hasCopyLayout() {
        return copyLayoutOrigin != null && copyLayoutStep != null && copySize != null;
    }

    public ArenaGrid getCopyGrid() {
        return grid(copyLayoutOrigin, copyLayoutStep);
    }

    /** Plans a two-by-two layout with the same isolation padding as TNT Run. */
    public ArenaGrid planCopyGrid(Vector size) {
        if (arenaMin == null || arenaMax == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("four-arena-config.boundary-missing"));
        Vector step =
                new Vector(
                        ArenaLayoutPlanner.rowStep(size).getX(),
                        0,
                        ArenaLayoutPlanner.rowStep(
                                        new Vector(size.getZ(), size.getY(), size.getX()))
                                .getX());
        return grid(Vector.getMinimum(arenaMin, arenaMax), step);
    }

    private static ArenaGrid grid(Vector origin, Vector step) {
        Vector first = origin.clone();
        Vector spacing = step.clone();
        return index -> {
            if (index < 0 || index >= ARENA_COUNT)
                throw new IllegalArgumentException("arena index: " + index);
            return first.clone()
                    .add(new Vector((index % 2) * spacing.getX(), 0, (index / 2) * spacing.getZ()));
        };
    }

    /** Only commit the new layout after all three pastes have succeeded. */
    public void recordCopyLayout(ArenaGrid grid, Vector size) {
        copyLayoutOrigin = grid.origin(0);
        copyLayoutStep = new Vector(grid.delta(1).getX(), 0, grid.delta(2).getZ());
        copySize = size.clone();
        copiesGenerated = true;
        templateCaptured = true;
    }

    public void setTemplateBounds(Vector first, Vector second) {
        arenaMin = Vector.getMinimum(first, second);
        arenaMax = Vector.getMaximum(first, second);
        copiesGenerated = false;
        templateCaptured = true;
        prepareWorldBuilt = false;
    }

    public boolean isCopyLayoutCurrent() {
        return copiesGenerated
                && hasCopyLayout()
                && arenaMin != null
                && arenaMax != null
                && copyLayoutOrigin.equals(Vector.getMinimum(arenaMin, arenaMax))
                && copySize.equals(
                        Vector.getMaximum(arenaMin, arenaMax)
                                .subtract(copyLayoutOrigin)
                                .add(new Vector(1, 1, 1)));
    }

    public void validateCopies() {
        if (!isCopyLayoutCurrent())
            throw new IllegalArgumentException(
                    MessageConfig.codeText("four-arena-config.generate-required"));
        ArenaPreparer.validateNonOverlappingCopies(getCopyGrid(), ARENA_COUNT, copySize);
    }

    @Override
    public boolean isPrepareReady() {
        // Existing prebuilt maps remain usable until an editor captures their first template.
        return super.isPrepareReady()
                && (!templateCaptured && !hasCopyLayout() || isCopyLayoutCurrent());
    }
}
