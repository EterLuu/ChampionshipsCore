package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.Random;

/** Per-course allocation of the parent stopped-challenge quota among its child mechanics. */
public record RiptideStoppedAllocation(int colorFloor, int dodge, int sideSweep) {
    private static final int SIDE_SWEEP_LIMIT = 2;

    public int total() {
        return colorFloor + dodge + sideSweep;
    }

    public static RiptideStoppedAllocation allocate(
            int total, String mode, int floorWeight, int dodgeWeight, int sideWeight, long seed) {
        if (total < 0 || total > 64)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-stopped-allocation.allocate-1"));
        if (floorWeight < 0
                || floorWeight > 100
                || dodgeWeight < 0
                || dodgeWeight > 100
                || sideWeight < 0
                || sideWeight > 100)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-stopped-allocation.allocate-2"));
        int[] weights = {floorWeight, dodgeWeight, sideWeight};
        int weightTotal = floorWeight + dodgeWeight + sideWeight;
        if (total > 0 && weightTotal == 0)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-stopped-allocation.allocate-3"));
        if (total > SIDE_SWEEP_LIMIT && floorWeight == 0 && dodgeWeight == 0)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-stopped-allocation.allocate-4"));
        if (mode == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-stopped-allocation.allocate-5"));
        int[] quotas =
                switch (mode.trim().toUpperCase(java.util.Locale.ROOT)) {
                    case "WEIGHTED" -> weighted(total, weights);
                    case "RANDOM" -> random(total, weights, seed);
                    default ->
                            throw new IllegalArgumentException(
                                    MessageConfig.codeText(
                                            "riptide-stopped-allocation.allocate-6"));
                };
        return new RiptideStoppedAllocation(quotas[0], quotas[1], quotas[2]);
    }

    private static int[] weighted(int total, int[] weights) {
        int[] quotas = new int[weights.length];
        for (int i = 0; i < total; i++) {
            int selected = -1;
            double best = -1;
            for (int child = 0; child < weights.length; child++) {
                int capacity = child == 2 ? SIDE_SWEEP_LIMIT : total;
                if (weights[child] == 0 || quotas[child] >= capacity) continue;
                double priority = (double) weights[child] / (quotas[child] + 1);
                if (priority > best) {
                    best = priority;
                    selected = child;
                }
            }
            if (selected < 0)
                throw new IllegalArgumentException(
                        MessageConfig.codeText("riptide-stopped-allocation.weighted-1"));
            quotas[selected]++;
        }
        return quotas;
    }

    private static int[] random(int total, int[] weights, long seed) {
        int[] quotas = new int[weights.length];
        var random = new Random(seed ^ 0x53544f505045444cL);
        for (int i = 0; i < total; i++) {
            int eligibleWeight = 0;
            for (int child = 0; child < weights.length; child++)
                if (weights[child] > 0 && (child != 2 || quotas[child] < SIDE_SWEEP_LIMIT))
                    eligibleWeight += weights[child];
            if (eligibleWeight == 0)
                throw new IllegalArgumentException(
                        MessageConfig.codeText("riptide-stopped-allocation.weighted-1"));
            int draw = random.nextInt(eligibleWeight);
            for (int child = 0; child < weights.length; child++) {
                if (weights[child] == 0 || child == 2 && quotas[child] >= SIDE_SWEEP_LIMIT)
                    continue;
                draw -= weights[child];
                if (draw < 0) {
                    quotas[child]++;
                    break;
                }
            }
        }
        return quotas;
    }
}
