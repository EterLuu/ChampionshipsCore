package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Drives the rule-introduction lesson of one Crafting Feast game: the restaurant clock and the
 * admission of every (re)connected cook into their own lesson kitchen. The shared introduction
 * keeps the 180 second timer, the recovery and the cancellation; this object only lives inside it.
 */
final class FeastLesson implements AutoCloseable {
    private final ChampionshipsCore plugin;
    private final CraftingFeastArea area;
    private final long generation;

    /** Entity id per admitted connection; a new id means a reconnect that needs a fresh kitchen. */
    private final Map<UUID, Integer> connections = new HashMap<>();

    private BukkitTask task;
    private int ticks;
    private boolean closed;

    FeastLesson(ChampionshipsCore plugin, CraftingFeastArea area) {
        this.plugin = plugin;
        this.area = area;
        generation = area.captureGameGeneration();
    }

    void start() {
        task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!current()) {
                                        close();
                                        return;
                                    }
                                    try {
                                        tick();
                                    } catch (RuntimeException error) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.SEVERE,
                                                        MessageConfig.codeText(
                                                                "crafting-feast.tutorial.run-failed"),
                                                        error);
                                        area.abortAndReset();
                                    }
                                },
                                5L,
                                5L);
    }

    private boolean current() {
        return !closed
                && plugin.isLoaded()
                && generation == area.captureGameGeneration()
                && area.lessonRunning();
    }

    private void tick() {
        ticks += 5;
        boolean second = ticks % 20 == 0;
        for (UUID id : area.getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !admissible(player)) {
                connections.remove(id);
                continue;
            }
            if (!Integer.valueOf(player.getEntityId()).equals(connections.get(id))) admit(player);
        }
        area.forEachRestaurant(restaurant -> restaurant.tick(second));
    }

    private boolean admissible(Player player) {
        return player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && area.isGameplayParticipant(player)
                && plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) == area;
    }

    /**
     * Gives a cook a clean lesson kitchen: survival for harvesting, empty inventory plus the normal
     * tools, and a place at their own team's kitchen spawn.
     */
    void admit(Player player) {
        if (!current() || !admissible(player)) return;
        connections.put(player.getUniqueId(), player.getEntityId());
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFireTicks(0);
        player.setFallDistance(0F);
        player.setFoodLevel(20);
        player.getInventory().clear();
        for (var effect : player.getActivePotionEffects())
            player.removePotionEffect(effect.getType());
        area.spawn(player);
        area.equip(player);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (task != null) task.cancel();
        task = null;
        for (UUID id : connections.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && plugin.getGameManager().getBasePlayerArea(id) == area) {
                player.closeInventory();
                player.resetTitle();
                player.sendActionBar(Component.empty());
            }
        }
        connections.clear();
    }
}
