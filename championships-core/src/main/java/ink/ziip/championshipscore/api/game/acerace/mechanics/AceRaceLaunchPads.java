package ink.ziip.championshipscore.api.game.acerace.mechanics;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.AceRaceMessages;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owns pad arms and flight budgets for one race at a time, on the area's Bukkit thread. */
public final class AceRaceLaunchPads implements AutoCloseable {
    private static final long LAUNCH_PAD_DELAY_TICKS = 1L;

    /**
     * Vanilla multiplies a player's horizontal motion by {@code slipperiness * 0.91} at the end of
     * a tick spent on the ground and by {@code 0.91} while airborne, and it picks the branch from
     * the ground state the tick <em>started</em> with. A pad impulse is only a velocity packet, so
     * a racer who leaves the wool before the packet lands keeps 0.91 instead of 0.546 and flies
     * about 1.667x as far. These constants model the intended grounded curve so the server can
     * measure and undo that difference.
     */
    private static final double LAUNCH_PAD_SLIPPERINESS = 0.6D;

    private static final double AIR_DRAG = 0.91D;
    private static final double LAUNCH_GROUND_DRAG = LAUNCH_PAD_SLIPPERINESS * AIR_DRAG;

    /**
     * A standing player has no upward motion; reject a jump that is already being processed at fire
     * time.
     */
    private static final double LAUNCH_READY_MAX_VERTICAL_VELOCITY = 0.05D;

    /**
     * Highest per-tick airborne gain a sprinting racer can legitimately add while holding forward.
     */
    private static final double SPRINT_AIR_ACCELERATION = 0.026D;

    private static final int LAUNCH_ENFORCEMENT_TICKS = 20;
    private static final int LAUNCH_DEBT_REPAY_TICKS = 6;

    /**
     * Share of the pad impulse a single tick of travel must show before the flight budget starts.
     * Keep it clear of the fastest legitimate ground speed, which the red station's Speed IX pushes
     * well above a normal sprint, yet far below the impulse's own first tick.
     */
    private static final double LAUNCH_DETECTION_FRACTION = 0.5D;

    /**
     * How long to wait for the impulse to show up in the racer's movement before dropping the
     * budget.
     */
    private static final int LAUNCH_IMPULSE_WAIT_TICKS = 40;

    private static final int LAUNCH_ENFORCEMENT_EXPIRED = Integer.MAX_VALUE;

    /** Absorbs movement-packet jitter so an honest flight never trips the correction. */
    private static final double LAUNCH_DISTANCE_TOLERANCE = 0.5D;

    private static final double MIN_LAUNCH_VERTICAL_MULTIPLIER = 0.9D;
    private static final double MAX_LAUNCH_VERTICAL_MULTIPLIER = 1.2D;

    private final Access access;
    private Run active;
    private boolean closed;

    public AceRaceLaunchPads(
            ChampionshipsCore plugin,
            Predicate<Player> participant,
            Consumer<UUID> releaseContact) {
        this(
                new Access() {
                    @Override
                    public boolean eligible(Player player) {
                        return plugin.isEnabled()
                                && player.isOnline()
                                && !player.isDead()
                                && Bukkit.getPlayer(player.getUniqueId()) == player
                                && participant.test(player);
                    }

                    @Override
                    public boolean supported(Player player) {
                        if (!player.isOnGround()) return false;
                        Block feet = player.getLocation().getBlock();
                        return feet.getType().isSolid()
                                || feet.getRelative(0, -1, 0).getType().isSolid();
                    }

                    @Override
                    public boolean onPad(Player player, Material material) {
                        return player.getLocation().getBlock().getRelative(0, -1, 0).getType()
                                == material;
                    }

                    @Override
                    public BukkitTask later(Runnable action) {
                        return plugin.getServer()
                                .getScheduler()
                                .runTaskLater(plugin, action, LAUNCH_PAD_DELAY_TICKS);
                    }

                    @Override
                    public BukkitTask repeat(Runnable action) {
                        return plugin.getServer()
                                .getScheduler()
                                .runTaskTimer(plugin, action, 1L, 1L);
                    }

                    @Override
                    public void releaseContact(UUID uuid) {
                        releaseContact.accept(uuid);
                    }

                    @Override
                    public void feedback(Player player, Material material) {
                        player.playSound(
                                player.getLocation(),
                                Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                                0.8F,
                                material == Material.RED_WOOL ? 1.1F : 1.35F);
                        CoreMessages.sendActionBar(player, AceRaceMessages.ACE_RACE_LAUNCH_PAD);
                    }
                });
    }

    AceRaceLaunchPads(Access access) {
        this.access = access;
    }

    interface Access {
        boolean eligible(Player player);

        boolean supported(Player player);

        boolean onPad(Player player, Material material);

        BukkitTask later(Runnable action);

        BukkitTask repeat(Runnable action);

        void releaseContact(UUID uuid);

        void feedback(Player player, Material material);
    }

    public void start() {
        stop();
        if (closed) return;
        Run run = new Run();
        active = run;
        try {
            BukkitTask task = access.repeat(() -> tick(run));
            if (active == run) run.task = task;
            else task.cancel();
        } catch (RuntimeException | Error failure) {
            if (active == run) stop();
            throw failure;
        }
    }

    public void stop() {
        Run run = active;
        active = null;
        if (run == null) return;
        if (run.task != null) run.task.cancel();
        for (Arm arm : run.pending.values()) if (arm.task != null) arm.task.cancel();
        run.pending.clear();
        run.flights.clear();
    }

    @Override
    public void close() {
        closed = true;
        stop();
    }

    public boolean hasPending(UUID uuid) {
        return active != null && active.pending.containsKey(uuid);
    }

    /** Leaving a pad cancels only its arm, preserving any flight already in progress. */
    public void cancelPending(UUID uuid) {
        if (active == null) return;
        Arm arm = active.pending.remove(uuid);
        if (arm != null && arm.task != null) arm.task.cancel();
    }

    /** Sanctioned speed sources (red stations, riptide) release only the flight budget. */
    public void releaseFlight(UUID uuid) {
        if (active != null) active.flights.remove(uuid);
    }

    public void cancelPlayer(UUID uuid) {
        cancelPending(uuid);
        releaseFlight(uuid);
        access.releaseContact(uuid);
    }

    public void arm(Player player, Material material) {
        if (!isLaunchPad(material))
            throw new IllegalArgumentException("Not an Ace Race launch pad: " + material);
        Run run = active;
        if (run == null || !access.eligible(player)) return;
        UUID uuid = player.getUniqueId();
        cancelPending(uuid);
        Arm arm = new Arm(player, material);
        run.pending.put(uuid, arm);
        try {
            BukkitTask task = access.later(() -> fire(run, arm));
            if (active == run && run.pending.get(uuid) == arm) arm.task = task;
            else task.cancel();
        } catch (RuntimeException | Error failure) {
            if (active == run && run.pending.remove(uuid, arm)) access.releaseContact(uuid);
            throw failure;
        }
    }

    private void fire(Run run, Arm arm) {
        UUID uuid = arm.player.getUniqueId();
        if (active != run || !run.pending.remove(uuid, arm)) return;
        Player player = arm.player;
        if (!access.eligible(player)
                || !delayedLaunchCanFire(
                        access.supported(player),
                        access.onPad(player, arm.material),
                        player.getVelocity().getY())) {
            access.releaseContact(uuid);
            return;
        }
        launch(run, player, arm.material);
    }

    private void launch(Run run, Player player, Material material) {
        Location origin = player.getLocation();
        Vector direction = origin.getDirection();
        double lift =
                calculateAimedVerticalVelocity(
                        launchBaseVerticalVelocity(material), direction.getY());
        direction.setY(0D);
        if (direction.lengthSquared() < 0.0001D) {
            double yawRadians = Math.toRadians(origin.getYaw());
            direction = new Vector(-Math.sin(yawRadians), 0D, Math.cos(yawRadians));
        } else direction.normalize();
        double speed = launchHorizontalVelocity(material);
        LaunchEnforcement flight = new LaunchEnforcement(player, speed, origin.clone());
        run.flights.put(player.getUniqueId(), flight);
        // Replace old momentum; the budget anchors to the client's observed impulse, not send time.
        try {
            player.setVelocity(
                    new Vector(direction.getX() * speed, lift, direction.getZ() * speed));
        } catch (RuntimeException | Error failure) {
            if (active == run && run.flights.remove(player.getUniqueId(), flight)) {
                access.releaseContact(player.getUniqueId());
            }
            throw failure;
        }
        if (active != run
                || run.flights.get(player.getUniqueId()) != flight
                || !access.eligible(player)) return;
        player.setFallDistance(0F);
        access.feedback(player, material);
    }

    private void tick(Run run) {
        if (active != run) return;
        for (LaunchEnforcement flight : List.copyOf(run.flights.values())) {
            if (active != run) return;
            Player player = flight.player;
            UUID uuid = player.getUniqueId();
            if (run.flights.get(uuid) != flight) continue;
            if (!access.eligible(player)
                    || player.isRiptiding()
                    || player.isGliding()
                    || flight.advance(access.supported(player)) >= LAUNCH_ENFORCEMENT_TICKS) {
                run.flights.remove(uuid, flight);
            }
        }
    }

    public static boolean isLaunchPad(Material material) {
        return material == Material.RED_WOOL || material == Material.ORANGE_WOOL;
    }

    private static final class Run {
        private final Map<UUID, Arm> pending = new HashMap<>();
        private final Map<UUID, LaunchEnforcement> flights = new HashMap<>();
        private BukkitTask task;
    }

    private static final class Arm {
        private final Player player;
        private final Material material;
        private BukkitTask task;

        private Arm(Player player, Material material) {
            this.player = player;
            this.material = material;
        }
    }

    static double calculateAimedVerticalVelocity(double verticalVelocity, double directionY) {
        double aimedVelocity = verticalVelocity * (1D + directionY);
        return Math.max(
                verticalVelocity * MIN_LAUNCH_VERTICAL_MULTIPLIER,
                Math.min(verticalVelocity * MAX_LAUNCH_VERTICAL_MULTIPLIER, aimedVelocity));
    }

    /**
     * Horizontal speed the intended flight carries during the given tick, where tick zero is the
     * launch itself. The launch tick still travels the raw impulse and only afterwards pays the
     * pad's grounded drag; every later tick pays air drag and may add the sprint air acceleration a
     * racer can reach by holding forward, which keeps this an upper bound on honest movement rather
     * than an exact replay.
     */
    static double launchEnvelopeSpeed(double horizontalVelocity, int ticksSinceLaunch) {
        double speed = horizontalVelocity;
        for (int tick = 1; tick <= ticksSinceLaunch; tick++) {
            speed = speed * (tick == 1 ? LAUNCH_GROUND_DRAG : AIR_DRAG) + SPRINT_AIR_ACCELERATION;
        }
        return speed;
    }

    /** Total horizontal distance the intended flight covers through the given tick, inclusive. */
    static double launchEnvelopeDistance(double horizontalVelocity, int ticksSinceLaunch) {
        double speed = horizontalVelocity;
        double distance = speed;
        for (int tick = 1; tick <= ticksSinceLaunch; tick++) {
            speed = speed * (tick == 1 ? LAUNCH_GROUND_DRAG : AIR_DRAG) + SPRINT_AIR_ACCELERATION;
            distance += speed;
        }
        return distance;
    }

    /**
     * Target speed for a racer already ahead of the envelope. The excess distance is repaid over
     * the next few ticks rather than merely capped, so the head start won during the round trip
     * before the first correction packet lands is given back instead of kept.
     */
    static double enforcedLaunchSpeed(double envelopeSpeed, double debt) {
        if (debt <= 0D) return envelopeSpeed;
        return Math.max(0D, envelopeSpeed - debt / LAUNCH_DEBT_REPAY_TICKS);
    }

    static @NotNull Vector rescaleHorizontalVelocity(
            @NotNull Vector currentVelocity,
            double stepX,
            double stepZ,
            double step,
            double target) {
        if (step <= target || step < 0.0001D) return currentVelocity;
        double scale = target / step;
        return new Vector(stepX * scale, currentVelocity.getY(), stepZ * scale);
    }

    static double launchHorizontalVelocity(@NotNull Material material) {
        return switch (material) {
            case RED_WOOL -> 2D;
            case ORANGE_WOOL -> 4D;
            default ->
                    throw new IllegalArgumentException("Not an Ace Race launch pad: " + material);
        };
    }

    static double launchBaseVerticalVelocity(@NotNull Material material) {
        return switch (material) {
            case RED_WOOL -> 0.75D;
            case ORANGE_WOOL -> 1.5D;
            default ->
                    throw new IllegalArgumentException("Not an Ace Race launch pad: " + material);
        };
    }

    static boolean delayedLaunchCanFire(boolean supported, boolean onPad, double verticalVelocity) {
        return supported && onPad && verticalVelocity <= LAUNCH_READY_MAX_VERTICAL_VELOCITY;
    }

    /**
     * Per-launch flight budget: measured horizontal travel checked against the intended grounded
     * curve.
     */
    private static final class LaunchEnforcement {
        private final Player player;
        private final double horizontalVelocity;
        private Location lastSample;
        private double travelled;
        private int ticksSinceLaunch;
        private int ticksWaitingForImpulse;
        private boolean impulseObserved;

        private LaunchEnforcement(
                Player player, double horizontalVelocity, @NotNull Location launchLocation) {
            this.player = player;
            this.horizontalVelocity = horizontalVelocity;
            this.lastSample = launchLocation;
        }

        /**
         * Samples one tick of travel, corrects an over-budget racer, and reports how far through
         * the enforcement window this launch is. {@link
         * AceRaceLaunchPads#LAUNCH_ENFORCEMENT_EXPIRED} ends it.
         */
        private int advance(boolean supported) {
            Location current = player.getLocation();
            if (current.getWorld() != lastSample.getWorld()) return LAUNCH_ENFORCEMENT_EXPIRED;
            double stepX = current.getX() - lastSample.getX();
            double stepZ = current.getZ() - lastSample.getZ();
            lastSample = current;
            double step = Math.sqrt(stepX * stepX + stepZ * stepZ);

            // The impulse only takes effect once the packet reaches the client, so anchor the
            // budget to visible acceleration, not the send tick. Otherwise a high-ping racer
            // could bank distance credit during the round trip and spend it on the head start
            // this budget is meant to remove.
            if (!impulseObserved) {
                if (step < horizontalVelocity * LAUNCH_DETECTION_FRACTION) {
                    return ++ticksWaitingForImpulse >= LAUNCH_IMPULSE_WAIT_TICKS
                            ? LAUNCH_ENFORCEMENT_EXPIRED
                            : 0;
                }
                impulseObserved = true;
                travelled = step;
                return 0;
            }

            // This budget describes a flight and stops the moment one ends. On the ground a racer
            // legitimately bunny hop, adding the vanilla 0.2 sprint impulse on each hop, more
            // than this curve allows. Policing that would cancel the landing jump. Observing
            // half the pad impulse in one step already established flight, so footing ends it.
            if (supported) return LAUNCH_ENFORCEMENT_EXPIRED;

            travelled += step;
            ticksSinceLaunch++;
            double debt =
                    travelled
                            - launchEnvelopeDistance(horizontalVelocity, ticksSinceLaunch)
                            - LAUNCH_DISTANCE_TOLERANCE;
            if (debt > 0D) {
                double target =
                        enforcedLaunchSpeed(
                                launchEnvelopeSpeed(horizontalVelocity, ticksSinceLaunch), debt);
                applyHorizontalSpeed(player, stepX, stepZ, step, target);
            }
            return ticksSinceLaunch;
        }

        /**
         * Rescales horizontal motion to {@code target}, keeping the racer's own heading and
         * vertical motion. The horizontal direction comes from the measured step because a player
         * entity's server-side delta movement is not a faithful record of client-driven motion. The
         * vertical component must remain the server's velocity: a position delta is a displacement,
         * not a velocity, and feeding it back here lets an airborne jump input re-inject upward
         * motion every correction tick.
         */
        private void applyHorizontalSpeed(
                @NotNull Player player, double stepX, double stepZ, double step, double target) {
            if (step <= target || step < 0.0001D) return;
            player.setVelocity(
                    rescaleHorizontalVelocity(player.getVelocity(), stepX, stepZ, step, target));
        }
    }
}
