package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Portal-link listener for the bingo game world. Vanilla/Paper resolve nether/end return portals to
 * the server's <em>default</em> overworld (the lobby world); this rewrites portal destinations whose
 * source is the bingo world ({@code bingo} and its {@code _nether}/{@code _the_end} dimensions) back to
 * the matching bingo dimension.
 *
 * <p>TODO(待确认): this assumes the bingo arena ships sibling {@code _nether}/{@code _the_end} worlds.
 * Whether the pre-built static bingo world actually has those dimensions depends on the final world/
 * spawn setup (still pending). When the siblings don't exist the handler is a no-op, so it is safe to
 * register regardless.
 */
public final class PortalListener implements Listener {

    private final ChampionshipsCore plugin;
    private final String overworldName;
    private final String netherName;
    private final String endName;
    private volatile World overworld;
    private volatile World nether;
    private volatile World theEnd;
    private volatile Location overworldSpawn;

    public PortalListener(ChampionshipsCore plugin, String baseWorldName) {
        this.plugin = plugin;
        this.overworldName = baseWorldName;
        this.netherName = baseWorldName + "_nether";
        this.endName = baseWorldName + "_the_end";
    }

    /** Refreshes the persistent dimension references; called on the global region after world creation. */
    public void refreshWorlds() {
        overworld = Bukkit.getWorld(overworldName);
        nether = Bukkit.getWorld(netherName);
        theEnd = Bukkit.getWorld(endName);
        overworldSpawn = overworld == null ? null : overworld.getSpawnLocation();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (!shouldIntercept(e.getFrom().getWorld(), e.getCause())) return;

        Target t = computeTarget(e.getFrom(), e.getCause());
        if (t == null || t.to == null) return;

        if (t.prepareEndPlatform) {
            e.setCancelled(true);
            org.bukkit.entity.Player player = e.getPlayer();
            ensureEndEntryPlatform(t.to.getWorld()).thenRun(() ->
                    FoliaScheduler.global(plugin).runEntity(player, () -> player.teleportAsync(t.to)))
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Failed to prepare Bingo end platform", throwable);
                        return null;
                    });
            return;
        }

        e.setTo(t.to);
        e.setSearchRadius(t.searchRadius);
        e.setCreationRadius(t.creationRadius);
        e.setCanCreatePortal(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (!shouldIntercept(e.getFrom().getWorld(), null)) return;

        Target t = computeTarget(e.getFrom(), null);
        if (t == null || t.to == null) return;

        if (t.prepareEndPlatform) {
            e.setCancelled(true);
            org.bukkit.entity.Entity entity = e.getEntity();
            ensureEndEntryPlatform(t.to.getWorld()).thenRun(() ->
                    FoliaScheduler.global(plugin).runEntity(entity, () -> entity.teleportAsync(t.to)))
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Failed to prepare Bingo end platform", throwable);
                        return null;
                    });
            return;
        }

        e.setTo(t.to);
        e.setSearchRadius(t.searchRadius);
        e.setCreationRadius(t.creationRadius);
        e.setCanCreatePortal(true);
    }

    private boolean shouldIntercept(World from, TeleportCause cause) {
        if (from == null) return false;

        String name = from.getName();
        boolean isGameWorld = name.equals(overworldName)
                || name.equals(netherName)
                || name.equals(endName);
        if (!isGameWorld) return false;

        if (cause == null) return true;
        return cause == TeleportCause.NETHER_PORTAL
                || cause == TeleportCause.END_PORTAL
                || cause == TeleportCause.END_GATEWAY;
    }

    private Target computeTarget(Location from, TeleportCause cause) {
        if (from == null || from.getWorld() == null) return null;

        World.Environment env = from.getWorld().getEnvironment();

        if (env == World.Environment.NORMAL) {
            if (cause == TeleportCause.NETHER_PORTAL) {
                if (nether == null) return null;
                return new Target(
                        scaled(nether, from.getX() / 8.0, from.getY(), from.getZ() / 8.0),
                        16, 16
                );
            }
            if (cause == TeleportCause.END_PORTAL || cause == TeleportCause.END_GATEWAY) {
                if (theEnd == null) return null;
                return new Target(new Location(theEnd, 100.5, 49.0, 0.5, 90f, 0f), 0, 0, true);
            }
            return null;
        }

        if (env == World.Environment.NETHER) {
            if (overworld == null) return null;
            return new Target(
                    scaled(overworld, from.getX() * 8.0, from.getY(), from.getZ() * 8.0),
                    128, 16
            );
        }

        if (env == World.Environment.THE_END) {
            Location spawn = overworldSpawn;
            if (overworld == null || spawn == null) return null;
            return new Target(spawn.clone(), 0, 0);
        }

        return null;
    }

    private Location scaled(World world, double x, double y, double z) {
        if (world == null) return null;
        double cy = world.getEnvironment() == World.Environment.NETHER
                ? Math.max(5.0, Math.min(118.0, y))
                : Math.max(5.0, Math.min(246.0, y));
        return new Location(world, x, cy, z);
    }

    private CompletableFuture<Void> ensureEndEntryPlatform(World endWorld) {
        if (endWorld == null) return CompletableFuture.completedFuture(null);
        final int cx = 100;
        final int cy = 48;
        final int cz = 0;

        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        List<CompletableFuture<Void>> changes = new ArrayList<>();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                Location blockLocation = new Location(endWorld, x, cy, z);
                changes.add(scheduler.runAtLocationFuture(blockLocation,
                        () -> blockLocation.getBlock().setType(Material.OBSIDIAN, false)));
            }
        }
        for (int y = cy + 1; y <= cy + 4; y++) {
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    Location blockLocation = new Location(endWorld, x, y, z);
                    changes.add(scheduler.runAtLocationFuture(blockLocation, () -> {
                        Block air = blockLocation.getBlock();
                        if (air.getType().isSolid()) air.setType(Material.AIR, false);
                    }));
                }
            }
        }
        return CompletableFuture.allOf(changes.toArray(CompletableFuture[]::new));
    }

    private static final class Target {
        final Location to;
        final int searchRadius;
        final int creationRadius;
        final boolean prepareEndPlatform;

        Target(Location to, int searchRadius, int creationRadius) {
            this(to, searchRadius, creationRadius, false);
        }

        Target(Location to, int searchRadius, int creationRadius, boolean prepareEndPlatform) {
            this.to = to;
            this.searchRadius = searchRadius;
            this.creationRadius = creationRadius;
            this.prepareEndPlatform = prepareEndPlatform;
        }
    }
}
