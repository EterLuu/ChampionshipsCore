package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.*;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles.Profile;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

final class SliderStation {
    static final NamespacedKey KEY = new NamespacedKey("championshipscore", "slider");
    final SliderHall hall;
    final SliderStationState state = new SliderStationState();
    private final World world;
    private final Vector origin;
    private final SliderDeck deck;
    private final SliderScreen screen;
    private TextDisplay loading;
    private int healAt;
    private boolean opened;
    private BooleanSupplier eligible;
    private Predicate<Player> holdsRail;
    private SliderPuzzle puzzle;
    private TextDisplay subtitle, feedback, nameplate;
    private String ownerName;
    private int failureTick = -1, overlayUntil = -1;
    private final RobotProps props;

    SliderStation(
            Plugin plugin,
            World world,
            Vector origin,
            SliderHall hall,
            SliderDeck deck,
            SliderMaps maps,
            SliderMaps.Edition edition,
            SliderAudience sweep,
            RobotProps props) {
        this.props = props;
        this.world = world;
        this.origin = origin;
        this.hall = hall;
        this.deck = deck;
        String tag = origin.getBlockX() + "/" + hall.name();
        screen = new SliderScreen(plugin, world, origin, hall, tag, maps, edition, sweep);
    }

    private TextDisplay label(double x, double y, double depth, float size) {
        var loc = screen.point(x, y, depth);
        loc.setYaw(hall.step == 1 ? 0 : 180);
        var display = world.createEntity(loc, TextDisplay.class);
        screen.tag(display);
        display.setBillboard(Display.Billboard.FIXED);
        display.setSeeThrough(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setLineWidth(500);
        display.setTransformation(
                new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(size), new Quaternionf()));
        return world.addEntity(display);
    }

    String ownerName() {
        return ownerName;
    }

    void assign(String name) {
        ownerName = name;
        updateNameplate(false);
    }

    String color() {
        return switch (hall) {
            case A -> "&9";
            case B -> "&6";
            case C -> "&a";
            case D -> "&d";
        };
    }

    void updateNameplate(boolean done) {
        if (nameplate == null) return;
        String key =
                ownerName == null ? "slider-name-free" : done ? "slider-name-done" : "slider-name";
        text(nameplate, key, color(), hall.name(), ownerName == null ? "" : ownerName);
    }

    private void text(TextDisplay display, String key, Object... args) {
        display.text(LegacyText.component(RobotArea.text(key, args)));
    }

    void prebuild(BooleanSupplier eligible) {
        this.eligible = eligible;
        screen.sweepOrphans(eligible);
        screen.frames(state.pictureRound(), eligible);
    }

    void open(BooleanSupplier eligible, Predicate<Player> holdsRail) {
        if (this.eligible == null) screen.sweepOrphans(eligible);
        this.eligible = eligible;
        this.holdsRail = holdsRail;
        opened = true;
        screen.urgent = true;
        screen.frames(state.pictureRound(), this.eligible);
        if (!screen.showing(state.pictureRound())) {
            loading = label(8, 4, .32, 2f);
            text(loading, "slider-loading");
        }
        var title = label(8, -2.5, .08, 2.3f);
        subtitle = label(8, -.45, .08, 1.6f);
        text(title, "slider-title");
        text(subtitle, "slider-idle");

        Vector outside = hall.outside();
        Location anchor = outside.clone().add(origin).toLocation(world).add(0, 2.6, 0);
        anchor.setDirection(new Vector(9.5 - outside.getX(), 0, SliderHall.MIN_Z - outside.getZ()));
        nameplate =
                world.spawn(
                        anchor,
                        TextDisplay.class,
                        display -> {
                            screen.tag(display);
                            display.setBillboard(Display.Billboard.FIXED);
                            display.setShadowed(true);
                            display.setTransformation(SliderScreen.scale(1.6, 1.6, 1.6));
                        });
        updateNameplate(false);
    }

    private void status(TextDisplay display, String key) {
        MessageConfig.worldTextStyle(Profile.ROBOT_SLIDER_STATUS).apply(display);
        text(display, key);
    }

    private void showStatus(String key) {
        if (feedback == null) feedback = label(8, 4, .34, 3.4f);
        status(feedback, key);
        overlayUntil = -1;
    }

    private void removeFeedback() {
        if (feedback != null) feedback.remove();
        feedback = null;
        overlayUntil = -1;
    }

    void maintain(int tick) {
        if (eligible == null) return;
        if (opened && loading != null && screen.showing(state.pictureRound())) {
            loading.remove();
            loading = null;
        }
        if (tick >= healAt || (opened && loading != null)) {
            screen.frames(state.pictureRound(), eligible);
            healAt = tick + 20;
        }
    }

    private void drawHeld(Player holder) {
        puzzle = deck.puzzle(hall, state.round(), state.attempt());
        screen.held(
                holder,
                puzzle,
                eligible,
                () -> holder.getUniqueId().equals(state.holder()) && holdsRail.test(holder));
        text(subtitle, "slider-held", holder.getName(), state.round());
    }

    static boolean handle(ItemStack item) {
        return RobotProps.kind(item) == RobotProps.Kind.SLIDER_HANDLE;
    }

    private void equip(Player player) {
        var item = new ItemStack(Material.TRIPWIRE_HOOK);
        item.editMeta(m -> m.displayName(LegacyText.component(RobotArea.text("slider-handle"))));
        props.give(player, RobotProps.Kind.SLIDER_HANDLE, item, RobotProps.SlotPolicy.HELD);
    }

    private void reclaim(Player player) {
        if (player != null) props.recover(player, RobotProps.Kind.SLIDER_HANDLE);
    }

    private void teleport(Player p, Vector rel, float yaw) {
        var loc = rel.clone().add(origin).toLocation(world);
        loc.setYaw(yaw);
        p.teleport(loc);
        p.setFallDistance(0);
    }

    int column(Player p) {
        var loc = p.getLocation();
        return hall.holdingColumn(
                loc.getBlockX() - origin.getBlockX(),
                loc.getBlockY() - origin.getBlockY(),
                loc.getBlockZ() - origin.getBlockZ());
    }

    private void resetPosition(Player p) {
        teleport(p, hall.start(), hall.run == 1 ? -90 : 90);
    }

    private void action(Player p, String key, Object... args) {
        p.sendActionBar(LegacyText.component(RobotArea.text(key, args)));
    }

    void sample(Player p, int tick, boolean solo, boolean done, SliderHall assigned) {
        UUID id = p.getUniqueId();
        int col = column(p);
        if (col >= 0 && hall != assigned) {
            teleport(p, hall.outside(), hall.step == 1 ? 180 : 0);
            String hint =
                    ownerName == null
                            ? RobotArea.text("slider-unassigned", hall.name(), assigned)
                            : RobotArea.text("slider-other", ownerName, hall.name(), assigned);
            if (state.intruder(id))
                hint = RobotArea.text("slider-occupied", ownerName) + " · " + hint;
            p.sendActionBar(LegacyText.component(hint));
            return;
        }
        if (state.intruder(id)) {
            var owner = Bukkit.getPlayer(state.holder());
            if (owner == null) return;
            teleport(p, hall.outside(), hall.step == 1 ? 180 : 0);
            action(p, "slider-occupied", owner.getName());
            return;
        }
        if (col < 0) return;
        if (done && !id.equals(state.holder())) {
            teleport(p, hall.outside(), hall.step == 1 ? 180 : 0);
            action(p, "slider-self-done");
            return;
        }
        if (state.grip(id, tick)) {
            if (col != 0) resetPosition(p);
            equip(p);
            drawHeld(p);
        } else if (state.move(id, col, tick)) {
            screen.move(col, 2);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .28f, 1.8f);
        }
        if (solo && id.equals(state.holder()) && !state.locked(tick)) {
            int delta = puzzle.target().column() - state.column();
            action(
                    p,
                    delta == 0 ? "slider-here" : delta > 0 ? "slider-ahead" : "slider-behind",
                    Math.abs(delta));
        }
    }

    /** A failed piece nudges forward; at the far end it nudges back so it stays on screen. */
    static double reboundColumn(int column) {
        return column == SliderHall.LAST ? column - .35 : column + .35;
    }

    boolean submit(Player p, int tick) {
        if (puzzle == null) return false;
        int col = column(p);
        switch (state.submit(p.getUniqueId(), col, puzzle.target().column(), tick)) {
            case LOCKED -> {
                if (state.waiting()) action(p, "slider-waiting");
                else if (state.aligning()) action(p, "slider-locked");
            }
            case FAIL -> {
                failureTick = tick;
                screen.move(reboundColumn(col), 1);
                resetPosition(p);
                removeFeedback();
                feedback = label(8, 4, .34, 3.4f);
                text(feedback, "slider-fail");
                overlayUntil = tick + 40;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .85f, .55f);
            }
            case ALIGNED -> {
                screen.move(col, 2);
                subtitle.text(Component.empty());
                return true;
            }
            default -> {}
        }
        return false;
    }

    boolean confirm(SliderStation opposite, int tick) {
        if (!state.confirm(opposite == null ? null : opposite.state, tick)) {
            if (state.waiting()) showStatus("slider-waiting");
            return false;
        }
        screen.frames(state.pictureRound(), eligible);
        showStatus("slider-pass");
        var id = state.holder();
        var holder = id == null ? null : Bukkit.getPlayer(id);
        if (holder != null && holdsRail.test(holder))
            holder.playSound(holder.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .7f, 1.35f);
        return true;
    }

    // Called only from the owning match clock, after revalidating the holder's connection,
    // eligibility and actual holding cell. No per-entity or per-station scheduled tasks.
    void tick(int tick, Player holder, boolean release) {
        if (failureTick >= 0) {
            int age = tick - failureTick;
            if (age >= 6) {
                failureTick = -1;
                if (holder != null) drawHeld(holder);
            } else if (age >= 2 && holder != null) screen.move(0, Math.max(1, 6 - age));
        }
        if (overlayUntil >= 0 && tick >= overlayUntil) removeFeedback();
        if (!state.advance(tick, release)) return;
        removeFeedback();
        screen.clearPuzzle();
        screen.frames(state.pictureRound(), eligible);
        if (state.complete()) {
            if (holder != null) reclaim(holder);
            screen.completed();
            status(subtitle, "slider-pass");
            showStatus("slider-check");
        } else if (holder != null && state.holder() != null) {
            resetPosition(holder);
            drawHeld(holder);
        } else {
            if (holder != null) reclaim(holder);
            text(subtitle, "slider-idle");
        }
    }

    void leave(UUID id, Player connection) {
        if (!id.equals(state.holder())) return;
        state.leave(id);
        reclaim(connection);
        screen.clearPuzzle();
        failureTick = -1;
        if (subtitle != null && state.aligning()) text(subtitle, "slider-idle");
    }

    void finishRetirement() {
        screen.finishRetirement();
    }

    void reset() {
        UUID id = state.holder();
        if (id != null) {
            state.leave(id);
            reclaim(Bukkit.getPlayer(id));
        }
        removeFeedback();
        if (nameplate != null) nameplate.remove();
        nameplate = null;
        screen.reset();
        loading = null;
        subtitle = null;
    }
}
