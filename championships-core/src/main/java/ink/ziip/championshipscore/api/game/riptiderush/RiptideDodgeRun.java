package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic three-wave schedule used by the stopped raft dodge stage.
 *
 * <p>Each wave enters from all four edges of the arena.  The old schedule
 * put every mob on one randomly selected line (and dropped creepers from
 * above), which made the stage read as a short obstacle wall instead of a
 * surrounding attack that players can circle and dodge.</p>
 */
public final class RiptideDodgeRun {
    public static final int WAVE_TICKS = 40;
    public static final int DURATION_TICKS = WAVE_TICKS * 3;

    /** The two added variants are Husk and Spider. */
    public enum Mob { ZOMBIE, HUSK, SKELETON, SPIDER, CREEPER }
    public enum Direction { NORTH, SOUTH, EAST, WEST, NORTHEAST, SOUTHWEST, DOWN }
    public record Spawn(Mob mob, double lateral, int tick, Direction direction) { }

    private final List<Spawn> spawns;
    private int tick;

    public RiptideDodgeRun(String variant, long seed, int width) { this(variant, seed, width, width); }

    public RiptideDodgeRun(String variant, long seed, int width, int length) {
        Random random = new Random(seed);
        Mob mob = resolveMob(variant, random);
        int count = mob == Mob.SPIDER ? Math.max(8, width / 2 + 2) : 7;
        List<Spawn> generated = new ArrayList<>();
        for (int wave = 0; wave < 3; wave++) {
            for (int i = 0; i < count; i++) {
                Direction direction = edgeDirection((i + wave) % 4);
                int edgeCount = (count + 3) / 4;
                int edgeIndex = i / 4;
                double span = direction == Direction.EAST || direction == Direction.WEST ? length : width;
                double lane = ((edgeIndex + .5D) / edgeCount - .5D) * Math.max(1D, span - 1D);
                int delay = wave * WAVE_TICKS + (i % 4) * 2;
                generated.add(new Spawn(mob, lane, delay, direction));
            }
        }
        spawns = List.copyOf(generated);
    }

    private static Mob resolveMob(String variant, Random random) {
        if (variant == null || variant.isBlank() || variant.equalsIgnoreCase("AUTO"))
            return Mob.values()[random.nextInt(Mob.values().length)];
        try { return Mob.valueOf(variant.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Mob.values()[random.nextInt(Mob.values().length)]; }
    }

    private static Direction edgeDirection(int edge) {
        return switch (edge) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /**
     * Movement is intentionally staged: the first wave gives players time to
     * read the attack, while later waves add pressure without becoming a blur.
     * Values are blocks per tick (20 ticks/second).
     */
    public static double speed(Mob mob, int wave) {
        int stage = Math.max(0, Math.min(2, wave));
        return switch (mob) {
            case ZOMBIE -> new double[]{.16D, .21D, .26D}[stage];
            case HUSK -> new double[]{.14D, .18D, .23D}[stage];
            case SKELETON -> new double[]{.12D, .17D, .22D}[stage];
            case SPIDER -> new double[]{.19D, .24D, .30D}[stage];
            case CREEPER -> new double[]{.10D, .17D, .27D}[stage];
        };
    }

    /** Compatibility helper for callers that do not track a wave. */
    public static double speed(Mob mob) { return speed(mob, 2); }

    public List<Spawn> spawns() { return spawns; }
    public List<Spawn> tick() {
        List<Spawn> due = spawns.stream().filter(spawn -> spawn.tick() == tick).toList();
        tick++;
        return due;
    }
    public boolean complete() { return tick >= DURATION_TICKS; }
    public int tickNumber() { return tick; }
}
