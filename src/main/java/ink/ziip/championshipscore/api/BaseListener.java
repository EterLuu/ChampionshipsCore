package ink.ziip.championshipscore.api;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseListener implements Listener {
    protected final ChampionshipsCore plugin;
    private final AtomicBoolean registered = new AtomicBoolean();

    protected BaseListener(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        runOnGlobalRegion(() -> {
            if (!registered.get()) return;
            try {
                Bukkit.getPluginManager().registerEvents(this, plugin);
            } catch (IllegalPluginAccessException ignored) {
                registered.set(false);
            }
        });
    }

    public void unRegister() {
        if (!registered.compareAndSet(true, false)) {
            return;
        }
        runOnGlobalRegion(() -> HandlerList.unregisterAll(this));
    }

    private void runOnGlobalRegion(Runnable task) {
        if (Bukkit.isGlobalTickThread()) {
            task.run();
        } else {
            FoliaScheduler.global(plugin).runTask(task);
        }
    }
}
