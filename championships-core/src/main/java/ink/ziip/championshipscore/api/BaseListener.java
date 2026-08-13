package ink.ziip.championshipscore.api;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.logging.Level;

public abstract class BaseListener implements Listener {
    protected final ChampionshipsCore plugin;
    private boolean registered;

    protected BaseListener(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public synchronized void register() {
        if (registered) return;
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        } catch (IllegalPluginAccessException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to register listener " + getClass().getName(), exception);
        }
    }

    public synchronized void unRegister() {
        if (!registered) return;
        HandlerList.unregisterAll(this);
        registered = false;
    }

    /** Per-area movement hooks are invoked by GameManager's constant-count routed listeners. */
    public void handleRoutedPlayerMoveLow(PlayerMoveEvent event) {
    }

    public void handleRoutedPlayerMoveNormal(PlayerMoveEvent event) {
    }

    public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {
    }
}
