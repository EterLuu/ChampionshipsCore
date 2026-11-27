package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.*;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.BooleanSupplier;

/** One physical card. Only puzzle geometry is private to its holder. */
final class SliderScreen {
    private final Plugin plugin;
    private final World world;
    private final Vector origin;
    private final SliderHall hall;
    private final String tag;
    private final SliderMaps maps;
    private final SliderAudience audience;
    private final GlowItemFrame[] frames = new GlowItemFrame[SliderMapIds.TILES];
    private final List<BlockDisplay> layers = new ArrayList<>();
    private final Map<BlockDisplay, Panel> moving = new LinkedHashMap<>();
    private BlockDisplay handle, fill;
    private final SliderMaps.Edition edition;
    private final byte[] applied = new byte[SliderMapIds.TILES];
    private final int[] born = new int[SliderMapIds.TILES];
    boolean urgent;
    private int revision, targetRound, cursor;
    private boolean pending, retired;
    private final String incarnation = UUID.randomUUID().toString();
    private static final NamespacedKey INCARNATION =
            new NamespacedKey("championshipscore", "slider_incarnation");
    private final Set<Entity> orphans = new LinkedHashSet<>();
    private SliderWork.Job retirement;
    private int puzzleRevision;
    private double motionColumn;
    private double barLength;
    private static final double HANDLE_WIDTH = .9, INITIAL_FILL_WIDTH = .01;

    SliderScreen(
            Plugin plugin,
            World world,
            Vector origin,
            SliderHall hall,
            String tag,
            SliderMaps maps,
            SliderMaps.Edition edition,
            SliderAudience audience) {
        this.audience = audience;
        this.edition = edition;
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.hall = hall;
        this.tag = tag;
        this.maps = maps;
    }

    /** x,y are viewer-local; depth is measured into the room from the backing wall. */
    Location point(double x, double y, double depth) {
        double left = hall.mapX(0) + (hall.step < 0 ? 1 : 0);
        return new Location(
                world,
                origin.getX() + left + hall.step * x,
                origin.getY() + SliderHall.MAP_TOP + 1 - y,
                origin.getZ() + hall.wall + (hall.step == 1 ? 1 : 0) + hall.step * depth);
    }

    boolean ready() {
        return maps.ready(edition);
    }

    boolean showing(int round) {
        return ready() && !pending && targetRound == round;
    }

    void frames(int round, BooleanSupplier eligible) {
        if (retired || !ready() || (pending && targetRound == round)) return;
        if (targetRound == round) {
            boolean intact = true;
            for (var frame : frames)
                if (frame == null || !frame.isValid()) {
                    intact = false;
                    break;
                }
            if (intact) return;
        }
        int change = ++revision;
        targetRound = round;
        cursor = 0;
        pending = true;
        maps.work.expose(
                new SliderSweep.Card(
                        () -> !retired && change == revision && eligible.getAsBoolean(),
                        () -> urgent,
                        this::nextCost,
                        () -> serviceTile(round)));
    }

    private int nextCost() {
        int tile = columnFirst(cursor % frames.length);
        var frame = frames[tile];
        if (frame == null || !frame.isValid()) return SliderSweep.SPAWN;
        if (applied[tile] == 0
                && Bukkit.getCurrentTick() - born[tile] < 2
                && cursor >= frames.length) return 0;
        return SliderSweep.EQUIP;
    }

    static int columnFirst(int cursor) {
        return (cursor % SliderHall.MAP_ROWS) * SliderHall.MAP_COLUMNS
                + cursor / SliderHall.MAP_ROWS;
    }

    private boolean serviceTile(int round) {
        int tile = columnFirst(cursor % frames.length);
        var frame = frames[tile];
        if (frame == null || !frame.isValid()) {
            frames[tile] = spawnFrame(tile);
            born[tile] = Bukkit.getCurrentTick();
            applied[tile] = 0;
            if (cursor >= frames.length) return false;
        } else if (applied[tile] != round
                && (applied[tile] != 0 || Bukkit.getCurrentTick() - born[tile] >= 2)) {
            frame.setItem(maps.item(edition, hall, round, tile), false);
            applied[tile] = (byte) round;
            audience.expose(hall, round, tile);
        }
        cursor++;
        if (cursor % frames.length == 0) {
            pending = false;
            for (byte appliedRound : applied)
                if (appliedRound != round) {
                    pending = true;
                    break;
                }
        }
        return !pending;
    }

    private GlowItemFrame spawnFrame(int tile) {
        var anchor =
                point(tile % SliderHall.MAP_COLUMNS + .5, tile / SliderHall.MAP_COLUMNS + .5, .5);
        // An unregistered entity has no tracker, world presence or packets.
        var f = world.createEntity(anchor, GlowItemFrame.class);
        f.setFacingDirection(hall.step == 1 ? BlockFace.SOUTH : BlockFace.NORTH, true);
        f.setVisible(false);
        f.setFixed(true);
        f.setPersistent(false);
        f.setItemDropChance(0);
        identify(f);

        // Paper tests tickCount % cursorInterval == 0 on the very first tracker tick.
        return world.addEntity(f);
    }

    static Transformation scale(double w, double h, double d) {
        return new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f((float) w, (float) h, (float) d),
                new Quaternionf());
    }

    private void identify(Entity e) {
        var pdc = e.getPersistentDataContainer();
        pdc.set(SliderStation.KEY, PersistentDataType.STRING, tag);
        pdc.set(INCARNATION, PersistentDataType.STRING, incarnation);
    }

    void tag(Display d) {
        d.setPersistent(false);
        identify(d);
        d.setViewRange(2);
        d.setBrightness(new Display.Brightness(15, 15));
    }

    private Location rectBase(double x, double y, double w, double h, double depth) {
        return point(x + (hall.step < 0 ? w : 0), y + h, depth);
    }

    private BlockDisplay rect(
            Location base, double w, double h, Material material, Player hiddenFrom) {
        return world.spawn(
                base,
                BlockDisplay.class,
                d -> {
                    tag(d);
                    d.setBlock(material.createBlockData());
                    d.setTransformation(scale(w, h, .008));
                    d.setTeleportDuration(2);
                    // CraftWorld invokes this initializer BEFORE it adds the entity to the tracker.
                    if (hiddenFrom != null) hiddenFrom.hideEntity(plugin, d);
                });
    }

    // x is a viewer-local offset within the shape. A negative column follows the holder.
    record Panel(
            int column, double x, double y, double w, double h, double depth, Material material) {
        boolean moves() {
            return column < 0;
        }
    }

    private static void layer(
            List<Panel> panels,
            List<SliderShape.Rect> rects,
            int column,
            double offsetX,
            double y,
            double depth,
            Material material) {
        for (var r : rects)
            panels.add(
                    new Panel(column, offsetX + r.x(), y + r.y(), r.w(), r.h(), depth, material));
    }

    static List<Panel> panels(SliderPuzzle puzzle) {
        var panels = new ArrayList<Panel>();
        double y = SliderHall.coreTop(puzzle.row());
        for (var n : puzzle.notches()) {
            layer(panels, n.shape().fill, n.column(), 0, y, .17, Material.BLACK_STAINED_GLASS);
            layer(panels, n.shape().outline, n.column(), 0, y, .19, Material.WHITE_CONCRETE);
        }
        var piece = puzzle.target().shape();
        layer(panels, piece.fill, -1, .088, y + .104, .22, Material.BLACK_STAINED_GLASS);
        layer(panels, piece.fill, -1, 0, y, .25, Material.WHITE_CONCRETE);
        layer(panels, piece.pieceOutline, -1, 0, y, .27, Material.LIGHT_BLUE_CONCRETE);
        return panels;
    }

    Location panelBase(Panel panel, double column) {
        double slideX = SliderHall.coreLeft(panel.moves() ? column : panel.column);
        double x = hall.viewerLeft(slideX, SliderHall.CORE_SIZE) + panel.x;
        return rectBase(x, panel.y, panel.w, panel.h, panel.depth);
    }

    void held(
            Player holder,
            SliderPuzzle puzzle,
            BooleanSupplier eligible,
            BooleanSupplier holdsRail) {
        clearPuzzle();
        var todo = panels(puzzle).iterator();
        int change = puzzleRevision;
        maps.work.add(
                () -> !retired && puzzleRevision == change && eligible.getAsBoolean(),
                () -> {
                    if (!holdsRail.getAsBoolean()) return false;
                    var panel = todo.next();
                    var display =
                            rect(
                                    panelBase(panel, motionColumn),
                                    panel.w,
                                    panel.h,
                                    panel.material,
                                    holder);
                    layers.add(display);
                    if (panel.moves()) moving.put(display, panel);
                    return !todo.hasNext();
                },
                8,
                false);
        handle = rect(handleBase(0), HANDLE_WIDTH, .76, Material.BLUE_CONCRETE, null);
        fill =
                rect(
                        fillBase(INITIAL_FILL_WIDTH),
                        INITIAL_FILL_WIDTH,
                        .40,
                        Material.LIGHT_BLUE_CONCRETE,
                        null);
        barLength = 0;
        move(0, 0);
    }

    private static double handleTravel(double col) {
        return col * (SliderHall.MAP_COLUMNS - HANDLE_WIDTH) / SliderHall.LAST;
    }

    Location handleBase(double col) {
        return rectBase(
                hall.viewerLeft(handleTravel(col), HANDLE_WIDTH),
                SliderHall.MAP_ROWS + .12,
                HANDLE_WIDTH,
                .76,
                .12);
    }

    Location fillBase(double length) {
        return rectBase(hall.viewerLeft(0, length), SliderHall.MAP_ROWS + .30, length, .40, .08);
    }

    static double fillLength(double col) {
        return handleTravel(col) + HANDLE_WIDTH / 2;
    }

    Transformation fillTransform(double length) {
        var transform = scale(length, .4, .008);
        // BlockDisplay boxes grow in positive world X. Anchor the other edge on westbound rails.
        if (hall.run < 0) transform.getTranslation().x = (float) (INITIAL_FILL_WIDTH - length);
        return transform;
    }

    void move(double col, int ticks) {
        motionColumn = col;
        moving.forEach(
                (entity, panel) -> {
                    entity.setTeleportDuration(ticks);
                    entity.teleport(panelBase(panel, col));
                });
        if (handle == null) return;
        handle.setTeleportDuration(ticks);
        handle.teleport(handleBase(col));
        double length = fillLength(col);
        if (length != barLength) {
            fill.setInterpolationDelay(0);
            fill.setInterpolationDuration(ticks);
            fill.setTransformation(fillTransform(length));
            barLength = length;
        }
    }

    void completed() {
        clearPuzzle();
        fill =
                rect(
                        fillBase(SliderHall.MAP_COLUMNS),
                        SliderHall.MAP_COLUMNS,
                        .40,
                        Material.LIME_CONCRETE,
                        null);
    }

    void clearPuzzle() {
        puzzleRevision++;
        layers.forEach(Entity::remove);
        layers.clear();
        moving.clear();
        if (handle != null) handle.remove();
        if (fill != null) fill.remove();
        handle = null;
        fill = null;
    }

    private Collection<Entity> taggedEntities() {
        return world.getNearbyEntities(
                point(8, 4, .3),
                10,
                11,
                2,
                e ->
                        (e instanceof Display || e instanceof GlowItemFrame)
                                && tag.equals(
                                        e.getPersistentDataContainer()
                                                .get(
                                                        SliderStation.KEY,
                                                        PersistentDataType.STRING)));
    }

    void sweepOrphans(BooleanSupplier eligible) {
        // This snapshot precedes the first entity of this generation. A cancelled sweep
        // hands its remaining entities to reset without ever searching the replacement lane.
        orphans.addAll(taggedEntities());
        if (orphans.isEmpty()) return;
        var old = orphans.iterator();
        maps.work.add(
                () -> !retired && eligible.getAsBoolean(),
                () -> {
                    var e = old.next();
                    removeTagged(e);
                    old.remove();
                    return !old.hasNext();
                },
                32,
                true);
    }

    void reset() {
        if (retired) return;
        retired = true;
        pending = false;
        revision++;
        puzzleRevision++;
        var owned = new LinkedHashSet<>(orphans);
        orphans.clear();
        // A late reset must not collect entities already admitted to this location by a new match.
        for (var e : taggedEntities())
            if (incarnation.equals(
                    e.getPersistentDataContainer().get(INCARNATION, PersistentDataType.STRING)))
                owned.add(e);
        for (var frame : frames) if (frame != null) owned.add(frame);
        owned.addAll(layers);
        if (handle != null) owned.add(handle);
        if (fill != null) owned.add(fill);
        layers.clear();
        moving.clear();
        handle = null;
        fill = null;
        Arrays.fill(frames, null);
        if (owned.isEmpty()) return;
        // The retiring room owns this screen instance even after it leaves the live lane list.
        // Drain only its frozen entity handles; a fresh generation gets a different incarnation.
        var old = owned.iterator();
        int generation = revision;
        retirement =
                new SliderWork.Job(
                        () -> retired && revision == generation, () -> retireTile(old), 32, true);
        maps.work.add(retirement);
    }

    void finishRetirement() {
        maps.work.finishRemoval(retirement);
        retirement = null;
    }

    private void removeTagged(Entity e) {
        if (e.isValid()
                && e.getWorld() == world
                && tag.equals(
                        e.getPersistentDataContainer()
                                .get(SliderStation.KEY, PersistentDataType.STRING))) e.remove();
    }

    private boolean retireTile(Iterator<Entity> old) {
        removeTagged(old.next());
        old.remove();
        return !old.hasNext();
    }
}
