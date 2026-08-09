package ink.ziip.championshipscore.api;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.IllegalPluginAccessException;

public abstract class BaseListener implements Listener {
    protected final ChampionshipsCore plugin;

    protected BaseListener(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            Bukkit.getPluginManager().registerEvents(this, ChampionshipsCore.getInstance());
        } catch (IllegalPluginAccessException ignored) {
        }
    }

    public void unRegister() {
        HandlerList.unregisterAll(this);
    }
}