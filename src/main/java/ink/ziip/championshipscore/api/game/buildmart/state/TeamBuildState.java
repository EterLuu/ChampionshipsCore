package ink.ziip.championshipscore.api.game.buildmart.state;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartBase;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A team's live Build Mart state: its three normal build slots, its single golden slot, and the running
 * tallies (completed builds and total stars) that drive the end-of-game awards. Anchors are pulled from
 * the team's {@link BuildMartBase}; slots whose anchor is unconfigured are still present but can't host a
 * build.
 */
@Getter
public class TeamBuildState {
    public static final int NORMAL_SLOTS = 3;

    private final ChampionshipTeam team;
    private final List<BuildSlot> normalSlots = new ArrayList<>();
    private final BuildSlot goldenSlot;

    /** Number of builds completed (normal + golden), the "chef" award metric. */
    private int completedCount;
    /** Sum of stars of completed builds, the "entrepreneur" award metric. */
    private int totalStars;

    public TeamBuildState(ChampionshipTeam team, @Nullable BuildMartBase base) {
        this.team = team;
        for (int i = 0; i < NORMAL_SLOTS; i++) {
            this.normalSlots.add(new BuildSlot(i, false,
                    anchor(base == null ? null : base.getNormalBuildAnchors(), i),
                    anchor(base == null ? null : base.getNormalReferenceAnchors(), i)));
        }
        this.goldenSlot = new BuildSlot(0, true,
                base == null ? null : base.getGoldenBuildAnchor(),
                base == null ? null : base.getGoldenReferenceAnchor());
    }

    @Nullable
    private static <T> T anchor(@Nullable List<T> list, int index) {
        return list != null && index < list.size() ? list.get(index) : null;
    }

    /** First empty normal slot, or {@code null} if all three are occupied. */
    @Nullable
    public BuildSlot firstFreeNormalSlot() {
        for (BuildSlot slot : normalSlots) {
            if (slot.isEmpty()) return slot;
        }
        return null;
    }

    /** Records a completed build's stars and bumps the completed counter. */
    public void recordCompletion(int stars) {
        completedCount++;
        totalStars += stars;
    }

    /** Average stars per completed build (the "quality assurance" metric); 0 when nothing is done. */
    public double averageStars() {
        return completedCount == 0 ? 0.0 : (double) totalStars / completedCount;
    }
}
