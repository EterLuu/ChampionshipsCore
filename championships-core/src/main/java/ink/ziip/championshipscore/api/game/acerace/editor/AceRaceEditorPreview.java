package ink.ziip.championshipscore.api.game.acerace.editor;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgressPoint;
import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Supplier;

/** One editor's preview resources, scoped to the original connection and prepare session. */
public final class AceRaceEditorPreview implements AutoCloseable {
    public record Marker(Location location, int configIndex, int binding) {
        public Marker {
            location = location.clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    public record Course(
            World world, String areaName, List<Marker> markers, List<AceRaceProgressPoint> gates) {
        public Course {
            markers = List.copyOf(markers);
            gates = List.copyOf(gates);
        }
    }

    interface Access {
        Object session(Player player);

        Course course();

        EnderCrystal spawn(Course course, Marker marker, int loadedIndex);

        BukkitTask repeat(Runnable action);

        void particles(Player player, Course course);
    }

    private record OwnedMarker(EnderCrystal entity, int configIndex) {}

    private static final class Run {
        final Player viewer;
        final Object session;
        final List<OwnedMarker> markers = new ArrayList<>();
        BukkitTask task;
        long revision;

        Run(Player viewer, Object session) {
            this.viewer = viewer;
            this.session = session;
        }
    }

    private final Access access;
    private Run current;
    private boolean closed;
    private long generation;

    public AceRaceEditorPreview(
            ChampionshipsCore plugin, AceRaceArea area, Supplier<Course> course) {
        this(new BukkitAccess(plugin, area, course));
    }

    AceRaceEditorPreview(Access access) {
        this.access = access;
    }

    public boolean toggle(Player viewer) {
        if (closed) return false;
        Object session = access.session(viewer);
        if (session == null) return false;
        if (current != null && current.viewer == viewer && current.session == session) {
            stop();
            return false;
        }
        long request = ++generation;
        if (current != null) stop(current);
        if (closed || generation != request) return false;
        Run run = new Run(viewer, session);
        current = run;
        try {
            refresh(run);
            if (!valid(run)) {
                stop(run);
                return false;
            }
            BukkitTask task = access.repeat(() -> tick(run));
            if (valid(run)) run.task = task;
            else {
                task.cancel();
                stop(run);
            }
            return current == run;
        } catch (RuntimeException | Error failure) {
            fail(run, failure);
            throw failure;
        }
    }

    public boolean enabled(Object session) {
        Run run = current;
        if (run == null) return false;
        if (!valid(run)) {
            stop(run);
            return false;
        }
        return run.session == session;
    }

    public void refresh() {
        Run run = current;
        if (run != null) refresh(run);
    }

    private void refresh(Run run) {
        if (!valid(run)) {
            stop(run);
            return;
        }
        long revision = ++run.revision;
        try {
            clearMarkers(run);
            if (!valid(run) || run.revision != revision) {
                stopIfInvalid(run);
                return;
            }
            Course course = access.course();
            if (course.world() == null) return;
            for (int index = 0; index < course.markers().size(); index++) {
                if (!valid(run) || run.revision != revision) {
                    stopIfInvalid(run);
                    return;
                }
                Marker marker = course.markers().get(index);
                EnderCrystal entity = access.spawn(course, marker, index);
                // Spawning invokes Bukkit events, which may stop or replace this preview.
                if (!valid(run) || run.revision != revision) {
                    entity.remove();
                    stopIfInvalid(run);
                    return;
                }
                run.markers.add(new OwnedMarker(entity, marker.configIndex()));
            }
        } catch (RuntimeException | Error failure) {
            if (run.revision == revision) fail(run, failure);
            throw failure;
        }
    }

    private void tick(Run run) {
        if (!valid(run)) {
            stop(run);
            return;
        }
        try {
            Course course = access.course();
            if (valid(run)) access.particles(run.viewer, course);
            else stop(run);
        } catch (RuntimeException | Error failure) {
            fail(run, failure);
            throw failure;
        }
    }

    private boolean valid(Run run) {
        return !closed
                && current == run
                && access.session(run.viewer) == run.session
                && !closed
                && current == run;
    }

    private void stopIfInvalid(Run run) {
        if (!valid(run)) stop(run);
    }

    /** Protection uses actual owned entities; arbitrary PDC data cannot impersonate a marker. */
    public int markerIndex(Entity entity) {
        if (current == null) return -1;
        for (OwnedMarker marker : current.markers)
            if (marker.entity() == entity && !entity.isDead()) return marker.configIndex();
        return -1;
    }

    public int markerIndex(Player viewer, Entity entity) {
        Run run = current;
        if (run == null || run.viewer != viewer) return -1;
        if (!valid(run)) {
            stop(run);
            return -1;
        }
        return markerIndex(entity);
    }

    public void stopSession(Object session) {
        Run run = current;
        if (run != null && run.session == session) stop(run);
    }

    public void stop() {
        generation++;
        if (current != null) stop(current);
    }

    private void fail(Run run, Throwable failure) {
        try {
            stop(run);
        } catch (RuntimeException | Error cleanup) {
            if (cleanup != failure) failure.addSuppressed(cleanup);
        }
    }

    private void stop(Run run) {
        if (current == run) current = null;
        run.revision++;
        BukkitTask task = run.task;
        run.task = null;
        try {
            if (task != null) task.cancel();
        } finally {
            clearMarkers(run);
        }
    }

    private void clearMarkers(Run run) {
        List<OwnedMarker> owned = List.copyOf(run.markers);
        run.markers.clear();
        RuntimeException failure = null;
        for (OwnedMarker marker : owned) {
            try {
                if (!marker.entity().isDead()) marker.entity().remove();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    @Override
    public void close() {
        closed = true;
        stop();
    }

    private record BukkitAccess(
            ChampionshipsCore plugin,
            AceRaceArea area,
            Supplier<Course> source,
            NamespacedKey markerKey,
            NamespacedKey areaKey)
            implements Access {
        BukkitAccess(ChampionshipsCore plugin, AceRaceArea area, Supplier<Course> source) {
            this(
                    plugin,
                    area,
                    source,
                    new NamespacedKey(plugin, "acerace_preview_respawn"),
                    new NamespacedKey(plugin, "acerace_preview_area"));
        }

        public Object session(Player viewer) {
            if (!plugin.isEnabled()
                    || !viewer.hasPermission("cc.admin")
                    || area.isDisposed()
                    || area.getGameStageEnum() != GameStageEnum.WAITING
                    || Bukkit.getPlayer(viewer.getUniqueId()) != viewer) return null;
            var session = plugin.getPrepareSessionManager().getSession(viewer);
            return session != null
                            && session.getGameType() == GameTypeEnum.AceRace
                            && session.getTarget().config() == area.getGameConfig()
                            && Objects.equals(
                                    session.getAreaName(), area.getGameConfig().getAreaName())
                            && plugin.getGameManager()
                                            .getAceRaceManager()
                                            .getArea(session.getAreaName())
                                    == area
                    ? session
                    : null;
        }

        public Course course() {
            return source.get();
        }

        public BukkitTask repeat(Runnable action) {
            return Bukkit.getScheduler().runTaskTimer(plugin, action, 10L, 10L);
        }

        public EnderCrystal spawn(Course course, Marker marker, int loadedIndex) {
            return course.world()
                    .spawn(
                            marker.location(),
                            EnderCrystal.class,
                            spawned -> {
                                spawned.setShowingBottom(false);
                                spawned.setInvulnerable(true);
                                spawned.setPersistent(false);
                                String name =
                                        marker.binding() < 0
                                                ? GuiConfig.text(
                                                        "map-editor.menus.step-list.games.ace-race.items.map-preview.states.marker-start.title")
                                                : GuiConfig.text(
                                                                "map-editor.menus.step-list.games.ace-race.items.map-preview.states.marker-bound.title")
                                                        .replace(
                                                                "%order%",
                                                                String.valueOf(
                                                                        marker.binding() + 1));
                                spawned.customName(Component.text(name));
                                spawned.setCustomNameVisible(true);
                                spawned.getPersistentDataContainer()
                                        .set(areaKey, PersistentDataType.STRING, course.areaName());
                                spawned.getPersistentDataContainer()
                                        .set(markerKey, PersistentDataType.INTEGER, loadedIndex);
                            });
        }

        public void particles(Player viewer, Course course) {
            World world = course.world();
            if (world == null || viewer.getWorld() != world) return;
            for (AceRaceProgressPoint point : course.gates()) {
                Vector first = point.pos1(), second = point.pos2();
                double dx = second.getX() - first.getX(),
                        dy = second.getY() - first.getY(),
                        dz = second.getZ() - first.getZ();
                int samples =
                        Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 2D));
                for (int sample = 0; sample <= samples; sample++) {
                    double ratio = sample / (double) samples;
                    viewer.spawnParticle(
                            Particle.END_ROD,
                            new Location(
                                    world,
                                    first.getX() + dx * ratio + 0.5D,
                                    first.getY() + dy * ratio + 0.15D,
                                    first.getZ() + dz * ratio + 0.5D),
                            1,
                            0D,
                            0D,
                            0D,
                            0D);
                }
            }
        }
    }
}
