package ink.ziip.championshipscore.shared.presentation;

import java.util.ArrayList;
import java.util.List;

/** Selects visible leaders while retaining an out-of-range viewer entry. */
public final class RankingWindow {
    private RankingWindow() {
    }

    public static <T> List<T> select(List<T> ranked, T viewerEntry, int leaderLimit) {
        if (leaderLimit < 0) throw new IllegalArgumentException("leaderLimit must not be negative");
        List<T> selected = new ArrayList<>(ranked.subList(0, Math.min(leaderLimit, ranked.size())));
        if (viewerEntry != null && ranked.contains(viewerEntry) && !selected.contains(viewerEntry)) {
            selected.add(viewerEntry);
        }
        return List.copyOf(selected);
    }
}
