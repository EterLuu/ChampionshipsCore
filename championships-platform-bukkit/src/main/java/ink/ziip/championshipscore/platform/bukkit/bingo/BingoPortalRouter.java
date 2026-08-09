package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.Objects;

/** Keeps custom Bingo overworld/nether/end portals inside their explicitly configured world set. */
public class BingoPortalRouter implements Listener {
    private final String overworldName;
    private final String netherName;
    private final String endName;

    public BingoPortalRouter(String baseWorldName) {
        this(baseWorldName, baseWorldName + "_nether", baseWorldName + "_the_end");
    }

    public BingoPortalRouter(String overworldName, String netherName, String endName) {
        this.overworldName = Objects.requireNonNull(overworldName, "overworldName");
        this.netherName = Objects.requireNonNull(netherName, "netherName");
        this.endName = Objects.requireNonNull(endName, "endName");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!shouldIntercept(event.getFrom().getWorld(), event.getCause())) return;
        Target target = computeTarget(event.getFrom(), event.getCause());
        if (target == null) return;
        event.setTo(target.location());
        event.setSearchRadius(target.searchRadius());
        event.setCreationRadius(target.creationRadius());
        event.setCanCreatePortal(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!shouldIntercept(event.getFrom().getWorld(), null)) return;
        Target target = computeTarget(event.getFrom(), null);
        if (target == null) return;
        event.setTo(target.location());
        event.setSearchRadius(target.searchRadius());
        event.setCreationRadius(target.creationRadius());
        event.setCanCreatePortal(true);
    }

    private boolean shouldIntercept(World from, TeleportCause cause) {
        if (from == null || (!from.getName().equals(overworldName) && !from.getName().equals(netherName)
                && !from.getName().equals(endName))) return false;
        return cause == null || cause == TeleportCause.NETHER_PORTAL || cause == TeleportCause.END_PORTAL
                || cause == TeleportCause.END_GATEWAY;
    }

    private Target computeTarget(Location from, TeleportCause cause) {
        World overworld = Bukkit.getWorld(overworldName);
        World nether = Bukkit.getWorld(netherName);
        World end = Bukkit.getWorld(endName);
        return switch (from.getWorld().getEnvironment()) {
            case NORMAL -> {
                if (cause == TeleportCause.NETHER_PORTAL && nether != null) {
                    yield new Target(scaled(nether, from.getX() / 8.0, from.getY(), from.getZ() / 8.0), 16, 16);
                }
                if ((cause == TeleportCause.END_PORTAL || cause == TeleportCause.END_GATEWAY) && end != null) {
                    yield new Target(new Location(end, 100.5, 49.0, 0.5, 90f, 0f), 0, 0);
                }
                yield null;
            }
            case NETHER -> overworld == null ? null : new Target(
                    scaled(overworld, from.getX() * 8.0, from.getY(), from.getZ() * 8.0), 128, 16);
            case THE_END -> overworld == null ? null : new Target(overworld.getSpawnLocation(), 0, 0);
            default -> null;
        };
    }

    private static Location scaled(World world, double x, double y, double z) {
        double maxY = world.getEnvironment() == World.Environment.NETHER ? 118.0 : 246.0;
        return new Location(world, x, Math.max(5.0, Math.min(maxY, y)), z);
    }

    private record Target(Location location, int searchRadius, int creationRadius) {
    }
}
