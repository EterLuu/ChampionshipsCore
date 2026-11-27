package ink.ziip.championshipscore.platform.bukkit.player;

import static org.junit.jupiter.api.Assertions.*;

import com.github.retrooper.packetevents.protocol.world.waypoint.*;
import com.github.retrooper.packetevents.util.Either;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint.Operation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class LocatorBarStateTest {
    private final UUID target = UUID.randomUUID();
    private final LocatorBarState state = new LocatorBarState();

    private LocatorBarState.Change packet(Operation operation, int x) {
        return new LocatorBarState.Change(
                operation,
                new TrackedWaypoint(
                        Either.createLeft(target),
                        new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, null),
                        operation == Operation.UNTRACK
                                ? EmptyWaypointInfo.EMPTY
                                : new Vec3iWaypointInfo(new Vector3i(x, 64, 0))));
    }

    private LocatorBarState.Change accept(
            LocatorBarState receiver, LocatorBarState.Change packet, Set<UUID> audience) {
        return receiver.accept(packet.operation(), packet.waypoint(), audience);
    }

    @Test
    void otherFieldIsFilteredButStationaryTargetAppearsOnScopeChange() {
        assertNull(accept(state, packet(Operation.TRACK, 1), Set.of()));
        List<LocatorBarState.Change> sent = new ArrayList<>();
        state.reconcile(Set.of(target), sent::add);
        assertEquals(1, sent.size());
        assertEquals(Operation.TRACK, sent.getFirst().operation());
        state.reconcile(Set.of(target), sent::add);
        assertEquals(1, sent.size(), "unchanged scope must not resend markers");
    }

    @Test
    void spectatorOrScopeChangeImmediatelyRemovesAndThenRestoresMarker() {
        assertNotNull(accept(state, packet(Operation.TRACK, 1), Set.of(target)));
        List<LocatorBarState.Change> sent = new ArrayList<>();
        state.reconcile(Set.of(), sent::add);
        assertEquals(Operation.UNTRACK, sent.getFirst().operation());
        assertNull(accept(state, packet(Operation.UPDATE, 9), Set.of()));
        state.reconcile(Set.of(target), sent::add);
        assertEquals(Operation.TRACK, sent.get(1).operation());
        assertEquals(
                new Vector3i(9, 64, 0),
                ((Vec3iWaypointInfo) sent.get(1).waypoint().getInfo()).getPosition());
    }

    @Test
    void nativeUpdateCreatesPreviouslyFilteredTarget() {
        assertNull(accept(state, packet(Operation.TRACK, 0), Set.of()));
        var update = packet(Operation.UPDATE, 4);
        var change = accept(state, update, Set.of(target));
        assertNotNull(change);
        assertEquals(Operation.TRACK, change.operation());
    }

    @Test
    void nativeUpdateOnChangedScopeBecomesRemoval() {
        assertNotNull(accept(state, packet(Operation.TRACK, 0), Set.of(target)));
        var update = packet(Operation.UPDATE, 4);
        var removal = accept(state, update, Set.of());
        assertNotNull(removal);
        assertEquals(Operation.UNTRACK, removal.operation());
        assertInstanceOf(EmptyWaypointInfo.class, removal.waypoint().getInfo());
    }

    @Test
    void nativeUntrackPreventsRevivingOfflineInvisibleOrOutOfRangeTarget() {
        for (boolean initiallyVisible : new boolean[] {false, true}) {
            accept(state, packet(Operation.TRACK, 1), initiallyVisible ? Set.of(target) : Set.of());
            assertEquals(
                    initiallyVisible,
                    accept(state, packet(Operation.UNTRACK, 0), Set.of(target)) != null);
            List<LocatorBarState.Change> sent = new ArrayList<>();
            state.reconcile(Set.of(target), sent::add);
            assertTrue(sent.isEmpty());
        }
    }

    @Test
    void respawnWorldChangeOrConnectionReleaseClearsBothVisibleAndSuppressedCaches() {
        accept(state, packet(Operation.TRACK, 1), Set.of(target));
        List<LocatorBarState.Change> sent = new ArrayList<>();
        state.clear(sent::add);
        assertEquals(Operation.UNTRACK, sent.getFirst().operation());
        state.reconcile(Set.of(target), sent::add);
        assertEquals(1, sent.size());
        accept(state, packet(Operation.TRACK, 2), Set.of());
        state.clear(sent::add);
        state.reconcile(Set.of(target), sent::add);
        assertEquals(1, sent.size());
    }

    @Test
    void separateViewersDoNotShareCachedMarkers() {
        LocatorBarState other = new LocatorBarState();
        accept(state, packet(Operation.TRACK, 1), Set.of(target));
        assertNull(accept(other, packet(Operation.TRACK, 1), Set.of()));
        List<LocatorBarState.Change> sent = new ArrayList<>();
        other.reconcile(Set.of(), sent::add);
        assertTrue(sent.isEmpty());
    }

    @Test
    void foliaFramesRemoveMissingTargetsAndDoNotResendStationaryMarkers() {
        List<LocatorBarState.Change> sent = new ArrayList<>();
        var frame = java.util.Map.of(target, packet(Operation.TRACK, 4).waypoint());
        state.replace(frame, sent::add);
        state.replace(frame, sent::add);
        assertEquals(1, sent.size());
        assertEquals(Operation.TRACK, sent.getFirst().operation());
        state.replace(java.util.Map.of(target, packet(Operation.UPDATE, 5).waypoint()), sent::add);
        assertEquals(Operation.UPDATE, sent.get(1).operation());
        state.replace(java.util.Map.of(), sent::add);
        assertEquals(Operation.UNTRACK, sent.get(2).operation());
        state.replace(frame, sent::add);
        assertEquals(Operation.TRACK, sent.get(3).operation());
    }

    @Test
    void foliaPositionPrecisionOrTeamColorChangeRecreatesTheMarker() {
        List<LocatorBarState.Change> sent = new ArrayList<>();
        state.replace(java.util.Map.of(target, packet(Operation.TRACK, 4).waypoint()), sent::add);
        var chunk =
                new TrackedWaypoint(
                        Either.createLeft(target),
                        new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, null),
                        new ChunkWaypointInfo(1, 2));
        state.replace(java.util.Map.of(target, chunk), sent::add);
        assertEquals(Operation.TRACK, sent.get(1).operation());
        var colored =
                new TrackedWaypoint(
                        Either.createLeft(target),
                        new WaypointIcon(
                                WaypointIcon.ICON_STYLE_DEFAULT,
                                new com.github.retrooper.packetevents.protocol.color.Color(
                                        0xff0000)),
                        chunk.getInfo());
        state.replace(java.util.Map.of(target, colored), sent::add);
        assertEquals(Operation.TRACK, sent.get(2).operation());
    }
}
