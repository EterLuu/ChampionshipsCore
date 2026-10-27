package ink.ziip.championshipscore.util.glow;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.util.*;

class TimedPlayerGlowTest {
    @Test
    void oldTimerCannotClearANewLifeAndClosingRestoresEveryTarget() throws Exception {
        try (var f = new Fixture()) {
            f.glow.show(f.viewer, List.of(f.target), 60);
            Runnable old = f.tasks.getFirst();
            f.glow.clearPlayer(f.viewer.getUniqueId());
            assertTrue(f.backend.visible.isEmpty());
            f.glow.show(f.viewer, List.of(f.target), 60);
            old.run();
            assertEquals(1, f.backend.visible.size());
            f.glow.clear();
            assertTrue(f.backend.visible.isEmpty());
            f.tasks.forEach(Runnable::run);
            assertTrue(f.backend.visible.isEmpty());
        }
    }

    @Test
    void departedTargetIsReleasedForAllRemainingViewers() throws Exception {
        try (var f = new Fixture()) {
            f.glow.show(f.viewer, List.of(f.target), 60);
            f.glow.clearPlayer(f.target.getUniqueId());
            assertTrue(f.backend.visible.isEmpty());
        }
    }

    static class Backend extends GlowingEntities {
        Set<String> visible;

        Backend() {
            super(null);
        }

        @Override
        public void setGlowing(Entity target, Player viewer) {
            visible.add(viewer.getUniqueId() + ":" + target.getUniqueId());
        }

        @Override
        public void unsetGlowing(Entity target, Player viewer) {
            visible.remove(viewer.getUniqueId() + ":" + target.getUniqueId());
        }
    }

    static class Fixture implements AutoCloseable {
        Object previous = field(Bukkit.class, "server").get(null);
        Map<UUID, Player> players = new HashMap<>();
        List<Runnable> tasks = new ArrayList<>();
        Player viewer = player(), target = player();
        Backend backend = allocate(Backend.class);
        TimedPlayerGlow glow;

        Fixture() throws Exception {
            backend.visible = new HashSet<>();
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "glowingEntities", backend);
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                tasks.add((Runnable) a[1]);
                                return proxy(BukkitTask.class, (t, n, b) -> null);
                            });
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) ->
                                            switch (m.getName()) {
                                                case "getPlayer" -> players.get(a[0]);
                                                case "getScheduler" -> scheduler;
                                                default -> throw new AssertionError(m.getName());
                                            }));
            glow = new TimedPlayerGlow(plugin);
        }

        Player player() {
            UUID id = UUID.randomUUID();
            Player p =
                    proxy(
                            Player.class,
                            (v, m, a) -> {
                                if (m.getName().equals("getUniqueId")) return id;
                                throw new AssertionError(m.getName());
                            });
            players.put(id, p);
            return p;
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previous);
        }
    }
}
