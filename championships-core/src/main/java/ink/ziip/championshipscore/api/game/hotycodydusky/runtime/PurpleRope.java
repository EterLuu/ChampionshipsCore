package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import io.papermc.paper.entity.TeleportFlag;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Quicksand trap and redesigned escape rope for one arena copy. Renders a beam-lashed braided hemp
 * rope with climbing knots and a wooden rescue handle using pure-SLERP interpolated BlockDisplays,
 * hiding legacy fence placeholders non-destructively while active.
 */
final class PurpleRope {
    static final int CLICKS_REQUIRED = 5;
    static final int SEGMENT_COUNT = 4;
    static final float HANDLE_DROP = 0.40f;
    static final float STRAP_LENGTH = 0.46f;
    static final float STRAP_SPREAD_RAD = (float) Math.toRadians(28.0);
    private static final float ROPE_WIDTH = 0.145f;
    private static final float SEGMENT_OVERLAP = 0.06f;
    private static final float STRAP_WIDTH = 0.085f;
    private static final float HANDLE_WIDTH = 0.58f;
    private static final float HANDLE_THICKNESS = 0.13f;
    private static final float[] SEGMENT_TWIST_RAD = {
        (float) Math.toRadians(15.0),
        (float) Math.toRadians(45.0),
        (float) Math.toRadians(75.0),
        (float) Math.toRadians(105.0)
    };
    private static final float[] KNOT_CANT_RAD = {
        (float) Math.toRadians(10.0),
        (float) Math.toRadians(-10.0),
        (float) Math.toRadians(10.0),
        0f
    };

    private static final double PULL_STEP = 0.33;
    private static final double SWAY_RANGE_SQUARED = 32 * 32;
    private static final int SWAY_INTERVAL = 42;
    private static final Transformation HIDDEN_TRANSFORM =
            new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf());

    // Angles in radians around +Z (positive swings toward +X East, negative toward -X West).
    private static final float[] ANGLES_REST = {0f, 0f, 0f, 0f};
    private static final float[] ANGLES_SWAY_WEST = {-0.03f, -0.055f, -0.08f, -0.10f};
    private static final float[] ANGLES_SWAY_EAST = {0.03f, 0.055f, 0.08f, 0.10f};
    private static final float[] ANGLES_TUG_WEST = {-0.08f, -0.13f, -0.17f, -0.20f};
    private static final float[] ANGLES_TUG_EAST = {0.08f, 0.13f, 0.17f, 0.20f};
    private static final float[] ANGLES_WHIP_PULL = {-0.38f, -0.62f, -0.82f, -0.96f};
    private static final float[] ANGLES_WHIP_REBOUND_EAST = {0.26f, 0.42f, 0.56f, 0.66f};
    private static final float[] ANGLES_WHIP_REBOUND_WEST = {-0.14f, -0.22f, -0.28f, -0.32f};

    /**
     * Computed rigid-body transforms for one animated frame (4 cords, 4 knots, 2 straps, 1 bar).
     */
    record RopeFrame(
            Vector3f[] joints,
            Transformation[] cords,
            Transformation[] knots,
            Transformation leftStrap,
            Transformation rightStrap,
            Transformation handleBar) {}

    private static final class Trapped {
        int clicks;
        UUID anchor;
        int lastClickTick = -10;
    }

    private final HotyCodyDuskyTeamArea area;
    private final Location arenaOrigin;
    private final Vector escapeRope;
    private final Location ropeOrigin;
    private final Location ropeTop;
    private final Location ropeRelease;
    private final List<Vector> quicksand;
    private final Map<UUID, Transformation> hiddenLegacy = new LinkedHashMap<>();
    private final List<UUID> staticParts = new ArrayList<>();
    private final UUID[] cordDisplays = new UUID[SEGMENT_COUNT];
    private final UUID[] knotDisplays = new UUID[SEGMENT_COUNT];
    private UUID leftStrapDisplay;
    private UUID rightStrapDisplay;
    private UUID handleBarDisplay;
    private UUID customHitbox;
    private final Map<UUID, Trapped> trapped = new HashMap<>();
    private float segmentLength = 1.07f;
    private boolean built;
    private boolean swayWest;
    private int nextSwayTick;
    private int whipStartTick = -1;
    private int tugResetTick = -1;

    PurpleRope(
            HotyCodyDuskyTeamArea area,
            Location arenaOrigin,
            Vector escapeRope,
            List<Vector> quicksand) {
        this.area = area;
        this.arenaOrigin = arenaOrigin.clone();
        this.escapeRope = escapeRope.clone();
        this.ropeOrigin = arenaOrigin.clone().add(escapeRope);
        this.ropeTop = ropeOrigin.clone().add(0.5, 1.0, 0.5);
        this.ropeRelease = ropeOrigin.clone().add(0.5, -0.3, 0.5);
        this.quicksand = quicksand;
    }

    /**
     * Computes exact end-to-end connected pendulum transforms in local space below the top beam
     * pivot (0, 0, 0). Every part uses a pure translation + unit quaternion + scale Transformation
     * so client-side interpolation is distortion-free SLERP.
     */
    static RopeFrame computeFrame(float segLen, float[] angles) {
        Vector3f[] joints = new Vector3f[SEGMENT_COUNT + 1];
        joints[0] = new Vector3f(0f, -0.06f, 0f);
        Transformation[] cords = new Transformation[SEGMENT_COUNT];
        Transformation[] knots = new Transformation[SEGMENT_COUNT];
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float theta = angles[i];
            Vector3f start = joints[i];
            cords[i] =
                    hangingPart(
                            start,
                            theta,
                            SEGMENT_TWIST_RAD[i],
                            ROPE_WIDTH,
                            segLen + SEGMENT_OVERLAP,
                            ROPE_WIDTH);
            joints[i + 1] =
                    new Vector3f(
                            start.x + (float) (segLen * Math.sin(theta)),
                            start.y - (float) (segLen * Math.cos(theta)),
                            start.z);
            float knotAngle = i + 1 < SEGMENT_COUNT ? 0.5f * (theta + angles[i + 1]) : theta;
            boolean bottom = i == SEGMENT_COUNT - 1;
            knots[i] =
                    centeredPart(
                            joints[i + 1],
                            knotAngle + KNOT_CANT_RAD[i],
                            (float) Math.toRadians(45.0),
                            bottom ? 0.25f : 0.225f,
                            bottom ? 0.20f : 0.165f,
                            bottom ? 0.25f : 0.225f);
        }
        Vector3f bottomJoint = joints[SEGMENT_COUNT];
        float endAngle = angles[SEGMENT_COUNT - 1];
        Transformation leftStrap =
                hangingPart(
                        bottomJoint,
                        endAngle - STRAP_SPREAD_RAD,
                        (float) Math.toRadians(30.0),
                        STRAP_WIDTH,
                        STRAP_LENGTH,
                        STRAP_WIDTH);
        Transformation rightStrap =
                hangingPart(
                        bottomJoint,
                        endAngle + STRAP_SPREAD_RAD,
                        (float) Math.toRadians(60.0),
                        STRAP_WIDTH,
                        STRAP_LENGTH,
                        STRAP_WIDTH);
        Vector3f barCenter =
                new Vector3f(
                        bottomJoint.x + (float) (HANDLE_DROP * Math.sin(endAngle)),
                        bottomJoint.y - (float) (HANDLE_DROP * Math.cos(endAngle)),
                        bottomJoint.z);
        Transformation handleBar =
                centeredPart(
                        barCenter, endAngle, 0f, HANDLE_WIDTH, HANDLE_THICKNESS, HANDLE_THICKNESS);
        return new RopeFrame(joints, cords, knots, leftStrap, rightStrap, handleBar);
    }

    static Transformation hangingPart(
            Vector3f pivot, float angleZ, float spinY, float w, float h, float d) {
        Quaternionf rot = new Quaternionf().rotateZ(angleZ).rotateY(spinY);
        Vector3f offset = new Vector3f(-0.5f * w, -h, -0.5f * d).rotate(rot).add(pivot);
        return new Transformation(offset, rot, new Vector3f(w, h, d), new Quaternionf());
    }

    static Transformation centeredPart(
            Vector3f center, float angleZ, float spinY, float w, float h, float d) {
        Quaternionf rot = new Quaternionf().rotateZ(angleZ).rotateY(spinY);
        Vector3f offset = new Vector3f(-0.5f * w, -0.5f * h, -0.5f * d).rotate(rot).add(center);
        return new Transformation(offset, rot, new Vector3f(w, h, d), new Quaternionf());
    }

    static boolean matchesAlternateClick(int clicks, boolean right) {
        return right == (clicks % 2 == 0);
    }

    private void ensureBuilt() {
        if (built) return;
        World world = ropeOrigin.getWorld();
        if (world == null) return;
        hideLegacyDisplays(world);

        double pivotY = detectBeamUndersideY(world);
        double handleBottomY = ropeOrigin.getY() - 0.85;
        double shaftLength = Math.max(2.4, pivotY - handleBottomY - HANDLE_DROP - 0.12);
        segmentLength = (float) (shaftLength / SEGMENT_COUNT);
        Location pivot =
                new Location(world, ropeOrigin.getX() + 0.5, pivotY, ropeOrigin.getZ() + 0.5);

        spawnBeamMount(world, pivot);
        RopeFrame rest = computeFrame(segmentLength, ANGLES_REST);
        BlockData hemp = Material.HAY_BLOCK.createBlockData();
        BlockData midKnot = Material.STRIPPED_SPRUCE_WOOD.createBlockData();
        BlockData endKnot = Material.STRIPPED_DARK_OAK_WOOD.createBlockData();
        BlockData handleWood = horizontalLog(Material.STRIPPED_OAK_LOG);

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            cordDisplays[i] = spawnPart(world, pivot, hemp, rest.cords()[i]).getUniqueId();
            knotDisplays[i] =
                    spawnPart(
                                    world,
                                    pivot,
                                    i == SEGMENT_COUNT - 1 ? endKnot : midKnot,
                                    rest.knots()[i])
                            .getUniqueId();
        }
        leftStrapDisplay = spawnPart(world, pivot, hemp, rest.leftStrap()).getUniqueId();
        rightStrapDisplay = spawnPart(world, pivot, hemp, rest.rightStrap()).getUniqueId();
        handleBarDisplay = spawnPart(world, pivot, handleWood, rest.handleBar()).getUniqueId();

        double hitboxBottom = ropeOrigin.getY() - 1.1;
        float hitboxHeight = (float) Math.max(3.6, pivotY - hitboxBottom);
        Interaction hitbox =
                world.spawn(
                        new Location(
                                world,
                                ropeOrigin.getX() + 0.5,
                                hitboxBottom,
                                ropeOrigin.getZ() + 0.5),
                        Interaction.class,
                        e -> {
                            e.setPersistent(false);
                            e.setInteractionWidth(0.85f);
                            e.setInteractionHeight(hitboxHeight);
                            e.setResponsive(true);
                            e.addScoreboardTag("trap.quicksand.rope");
                        });
        customHitbox = hitbox.getUniqueId();
        built = true;
    }

    private void hideLegacyDisplays(World world) {
        var entities =
                world.getNearbyEntities(
                        ropeOrigin.clone().add(0.5, 1.5, 1.5),
                        4,
                        5,
                        5,
                        e -> e.getScoreboardTags().contains("trap.quicksand.rope"));
        for (Entity entity : entities) {
            if (entity instanceof BlockDisplay display
                    && !hiddenLegacy.containsKey(display.getUniqueId())) {
                hiddenLegacy.put(display.getUniqueId(), display.getTransformation());
                display.setInterpolationDuration(0);
                display.setInterpolationDelay(0);
                display.setTransformation(HIDDEN_TRANSFORM);
            }
        }
    }

    private double detectBeamUndersideY(World world) {
        int bx = ropeOrigin.getBlockX();
        int bz = ropeOrigin.getBlockZ();
        int minY = ropeOrigin.getBlockY() + 2;
        int maxY = ropeOrigin.getBlockY() + 6;
        for (int y = minY; y <= maxY; y++) {
            if (!world.getBlockAt(bx, y, bz).isPassable()) return y;
        }
        return ropeOrigin.getY() + 4.0;
    }

    private void spawnBeamMount(World world, Location pivot) {
        // Hemp lashing wrapped snugly around the 1x1 overhead beam above pivotY.
        staticParts.add(
                spawnPart(
                                world,
                                pivot,
                                Material.HAY_BLOCK.createBlockData(),
                                centeredPart(
                                        new Vector3f(0f, 0.50f, 0f), 0f, 0f, 1.08f, 1.08f, 0.28f))
                        .getUniqueId());
        // Tight dark spruce cinch strap binding the lashing.
        staticParts.add(
                spawnPart(
                                world,
                                pivot,
                                Material.STRIPPED_SPRUCE_WOOD.createBlockData(),
                                centeredPart(
                                        new Vector3f(0f, 0.50f, 0f), 0f, 0f, 1.11f, 1.11f, 0.09f))
                        .getUniqueId());
        // Dark wrought-iron shackle / eyelet underneath the beam where the rope attaches.
        staticParts.add(
                spawnPart(
                                world,
                                pivot,
                                Material.NETHERITE_BLOCK.createBlockData(),
                                centeredPart(
                                        new Vector3f(0f, -0.04f, 0f),
                                        0f,
                                        (float) Math.toRadians(45.0),
                                        0.20f,
                                        0.14f,
                                        0.20f))
                        .getUniqueId());
    }

    private static BlockData horizontalLog(Material material) {
        BlockData data = material.createBlockData();
        if (data instanceof Orientable orientable) orientable.setAxis(Axis.X);
        return data;
    }

    private static BlockDisplay spawnPart(
            World world, Location at, BlockData block, Transformation transform) {
        return world.spawn(
                at,
                BlockDisplay.class,
                display -> {
                    display.setPersistent(false);
                    display.setBlock(block);
                    display.setViewRange(0.75f);
                    display.setTransformation(transform);
                });
    }

    void tick(int now, List<Player> players, List<UUID> caught) {
        if (!built && now % 10 == 0) ensureBuilt();
        if (now % 2 == 0) tickTraps(players, caught);
        tickAnimation(now, players);
    }

    private void tickTraps(List<Player> players, List<UUID> caught) {
        var present = new HashSet<UUID>();
        for (Player p : players) {
            UUID id = p.getUniqueId();
            present.add(id);
            Vector pos = p.getLocation().subtract(arenaOrigin).toVector();
            if (escapeRope.distanceSquared(pos) > 400) {
                release(id);
                continue;
            }
            Trapped state = trapped.get(id);
            double range = state != null ? 9 : 1;
            if (quicksand.stream().noneMatch(v -> v.distanceSquared(pos) <= range)) {
                release(id);
                continue;
            }
            boolean anchored = verifyAnchor(p, state);
            if (!anchored && p.getLocation().getBlock().getType() == Material.SOUL_SAND) {
                release(id);
                caught.add(id);
                continue;
            }
            if (state == null) {
                trapped.put(id, new Trapped());
                p.playSound(p.getLocation(), Sound.BLOCK_SOUL_SAND_STEP, 0.8f, 0.5f);
                CoreMessages.sendActionBar(p, MessageConfig.codeText("hoty-items.rope-grab"));
            }
        }
        for (UUID id : List.copyOf(trapped.keySet())) {
            if (!present.contains(id)) release(id);
        }
    }

    private boolean verifyAnchor(Player p, Trapped state) {
        if (state == null || state.anchor == null) return false;
        Entity entity = Bukkit.getEntity(state.anchor);
        if (entity != null && entity.isValid() && entity.getPassengers().contains(p)) return true;
        removeAnchor(state.anchor);
        state.anchor = null;
        return false;
    }

    boolean recentlyClicked(UUID id, int now) {
        Trapped state = trapped.get(id);
        return state != null && state.lastClickTick == now;
    }

    boolean click(Player p, Entity target, boolean right, int now) {
        if (!built) ensureBuilt();
        Trapped state = trapped.get(p.getUniqueId());
        if (state == null || !area.canUseBasinItem(p)) return false;
        var items = area.basinItems();
        if (items != null && items.tools.isJailed(p)) return false;
        if (target != null) {
            if (!target.getScoreboardTags().contains("trap.quicksand.rope")
                    || !target.getWorld().equals(p.getWorld())
                    || p.getEyeLocation().distanceSquared(target.getLocation()) > 36) return false;
        } else {
            if (!ropeOrigin.getWorld().equals(p.getWorld())
                    || p.getLocation().distanceSquared(ropeOrigin) > 36) return false;
        }
        if (state.lastClickTick == now) return true;
        state.lastClickTick = now;

        boolean freshlyGrabbed = false;
        if (!verifyAnchor(p, state)) {
            BlockDisplay anchor = spawnAnchor(p.getLocation());
            if (anchor != null && anchor.addPassenger(p)) {
                state.anchor = anchor.getUniqueId();
                freshlyGrabbed = true;
            } else if (anchor != null) {
                anchor.remove();
            }
        }

        if (!matchesAlternateClick(state.clicks, right)) {
            if (freshlyGrabbed) {
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 1.25f);
                promptNext(p, state.clicks);
            }
            return true;
        }

        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 1.25f);
        state.clicks++;
        if (state.clicks >= CLICKS_REQUIRED) {
            release(p.getUniqueId());
            Location exit = ropeRelease.clone();
            exit.setDirection(p.getLocation().getDirection());
            p.teleport(exit);
            p.setVelocity(new Vector(-0.55, 0.38, 0));
            p.setFallDistance(0);
            tugResetTick = -1;
            whipStartTick = now;
            applyFrame(computeFrame(segmentLength, ANGLES_WHIP_PULL), 5);
            CoreMessages.sendActionBar(p, MessageConfig.codeText("hoty-items.escaped"));
            return true;
        }

        pullPlayer(p, state);
        if (whipStartTick < 0) {
            boolean tugWest = p.getLocation().getX() <= ropeOrigin.getX() + 0.5;
            applyFrame(computeFrame(segmentLength, tugWest ? ANGLES_TUG_WEST : ANGLES_TUG_EAST), 4);
            tugResetTick = now + 6;
        }
        promptNext(p, state.clicks);
        return true;
    }

    private void pullPlayer(Player p, Trapped state) {
        Entity anchor = state.anchor == null ? null : Bukkit.getEntity(state.anchor);
        Location current =
                anchor != null && anchor.isValid() ? anchor.getLocation() : p.getLocation();
        Vector toward = ropeTop.toVector().subtract(current.toVector());
        if (toward.lengthSquared() <= 0.04) return;
        Location next = current.clone().add(toward.normalize().multiply(PULL_STEP));
        if (anchor != null && anchor.isValid() && anchor.getPassengers().contains(p)) {
            anchor.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        } else {
            next.setDirection(p.getLocation().getDirection());
            p.teleport(next);
        }
    }

    private static void promptNext(Player p, int clicks) {
        CoreMessages.sendActionBar(
                p,
                MessageConfig.codeText(
                        clicks % 2 == 0 ? "hoty-items.rope-right" : "hoty-items.rope-left",
                        clicks));
    }

    private static BlockDisplay spawnAnchor(Location at) {
        World world = at.getWorld();
        if (world == null) return null;
        return world.spawn(
                at,
                BlockDisplay.class,
                display -> {
                    display.setPersistent(false);
                    display.setTeleportDuration(2);
                    display.setViewRange(0.25f);
                    display.setBlock(Material.AIR.createBlockData());
                });
    }

    private void tickAnimation(int now, List<Player> players) {
        if (!built) return;
        if (whipStartTick >= 0) {
            int dt = now - whipStartTick;
            if (dt == 6) applyFrame(computeFrame(segmentLength, ANGLES_SWAY_WEST), 5);
            else if (dt == 11) applyFrame(computeFrame(segmentLength, ANGLES_WHIP_REBOUND_EAST), 6);
            else if (dt == 17) applyFrame(computeFrame(segmentLength, ANGLES_WHIP_REBOUND_WEST), 7);
            else if (dt == 24) applyFrame(computeFrame(segmentLength, ANGLES_SWAY_EAST), 8);
            else if (dt >= 25) {
                whipStartTick = -1;
                swayWest = false;
                nextSwayTick = now + 20;
            }
            return;
        }
        if (tugResetTick >= 0 && now >= tugResetTick) {
            tugResetTick = -1;
            applyFrame(computeFrame(segmentLength, ANGLES_REST), 6);
            nextSwayTick = now + 18;
            return;
        }
        if (now < nextSwayTick) return;
        boolean viewerNearby = false;
        for (Player p : players) {
            if (p.getWorld().equals(ropeOrigin.getWorld())
                    && p.getLocation().distanceSquared(ropeOrigin) <= SWAY_RANGE_SQUARED) {
                viewerNearby = true;
                break;
            }
        }
        nextSwayTick = now + SWAY_INTERVAL;
        if (!viewerNearby) return;
        swayWest = !swayWest;
        applyFrame(computeFrame(segmentLength, swayWest ? ANGLES_SWAY_WEST : ANGLES_SWAY_EAST), 40);
    }

    private void applyFrame(RopeFrame frame, int interpolationTicks) {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            applyTransform(cordDisplays[i], frame.cords()[i], interpolationTicks);
            applyTransform(knotDisplays[i], frame.knots()[i], interpolationTicks);
        }
        applyTransform(leftStrapDisplay, frame.leftStrap(), interpolationTicks);
        applyTransform(rightStrapDisplay, frame.rightStrap(), interpolationTicks);
        applyTransform(handleBarDisplay, frame.handleBar(), interpolationTicks);
    }

    private static void applyTransform(UUID id, Transformation transform, int interpolationTicks) {
        if (id == null) return;
        if (Bukkit.getEntity(id) instanceof BlockDisplay display && display.isValid()) {
            display.setInterpolationDuration(interpolationTicks);
            display.setInterpolationDelay(0);
            display.setTransformation(transform);
        }
    }

    void dismount(UUID id) {
        Trapped state = trapped.get(id);
        if (state != null && state.anchor != null) {
            removeAnchor(state.anchor);
            state.anchor = null;
        }
    }

    void forget(UUID id) {
        release(id);
    }

    private void release(UUID id) {
        Trapped state = trapped.remove(id);
        if (state != null && state.anchor != null) removeAnchor(state.anchor);
    }

    private static void removeAnchor(UUID anchorId) {
        Entity entity = Bukkit.getEntity(anchorId);
        if (entity != null) {
            entity.eject();
            entity.remove();
        }
    }

    private static void removeEntity(UUID id) {
        if (id == null) return;
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    void close() {
        for (Trapped state : trapped.values()) {
            if (state.anchor != null) removeAnchor(state.anchor);
        }
        trapped.clear();
        whipStartTick = -1;
        tugResetTick = -1;
        for (UUID id : staticParts) removeEntity(id);
        staticParts.clear();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            removeEntity(cordDisplays[i]);
            cordDisplays[i] = null;
            removeEntity(knotDisplays[i]);
            knotDisplays[i] = null;
        }
        removeEntity(leftStrapDisplay);
        leftStrapDisplay = null;
        removeEntity(rightStrapDisplay);
        rightStrapDisplay = null;
        removeEntity(handleBarDisplay);
        handleBarDisplay = null;
        removeEntity(customHitbox);
        customHitbox = null;
        for (var entry : hiddenLegacy.entrySet()) {
            if (Bukkit.getEntity(entry.getKey()) instanceof BlockDisplay display
                    && display.isValid()) {
                display.setInterpolationDuration(0);
                display.setInterpolationDelay(0);
                display.setTransformation(entry.getValue());
            }
        }
        hiddenLegacy.clear();
        built = false;
    }
}
