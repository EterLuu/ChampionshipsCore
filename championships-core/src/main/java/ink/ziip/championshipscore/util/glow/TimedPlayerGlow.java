package ink.ziip.championshipscore.util.glow;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Owns short player reveals; closing a life or arena always releases its outstanding glow. */
public final class TimedPlayerGlow {
    private final ChampionshipsCore plugin;
    private final Map<UUID, Reveal> reveals = new HashMap<>();

    public TimedPlayerGlow(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void show(Player viewer, Collection<Player> targets, long ticks) {
        clearViewer(viewer.getUniqueId());
        Reveal reveal = new Reveal(viewer);
        reveals.put(viewer.getUniqueId(), reveal);
        for (Player target : targets) {
            if (target == viewer) continue;
            reveal.targets.add(target.getUniqueId());
            plugin.getGlowingEntities().setGlowing(target, viewer);
        }
        reveal.task =
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    if (reveals.get(viewer.getUniqueId()) == reveal)
                                        clearViewer(viewer.getUniqueId());
                                },
                                ticks);
    }

    public void clearPlayer(UUID player) {
        clearViewer(player);
        Player target = Bukkit.getPlayer(player);
        for (Reveal reveal : reveals.values()) {
            if (reveal.targets.remove(player) && target != null && reveal.connected())
                plugin.getGlowingEntities().unsetGlowing(target, reveal.viewer);
        }
    }

    public void clear() {
        for (UUID viewer : List.copyOf(reveals.keySet())) clearViewer(viewer);
    }

    private void clearViewer(UUID id) {
        Reveal reveal = reveals.remove(id);
        if (reveal == null) return;
        if (reveal.task != null) reveal.task.cancel();
        if (!reveal.connected()) return;
        for (UUID targetId : reveal.targets) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) plugin.getGlowingEntities().unsetGlowing(target, reveal.viewer);
        }
    }

    private static final class Reveal {
        final Player viewer;
        final Set<UUID> targets = new HashSet<>();
        BukkitTask task;

        Reveal(Player viewer) {
            this.viewer = viewer;
        }

        boolean connected() {
            return Bukkit.getPlayer(viewer.getUniqueId()) == viewer;
        }
    }
}
