package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza.Side;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointDeck;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointFit;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointRigState;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointRigState.Phase;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointShape;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** One team's hydraulic slider. All animation deadlines use the area's match clock. */
final class JointRig {
    static final NamespacedKey KEY = new NamespacedKey("championshipscore", "joint_rig");

    record Pass(String player, int round, long duration) {}

    final JointRigState state = new JointRigState();
    final JointTool tools;
    private final World world;
    private final Vector origin;
    private final JointDeck deck;
    private final JointWork work;
    private final String tag;
    private static final int FORWARD_TICKS = 6, RETURN_TICKS = 4;
    private static final int BLOCKED_HOLD = 20, CLOSED_HOLD = 30;

    private enum Motion {
        APPROACH,
        HOLD,
        RETURN
    }

    private final JointPiston piston;
    private final IntSupplier clock;
    private TextDisplay title, status, buttonLabel;
    private TextDisplay track;
    private String trackText;
    private BooleanSupplier live, present;
    private Predicate<Player> participant;
    private Runnable eject, complete;
    private Consumer<String> feedback;
    private Consumer<Pass> passed;
    private String statusText;
    private UUID operator;
    private Motion motion = Motion.APPROACH;
    private boolean judged;
    private JointFit.Result result;
    private Pass attempt;
    private long token, revision;
    private int round, column, deadline = -1, passedRounds;
    private boolean queued, suspended, retired;

    JointRig(
            World world,
            Vector origin,
            JointDeck deck,
            JointWork work,
            RobotProps props,
            IntSupplier clock) {
        piston = new JointPiston(world, origin);
        this.clock = clock;
        this.tools = new JointTool(props);
        this.world = world;
        this.origin = origin.clone();
        this.deck = deck;
        this.work = work;
        tag = origin.getBlockX() + "/" + origin.getBlockZ();
    }

    void bind(
            BooleanSupplier live,
            BooleanSupplier present,
            Predicate<Player> participant,
            Runnable eject,
            Consumer<String> feedback,
            Consumer<Pass> passed,
            Runnable complete) {
        this.live = live;
        this.present = present;
        this.participant = participant;
        this.eject = eject;
        this.feedback = feedback;
        this.passed = passed;
        this.complete = complete;
    }

    private Block block(int x, int y, int z) {
        return world.getBlockAt(
                origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
    }

    private Location point(double x, double y, double z) {
        return origin.clone().add(new Vector(x, y, z)).toLocation(world);
    }

    private void resetLoads(Deque<Runnable> steps, int[][] grayCut) {
        JointPlaza.forBlock(
                Side.WEST,
                (x, y, z) -> {
                    boolean excavated =
                            grayCut != null
                                    && JointPlaza.GRAY_MAX_X + 1 - x
                                            <= grayCut[y - JointPlaza.BLOCK_MIN_Y][
                                                    z - JointPlaza.BLOCK_MIN_Z];
                    Material type =
                            excavated ? Material.AIR : JointPlaza.loadMaterial(Side.WEST, x);
                    steps.add(() -> setBlock(x, y, z, type));
                });
        JointPlaza.forBlock(
                Side.EAST,
                (x, y, z) ->
                        steps.add(() -> setBlock(x, y, z, JointPlaza.loadMaterial(Side.EAST, x))));
    }

    /** Block operations share the joint rigs’ per-tick budget. */
    private void queue(Deque<Runnable> steps, BooleanSupplier valid, Runnable done) {
        long epoch = state.epoch();
        long expectedRevision = revision;
        Phase phase = state.phase();
        int expectedRound = state.round();
        BooleanSupplier current =
                () ->
                        revision == expectedRevision
                                && !suspended
                                && valid.getAsBoolean()
                                && state.current(epoch, phase, expectedRound);
        queued = true;
        work.add(
                current,
                () -> {
                    if (!current.getAsBoolean()) return true;
                    if (phase != Phase.DORMANT && !eligible()) {
                        suspend();
                        return true;
                    }
                    if (!steps.isEmpty()) steps.removeFirst().run();
                    if (!steps.isEmpty()) return false;
                    queued = false;
                    done.run();
                    return true;
                },
                1);
    }

    private void setBlock(int x, int y, int z, Material type) {
        Block target = block(x, y, z);
        if (target.getType() != type) target.setType(type, false);
    }

    void prepare(BooleanSupplier preparing) {
        clear();
        long epoch = state.epoch();
        var steps = new ArrayDeque<Runnable>();
        resetLoads(steps, null);
        steps.add(this::lamps);
        steps.add(() -> panel(false));
        queue(
                steps,
                () -> preparing.getAsBoolean() && state.current(epoch, Phase.DORMANT, 1),
                () -> setStatus("joint-idle"));
    }

    boolean prepared() {
        return state.phase() == Phase.DORMANT && !queued;
    }

    void open() {
        if (!live.getAsBoolean() || !present.getAsBoolean()) return;
        if (suspended) {
            suspended = false;
            if (state.phase() == Phase.CARVING) carve();
            else setStatus("joint-round", state.round());
        } else if (state.open()) carve();
    }

    void operatorLeft(UUID id) {
        if (id.equals(operator)) suspend();
    }

    boolean suspended() {
        return suspended;
    }

    private boolean current(Phase phase) {
        return !suspended && live.getAsBoolean() && state.current(token, phase, round);
    }

    private void carve() {
        token = state.epoch();
        round = state.round();
        column = 0;
        deadline = -1;
        eject.run();
        if (!current(Phase.CARVING)) return;
        setStatus("joint-carving");
        var steps = new ArrayDeque<Runnable>();
        resetLoads(steps, deck.initialGrayCut(round));
        queue(steps, () -> current(Phase.CARVING), () -> deadline = -1);
    }

    private JointShape shape() {
        return deck.shape(round);
    }

    private boolean eligible() {
        if (operator == null) return present.getAsBoolean();
        Player player = Bukkit.getPlayer(operator);
        return player != null && participant.test(player);
    }

    void tick(int tick) {
        Phase phase = state.phase();
        if (phase == Phase.DORMANT || phase == Phase.COMPLETE || !current(phase)) return;
        if (!eligible()) {
            suspend();
            return;
        }
        if (phase == Phase.CARVING || phase == Phase.TESTING) {
            eject.run();
            if (!current(phase)) return;
        }
        if (!queued && phase == Phase.CARVING && tick >= deadline) {
            if (column == JointShape.MAX_DEPTH) {
                state.ready(token, tick);
                if (state.pressDemo()) startPress(null, tick);
                else setStatus("joint-round", round);
            } else {
                int x = JointPlaza.WHITE_MIN_X + column++;
                var steps = new ArrayDeque<Runnable>();
                for (int r = 0; r < JointShape.SIZE; r++) {
                    for (int c = 0; c < JointShape.SIZE; c++) {
                        if (JointPlaza.WHITE_BASE_X - x <= shape().depth(r, c)) continue;
                        int y = r + JointPlaza.BLOCK_MIN_Y, z = c + JointPlaza.BLOCK_MIN_Z;
                        steps.add(
                                () -> {
                                    setBlock(x, y, z, Material.AIR);
                                    world.spawnParticle(
                                            Particle.BLOCK,
                                            point(x + .5, y + .5, z + .5),
                                            3,
                                            .2,
                                            .2,
                                            .2,
                                            Material.WHITE_CONCRETE.createBlockData());
                                });
                    }
                }
                sound(Sound.BLOCK_GRINDSTONE_USE, .7f);
                queue(steps, () -> current(Phase.CARVING), () -> deadline = clock.getAsInt() + 3);
            }
        } else if (!queued && phase == Phase.TESTING && tick >= deadline) {
            testing(tick);
        }
        maintain();
    }

    boolean press(Player player, Block target, int tick) {
        if (!valid(player, target)
                || !JointPlaza.button(
                        target.getX() - origin.getBlockX(),
                        target.getY() - origin.getBlockY(),
                        target.getZ() - origin.getBlockZ())) return false;
        if (!state.press()) {
            if (state.phase() == Phase.CARVING || state.phase() == Phase.TESTING)
                player.sendActionBar(LegacyText.component(RobotArea.text("joint-locked")));
            return true;
        }
        startPress(player, tick);
        return true;
    }

    private void startPress(Player player, int tick) {
        token = state.epoch();
        round = state.round();
        operator = player == null ? null : player.getUniqueId();
        attempt = player == null ? null : new Pass(player.getName(), round, state.duration(tick));
        setStatus(state.demo() ? "joint-demo" : "joint-testing");
        panel(true);
        judged = false;
        result = null;
        motion = Motion.APPROACH;
        eject.run();
        if (!current(Phase.TESTING)) return;
        var solid = new boolean[JointShape.SIZE][JointShape.SIZE][JointShape.MAX_DEPTH];
        for (int row = 0; row < JointShape.SIZE; row++)
            for (int col = 0; col < JointShape.SIZE; col++)
                for (int d = 0; d < JointShape.MAX_DEPTH; d++)
                    solid[row][col][d] =
                            !block(
                                            JointPlaza.GRAY_MAX_X - d,
                                            JointPlaza.BLOCK_MIN_Y + row,
                                            JointPlaza.BLOCK_MIN_Z + col)
                                    .getType()
                                    .isAir();
        result = JointFit.evaluate(shape(), solid, JointPlaza.STROKE);
        deadline = tick;
    }

    private void atomic(Runnable action) {
        long epoch = token, expectedRevision = revision;
        BooleanSupplier currentAttempt =
                () ->
                        revision == expectedRevision
                                && state.epoch() == epoch
                                && current(Phase.TESTING);
        queued = true;
        work.add(
                currentAttempt,
                () -> {
                    queued = false;
                    if (!eligible()) {
                        suspend();
                        return true;
                    }
                    eject.run();
                    if (currentAttempt.getAsBoolean()) {
                        if (eligible()) action.run();
                        else suspend();
                    }
                    return true;
                },
                JointWork.BUDGET);
    }

    private void testing(int tick) {
        if (motion == Motion.APPROACH && piston.extension() < result.distance()) {
            atomic(() -> step(true));
        } else if (motion == Motion.APPROACH) {
            judged = true;
            boolean blocked = result.outcome() == JointFit.Outcome.BLOCKED;
            if (result.outcome() == JointFit.Outcome.PASS) {
                if (!state.demo()) {
                    passedRounds = round;
                    lamps();
                    setStatus("joint-pass");
                    passed.accept(attempt);
                }
                sound(Sound.ENTITY_PLAYER_LEVELUP, 1.25f);
            } else {
                if (!state.demo()) {
                    if (blocked) setStatus("joint-blocked", result.distance(), JointPlaza.STROKE);
                    else setStatus("joint-gap", result.gaps());
                    feedback.accept(statusText);
                }
                sound(Sound.BLOCK_ANVIL_LAND, .6f);
            }
            motion = Motion.HOLD;
            deadline = tick + (blocked ? BLOCKED_HOLD : CLOSED_HOLD);
        } else if (piston.extension() > 0) {
            motion = Motion.RETURN;
            atomic(() -> step(false));
        } else restore();
    }

    private void step(boolean forward) {
        try {
            piston.step(shape(), forward);
            sound(forward ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT, .65f);
            deadline = clock.getAsInt() + (forward ? FORWARD_TICKS : RETURN_TICKS);
        } catch (RuntimeException failure) {
            Bukkit.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Cannot move verification slider " + tag,
                            failure);
            if (!judged)
                result = new JointFit.Result(JointFit.Outcome.BLOCKED, piston.extension(), 0);
            feedback.accept(RobotArea.text("joint-reset"));
            restore();
        }
    }

    private void restore() {
        piston.reset();
        panel(false);
        if (state.demo())
            feedback.accept(
                    RobotArea.text("joint-demo-done", result.distance(), JointPlaza.STROKE));
        state.finish(token, result.outcome(), clock.getAsInt());
        operator = null;
        judged = false;
        if (state.phase() == Phase.CARVING) carve();
        else if (state.phase() == Phase.COMPLETE) {
            setStatus("joint-pass");
            complete.run();
        } else setStatus("joint-round", round);
    }

    private boolean valid(Player player, Block target) {
        return target != null
                && !suspended
                && live.getAsBoolean()
                && participant.test(player)
                && target.getWorld() == world
                && player.getEyeLocation().distanceSquared(target.getLocation().add(.5, .5, .5))
                        <= 7.5 * 7.5;
    }

    private boolean editable(Player player, Block target) {
        return valid(player, target)
                && state.canEdit()
                && JointPlaza.inDigZone(
                        target.getX() - origin.getBlockX(),
                        target.getY() - origin.getBlockY(),
                        target.getZ() - origin.getBlockZ());
    }

    boolean canDig(Player player, Block target) {
        return tools.tool(player.getInventory().getItemInMainHand())
                && editable(player, target)
                && target.getType() == Material.GRAY_CONCRETE;
    }

    boolean fill(Player player, Block clicked, BlockFace face) {
        if (!valid(player, clicked) || !tools.block(player.getInventory().getItemInMainHand()))
            return false;
        Block target = clicked.getRelative(face);
        if (!editable(player, target) || !target.getType().isAir()) return false;
        BoundingBox cell = BoundingBox.of(target);
        for (Player other : world.getPlayers())
            if (participant.test(other) && other.getBoundingBox().overlaps(cell)) return false;
        target.setType(Material.GRAY_CONCRETE, false);
        player.playSound(target.getLocation(), Sound.BLOCK_STONE_PLACE, .7f, 1);
        return true;
    }

    private void tag(Display display) {
        display.setPersistent(false);
        display.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, tag);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(1.5f);
    }

    private TextDisplay label(JointPlaza.Face anchor, String text) {
        Location location = point(anchor.x(), anchor.y() - anchor.height() / 2, anchor.z());
        location.setYaw(anchor.yaw());
        return world.spawn(
                location,
                TextDisplay.class,
                display -> {
                    tag(display);
                    display.setSeeThrough(false);
                    display.setBillboard(Display.Billboard.FIXED);
                    display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    display.setLineWidth(1000);
                    labelText(display, anchor, text);
                });
    }

    private static void labelText(TextDisplay display, JointPlaza.Face face, String text) {
        var component = LegacyText.component(text);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        // A single text line is 10 font pixels at 1/40 block per pixel. Fit long feedback
        // horizontally into its allocated face, allowing ten pixels per bold CJK glyph.
        int glyphs = Math.max(1, plain.codePointCount(0, plain.length()));
        float height = (float) (face.height() * 4);
        float width = (float) (face.width() * 4 / glyphs);
        display.setTransformation(transform(0, width, height, 1));
        display.text(component);
    }

    private static Transformation transform(double shift, float x, float y, float z) {
        return new Transformation(
                new Vector3f((float) shift, 0, 0),
                new Quaternionf(),
                new Vector3f(x, y, z),
                new Quaternionf());
    }

    void retire() {
        retired = true;
        removeDisplays();
    }

    private void maintain() {
        if (retired || suspended || !live.getAsBoolean() || state.phase() == Phase.DORMANT) return;
        if (title == null || !title.isValid())
            title = label(JointPlaza.TITLE, RobotArea.text("joint-title"));
        if (status == null || !status.isValid()) status = label(JointPlaza.STATUS, statusText);
        if (buttonLabel == null || !buttonLabel.isValid())
            buttonLabel = label(JointPlaza.BUTTON_LABEL, RobotArea.text("joint-button"));
        int distance = piston.extension();
        String color = !judged ? "&b" : result.outcome() == JointFit.Outcome.PASS ? "&a" : "&c";
        String bar = color + "■".repeat(distance) + "&8" + "■".repeat(JointPlaza.STROKE - distance);
        if (track == null || !track.isValid()) track = label(JointPlaza.TRACK, bar);
        else if (!bar.equals(trackText)) labelText(track, JointPlaza.TRACK, bar);
        trackText = bar;
    }

    private void setStatus(String key, Object... args) {
        statusText = RobotArea.text(key, args);
        if (status != null && status.isValid()) labelText(status, JointPlaza.STATUS, statusText);
    }

    private void lamps() {
        for (int i = 0; i < JointPlaza.ROUND_LAMPS.size(); i++) {
            Material color = i < passedRounds ? JointPlaza.LAMP_ON : JointPlaza.LAMP_OFF;
            for (var cell : JointPlaza.ROUND_LAMPS.get(i))
                setBlock(cell.x(), cell.y(), cell.z(), color);
        }
    }

    private void panel(boolean pressed) {
        for (var cell : JointPlaza.BUTTON_CELLS)
            setBlock(
                    cell.x(),
                    cell.y(),
                    cell.z(),
                    pressed ? JointPlaza.BUTTON_PRESSED : JointPlaza.BUTTON_IDLE);
    }

    private void sound(Sound sound, float pitch) {
        var anchor = JointPlaza.BUTTON_LABEL;
        Location at = point(anchor.x(), anchor.y(), anchor.z());
        for (Player player : at.getNearbyPlayers(40))
            if (participant.test(player))
                player.playSound(at, sound, SoundCategory.BLOCKS, .7f, pitch);
    }

    /** Synchronous safety rollback also invalidates every queued block mutation. */
    private void clear() {
        revision++;
        state.reset();
        suspended = false;
        queued = false;
        deadline = -1;
        passedRounds = 0;
        result = null;
        operator = null;
        judged = retired = false;
        piston.reset();
        removeDisplays();
    }

    private void removeDisplays() {
        for (Entity entity : world.getNearbyEntities(JointPlaza.displayBounds().shift(origin)))
            if (tag.equals(entity.getPersistentDataContainer().get(KEY, PersistentDataType.STRING)))
                entity.remove();
        title = status = buttonLabel = null;
        track = null;
        trackText = null;
    }

    void interrupt() {
        clear();
        var steps = new ArrayDeque<Runnable>();
        resetLoads(steps, null);
        steps.forEach(Runnable::run);
        lamps();
        panel(false);
    }

    /** Preserve committed rounds and excavation when the entire team is offline. */
    void suspend() {
        if (suspended
                || !live.getAsBoolean()
                || state.phase() == Phase.DORMANT
                || state.phase() == Phase.COMPLETE) return;
        revision++;
        queued = false;
        piston.reset();
        panel(false);
        removeDisplays();
        if (state.phase() == Phase.TESTING)
            state.finish(
                    token, judged ? result.outcome() : JointFit.Outcome.BLOCKED, clock.getAsInt());
        operator = null;
        judged = false;
        if (state.phase() == Phase.COMPLETE) {
            setStatus("joint-pass");
            complete.run();
        } else suspended = true;
    }
}
