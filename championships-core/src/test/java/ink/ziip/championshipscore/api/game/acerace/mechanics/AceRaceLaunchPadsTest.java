package ink.ziip.championshipscore.api.game.acerace.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class AceRaceLaunchPadsTest {
    @Test
    void padImpulseReplacesMomentumAndKeepsDistinctStrengths() {
        for (Material pad : List.of(Material.RED_WOOL, Material.ORANGE_WOOL)) {
            Fixture f = new Fixture();
            f.racer.velocity = new Vector(50, 0, -50);
            f.racer.location.setYaw(90);
            f.racer.location.setPitch(90);
            f.pads.arm(f.racer.player, pad);
            assertEquals(0, f.racer.writes);
            f.fire();
            double speed = pad == Material.RED_WOOL ? 2 : 4;
            assertEquals(-speed, f.racer.velocity.getX(), 1e-9);
            assertEquals(0, f.racer.velocity.getZ(), 1e-9);
            assertEquals(
                    (pad == Material.RED_WOOL ? 0.75 : 1.5) * 0.9, f.racer.velocity.getY(), 1e-9);
            assertEquals(0, f.racer.fallDistance);
            assertEquals(1, f.feedback);
            assertFalse(f.pads.hasPending(f.racer.uuid));
        }
    }

    @Test
    void replacedArmCannotRemoveNewTaskOrReleaseItsContact() {
        Fixture f = new Fixture();
        f.arm();
        Task stale = f.latestArm();
        f.arm();
        assertTrue(stale.cancelled);
        stale.run();
        assertTrue(f.pads.hasPending(f.racer.uuid));
        assertEquals(0, f.released.size());
        assertEquals(0, f.racer.writes);
        f.fire();
        assertEquals(1, f.racer.writes);
    }

    @Test
    void jumpingOrLeavingPadCancelsOnlyThePendingImpulse() {
        Fixture f = new Fixture();
        f.arm();
        f.pads.cancelPending(f.racer.uuid);
        f.fire();
        assertEquals(0, f.racer.writes);
        assertFalse(f.pads.hasPending(f.racer.uuid));
    }

    @Test
    void oldArmAndTickerCannotTouchRestartedRace() {
        Fixture f = new Fixture();
        f.arm();
        Task staleArm = f.latestArm();
        Task staleTick = f.latestTick();
        f.pads.start();
        f.arm();
        staleArm.run();
        staleTick.run();
        assertTrue(f.pads.hasPending(f.racer.uuid));
        assertEquals(0, f.racer.writes);
        f.fire();
        f.observeImpulse();
        f.racer.move(4);
        staleTick.run();
        assertEquals(1, f.racer.writes);
        f.tick();
        assertEquals(2, f.racer.writes);
    }

    @Test
    void stopAndCloseCancelAllTasksAndCloseCannotBeReopened() {
        Fixture f = new Fixture();
        f.arm();
        f.pads.close();
        f.pads.start();
        f.arm();
        f.fire();
        f.tick();
        assertTrue(f.tasks.stream().allMatch(task -> task.cancelled));
        assertFalse(f.pads.hasPending(f.racer.uuid));
        assertEquals(0, f.racer.writes);
        assertEquals(2, f.tasks.size());
    }

    @Test
    void lostGameplayEligibilityPreventsDelayedImpulse() {
        Fixture f = new Fixture();
        f.arm();
        f.racer.eligible = false;
        f.fire();
        assertEquals(0, f.racer.writes);
        assertEquals(List.of(f.racer.uuid), f.released);
    }

    @Test
    void lostGameplayEligibilityDropsFlightBeforeCorrection() {
        Fixture f = new Fixture();
        f.launch();
        f.observeImpulse();
        f.racer.eligible = false;
        f.racer.move(4);
        f.tick();
        f.racer.eligible = true;
        f.racer.move(4);
        f.tick();
        assertEquals(1, f.racer.writes);
    }

    @Test
    void reconnectCannotInheritArmedImpulseOrFlightBudget() {
        for (boolean launched : List.of(false, true)) {
            Fixture f = new Fixture();
            f.arm();
            if (launched) {
                f.fire();
                f.observeImpulse();
            }
            Racer replacement = new Racer(f.racer.uuid, f.world);
            f.connected.put(replacement.uuid, replacement);
            f.fire();
            f.racer.move(5);
            replacement.move(5);
            f.tick();
            assertEquals(launched ? 1 : 0, f.racer.writes);
            assertEquals(0, replacement.writes);
        }
    }

    @Test
    void invalidGroundedContactReleasesLatchAndAllowsNewAttempt() {
        for (int condition = 0; condition < 3; condition++) {
            Fixture f = new Fixture();
            f.arm();
            if (condition == 0) f.racer.supported = false;
            if (condition == 1) f.racer.onPad = false;
            if (condition == 2) f.racer.velocity.setY(0.42);
            f.fire();
            assertEquals(0, f.racer.writes);
            assertEquals(List.of(f.racer.uuid), f.released);
            f.racer.supported = true;
            f.racer.onPad = true;
            f.racer.velocity.setY(0);
            f.launch();
            assertEquals(1, f.racer.writes);
        }
    }

    @Test
    void cancellingANewArmKeepsTheExistingFlightBudget() {
        Fixture f = new Fixture();
        f.launch();
        f.observeImpulse();
        f.arm();
        f.pads.cancelPending(f.racer.uuid);
        f.fire();
        f.racer.move(4);
        f.tick();
        assertEquals(2, f.racer.writes);
        assertEquals(0.75, f.racer.velocity.getY(), 1e-9);
    }

    @Test
    void aNewLaunchReplacesTheOldFlightBudget() {
        Fixture f = new Fixture();
        f.launch();
        f.observeImpulse();
        f.racer.supported = true;
        f.racer.velocity.setY(0);
        f.pads.arm(f.racer.player, Material.ORANGE_WOOL);
        f.fire();
        f.racer.supported = false;
        f.racer.move(4);
        f.tick();
        f.racer.move(2.21);
        f.tick();
        assertEquals(2, f.racer.writes);
    }

    @Test
    void sanctionedSpeedAndFlightModesReleaseTheBudget() {
        for (int source = 0; source < 3; source++) {
            Fixture f = new Fixture();
            f.launch();
            f.observeImpulse();
            if (source == 0) f.pads.releaseFlight(f.racer.uuid);
            if (source == 1) f.racer.riptiding = true;
            if (source == 2) f.racer.gliding = true;
            f.racer.move(4);
            f.tick();
            f.racer.riptiding = false;
            f.racer.gliding = false;
            f.racer.move(4);
            f.tick();
            assertEquals(1, f.racer.writes);
        }
    }

    @Test
    void landingOrChangingWorldEndsTheBudget() {
        for (boolean land : List.of(false, true)) {
            Fixture f = new Fixture();
            f.launch();
            f.observeImpulse();
            if (land) f.racer.supported = true;
            else f.racer.location.setWorld(world());
            f.racer.move(4);
            f.tick();
            f.racer.supported = false;
            f.racer.location.setWorld(f.world);
            f.racer.move(4);
            f.tick();
            assertEquals(1, f.racer.writes);
        }
    }

    @Test
    void waitingForClientImpulseDoesNotBankDistanceCredit() {
        Fixture f = new Fixture();
        f.launch();
        for (int tick = 0; tick < 30; tick++) f.tick();
        f.observeImpulse();
        f.racer.move(4);
        f.tick();
        assertEquals(2, f.racer.writes);
    }

    @Test
    void missingImpulseExpiresAfterFortyTicks() {
        Fixture f = new Fixture();
        f.launch();
        for (int tick = 0; tick < 40; tick++) f.tick();
        f.observeImpulse();
        f.racer.move(4);
        f.tick();
        assertEquals(1, f.racer.writes);
    }

    @Test
    void honestFlightIsUntouchedAndEnforcementExpiresAfterTwentyTicks() {
        Fixture f = new Fixture();
        f.launch();
        f.observeImpulse();
        double speed = 2;
        for (int tick = 1; tick <= 20; tick++) {
            speed = speed * (tick == 1 ? 0.546 : 0.91) + 0.026;
            f.racer.move(speed);
            f.tick();
        }
        f.racer.move(20);
        f.tick();
        assertEquals(1, f.racer.writes);
    }

    @Test
    void cancellingOnePlayerLeavesOtherPlayerArmed() {
        Fixture f = new Fixture();
        Racer other = new Racer(UUID.randomUUID(), f.world);
        f.connected.put(other.uuid, other);
        f.arm();
        Task first = f.latestArm();
        f.pads.arm(other.player, Material.RED_WOOL);
        f.pads.cancelPlayer(f.racer.uuid);
        first.run();
        f.fire();
        assertEquals(0, f.racer.writes);
        assertEquals(1, other.writes);
        assertEquals(List.of(f.racer.uuid), f.released);
    }

    @Test
    void rejectedArmCanRetryWithoutDiscardingAnExistingFlight() {
        Fixture f = new Fixture();
        f.launch();
        f.observeImpulse();
        f.rejectLater = true;
        assertThrows(IllegalStateException.class, f::arm);
        assertFalse(f.pads.hasPending(f.racer.uuid));
        assertEquals(List.of(f.racer.uuid), f.released);
        f.racer.move(4);
        f.tick();
        assertEquals(2, f.racer.writes);
        f.rejectLater = false;
        f.arm();
        assertTrue(f.pads.hasPending(f.racer.uuid));
    }

    @Test
    void failedImpulseLeavesNoBudgetOrContactLatch() {
        Fixture f = new Fixture();
        f.racer.onVelocity =
                () -> {
                    throw new IllegalStateException("velocity rejected");
                };
        assertThrows(IllegalStateException.class, f::launch);
        assertEquals(List.of(f.racer.uuid), f.released);
        assertFalse(f.pads.hasPending(f.racer.uuid));
        f.racer.onVelocity = null;
        f.observeImpulse();
        f.racer.move(4);
        f.tick();
        assertEquals(1, f.racer.writes);
        assertEquals(0, f.feedback);
        f.racer.supported = true;
        f.racer.velocity.setY(0);
        f.launch();
        assertEquals(1, f.feedback);
    }

    @Test
    void rejectedTickerLeavesNoActiveRunAndCanRetry() {
        Fixture f = new Fixture();
        f.rejectRepeat = true;
        assertThrows(IllegalStateException.class, f.pads::start);
        f.arm();
        assertFalse(f.pads.hasPending(f.racer.uuid));
        f.rejectRepeat = false;
        f.pads.start();
        f.launch();
        assertEquals(1, f.racer.writes);
    }

    @Test
    void resetDuringVelocityApplicationCannotPublishFeedbackIntoNewRun() {
        Fixture f = new Fixture();
        f.racer.onVelocity =
                () -> {
                    f.pads.start();
                    f.arm();
                };
        f.launch();
        assertEquals(0, f.feedback);
        assertTrue(f.pads.hasPending(f.racer.uuid));
        f.racer.onVelocity = null;
        f.racer.velocity.setY(0);
        f.fire();
        assertEquals(1, f.feedback);
    }

    @Test
    void resetDuringCorrectionDoesNotRemoveNewArms() {
        Fixture f = new Fixture();
        f.launch();
        f.observeImpulse();
        f.racer.onVelocity =
                () -> {
                    f.pads.start();
                    f.arm();
                };
        f.racer.move(4);
        f.tick();
        assertTrue(f.pads.hasPending(f.racer.uuid));
        f.racer.onVelocity = null;
        f.racer.supported = true;
        f.racer.velocity.setY(0);
        f.fire();
        assertEquals(2, f.feedback);
    }

    /** Pure launch envelope and correction functions, independent of scheduled pad contact. */
    @Nested
    class LaunchPhysics {
        @Test
        void aimedLiftKeepsBaseAndScalingWithinBothClamps() {
            assertEquals(0.9D, AceRaceLaunchPads.calculateAimedVerticalVelocity(1D, -1D));
            assertEquals(1D, AceRaceLaunchPads.calculateAimedVerticalVelocity(1D, 0D));
            assertEquals(1.1D, AceRaceLaunchPads.calculateAimedVerticalVelocity(1D, 0.1D));
            assertEquals(1.2D, AceRaceLaunchPads.calculateAimedVerticalVelocity(1D, 1D));
            assertEquals(
                    0.675D,
                    AceRaceLaunchPads.calculateAimedVerticalVelocity(
                            AceRaceLaunchPads.launchBaseVerticalVelocity(Material.RED_WOOL), -1D));
        }

        /**
         * Replays a client's horizontal speed after a pad impulse. The only difference between an
         * honest and an exploited launch is the drag paid on the launch tick: {@code 0.6 * 0.91}
         * while still standing on the wool versus {@code 0.91} once the racer jumped clear of it.
         * Both racers hold forward, so both carry the same sprint air acceleration.
         */
        private static double branchSpeed(
                double horizontalVelocity,
                int ticks,
                double launchTickDrag,
                double airAcceleration) {
            double speed = horizontalVelocity;
            for (int tick = 1; tick <= ticks; tick++) {
                speed = speed * (tick == 1 ? launchTickDrag : 0.91D) + airAcceleration;
            }
            return speed;
        }

        private static double groundedBranchSpeed(double horizontalVelocity, int ticks) {
            return branchSpeed(horizontalVelocity, ticks, 0.6D * 0.91D, 0.026D);
        }

        /**
         * Same launch, but applied after the racer left the ground: the branch this fix exists to
         * remove.
         */
        private static double airborneBranchSpeed(double horizontalVelocity, int ticks) {
            return branchSpeed(horizontalVelocity, ticks, 0.91D, 0.026D);
        }

        /** An honest racer who never touches the movement keys, the slowest legitimate flight. */
        private static double coastingBranchSpeed(double horizontalVelocity, int ticks) {
            return branchSpeed(horizontalVelocity, ticks, 0.6D * 0.91D, 0D);
        }

        @Test
        void launchEnvelopeTracksBothPadsWithoutPenalizingHonestFlight() {
            for (Material pad : List.of(Material.RED_WOOL, Material.ORANGE_WOOL)) {
                double horizontal = AceRaceLaunchPads.launchHorizontalVelocity(pad);
                assertEquals(
                        horizontal,
                        AceRaceLaunchPads.launchEnvelopeSpeed(horizontal, 0),
                        pad.name());
                assertEquals(
                        horizontal,
                        AceRaceLaunchPads.launchEnvelopeDistance(horizontal, 0),
                        pad.name());
                double sprinting = horizontal;
                double coasting = horizontal;
                double previousBudget = horizontal;
                for (int tick = 1; tick <= 20; tick++) {
                    String context = pad + " tick=" + tick;
                    double grounded = groundedBranchSpeed(horizontal, tick);
                    double envelope = AceRaceLaunchPads.launchEnvelopeSpeed(horizontal, tick);
                    assertEquals(grounded, envelope, 1e-9D, context);
                    assertTrue(
                            envelope < airborneBranchSpeed(horizontal, tick), context + " exploit");
                    sprinting += grounded;
                    coasting += coastingBranchSpeed(horizontal, tick);
                    double budget = AceRaceLaunchPads.launchEnvelopeDistance(horizontal, tick);
                    assertTrue(sprinting <= budget, context + " sprinting");
                    assertTrue(coasting <= budget, context + " coasting");
                    assertTrue(budget > previousBudget, context + " distance must increase");
                    previousBudget = budget;
                }
            }
            assertEquals(1.118D, AceRaceLaunchPads.launchEnvelopeSpeed(2D, 1), 1e-9D);
        }

        @Test
        void jumpExploitExceedsTheDistanceBudgetWithinThreeTicks() {
            double horizontal = AceRaceLaunchPads.launchHorizontalVelocity(Material.RED_WOOL);
            double exploited = horizontal;
            double budget = AceRaceLaunchPads.launchEnvelopeDistance(horizontal, 3);
            for (int tick = 1; tick <= 3; tick++)
                exploited += airborneBranchSpeed(horizontal, tick);
            // The 0.5 block tolerance that absorbs movement-packet jitter must not hide the
            // exploit.
            assertTrue(
                    exploited - budget > 0.5D,
                    "exploited "
                            + exploited
                            + " should clear budget "
                            + budget
                            + " plus tolerance");
        }

        @Test
        void excessDistanceReducesSpeedWithoutReversingOrPenalizingDebtFreeFlight() {
            double[][] cases = {
                {1.5, 0, 1.5}, {1.5, -3, 1.5}, {1.5, 3, 1}, {1.5, 0.6, 1.4}, {0.4, 100, 0}
            };
            for (double[] example : cases) {
                assertEquals(
                        example[2],
                        AceRaceLaunchPads.enforcedLaunchSpeed(example[0], example[1]),
                        1e-9D,
                        "speed=" + example[0] + " debt=" + example[1]);
            }
        }

        @Test
        void horizontalCorrectionKeepsServerVerticalVelocity() {
            Vector corrected =
                    AceRaceLaunchPads.rescaleHorizontalVelocity(
                            new Vector(1.4D, 0.37D, 0D), 2D, 0D, 2D, 1D);

            assertEquals(1D, corrected.getX(), 1e-9D);
            assertEquals(
                    0.37D,
                    corrected.getY(),
                    1e-9D,
                    "a movement displacement must never be reused as the vertical velocity");
            assertEquals(0D, corrected.getZ(), 1e-9D);
        }

        @Test
        void delayedLaunchRequiresAValidGroundedPadContact() {
            assertTrue(AceRaceLaunchPads.delayedLaunchCanFire(true, true, 0D));
            assertFalse(AceRaceLaunchPads.delayedLaunchCanFire(true, true, 0.051D));
            assertFalse(AceRaceLaunchPads.delayedLaunchCanFire(false, true, 0D));
            assertFalse(AceRaceLaunchPads.delayedLaunchCanFire(true, false, 0D));
        }
    }

    private static class Fixture implements AceRaceLaunchPads.Access {
        final World world = world();
        final Racer racer = new Racer(UUID.randomUUID(), world);
        final Map<UUID, Racer> connected = new HashMap<>();
        final List<Task> tasks = new ArrayList<>();
        final List<UUID> released = new ArrayList<>();
        final AceRaceLaunchPads pads = new AceRaceLaunchPads(this);
        boolean rejectLater;
        boolean rejectRepeat;
        int feedback;

        Fixture() {
            connected.put(racer.uuid, racer);
            pads.start();
        }

        void arm() {
            pads.arm(racer.player, Material.RED_WOOL);
        }

        void fire() {
            latestArm().run();
        }

        void launch() {
            arm();
            fire();
        }

        void tick() {
            latestTick().run();
        }

        void observeImpulse() {
            racer.supported = false;
            racer.move(2);
            tick();
        }

        Task latestArm() {
            return tasks.stream().filter(task -> !task.repeating).toList().getLast();
        }

        Task latestTick() {
            return tasks.stream().filter(task -> task.repeating).toList().getLast();
        }

        Racer racer(Player player) {
            return connected.get(player.getUniqueId());
        }

        @Override
        public boolean eligible(Player player) {
            Racer current = racer(player);
            return current != null && current.player == player && current.eligible;
        }

        @Override
        public boolean supported(Player player) {
            return racer(player).supported;
        }

        @Override
        public boolean onPad(Player player, Material material) {
            return racer(player).onPad;
        }

        @Override
        public BukkitTask later(Runnable action) {
            if (rejectLater) throw new IllegalStateException("rejected");
            Task task = new Task(action, false);
            tasks.add(task);
            return task;
        }

        @Override
        public BukkitTask repeat(Runnable action) {
            if (rejectRepeat) throw new IllegalStateException("rejected");
            Task task = new Task(action, true);
            tasks.add(task);
            return task;
        }

        @Override
        public void releaseContact(UUID uuid) {
            released.add(uuid);
        }

        @Override
        public void feedback(Player player, Material material) {
            feedback++;
        }
    }

    private static class Racer {
        final UUID uuid;
        final Player player;
        final Location location;
        Vector velocity = new Vector();
        boolean supported = true;
        boolean onPad = true;
        boolean eligible = true;
        boolean riptiding;
        boolean gliding;
        float fallDistance = 10;
        int writes;
        Runnable onVelocity;

        Racer(UUID uuid, World world) {
            this.uuid = uuid;
            location = new Location(world, 0, 70, 0);
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> uuid;
                                                case "getLocation" -> location.clone();
                                                case "getVelocity" -> velocity.clone();
                                                case "getWorld" -> location.getWorld();
                                                case "isRiptiding" -> riptiding;
                                                case "isGliding" -> gliding;
                                                case "setVelocity" -> {
                                                    velocity = ((Vector) args[0]).clone();
                                                    writes++;
                                                    if (onVelocity != null) onVelocity.run();
                                                    yield null;
                                                }
                                                case "setFallDistance" -> {
                                                    fallDistance = (float) args[0];
                                                    yield null;
                                                }
                                                case "equals" -> proxy == args[0];
                                                case "hashCode" -> System.identityHashCode(proxy);
                                                case "toString" -> "Racer:" + uuid;
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected player call: "
                                                                        + method);
                                            });
        }

        void move(double distance) {
            location.add(0, 0, distance);
        }
    }

    private static World world() {
        UUID uuid = UUID.randomUUID();
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getUID" -> uuid;
                                    case "equals" -> proxy == args[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "toString" -> "World:" + uuid;
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected world call: " + method);
                                });
    }

    private static class Task implements BukkitTask {
        final Runnable action;
        final boolean repeating;
        boolean cancelled;

        Task(Runnable action, boolean repeating) {
            this.action = action;
            this.repeating = repeating;
        }

        // Deliberately execute cancelled tasks to simulate an already queued stale callback.
        void run() {
            action.run();
        }

        @Override
        public int getTaskId() {
            return 1;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
