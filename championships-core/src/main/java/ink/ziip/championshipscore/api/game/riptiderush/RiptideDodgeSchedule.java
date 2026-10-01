package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Server-independent attack model shared with the offline movement simulation. */
public final class RiptideDodgeSchedule {
    public static final int DURATION_TICKS = 180;
    public static final int INITIAL_DELAY_TICKS = 20;
    public static final double EDGE_CLEARANCE = 4.5D;
    public static final double DOWNWARD_SPAWN_OFFSET = 8.5D;
    public static final double COLLISION_WIDTH = .6D;

    public enum Mob { ZOMBIE, HUSK, SKELETON, SPIDER, CREEPER }
    private static final double[] SPEED_MULTIPLIERS = {.85D, 1.05D, 1.25D, 1.45D, 1.65D};
    /** Per mob and course phase: minimum interval, maximum interval, active cap on a 7x9 raft. */
    private static final int[][][] PROFILES = {
        {{6,9,6}, {5,8,4}, {6,9,12}, {6,9,9}, {3,6,6}},
        {{5,8,7}, {5,8,9}, {3,5,6}, {5,8,12}, {3,6,9}},
        {{6,9,9}, {5,8,6}, {6,9,9}, {3,6,7}, {3,5,6}},
        {{6,9,6}, {6,9,9}, {5,8,6}, {6,9,4}, {11,18,3}},
        {{3,5,18}, {3,6,7}, {5,8,4}, {3,5,6}, {3,5,4}}
    };
    /** Directions in raft coordinates: X across the deck, Z along the course. */
    public enum Direction {
        NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0), DOWN(0, -1, 0);
        public final int x, y, z;
        Direction(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        /** The arrival side relative to the raft, rather than the mob's travel direction. */
        public String arrivalSide() {
            return switch (this) {
                case NORTH -> "前方";
                case SOUTH -> "后方";
                case EAST -> "右方";
                case WEST -> "左方";
                case DOWN -> "上方";
            };
        }
    }

    public record Settings(int minimumInterval, int maximumInterval, int maximumActive, double speedMultiplier) {
        public Settings(int minimumInterval, int maximumInterval, int maximumActive) {
            this(minimumInterval, maximumInterval, maximumActive, 1D);
        }
        public Settings {
            if (minimumInterval < 1 || maximumInterval < minimumInterval || maximumActive < 1
                    || !Double.isFinite(speedMultiplier) || speedMultiplier <= 0)
                throw new IllegalArgumentException("invalid dodge settings");
        }
    }
    public record Position(double x, double y, double z) { }
    public record Spawn(Mob mob, int tick, Direction direction, double x, double z, double speed, int lifetimeTicks) {
        public Position positionAt(int age) {
            double distance = speed * age;
            return new Position(x + direction.x * distance,
                    spawnHeightOffset(mob) + (direction == Direction.DOWN ? DOWNWARD_SPAWN_OFFSET : 0)
                            + direction.y * distance,
                    z + direction.z * distance);
        }
        public boolean activeAt(int tickNumber) {
            return tickNumber >= tick && tickNumber < tick + lifetimeTicks;
        }
    }

    private RiptideDodgeSchedule() { }

    /** Reduced attack density; small decks need fewer concurrent paths. */
    public static Settings settingsFor(int width, int length) {
        return settingsFor(width,length,0,Mob.ZOMBIE);
    }

    public static Settings settingsFor(int width, int length, int stage, Mob mob) {
        int phase = Math.max(0, Math.min(4,stage));
        int[] profile = PROFILES[mob.ordinal()][phase];
        int cap = Math.max(1, (int) Math.floor(profile[2] * Math.min(1D,width * (double) length / 63D)));
        double multiplier = phase == 0 && mob == Mob.CREEPER ? .90D : SPEED_MULTIPLIERS[phase];
        return new Settings(profile[0],profile[1],cap,multiplier);
    }

    public static List<Spawn> generate(String variant, long seed, int width, int length, int stage) {
        Mob mob = resolveMob(variant,random(seed));
        return generate(variant,seed,width,length,settingsFor(width,length,stage,mob));
    }

    public static List<Spawn> generate(String variant, long seed, int width, int length, Settings settings) {
        if (width < 3 || length < 3) throw new IllegalArgumentException("deck spans must be at least three");
        Random random = random(seed);
        Mob mob = resolveMob(variant, random);
        Direction direction = switch (mob) {
            case ZOMBIE -> random.nextBoolean() ? Direction.NORTH : Direction.SOUTH;
            case HUSK -> random.nextBoolean() ? Direction.EAST : Direction.WEST;
            case SKELETON, SPIDER -> Direction.values()[random.nextInt(4)];
            case CREEPER -> Direction.DOWN;
        };
        double speed = speed(mob) * settings.speedMultiplier();
        List<Spawn> generated = new ArrayList<>();
        List<Integer> departures = new ArrayList<>();
        for (int tick = INITIAL_DELAY_TICKS; tick < DURATION_TICKS;
             tick += settings.minimumInterval() + random.nextInt(settings.maximumInterval() - settings.minimumInterval() + 1)) {
            final int now = tick;
            departures.removeIf(end -> end <= now);
            if (departures.size() >= settings.maximumActive()) continue;
            double x = randomLane(random, width), z = randomLane(random, length);
            double travel;
            switch (direction) {
                case NORTH -> { z = spawnDistance(length); travel = 2 * z; }
                case SOUTH -> { z = -spawnDistance(length); travel = -2 * z; }
                case EAST -> { x = -spawnDistance(width); travel = -2 * x; }
                case WEST -> { x = spawnDistance(width); travel = 2 * x; }
                case DOWN -> travel = spawnHeightOffset(mob) + DOWNWARD_SPAWN_OFFSET + 3;
                default -> throw new IllegalStateException("unknown direction");
            }
            int lifetime = (int) Math.ceil(travel / speed);
            generated.add(new Spawn(mob, tick, direction, x, z, speed, lifetime));
            departures.add(tick + lifetime);
        }
        return List.copyOf(generated);
    }

    private static double randomLane(Random random, int span) {
        return (random.nextDouble() - .5D) * (span - 1D);
    }

    private static Random random(long seed) {
        // Mix sequential content/test seeds before the first direction draw.
        long mixed = seed + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return new Random(mixed ^ (mixed >>> 31));
    }

    private static Mob resolveMob(String variant, Random random) {
        if (variant != null && !variant.isBlank() && !variant.equalsIgnoreCase("AUTO")) {
            try { return Mob.valueOf(variant.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        return Mob.values()[random.nextInt(Mob.values().length)];
    }

    public static double spawnDistance(double span) { return span / 2D + EDGE_CLEARANCE; }
    public static double spawnHeightOffset(Mob mob) {
        return switch (mob) {
            case ZOMBIE, HUSK, SKELETON -> .2D;
            case SPIDER -> 0D;
            case CREEPER -> 1.2D;
        };
    }
    /** Reference speeds; the selected course phase applies its own increasing multiplier. */
    public static double speed(Mob mob) {
        return switch (mob) {
            case ZOMBIE -> .26D;
            case HUSK -> .23D;
            case SKELETON -> .22D;
            case SPIDER -> .30D;
            case CREEPER -> .27D;
        };
    }
}
