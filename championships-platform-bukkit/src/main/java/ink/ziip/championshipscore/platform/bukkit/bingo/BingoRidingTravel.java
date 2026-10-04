package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Round-scoped riding distance. Independent observation streams must never be added together. */
public final class BingoRidingTravel {
    public enum Source {
        PLAYER,
        VEHICLE
    }

    private record Key(UUID player, Statistic statistic, Source source) {}

    private final Map<Key, Double> centimeters = new ConcurrentHashMap<>();

    /** Matches ServerPlayer.checkRidingStatistics, including all horse and nautilus variants. */
    public static Statistic statistic(Entity vehicle) {
        if (vehicle instanceof Minecart) return Statistic.MINECART_ONE_CM;
        if (vehicle instanceof Boat) return Statistic.BOAT_ONE_CM;
        if (vehicle instanceof Pig) return Statistic.PIG_ONE_CM;
        if (vehicle instanceof AbstractHorse) return Statistic.HORSE_ONE_CM;
        if (vehicle instanceof Strider) return Statistic.STRIDER_ONE_CM;
        if (vehicle instanceof HappyGhast) return Statistic.HAPPY_GHAST_ONE_CM;
        if (vehicle instanceof AbstractNautilus) return Statistic.NAUTILUS_ONE_CM;
        return null;
    }

    public static double distance(PlayerMoveEvent event) {
        if (event.isCancelled()
                || event instanceof PlayerTeleportEvent
                || statistic(event.getPlayer().getVehicle()) == null) return 0;
        return distance(event.getFrom(), event.getTo());
    }

    public static double distance(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()) return 0;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) * 100;
        return Double.isFinite(distance) ? distance : 0;
    }

    public void record(UUID player, Statistic statistic, double distance, Source source) {
        if (statistic != null && Double.isFinite(distance) && distance > 0)
            centimeters.merge(new Key(player, statistic, source), distance, Double::sum);
    }

    public int delta(UUID player, Statistic statistic, int vanillaDelta) {
        double tracked =
                Math.max(
                        centimeters.getOrDefault(new Key(player, statistic, Source.PLAYER), 0.0),
                        centimeters.getOrDefault(new Key(player, statistic, Source.VEHICLE), 0.0));
        return Math.max(vanillaDelta, (int) Math.min(Integer.MAX_VALUE, Math.floor(tracked)));
    }

    public void clear() {
        centimeters.clear();
    }
}
