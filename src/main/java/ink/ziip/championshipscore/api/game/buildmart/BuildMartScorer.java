package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.buildmart.state.TeamBuildState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Computes the end-of-game Build Mart awards from the per-team build stats: entrepreneur (most total
 * stars), chef (most completed builds) and quality assurance (highest average stars). Each ranking is
 * sorted descending and excludes teams that scored nothing on that metric, so a sparse round doesn't hand
 * out awards for zero work.
 */
public final class BuildMartScorer {
    /** Bonus per award rank: 1st place team = 100, 2nd = 50, 3rd = 25 (per team member). */
    public static final int[] AWARD_POINTS = {100, 50, 25};

    private BuildMartScorer() {
    }

    public static List<TeamBuildState> rankByEntrepreneur(Collection<TeamBuildState> states) {
        return ranked(states, TeamBuildState::getTotalStars);
    }

    public static List<TeamBuildState> rankByChef(Collection<TeamBuildState> states) {
        return ranked(states, TeamBuildState::getCompletedCount);
    }

    public static List<TeamBuildState> rankByQuality(Collection<TeamBuildState> states) {
        return ranked(states, TeamBuildState::averageStars);
    }

    /** Teams sorted by {@code metric} descending, dropping any with a non-positive metric value. */
    private static List<TeamBuildState> ranked(Collection<TeamBuildState> states, ToDoubleFunction<TeamBuildState> metric) {
        List<TeamBuildState> list = new ArrayList<>();
        for (TeamBuildState state : states) {
            if (metric.applyAsDouble(state) > 0) list.add(state);
        }
        list.sort(Comparator.comparingDouble(metric).reversed());
        return list;
    }
}
