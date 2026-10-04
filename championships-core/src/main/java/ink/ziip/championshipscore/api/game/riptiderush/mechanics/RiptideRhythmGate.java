package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

/** Repeating, speed-adjusted shutter patterns. */
final class RiptideRhythmGate {
    static int cycleTicks(double speed) {
        return Math.max(64, (int) Math.ceil(240D / speed));
    }

    /** y is relative to the deck block: 1 is foot level, 4 is the solid indicator lintel. */
    static boolean opening(
            String variant, boolean mirrored, int tick, double speed, int lateral, int y) {
        if (y < 1 || y > 3) return false;
        double phase = Math.floorMod(tick, cycleTicks(speed)) / (double) cycleTicks(speed);
        int side = mirrored ? -lateral : lateral;
        int beat = Math.min(3, (int) (phase * 4D));
        int distance = Math.abs(side);
        return switch (variant) {
            case "HORIZONTAL_WINDOW" ->
                    y <= 2
                            && Math.abs(
                                            side
                                                    - (phase < .25
                                                            ? -2
                                                            : phase < .5 ? 0 : phase < .75 ? 2 : 0))
                                    <= 1;
            case "VERTICAL_WINDOW" -> Math.abs(lateral) <= 1 && (phase < .5 ? y <= 2 : y >= 2);
            case "WINDOW_SHUTTER" -> Math.abs(lateral) <= 1 && y <= 2 && phase < .65;
            case "STAGGERED_WINDOWS" -> phase < .5 ? side < 0 && y <= 2 : side > 0 && y >= 2;
            case "ALTERNATING" -> phase < .5 ? side > 0 : side < 0;
            case "DOUBLE_BEAT" -> phase < .375 || phase >= .5 && phase < .75;
            case "CENTER_SIDES" -> phase < .5 ? Math.abs(lateral) <= 1 : Math.abs(lateral) >= 2;
            case "SWEEP" ->
                    Math.abs(side - (phase < .25 ? -2 : phase < .5 ? 0 : phase < .75 ? 2 : 0)) <= 1;
            case "IN_OUT" ->
                    Math.abs(lateral) <= (phase < .25 ? 3 : phase < .5 ? 2 : phase < .75 ? 1 : 2);
            case "CROSS_BEAT" ->
                    phase < .25 ? side < 0 : phase < .5 ? true : phase < .75 ? side > 0 : false;
            // Four-beat patterns keep every beat at least a quarter cycle, so the
            // fastest raft still has a readable 0.8 second opening.
            case "TRIPLE_PULSE" -> beat == 3 ? false : beat != 1;
            case "LEFT_RIGHT_CENTER" ->
                    beat == 0 ? side < 0 : beat == 1 ? side > 0 : beat == 2 ? distance <= 1 : true;
            case "EDGE_SWAP" -> beat % 2 == 0 ? distance >= 2 : distance <= 1;
            case "PINBALL" ->
                    beat == 0
                            ? side <= -2
                            : beat == 1 ? side >= 2 : beat == 2 ? distance <= 1 : distance >= 1;
            case "SNAKE" ->
                    side == (beat == 0 ? -2 : beat == 1 ? 0 : beat == 2 ? 2 : 0)
                            || Math.abs(side - (beat == 0 ? -2 : beat == 1 ? 0 : beat == 2 ? 2 : 0))
                                    == 1;
            case "CENTER_PULSE" -> beat % 2 == 0 ? distance <= 1 : distance >= 2;
            case "EDGE_PULSE" -> beat % 2 == 0 ? distance >= 2 : distance <= 1;
            case "FOLD" -> beat == 0 || beat == 3 ? distance <= 1 : distance >= 1;
            case "SPLIT_MERGE" -> beat == 0 || beat == 2 ? side < 0 || side > 0 : distance <= 1;
            case "REST_ACCENT" -> beat == 0 || beat == 2;
            case "MIRROR_CHASE" ->
                    beat == 0
                            ? side < 0
                            : beat == 1 ? side <= 1 : beat == 2 ? side > 0 : side >= -1;
            case "DIAGONAL" ->
                    (beat == 0 && side <= 0)
                            || (beat == 1 && side >= 0)
                            || (beat == 2 && side >= 0)
                            || (beat == 3 && side <= 0);
            case "DOUBLE_WINDOW" -> beat == 0 || beat == 2 ? distance <= 1 : distance >= 1;
            case "BACKBEAT" -> beat == 0 || beat == 1 || beat == 3;
            case "QUICK_TURN" ->
                    beat == 0
                            ? side <= 0
                            : beat == 1 ? side >= 0 : beat == 2 ? side <= 1 : side >= -1;
            default -> phase < .65;
        };
    }

    static boolean window(String variant) {
        return switch (variant) {
            case "HORIZONTAL_WINDOW", "VERTICAL_WINDOW", "WINDOW_SHUTTER", "STAGGERED_WINDOWS" ->
                    true;
            default -> false;
        };
    }
}
