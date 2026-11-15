package ink.ziip.championshipscore.api.game.acerace.editor;

import static ink.ziip.championshipscore.support.TestObjects.proxy;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

class AceRaceEditorPreviewTest {
    @Test
    void toggleOwnsMarkersAndTaskAndSecondToggleRemovesBoth() {
        var f = new Fixture();
        assertTrue(f.preview.toggle(f.player));
        assertEquals(2, f.entities.size());
        assertTrue(f.preview.enabled(f.session));
        f.tasks.getFirst().action.run();
        assertEquals(1, f.particles);
        assertFalse(f.preview.toggle(f.player));
        assertTrue(f.tasks.getFirst().cancelled);
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
        assertFalse(f.preview.enabled(f.session));
    }

    @Test
    void noSessionCannotCreateEntities() {
        var f = new Fixture();
        f.liveSession = null;
        assertFalse(f.preview.toggle(f.player));
        assertTrue(f.entities.isEmpty());
        assertTrue(f.tasks.isEmpty());
    }

    @Test
    void oldTickCannotCancelOrRenderNewPreview() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        var old = f.tasks.getFirst();
        f.preview.stop();
        f.preview.toggle(f.player);
        old.action.run();
        assertTrue(f.preview.enabled(f.session));
        assertFalse(f.tasks.getLast().cancelled);
        assertEquals(0, f.particles);
        assertFalse(f.entities.getLast().dead);
    }

    @Test
    void oldSessionExitCannotStopReplacementSessionOnSameMap() {
        var f = new Fixture();
        Object oldSession = f.session;
        f.preview.toggle(f.player);
        f.session = f.liveSession = new Object();
        f.preview.toggle(f.player);
        f.preview.stopSession(oldSession);
        assertTrue(f.preview.enabled(f.session));
        assertFalse(f.tasks.getLast().cancelled);
        f.preview.stopSession(f.session);
        assertTrue(f.tasks.getLast().cancelled);
    }

    @Test
    void changedSessionInvalidatesInteractionBeforeNextTick() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        var marker = f.entities.getFirst();
        f.liveSession = new Object();
        assertEquals(-1, f.preview.markerIndex(f.player, marker.entity));
        assertTrue(marker.dead);
        assertTrue(f.tasks.getFirst().cancelled);
    }

    @Test
    void replacementConnectionWithSameUuidDoesNotInheritPreview() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        var oldTask = f.tasks.getFirst();
        var oldPlayer = f.player;
        f.player = player(oldPlayer.getUniqueId());
        oldTask.action.run();
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
        assertFalse(f.preview.enabled(f.session));
        assertTrue(f.preview.toggle(f.player));
    }

    @Test
    void markerIndexIsFrozenRawConfigurationIndexAndCannotBeSpoofedByUuid() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        var marker = f.entities.get(1);
        assertEquals(7, f.preview.markerIndex(f.player, marker.entity));
        var impostor = new Crystal(marker.id);
        assertEquals(-1, f.preview.markerIndex(impostor.entity));
        assertEquals(-1, f.preview.markerIndex(player(UUID.randomUUID()), marker.entity));
    }

    @Test
    void refreshReplacesMarkersAndChangesIndexMappingWithoutAddingTasks() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        var old = List.copyOf(f.entities);
        f.markers = List.of(marker(f.world, 12));
        f.preview.refresh();
        assertTrue(old.stream().allMatch(e -> e.dead));
        assertEquals(12, f.preview.markerIndex(f.entities.getLast().entity));
        assertEquals(1, f.tasks.size());
        assertEquals(-1, f.preview.markerIndex(old.getFirst().entity));
    }

    @Test
    void failedInitialSpawnRemovesAlreadySpawnedMarkersAndAllowsRetry() {
        var f = new Fixture();
        f.failSpawn = 2;
        assertThrows(IllegalStateException.class, () -> f.preview.toggle(f.player));
        assertTrue(f.entities.getFirst().dead);
        assertFalse(f.preview.enabled(f.session));
        f.failSpawn = -1;
        assertTrue(f.preview.toggle(f.player));
    }

    @Test
    void failedRefreshRemovesPartialReplacementAndCancelsTask() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        f.failSpawn = 4;
        assertThrows(IllegalStateException.class, f.preview::refresh);
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
        assertTrue(f.tasks.getFirst().cancelled);
        assertFalse(f.preview.enabled(f.session));
    }

    @Test
    void schedulingRejectionRollsBackAllEntities() {
        var f = new Fixture();
        f.reject = true;
        assertThrows(IllegalStateException.class, () -> f.preview.toggle(f.player));
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
        assertFalse(f.preview.enabled(f.session));
    }

    @Test
    void stopDuringSpawnRemovesTheReturnedEntityAsWell() {
        var f = new Fixture();
        f.onSpawn = () -> f.preview.stop();
        assertFalse(f.preview.toggle(f.player));
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
        assertTrue(f.tasks.isEmpty());
    }

    @Test
    void reentrantReplacementDuringSpawnSurvivesOldCleanup() {
        var f = new Fixture();
        f.onSpawn =
                () -> {
                    f.onSpawn = () -> {};
                    f.session = f.liveSession = new Object();
                    f.preview.toggle(f.player);
                };
        assertFalse(f.preview.toggle(f.player));
        assertTrue(f.preview.enabled(f.session));
        assertEquals(2, f.entities.stream().filter(e -> !e.dead).count());
        assertFalse(f.tasks.getLast().cancelled);
    }

    @Test
    void reentrantRefreshDuringSpawnDoesNotAppendAnObsoleteMarker() {
        var f = new Fixture();
        f.onSpawn =
                () -> {
                    f.onSpawn = () -> {};
                    f.preview.refresh();
                };
        assertTrue(f.preview.toggle(f.player));
        assertEquals(2, f.entities.stream().filter(e -> !e.dead).count());
        assertEquals(-1, f.preview.markerIndex(f.entities.getFirst().entity));
    }

    @Test
    void stopDuringTaskRegistrationCancelsTheReturnedHandle() {
        var f = new Fixture();
        f.onRepeat = f.preview::stop;
        assertFalse(f.preview.toggle(f.player));
        assertTrue(f.tasks.getFirst().cancelled);
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
    }

    @Test
    void failedParticleTickStopsThePreview() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        f.failParticles = true;
        assertThrows(IllegalStateException.class, f.tasks.getFirst().action::run);
        assertTrue(f.tasks.getFirst().cancelled);
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
    }

    @Test
    void closeIsPermanentAndOldTicksCannotRecreateEntities() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        f.preview.close();
        f.tasks.getFirst().action.run();
        assertFalse(f.preview.toggle(f.player));
        assertEquals(2, f.entities.size());
        assertTrue(f.entities.stream().allMatch(e -> e.dead));
    }

    @Test
    void missingWorldKeepsTaskButCreatesNoMarkers() {
        var f = new Fixture();
        f.world = null;
        assertTrue(f.preview.toggle(f.player));
        assertTrue(f.entities.isEmpty());
        f.preview.stop();
        assertTrue(f.tasks.getFirst().cancelled);
    }

    @Test
    void markerLocationAndCourseListsAreDefensivelyCaptured() {
        World world = world();
        Location location = new Location(world, 1, 2, 3);
        var marker = new AceRaceEditorPreview.Marker(location, 7, -1);
        location.setX(9);
        marker.location().setX(20);
        assertEquals(1, marker.location().getX());
        var markers = new ArrayList<>(List.of(marker));
        var course = new AceRaceEditorPreview.Course(world, "track", markers, List.of());
        markers.clear();
        assertEquals(1, course.markers().size());
    }

    @Test
    void replacementStartedByEntityRemovalIsNotOverwrittenByTheOuterToggle() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        f.entities.getFirst().onRemove =
                () -> {
                    f.session = f.liveSession = new Object();
                    f.preview.toggle(f.player);
                };
        f.session = f.liveSession = new Object();
        assertFalse(f.preview.toggle(f.player));
        assertTrue(f.preview.enabled(f.session));
        assertEquals(2, f.entities.stream().filter(entity -> !entity.dead).count());
    }

    @Test
    void cleanupFailureDoesNotHideTheOriginalSpawnFailure() {
        var f = new Fixture();
        f.failSpawn = 2;
        f.onSpawn =
                () -> f.entities.getFirst().removalFailure = new IllegalStateException("remove");
        var error = assertThrows(IllegalStateException.class, () -> f.preview.toggle(f.player));
        assertEquals("spawn", error.getMessage());
        assertEquals("remove", error.getSuppressed()[0].getMessage());
        assertFalse(f.preview.enabled(f.session));
    }

    @Test
    void oneRemovalFailureDoesNotPreventCleaningTheOtherMarkers() {
        var f = new Fixture();
        f.preview.toggle(f.player);
        f.entities.getFirst().removalFailure = new IllegalStateException("remove");
        assertThrows(IllegalStateException.class, f.preview::stop);
        assertTrue(f.entities.getLast().dead);
        assertTrue(f.tasks.getFirst().cancelled);
    }

    private static AceRaceEditorPreview.Marker marker(World world, int index) {
        return new AceRaceEditorPreview.Marker(new Location(world, index, 64, 0), index, -1);
    }

    private static Player player(UUID id) {
        return proxy(
                Player.class,
                (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return id;
                    throw new AssertionError(m.getName());
                });
    }

    private static World world() {
        return proxy(
                World.class,
                (p, m, a) -> {
                    throw new AssertionError(m.getName());
                });
    }

    private static final class Task {
        final Runnable action;
        boolean cancelled;
        final BukkitTask handle =
                proxy(
                        BukkitTask.class,
                        (p, m, a) -> {
                            if (m.getName().equals("cancel")) {
                                cancelled = true;
                                return null;
                            }
                            throw new AssertionError(m.getName());
                        });

        Task(Runnable action) {
            this.action = action;
        }
    }

    private static final class Crystal {
        final UUID id;
        boolean dead;
        RuntimeException removalFailure;
        Runnable onRemove = () -> {};
        final EnderCrystal entity =
                proxy(
                        EnderCrystal.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getUniqueId" -> this.id();
                                    case "isDead" -> dead;
                                    case "remove" -> {
                                        onRemove.run();
                                        if (removalFailure != null) throw removalFailure;
                                        dead = true;
                                        yield null;
                                    }
                                    default -> throw new AssertionError(m.getName());
                                });

        Crystal(UUID id) {
            this.id = id;
        }

        UUID id() {
            return id;
        }
    }

    private static final class Fixture implements AceRaceEditorPreview.Access {
        Player player = player(UUID.randomUUID());
        Object session = new Object(), liveSession = session;
        World world = world();
        List<AceRaceEditorPreview.Marker> markers = List.of(marker(world, 2), marker(world, 7));
        final AceRaceEditorPreview preview = new AceRaceEditorPreview(this);
        final List<Crystal> entities = new ArrayList<>();
        final List<Task> tasks = new ArrayList<>();
        int particles, spawns, failSpawn = -1;
        boolean reject, failParticles;
        Runnable onSpawn = () -> {}, onRepeat = () -> {};

        public Object session(Player player) {
            return this.player == player ? liveSession : null;
        }

        public AceRaceEditorPreview.Course course() {
            return new AceRaceEditorPreview.Course(world, "track", markers, List.of());
        }

        public EnderCrystal spawn(
                AceRaceEditorPreview.Course course,
                AceRaceEditorPreview.Marker marker,
                int loadedIndex) {
            if (++spawns == failSpawn) throw new IllegalStateException("spawn");
            var crystal = new Crystal(UUID.randomUUID());
            entities.add(crystal);
            onSpawn.run();
            return crystal.entity;
        }

        public BukkitTask repeat(Runnable action) {
            if (reject) throw new IllegalStateException("scheduler");
            var task = new Task(action);
            tasks.add(task);
            onRepeat.run();
            return task.handle;
        }

        public void particles(Player viewer, AceRaceEditorPreview.Course course) {
            if (failParticles) throw new IllegalStateException("particles");
            particles++;
        }
    }
}
