package ink.ziip.championshipscore.platform.bukkit.player;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.threadedregions.scheduler.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

class PlayerRespawnRecoveryTest {
    @Test
    void deathWaitsForNativeLifeAndEventDoesNotDuplicateRecovery() {
        var f = new Fixture();
        f.dead = true;
        f.health = 0;
        var first = f.death();
        assertSame(
                first,
                f.service.respawn(
                        f.player,
                        f.owner,
                        () -> f.current,
                        () -> f.destination,
                        () -> f.restores++));
        f.run(0);
        assertFalse(first.isDone());
        assertEquals(0, f.restores);
        f.dead = false;
        f.health = 20;
        f.run(1);
        assertTrue(first.join());
        assertEquals(1, f.restores);
        f.run(1);
        assertEquals(1, f.restores);
    }

    @Test
    void newerDeathInvalidatesAnOlderWarmAndRetiredCallback() {
        var f = new Fixture();
        f.destination = new Location(f.other, 2, 70, 3);
        var old = f.death();
        f.run(0);
        var cold = f.warm;
        f.warm = new CompletableFuture<>();
        var next = f.death();
        assertFalse(old.join());
        cold.complete(null);
        f.retired.get(0).run();
        assertEquals(0, f.moves);
        assertFalse(next.isDone());
        f.run(1);
        f.warm.complete(null);
        f.run(2);
        assertEquals(1, f.moves);
        f.teleport.complete(true);
        f.run(3);
        assertTrue(next.join());
        assertEquals(1, f.restores);
    }

    @Test
    void ownerConnectionDisableAndCloseAreRecheckedAfterChunkWarmBeforeTeleport() {
        for (String cause : List.of("owner", "connection", "disable", "clear", "close")) {
            var f = new Fixture();
            f.destination = new Location(f.other, 2, 70, 3);
            var result = f.death();
            f.run(0);
            switch (cause) {
                case "owner" -> f.current = false;
                case "connection" -> f.online = f.newPlayer();
                case "disable" -> f.enabled = false;
                case "clear" -> f.service.clear();
                case "close" -> f.service.close();
            }
            f.warm.complete(null);
            if (f.tasks.size() > 1) f.run(1);
            assertFalse(result.join(), cause);
            assertEquals(0, f.moves, cause);
            assertEquals(0, f.restores, cause);
        }
    }

    @Test
    void lateTeleportCompletionCannotRestoreIntoANewOwner() {
        var f = new Fixture();
        f.destination = new Location(f.other, 2, 70, 3);
        var result = f.death();
        f.run(0);
        f.warm.complete(null);
        f.run(1);
        assertEquals(1, f.moves);
        f.current = false;
        f.teleport.complete(true);
        f.run(2);
        assertFalse(result.join());
        assertEquals(0, f.restores);
    }

    @Test
    void oldConnectionCancellationDoesNotConsumeTheReconnectedRequest() {
        var f = new Fixture();
        var old = f.death();
        Player previous = f.player;
        f.player = f.newPlayer();
        f.online = f.player;
        var next = f.death();
        f.service.cancel(previous);
        f.run(0);
        f.retired.get(0).run();
        assertFalse(old.join());
        assertFalse(next.isDone());
        f.run(1);
        assertTrue(next.join());
        assertEquals(1, f.restores);
    }

    @Test
    void rejectedRetiredAndFailedSchedulesFinishAndAllowFreshRequests() {
        for (String cause : List.of("reject", "retired", "throw")) {
            var f = new Fixture();
            f.reject = cause.equals("reject");
            f.throwSchedule = cause.equals("throw");
            var result = f.death();
            if (cause.equals("retired")) f.retired.get(0).run();
            if (cause.equals("throw")) assertThrows(CompletionException.class, result::join);
            else assertFalse(result.join());
            f.reject = false;
            f.throwSchedule = false;
            var next = f.death();
            f.run(1);
            assertTrue(next.join());
            assertEquals(1, f.restores);
        }
    }

    @Test
    void clearAndRestoreFailureDoNotReanimateOldWorkOrBlockTheNextDeath() {
        var f = new Fixture();
        var old = f.death();
        f.service.clear();
        assertFalse(old.join());
        var next =
                f.service.death(
                        f.player,
                        f.owner,
                        () -> true,
                        () -> null,
                        () -> {
                            throw new IllegalStateException("restore");
                        });
        f.run(0);
        f.run(1);
        assertThrows(CompletionException.class, next::join);
        var last = f.death();
        f.run(2);
        assertTrue(last.join());
        f.service.close();
        assertFalse(f.death().join());
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final Object owner = new Object();
        final List<Runnable> tasks = new ArrayList<>(), retired = new ArrayList<>();
        final EntityScheduler scheduler;
        final World world = world("main"), other = world("nether");
        final PlayerRespawnRecovery service;
        Player player, online;
        Location location = new Location(world, 0, 70, 0), destination = location;
        CompletableFuture<Chunk> warm = new CompletableFuture<>();
        CompletableFuture<Boolean> teleport = new CompletableFuture<>();
        boolean dead, current = true, enabled = true, reject, throwSchedule;
        double health = 20;
        int restores, moves;

        Fixture() {
            scheduler =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                assertEquals("runDelayed", m.getName());
                                var callback = (Consumer<ScheduledTask>) a[1];
                                tasks.add(() -> callback.accept(null));
                                retired.add((Runnable) a[2]);
                                if (throwSchedule) throw new IllegalStateException("schedule");
                                return reject
                                        ? null
                                        : proxy(ScheduledTask.class, (p2, m2, a2) -> null);
                            });
            player = newPlayer();
            online = player;
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                assertEquals("getPlayer", m.getName());
                                return online;
                            });
            Plugin plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isEnabled" -> enabled;
                                        case "getServer" -> server;
                                        default -> throw new AssertionError(m.getName());
                                    });
            service = new PlayerRespawnRecovery(plugin);
        }

        World world(String name) {
            return proxy(
                    World.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getName" -> name;
                                case "getChunkAtAsync" -> warm;
                                default -> throw new AssertionError(m.getName());
                            });
        }

        Player newPlayer() {
            return proxy(
                    Player.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getUniqueId" -> id;
                                case "getScheduler" -> scheduler;
                                case "isOnline" -> true;
                                case "isDead" -> dead;
                                case "getHealth" -> health;
                                case "getLocation" -> location;
                                case "teleportAsync" -> {
                                    moves++;
                                    location = (Location) a[0];
                                    yield teleport;
                                }
                                default -> throw new AssertionError(m.getName());
                            });
        }

        CompletableFuture<Boolean> death() {
            return service.death(player, owner, () -> current, () -> destination, () -> restores++);
        }

        void run(int index) {
            tasks.get(index).run();
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "equals" -> p == a[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    case "toString" -> type.getSimpleName();
                                    default -> handler.invoke(p, m, a);
                                }));
    }
}
