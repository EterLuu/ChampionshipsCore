package ink.ziip.championshipscore.api;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class BaseListener implements Listener {
    protected final ChampionshipsCore plugin;

    protected BaseListener(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, ChampionshipsCore.getInstance());
    }

    public void unRegister() {
        HandlerList.unregisterAll(this);
    }
}