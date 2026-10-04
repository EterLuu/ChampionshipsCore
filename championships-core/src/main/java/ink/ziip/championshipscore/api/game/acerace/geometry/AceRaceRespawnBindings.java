package ink.ziip.championshipscore.api.game.acerace.geometry;

import ink.ziip.championshipscore.api.game.acerace.model.AceRaceLine;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceProgressPoint;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceRespawnPoint;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Assigns recovery markers to progress gates using the configured route geometry. */
public final class AceRaceRespawnBindings {
    private AceRaceRespawnBindings() {}

    private static final double RESPAWN_GATE_BIND_DISTANCE_SQUARED = 6D * 6D;
    private static final double RESPAWN_ROUTE_CORRIDOR_MARGIN = 28D;

    public static @NotNull List<Integer> bindRespawnPoints(
            @NotNull List<AceRaceProgressPoint> progressPoints,
            @NotNull List<AceRaceRespawnPoint> respawnPoints,
            Location startApproach,
            Location finishApproach) {
        if (progressPoints.isEmpty()) return new ArrayList<>();
        List<AceRaceLine> gates =
                progressPoints.stream()
                        .map(point -> new AceRaceLine(point.pos1(), point.pos2()))
                        .toList();
        World world = startApproach == null ? null : startApproach.getWorld();
        if (world == null && finishApproach != null) world = finishApproach.getWorld();
        if (world == null && !respawnPoints.isEmpty())
            world = respawnPoints.getFirst().destination().getWorld();

        List<Location> gateCenters = new ArrayList<>();
        for (AceRaceLine gate : gates) gateCenters.add(gate.center(world));
        List<Location> routeNodes = new ArrayList<>();
        routeNodes.add(
                startApproach != null ? startApproach.clone() : gateCenters.getFirst().clone());
        routeNodes.addAll(gateCenters.stream().map(Location::clone).toList());
        routeNodes.add(
                finishApproach != null ? finishApproach.clone() : gateCenters.getLast().clone());

        List<Integer> bindings = new ArrayList<>(respawnPoints.size());
        for (AceRaceRespawnPoint respawnPoint : respawnPoints) {
            Location location = respawnPoint.destination();
            int nearGate = nearestGateWithinBindingRadius(location, gates);
            if (nearGate >= 0) {
                // A marker close to a gate is deliberately considered part of the segment after it;
                // its capture radius is the same safety margin used when recovering a missed gate.
                bindings.add(nearGate);
                continue;
            }

            List<Integer> orientedCandidates = new ArrayList<>();
            Location firstGateNext = gates.size() > 1 ? gateCenters.get(1) : routeNodes.getLast();
            if (isBeforeGate(location, gates.getFirst(), firstGateNext)) {
                orientedCandidates.add(0);
            }
            for (int segment = 1; segment < gates.size(); segment++) {
                AceRaceLine previousGate = gates.get(segment - 1);
                AceRaceLine nextGate = gates.get(segment);
                Location nextGateNext =
                        segment + 1 < gates.size()
                                ? gateCenters.get(segment + 1)
                                : routeNodes.getLast();
                if (isAfterGate(location, previousGate, gateCenters.get(segment))
                        && isBeforeGate(location, nextGate, nextGateNext)) {
                    orientedCandidates.add(segment);
                }
            }
            if (isAfterGate(location, gates.getLast(), routeNodes.getLast())) {
                orientedCandidates.add(gates.size());
            }

            List<Integer> corridorCandidates = new ArrayList<>();
            for (int segment = 0; segment < routeNodes.size() - 1; segment++) {
                if (withinRouteCorridor(
                        location, routeNodes.get(segment), routeNodes.get(segment + 1))) {
                    corridorCandidates.add(segment);
                }
            }
            List<Integer> candidates =
                    corridorCandidates.isEmpty() ? orientedCandidates : corridorCandidates;
            if (corridorCandidates.size() > 1 && !orientedCandidates.isEmpty()) {
                List<Integer> intersection =
                        corridorCandidates.stream().filter(orientedCandidates::contains).toList();
                if (!intersection.isEmpty()) candidates = intersection;
            }

            int binding =
                    candidates.stream()
                            .min(
                                    Comparator.comparingDouble(
                                            segment ->
                                                    distanceSquaredToSegment(
                                                            location,
                                                            routeNodes.get(segment),
                                                            routeNodes.get(segment + 1))))
                            .orElseGet(() -> nearestRouteSegment(location, routeNodes));
            // Route segment 0 is before the first gate and therefore has binding -1;
            // segment N is after gate N and therefore has binding N-1.
            bindings.add(binding - 1);
        }

        // Some maps place the final recovery marker before the last gate and go straight into the
        // finish line. Keep that marker as a final-gate safety net, selected by distance rather
        // than
        // by whichever marker happened to be saved last.
        int finalProgressPoint = progressPoints.size() - 1;
        if (!bindings.contains(finalProgressPoint) && !respawnPoints.isEmpty()) {
            int fallback = -1;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (int index = 0; index < respawnPoints.size(); index++) {
                if (bindings.get(index) != finalProgressPoint - 1 && finalProgressPoint > 0)
                    continue;
                double distance =
                        gates.getLast().distanceSquared(respawnPoints.get(index).destination());
                if (distance >= nearestDistance) continue;
                nearestDistance = distance;
                fallback = index;
            }
            if (fallback >= 0) bindings.set(fallback, finalProgressPoint);
        }
        return bindings;
    }

    private static boolean withinRouteCorridor(
            @NotNull Location location, @NotNull Location from, @NotNull Location to) {
        if (location.getWorld() != from.getWorld() || from.getWorld() != to.getWorld())
            return false;
        double minX = Math.min(from.getX(), to.getX()) - RESPAWN_ROUTE_CORRIDOR_MARGIN;
        double maxX = Math.max(from.getX(), to.getX()) + RESPAWN_ROUTE_CORRIDOR_MARGIN;
        double minZ = Math.min(from.getZ(), to.getZ()) - RESPAWN_ROUTE_CORRIDOR_MARGIN;
        double maxZ = Math.max(from.getZ(), to.getZ()) + RESPAWN_ROUTE_CORRIDOR_MARGIN;
        return location.getX() >= minX
                && location.getX() <= maxX
                && location.getZ() >= minZ
                && location.getZ() <= maxZ;
    }

    private static int nearestGateWithinBindingRadius(
            @NotNull Location location, @NotNull List<AceRaceLine> gates) {
        int nearest = -1;
        double nearestDistance = RESPAWN_GATE_BIND_DISTANCE_SQUARED;
        for (int index = 0; index < gates.size(); index++) {
            double distance = gates.get(index).distanceSquared(location);
            if (distance >= nearestDistance) continue;
            nearestDistance = distance;
            nearest = index;
        }
        return nearest;
    }

    private static boolean isAfterGate(
            @NotNull Location location,
            @NotNull AceRaceLine gate,
            @NotNull Location nextGateCenter) {
        int expectedSide = gate.side(nextGateCenter);
        int actualSide = gate.side(location);
        return expectedSide != 0 && (actualSide == expectedSide || actualSide == 0);
    }

    private static boolean isBeforeGate(
            @NotNull Location location,
            @NotNull AceRaceLine gate,
            @NotNull Location nextGateCenter) {
        int expectedSide = gate.side(nextGateCenter);
        int actualSide = gate.side(location);
        return expectedSide != 0 && (actualSide == -expectedSide || actualSide == 0);
    }

    private static int nearestRouteSegment(
            @NotNull Location location, @NotNull List<Location> routeNodes) {
        int nearest = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int segment = 0; segment < routeNodes.size() - 1; segment++) {
            double distance =
                    distanceSquaredToSegment(
                            location, routeNodes.get(segment), routeNodes.get(segment + 1));
            if (distance >= nearestDistance) continue;
            nearestDistance = distance;
            nearest = segment;
        }
        return nearest;
    }

    private static double distanceSquaredToSegment(
            @NotNull Location location, @NotNull Location from, @NotNull Location to) {
        if (location.getWorld() != from.getWorld() || from.getWorld() != to.getWorld())
            return Double.POSITIVE_INFINITY;
        double x = to.getX() - from.getX();
        double y = to.getY() - from.getY();
        double z = to.getZ() - from.getZ();
        double lengthSquared = x * x + y * y + z * z;
        double progress =
                lengthSquared <= 0.0001D
                        ? 0D
                        : ((location.getX() - from.getX()) * x
                                        + (location.getY() - from.getY()) * y
                                        + (location.getZ() - from.getZ()) * z)
                                / lengthSquared;
        progress = Math.max(0D, Math.min(1D, progress));
        double nearestX = from.getX() + x * progress;
        double nearestY = from.getY() + y * progress;
        double nearestZ = from.getZ() + z * progress;
        double dx = location.getX() - nearestX;
        double dy = location.getY() - nearestY;
        double dz = location.getZ() - nearestZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
