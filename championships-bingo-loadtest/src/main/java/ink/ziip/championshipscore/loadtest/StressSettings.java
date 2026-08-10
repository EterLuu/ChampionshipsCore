package ink.ziip.championshipscore.loadtest;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record StressSettings(
        String worldName,
        int viewDistance,
        int movementPeriodTicks,
        int anchorRadiusBlocks,
        int stationaryPlayerSeparationBlocks,
        int entityMinimumSpawnDistanceBlocks,
        int entityMaximumSpawnDistanceBlocks,
        int layoutSwitchIntervalSeconds,
        double stationaryDispersalSpeedBlocksPerSecond,
        List<Stage> stages,
        int maxConcurrentLoads,
        int maxSubmissionsPerTick,
        int entitySpawnsPerTick,
        int releaseDelayTicks,
        int statusIntervalTicks,
        long maxCompletedLoads,
        int maxPendingRequests,
        long maxFailures,
        long minimumFreeDiskBytes,
        double minimumSchedulerTps,
        int lowTpsGraceSamples
) {
    static StressSettings load(FileConfiguration config) {
        List<Integer> walkers = config.getIntegerList("stage-walkers");
        List<Integer> durations = config.getIntegerList("stage-duration-seconds");
        List<String> modes = config.getStringList("stage-modes");
        List<Double> speeds = config.getDoubleList("stage-speed-blocks-per-second");
        List<Integer> entityTargets = config.getIntegerList("stage-target-world-entities");
        if (walkers.isEmpty() || walkers.size() != durations.size()) {
            throw new IllegalArgumentException("stage-walkers and stage-duration-seconds must have equal sizes");
        }
        requireOptionalSize(modes, walkers.size(), "stage-modes");
        requireOptionalSize(speeds, walkers.size(), "stage-speed-blocks-per-second");
        requireOptionalSize(entityTargets, walkers.size(), "stage-target-world-entities");

        double legacySpeed = config.getDouble("speed-blocks-per-second", 7.0);
        List<Stage> stages = new ArrayList<>();
        int previous = 0;
        for (int index = 0; index < walkers.size(); index++) {
            int count = walkers.get(index);
            int duration = durations.get(index);
            if (count < previous || count < 1 || count > 64 || duration < 1) {
                throw new IllegalArgumentException("Stages must be non-decreasing, use 1-64 walkers, and positive durations");
            }
            Mode mode = modes.isEmpty() ? Mode.FLIGHT : Mode.parse(modes.get(index));
            double speed = speeds.isEmpty() ? legacySpeed : speeds.get(index);
            speed = bounded(speed, 0.0, 100.0, "stage-speed-blocks-per-second");
            if ((mode == Mode.FLIGHT || mode == Mode.MIXED) && speed <= 0.0) {
                throw new IllegalArgumentException("Flight and mixed stages require a positive speed");
            }
            if (mode == Mode.MIXED && count % 2 != 0) {
                throw new IllegalArgumentException("Mixed stages require an even walker count");
            }
            int target = entityTargets.isEmpty() ? 0 : entityTargets.get(index);
            target = bounded(target, 0, 50000, "stage-target-world-entities");
            stages.add(new Stage(count, duration, mode, speed, target));
            previous = count;
        }
        int minimumSpawnDistance = bounded(config.getInt("entity-minimum-spawn-distance-blocks", 24),
                1, 256, "entity-minimum-spawn-distance-blocks");
        int maximumSpawnDistance = bounded(config.getInt("entity-maximum-spawn-distance-blocks", 128),
                2, 512, "entity-maximum-spawn-distance-blocks");
        if (maximumSpawnDistance - minimumSpawnDistance < 3) {
            throw new IllegalArgumentException("entity spawn distance band must be at least three blocks wide");
        }
        int viewDistance = bounded(config.getInt("view-distance", 10), 2, 16, "view-distance");
        if (maximumSpawnDistance > viewDistance * 16) {
            throw new IllegalArgumentException("entity maximum spawn distance must fit inside view-distance");
        }
        return new StressSettings(
                config.getString("world", "bingo"),
                viewDistance,
                bounded(config.getInt("movement-period-ticks", 10), 1, 100, "movement-period-ticks"),
                bounded(config.getInt("team-anchor-radius-blocks", 1024), 512, 100000,
                        "team-anchor-radius-blocks"),
                bounded(config.getInt("stationary-player-separation-blocks", 384), 0, 4096,
                        "stationary-player-separation-blocks"),
                minimumSpawnDistance,
                maximumSpawnDistance,
                bounded(config.getInt("layout-switch-interval-seconds", 60), 0, 3600,
                        "layout-switch-interval-seconds"),
                bounded(config.getDouble("stationary-dispersal-speed-blocks-per-second", 7.0),
                        0.1, 20.0, "stationary-dispersal-speed-blocks-per-second"),
                List.copyOf(stages),
                bounded(config.getInt("max-concurrent-loads", 384), 1, 2048, "max-concurrent-loads"),
                bounded(config.getInt("max-submissions-per-tick", 96), 1, 512,
                        "max-submissions-per-tick"),
                bounded(config.getInt("entity-spawns-per-tick", 25), 1, 500,
                        "entity-spawns-per-tick"),
                bounded(config.getInt("ticket-release-delay-seconds", 5), 0, 300,
                        "ticket-release-delay-seconds") * 20,
                bounded(config.getInt("status-interval-seconds", 10), 1, 300,
                        "status-interval-seconds") * 20,
                positive(config.getLong("limits.max-completed-loads", 250000L), "max-completed-loads"),
                bounded(config.getInt("limits.max-pending-requests", 60000), 1, 1000000,
                        "max-pending-requests"),
                positive(config.getLong("limits.max-failures", 100L), "max-failures"),
                positive(config.getLong("limits.minimum-free-disk-gib", 80L), "minimum-free-disk-gib")
                        * 1024L * 1024L * 1024L,
                bounded(config.getDouble("limits.minimum-scheduler-tps", 3.0), 0.1, 20.0,
                        "minimum-scheduler-tps"),
                bounded(config.getInt("limits.low-tps-grace-samples", 2), 1, 20,
                        "low-tps-grace-samples"));
    }

    private static void requireOptionalSize(List<?> values, int expected, String key) {
        if (!values.isEmpty() && values.size() != expected) {
            throw new IllegalArgumentException(key + " must be empty or match stage-walkers");
        }
    }

    private static int bounded(int value, int minimum, int maximum, String key) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(key + " is out of range");
        return value;
    }

    private static double bounded(double value, double minimum, double maximum, String key) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " is out of range");
        }
        return value;
    }

    private static long positive(long value, String key) {
        if (value < 1) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    enum Mode {
        FLIGHT,
        DWELL,
        MIXED;

        private static Mode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown stage mode: " + value, invalid);
            }
        }
    }

    record Stage(int walkers, int durationSeconds, Mode mode, double speedBlocksPerSecond,
                 int targetWorldEntities) {
        int stationaryWalkers() {
            return switch (mode) {
                case FLIGHT -> 0;
                case DWELL -> walkers;
                case MIXED -> walkers / 2;
            };
        }

        int flyingWalkers() {
            return walkers - stationaryWalkers();
        }
    }
}
