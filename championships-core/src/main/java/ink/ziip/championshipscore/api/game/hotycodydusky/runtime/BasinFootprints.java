package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.joml.Matrix4f;

import java.util.*;

final class BasinFootprints {
    private record Mark(TextDisplay display, Location at, int end) {}

    private static final int RANGE = 16;
    private final ChampionshipsCore plugin;
    private final Map<UUID, Integer> counters = new HashMap<>();
    private final Deque<Mark> marks = new ArrayDeque<>();
    private boolean left;

    BasinFootprints(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    void tick(int now, Map<Integer, List<Player>> groups) {
        while (!marks.isEmpty() && marks.getFirst().end() <= now)
            marks.removeFirst().display().remove();
        // A player rounding a corner can also discover tracks made before they arrived.
        if (now % 10 == 0) marks.forEach(this::reveal);
        for (var group : groups.values()) {
            for (Player player : group) {
                int cm = player.getStatistic(Statistic.SPRINT_ONE_CM);
                UUID id = player.getUniqueId();
                int previous = counters.computeIfAbsent(id, ignored -> cm);
                if (cm < previous || cm - previous >= 50) {
                    counters.put(id, cm);
                    if (cm > previous) stamp(player.getLocation(), player, now);
                }
            }
        }
    }

    void cloneStep(Zombie body, Player owner, int now) {
        var v = body.getVelocity();
        if (Math.floor(v.getX() * 10) == 0 || Math.floor(v.getZ() * 10) == 0) return;
        if (counters.merge(body.getUniqueId(), 25, Integer::sum) >= 50) {
            counters.remove(body.getUniqueId());
            stamp(body.getLocation(), owner, now);
        }
    }

    private void stamp(Location at, Player owner, int now) {
        var color = BasinAppearance.teamColor(plugin.getTeamManager().getTeamByPlayer(owner));
        var glyph = Component.text(left ? "■    " : "    ■", color);
        left = !left;
        at.setPitch(90);
        TextDisplay display =
                at.getWorld()
                        .spawn(
                                at,
                                TextDisplay.class,
                                e -> {
                                    e.setVisibleByDefault(false);
                                    e.setPersistent(false);
                                    e.setViewRange(RANGE / 64f);
                                    e.text(glyph);
                                    e.setAlignment(TextDisplay.TextAlignment.LEFT);
                                    e.setBackgroundColor(Color.fromARGB(0));
                                    e.setTextOpacity((byte) 90);
                                    e.setTransformationMatrix(
                                            new Matrix4f()
                                                    .translation(0, 0, -0.001f)
                                                    .scale(-1, 1, -1));
                                });
        Mark mark = new Mark(display, at, now + 100);
        marks.addLast(mark);
        reveal(mark);
    }

    private void reveal(Mark mark) {
        for (Player viewer : mark.at().getNearbyPlayers(RANGE)) {
            if (viewer.getLocation().distanceSquared(mark.at()) <= RANGE * RANGE
                    && !viewer.canSee(mark.display())) viewer.showEntity(plugin, mark.display());
        }
    }

    void forget(UUID id) {
        counters.remove(id);
    }

    void close() {
        marks.forEach(m -> m.display().remove());
        marks.clear();
        counters.clear();
    }
}
