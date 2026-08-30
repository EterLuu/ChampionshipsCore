package ink.ziip.championshipscore.shared.presentation;

/** Pure timing policy shared by Core and remote game workers. */
public final class RuleIntroductionTimeline {
    public static final int FIRST_SECTION_SECOND = 10;
    public static final int DEFAULT_INTERVAL_SECONDS = 10;

    private RuleIntroductionTimeline() {
    }

    /** Returns the section to broadcast at this second, or {@code -1} when none is due. */
    public static int sectionAt(int elapsedSeconds, int durationSeconds, int sectionCount) {
        if (sectionCount < 1 || elapsedSeconds < FIRST_SECTION_SECOND || elapsedSeconds >= durationSeconds) return -1;
        int interval = intervalSeconds(durationSeconds, sectionCount);
        int sinceFirst = elapsedSeconds - FIRST_SECTION_SECOND;
        if (sinceFirst % interval != 0) return -1;
        int section = sinceFirst / interval;
        return section < sectionCount ? section : -1;
    }

    public static int intervalSeconds(int durationSeconds, int sectionCount) {
        if (sectionCount <= 1) return DEFAULT_INTERVAL_SECONDS;
        int availableAfterFirst = Math.max(1, durationSeconds - FIRST_SECTION_SECOND - 1);
        return Math.max(1, Math.min(DEFAULT_INTERVAL_SECONDS, availableAfterFirst / (sectionCount - 1)));
    }
}
