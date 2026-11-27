package ink.ziip.championshipscore.api.game.craftingfeast.model;

/**
 * Customer flow over a service of {@code serviceSeconds}. The flow opens at {@code startPercent} of
 * the base arrival rate and climbs in {@link #STEP} percent steps until it reaches {@code
 * peakPercent} when {@code peakSeconds} of service remain; that last stretch is the evening peak. A
 * peak no higher than the start keeps a flat flow without an evening peak.
 */
public record FeastPace(
        int arrivalSeconds,
        int startPercent,
        int peakPercent,
        int peakSeconds,
        int serviceSeconds) {
    static final int MINIMUM_INTERVAL = 3;

    /** Flow increments; each one is announced, so the climb stays readable instead of spamming. */
    static final int STEP = 10;

    /** Seconds of service before the evening peak starts. */
    public int peakStart() {
        return Math.max(0, serviceSeconds - peakSeconds);
    }

    /** Arrival rate after {@code elapsedSeconds} of service, as a percentage of the base rate. */
    public int percent(int elapsedSeconds) {
        if (peakPercent <= startPercent) return startPercent;
        int elapsed = Math.max(0, elapsedSeconds);
        int climb = peakStart();
        if (elapsed >= climb) return peakPercent;
        int rise = (peakPercent - startPercent) * elapsed / climb;
        return startPercent + rise / STEP * STEP;
    }

    public boolean peak(int elapsedSeconds) {
        return peakPercent > startPercent
                && peakSeconds > 0
                && Math.max(0, elapsedSeconds) >= peakStart();
    }

    /** Seconds between two guests at the given elapsed service time, never below three. */
    public int interval(int elapsedSeconds) {
        return Math.max(
                MINIMUM_INTERVAL, Math.round(arrivalSeconds * 100f / percent(elapsedSeconds)));
    }
}
