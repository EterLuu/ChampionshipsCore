package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Bukkit boundary for queue safety, menu selection and session settlement. */
final class DailyListener extends BaseListener {
    private final DailyManager daily;
    private final DailyGameMenu menu;

    DailyListener(ChampionshipsCore plugin, DailyManager daily, DailyGameMenu menu) {
        super(plugin);
        this.daily = daily;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenu(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof DailyLeaderboardMenu.LeaderboardHolder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getClickedInventory() == event.getView().getTopInventory())
                daily.leaderboardMenu().click(player, event.getRawSlot(), holder);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof DailyGameMenu.MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        menu.click(player, event.getRawSlot(), (DailyGameMenu.MenuHolder) event.getInventory().getHolder());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DailyGameMenu.MenuHolder
                || event.getView().getTopInventory().getHolder() instanceof DailyLeaderboardMenu.LeaderboardHolder)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && daily.isQueued(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        daily.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        daily.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(SingleGameEndEvent event) {
        daily.finish(event.getGameInstance());
    }
}
