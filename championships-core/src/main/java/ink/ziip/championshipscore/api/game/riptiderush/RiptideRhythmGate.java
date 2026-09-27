package ink.ziip.championshipscore.api.game.riptiderush;

/** Repeating, speed-adjusted shutter patterns. */
final class RiptideRhythmGate {
    static int cycleTicks(double speed) {
        return Math.max(64, (int) Math.ceil(240D / speed));
    }

    /** y is relative to the deck block: 1 is foot level, 4 is the solid indicator lintel. */
    static boolean opening(String variant, boolean mirrored, int tick, double speed, int lateral, int y) {
        if (y < 1 || y > 3) return false;
        double phase = Math.floorMod(tick, cycleTicks(speed)) / (double) cycleTicks(speed);
        int side = mirrored ? -lateral : lateral;
        return switch (variant) {
            case "HORIZONTAL_WINDOW" -> y <= 2
                    && Math.abs(side - (phase < .25 ? -2 : phase < .5 ? 0 : phase < .75 ? 2 : 0)) <= 1;
            case "VERTICAL_WINDOW" -> Math.abs(lateral) <= 1 && (phase < .5 ? y <= 2 : y >= 2);
            case "WINDOW_SHUTTER" -> Math.abs(lateral) <= 1 && y <= 2 && phase < .65;
            case "STAGGERED_WINDOWS" -> phase < .5 ? side < 0 && y <= 2 : side > 0 && y >= 2;
            case "ALTERNATING" -> phase < .5 ? side > 0 : side < 0;
            case "DOUBLE_BEAT" -> phase < .375 || phase >= .5 && phase < .75;
            case "CENTER_SIDES" -> phase < .5 ? Math.abs(lateral) <= 1 : Math.abs(lateral) >= 2;
            case "SWEEP" -> Math.abs(side - (phase < .25 ? -2 : phase < .5 ? 0 : phase < .75 ? 2 : 0)) <= 1;
            case "IN_OUT" -> Math.abs(lateral) <= (phase < .25 ? 3 : phase < .5 ? 2 : phase < .75 ? 1 : 2);
            case "CROSS_BEAT" -> phase < .25 ? side < 0 : phase < .5 ? true : phase < .75 ? side > 0 : false;
            default -> phase < .65;
        };
    }

    static boolean window(String variant) {
        return switch (variant) {
            case "HORIZONTAL_WINDOW", "VERTICAL_WINDOW", "WINDOW_SHUTTER", "STAGGERED_WINDOWS" -> true;
            default -> false;
        };
    }
}
