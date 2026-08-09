package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.world.WorldManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.jetbrains.annotations.NotNull;

/** Blocks vanilla portal travel outside Bingo's dedicated linked dimensions. */
public final class PortalGuardListener extends BaseListener {
    public PortalGuardListener(@NotNull ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(@NotNull PlayerPortalEvent event) {
        if (!WorldManager.isBingoWorld(event.getFrom().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        if (!WorldManager.isBingoWorld(event.getFrom().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(@NotNull PortalCreateEvent event) {
        if (!WorldManager.isBingoWorld(event.getWorld())) event.setCancelled(true);
    }
}
