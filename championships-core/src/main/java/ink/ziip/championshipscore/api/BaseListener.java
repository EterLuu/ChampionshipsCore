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
    private ink.ziip.championshipscore.api.game.instance.BaseGameInstance gameInstance;

    /** Called once by the instance constructor, before its gameplay listener is registered. */
    public final void bindGameInstance(
            ink.ziip.championshipscore.api.game.instance.BaseGameInstance instance) {
        if (gameInstance != null && gameInstance != instance)
            throw new IllegalStateException("Listener already bound");
        gameInstance = instance;
    }

    protected BaseListener(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public synchronized void register() {
        if (registered) return;
        try {
            if (gameInstance == null) Bukkit.getPluginManager().registerEvents(this, plugin);
            else {
                // Preserve Bukkit's method discovery, priority, cancellation and timing semantics.
                var registrations =
                        plugin.getPluginLoader().createRegisteredListeners(this, plugin);
                registrations.forEach(
                        (eventClass, listeners) -> {
                            for (org.bukkit.plugin.RegisteredListener original : listeners) {
                                Bukkit.getPluginManager()
                                        .registerEvent(
                                                eventClass,
                                                this,
                                                original.getPriority(),
                                                (listener, event) -> {
                                                    if (!ink.ziip.championshipscore.api.game
                                                            .spectate.SpectatorGameEventGate
                                                            .suppress(
                                                                    event,
                                                                    plugin.getGameManager()
                                                                                    .getSpectatorManager()
                                                                            ::isSpectatorLike))
                                                        original.callEvent(event);
                                                },
                                                plugin,
                                                original.isIgnoringCancelled());
                            }
                        });
            }
            registered = true;
        } catch (IllegalPluginAccessException exception) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Unable to register listener " + getClass().getName(),
                            exception);
        }
    }

    public synchronized void unRegister() {
        if (!registered) return;
        HandlerList.unregisterAll(this);
        registered = false;
    }

    /** Per-area movement hooks are invoked by GameManager's constant-count routed listeners. */
    public void handleRoutedPlayerMoveLow(PlayerMoveEvent event) {}

    public void handleRoutedPlayerMoveNormal(PlayerMoveEvent event) {}

    public void handleRoutedPlayerMoveHigh(PlayerMoveEvent event) {}
}
