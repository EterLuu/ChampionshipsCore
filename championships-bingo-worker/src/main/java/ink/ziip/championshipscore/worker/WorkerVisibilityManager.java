package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Single worker owner for entity visibility; roster identity stays with the match registry. */
final class WorkerVisibilityManager implements Listener {
    private volatile boolean closed;
    private final Plugin plugin;
    private final WorkerMatchRegistry registry;
    private final PlatformScheduler scheduler;
    private final Set<Pair> hidden = ConcurrentHashMap.newKeySet();

    WorkerVisibilityManager(Plugin plugin, WorkerMatchRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        scheduler = new PlatformScheduler(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reconcile();
    }

    static boolean allows(boolean participant, boolean viewerSpectator, boolean targetSpectator) {
        return viewerSpectator || !participant || !targetSpectator;
    }

    void reconcile() {
        reconcile(null);
    }

    private void reconcile(UUID replacedConnection) {
        if (closed) return;
        List<? extends Player> online = List.copyOf(plugin.getServer().getOnlinePlayers());
        for (Player viewer : online)
            scheduler.runEntity(
                    viewer,
                    () -> {
                        if (closed) return;
                        boolean participant = registry.isPlaying(viewer.getUniqueId());
                        boolean viewerSpectator =
                                registry.isSpectator(viewer.getUniqueId())
                                        || viewer.getGameMode() == GameMode.SPECTATOR;
                        for (Player target : online) {
                            if (viewer.equals(target)) continue;
                            boolean targetSpectator =
                                    registry.isSpectator(target.getUniqueId())
                                            || target.getGameMode() == GameMode.SPECTATOR;
                            Pair pair = new Pair(viewer.getUniqueId(), target.getUniqueId());
                            if (allows(participant, viewerSpectator, targetSpectator)) {
                                boolean wasHidden = hidden.remove(pair);
                                if (wasHidden || pair.contains(replacedConnection))
                                    viewer.showEntity(plugin, target);
                            } else {
                                boolean newlyHidden = hidden.add(pair);
                                if (newlyHidden || pair.contains(replacedConnection))
                                    viewer.hideEntity(plugin, target);
                            }
                        }
                    });
    }

    void release(UUID id) {
        for (Pair pair : Set.copyOf(hidden)) {
            if (!pair.contains(id) || !hidden.remove(pair)) continue;
            Player viewer = plugin.getServer().getPlayer(pair.viewer());
            Player target = plugin.getServer().getPlayer(pair.target());
            if (viewer != null && target != null)
                scheduler.runEntity(viewer, () -> viewer.showEntity(plugin, target));
        }
    }

    /**
     * May use an enabled dependency scheduler to finish Folia cleanup after this plugin is
     * disabled.
     */
    void close(PlatformScheduler cleanupScheduler) {
        closed = true;
        HandlerList.unregisterAll(this);
        for (Pair pair : Set.copyOf(hidden)) {
            Player viewer = plugin.getServer().getPlayer(pair.viewer());
            Player target = plugin.getServer().getPlayer(pair.target());
            if (viewer != null && target != null)
                cleanupScheduler.runEntity(viewer, () -> viewer.showEntity(plugin, target));
        }
        hidden.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        reconcile(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hidden.removeIf(pair -> pair.contains(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMode(PlayerGameModeChangeEvent event) {
        if (plugin.isEnabled()) scheduler.runGlobal(this::reconcile);
    }

    private record Pair(UUID viewer, UUID target) {
        boolean contains(UUID id) {
            return id != null && (viewer.equals(id) || target.equals(id));
        }
    }
}
