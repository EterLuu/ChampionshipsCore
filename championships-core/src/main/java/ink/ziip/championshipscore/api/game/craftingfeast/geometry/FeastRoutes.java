package ink.ziip.championshipscore.api.game.craftingfeast.geometry;

import org.bukkit.Location;

import java.util.List;

/**
 * Customer waypoints. Seats are scattered over the hall and private rooms, so customers walk
 * straight to the back of their chair and the native pathfinder finds the way around tables and
 * walls. All points are copy-local.
 */
public final class FeastRoutes {
    private FeastRoutes() {}

    public static Location approach(Location seat, double floorY) {
        // Seat yaw faces the table; approach its back without asking the pathfinder to climb it.
        double yaw = Math.toRadians(seat.getYaw());
        Location point = seat.clone().add(Math.sin(yaw) * 1.6, 0, -Math.cos(yaw) * 1.6);
        // Native navigation finishes at a block centre, not an arbitrary fractional coordinate.
        point.setX(point.getBlockX() + 0.5);
        point.setZ(point.getBlockZ() + 0.5);
        point.setY(floorY);
        return point;
    }

    public static List<Location> seating(Location counter, Location seat) {
        return List.of(approach(seat, counter.getY()));
    }

    public static List<Location> leaving(Location entrance, Location seat) {
        return List.of(approach(seat, entrance.getY()), entrance.clone());
    }
}
