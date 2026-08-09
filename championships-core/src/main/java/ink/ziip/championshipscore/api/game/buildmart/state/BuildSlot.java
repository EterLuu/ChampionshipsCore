package ink.ziip.championshipscore.api.game.buildmart.state;

import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * One build plot of a team's base: the assigned blueprint (if any) plus the build/reference anchors the
 * blueprint is pasted and validated against. A slot is empty when no blueprint is assigned; completing
 * (or, for golden, expiring) a blueprint clears it back to empty so a fresh order can be assigned.
 */
@Getter
public class BuildSlot {
    private final int index;
    private final boolean golden;
    @Nullable
    private final Location buildAnchor;
    @Nullable
    private final Location referenceAnchor;

    @Setter
    @Nullable
    private BuildMartBlueprint blueprint;

    public BuildSlot(int index, boolean golden, @Nullable Location buildAnchor, @Nullable Location referenceAnchor) {
        this.index = index;
        this.golden = golden;
        this.buildAnchor = buildAnchor;
        this.referenceAnchor = referenceAnchor;
    }

    public boolean isEmpty() {
        return blueprint == null;
    }

    public void clear() {
        this.blueprint = null;
    }
}
