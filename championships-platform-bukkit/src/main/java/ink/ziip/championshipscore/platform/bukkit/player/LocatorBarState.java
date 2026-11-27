package ink.ziip.championshipscore.platform.bukkit.player;

import com.github.retrooper.packetevents.protocol.world.waypoint.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint.Operation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Per-connection wire state, never a source of player roles or arena membership. */
final class LocatorBarState {
    record Change(Operation operation, TrackedWaypoint waypoint) {}

    private final Map<UUID, TrackedWaypoint> available = new HashMap<>();
    private final Set<UUID> shown = new HashSet<>();

    synchronized Change accept(Operation operation, TrackedWaypoint waypoint, Set<UUID> audience) {
        var identifier = waypoint.getIdentifier();
        if (!identifier.isLeft()) return null;
        UUID id = identifier.getLeft();
        if (operation == Operation.UNTRACK) {
            available.remove(id);
            return shown.remove(id) ? new Change(operation, waypoint) : null;
        }
        available.put(id, waypoint);
        if (!audience.contains(id)) {
            if (!shown.remove(id)) return null;
            return new Change(Operation.UNTRACK, removal(waypoint));
        }
        // A filtered native TRACK remains tracked by the server. Promote its first UPDATE.
        return new Change(shown.add(id) ? Operation.TRACK : operation, waypoint);
    }

    synchronized void reconcile(Set<UUID> audience, Consumer<Change> send) {
        for (var entry : available.entrySet()) {
            UUID id = entry.getKey();
            if (audience.contains(id)) {
                if (shown.add(id)) send.accept(new Change(Operation.TRACK, entry.getValue()));
            } else if (shown.remove(id)) {
                send.accept(new Change(Operation.UNTRACK, removal(entry.getValue())));
            }
        }
    }

    synchronized void clear(Consumer<Change> send) {
        for (UUID id : shown) {
            send.accept(new Change(Operation.UNTRACK, removal(available.get(id))));
        }
        shown.clear();
        available.clear();
    }

    /** Folia has no native transmitters; replace its frame using the same connection cache. */
    synchronized void replace(Map<UUID, TrackedWaypoint> frame, Consumer<Change> send) {
        for (UUID id : Set.copyOf(available.keySet())) {
            if (!frame.containsKey(id)) {
                var change = accept(Operation.UNTRACK, removal(available.get(id)), Set.of());
                if (change != null) send.accept(change);
            }
        }
        for (var entry : frame.entrySet()) {
            TrackedWaypoint previous = available.get(entry.getKey());
            TrackedWaypoint next = entry.getValue();
            boolean sameIcon =
                    previous != null
                            && previous.getIcon().getStyle().equals(next.getIcon().getStyle())
                            && java.util.Objects.equals(
                                    previous.getIcon().getColor(), next.getIcon().getColor());
            if (shown.contains(entry.getKey())
                    && sameIcon
                    && samePosition(previous.getInfo(), next.getInfo())) continue;
            Operation operation =
                    previous == null
                                    || !sameIcon
                                    || previous.getInfo().getType() != next.getInfo().getType()
                            ? Operation.TRACK
                            : Operation.UPDATE;
            var change = accept(operation, next, frame.keySet());
            if (change != null) send.accept(change);
        }
    }

    private static boolean samePosition(WaypointInfo first, WaypointInfo second) {
        if (first instanceof Vec3iWaypointInfo a && second instanceof Vec3iWaypointInfo b)
            return a.getPosition().equals(b.getPosition());
        if (first instanceof ChunkWaypointInfo a && second instanceof ChunkWaypointInfo b)
            return a.getChunkX() == b.getChunkX() && a.getChunkZ() == b.getChunkZ();
        return first instanceof AzimuthWaypointInfo a
                && second instanceof AzimuthWaypointInfo b
                && Math.abs(a.getAngle() - b.getAngle()) <= Math.toRadians(0.5);
    }

    private static TrackedWaypoint removal(TrackedWaypoint waypoint) {
        return new TrackedWaypoint(
                waypoint.getIdentifier(), waypoint.getIcon(), EmptyWaypointInfo.EMPTY);
    }
}
