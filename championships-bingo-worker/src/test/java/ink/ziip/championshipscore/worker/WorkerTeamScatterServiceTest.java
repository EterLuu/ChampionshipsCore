package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

class WorkerTeamScatterServiceTest {
    @Test
    void sixtyFourPlayersReceiveExactlySixteenTeamDestinations() {
        AtomicInteger searches = new AtomicInteger();
        Map<Player, Location> targets = new HashMap<>();
        WorkerTeamScatterService service =
                new WorkerTeamScatterService(
                        (world, area, tries) -> {
                            searches.incrementAndGet();
                            return CompletableFuture.completedFuture(location(area));
                        },
                        (player, destination) -> {
                            targets.put(player, destination.clone());
                            // The teleport adapter cannot mutate the cached destination for other
                            // teammates.
                            destination.add(100, 0, 100);
                            return CompletableFuture.completedFuture(true);
                        });
        service.prepareAsync(null, IntStream.range(0, 16).boxed().toList(), 32).join();

        for (int team = 0; team < 16; team++) {
            Location first = null;
            for (int member = 0; member < 4; member++) {
                Player player = player(UUID.randomUUID(), true);
                service.teleportAsync(player, team).join();
                assertTrue(service.hasPlacedPlayer(player.getUniqueId()));
                if (first == null) first = targets.get(player);
                else assertEquals(first, targets.get(player));
            }
        }
        assertEquals(16, searches.get());
        assertEquals(16, targets.values().stream().distinct().count());
        assertEquals(64, targets.size());
    }

    @Test
    void pendingArrivalsShareTheTeamSearchAndDuplicateEntityRequestsShareTheTeleport() {
        CompletableFuture<Location> location = new CompletableFuture<>();
        CompletableFuture<Boolean> accepted = new CompletableFuture<>();
        AtomicInteger teleports = new AtomicInteger();
        WorkerTeamScatterService service =
                new WorkerTeamScatterService(
                        (world, area, tries) -> location,
                        (player, destination) -> {
                            teleports.incrementAndGet();
                            return accepted;
                        });
        CompletableFuture<Void> prepared = service.prepareAsync(null, List.of(42), 32);
        Player player = player(UUID.randomUUID(), true);
        CompletableFuture<Void> first = service.teleportAsync(player, 42);
        CompletableFuture<Void> duplicate = service.teleportAsync(player, 42);
        assertFalse(prepared.isDone());
        assertFalse(first.isDone());
        assertEquals(0, teleports.get());

        location.complete(new Location(null, 123.5, 70, -456.5));
        assertTrue(prepared.isDone());
        assertFalse(first.isDone());
        assertEquals(1, teleports.get());
        accepted.complete(true);
        first.join();
        duplicate.join();
        service.teleportAsync(player(player.getUniqueId(), true), 42).join();
        assertEquals(2, teleports.get());
    }

    @Test
    void limitsConcurrentTerrainSearchesAndWaitsForEveryTeam() {
        List<CompletableFuture<Location>> searches = new ArrayList<>();
        AtomicInteger active = new AtomicInteger();
        WorkerTeamScatterService service =
                new WorkerTeamScatterService(
                        (world, area, tries) -> {
                            assertTrue(active.incrementAndGet() <= 4);
                            CompletableFuture<Location> result = new CompletableFuture<>();
                            searches.add(result);
                            return result;
                        },
                        (player, target) -> CompletableFuture.completedFuture(true));
        CompletableFuture<Void> prepared =
                service.prepareAsync(null, IntStream.range(0, 16).boxed().toList(), 32);
        assertEquals(4, searches.size());
        for (int index = 0; index < 16; index++) {
            assertFalse(prepared.isDone());
            active.decrementAndGet();
            searches.get(index).complete(new Location(null, index, 70, index));
        }
        prepared.join();
        assertEquals(16, searches.size());
        assertEquals(0, active.get());
    }

    @Test
    void retriesOceanSearchesWithinLargerIsolatedTeamTerritories() {
        List<WorkerScatterPlan.SearchArea> attempts = new ArrayList<>();
        List<Location> targets = new ArrayList<>();
        WorkerTeamScatterService service =
                new WorkerTeamScatterService(
                        (world, area, tries) -> {
                            attempts.add(area);
                            return attempts.size() % 2 == 1
                                    ? CompletableFuture.failedFuture(
                                            new IllegalStateException("Ocean"))
                                    : CompletableFuture.completedFuture(location(area));
                        },
                        (player, destination) -> {
                            targets.add(destination);
                            return CompletableFuture.completedFuture(true);
                        });
        service.prepareAsync(null, IntStream.range(0, 16).boxed().toList(), 32).join();
        for (int index = 0; index < attempts.size(); index += 2) {
            var preferred = attempts.get(index);
            var expanded = attempts.get(index + 1);
            assertEquals(WorkerScatterPlan.expand(preferred, 16), expanded);
            assertTrue(expanded.minX() < preferred.minX() || expanded.maxX() > preferred.maxX());
        }
        for (int team = 0; team < 16; team++)
            service.teleportAsync(player(UUID.randomUUID(), true), team).join();
        assertEquals(32, attempts.size());
        assertEquals(16, targets.stream().distinct().count());
    }

    @Test
    void failedSearchOrRejectedTeleportDoesNotCountAsSuccessfulPlacement() {
        WorkerTeamScatterService failed =
                new WorkerTeamScatterService(
                        (world, area, tries) ->
                                CompletableFuture.failedFuture(
                                        new IllegalStateException("No safe terrain")),
                        (player, target) -> {
                            throw new AssertionError("Must not fall back to shared spawn");
                        });
        assertThrows(
                CompletionException.class, () -> failed.prepareAsync(null, List.of(0), 32).join());
        Player player = player(UUID.randomUUID(), true);
        assertThrows(CompletionException.class, () -> failed.teleportAsync(player, 0).join());
        assertFalse(failed.hasPlacedPlayer(player.getUniqueId()));

        WorkerTeamScatterService rejected =
                new WorkerTeamScatterService(
                        (world, area, tries) -> CompletableFuture.completedFuture(location(area)),
                        (entity, target) -> CompletableFuture.completedFuture(false));
        rejected.prepareAsync(null, List.of(0), 32).join();
        assertThrows(CompletionException.class, () -> rejected.teleportAsync(player, 0).join());
        assertFalse(rejected.hasPlacedPlayer(player.getUniqueId()));
        rejected.teleportAsync(player(UUID.randomUUID(), false), 0).join();
    }

    @Test
    void cancellationCompletesPendingSearchesAndPreventsLateTeleports() {
        CompletableFuture<Location> search = new CompletableFuture<>();
        WorkerTeamScatterService service =
                new WorkerTeamScatterService(
                        (world, area, tries) -> search,
                        (player, target) -> {
                            throw new AssertionError("Cancelled match cannot teleport");
                        });
        CompletableFuture<Void> prepared = service.prepareAsync(null, List.of(0, 1, 2, 3, 4), 32);
        CompletableFuture<Void> arrival = service.teleportAsync(player(UUID.randomUUID(), true), 0);
        service.cancelPending();
        assertTrue(prepared.isCompletedExceptionally());
        assertTrue(arrival.isCompletedExceptionally());
        search.complete(new Location(null, 0, 70, 0));
        assertTrue(
                service.teleportAsync(player(UUID.randomUUID(), true), 0)
                        .isCompletedExceptionally());
    }

    private static Location location(WorkerScatterPlan.SearchArea area) {
        return new Location(
                null, (area.minX() + area.maxX()) / 2.0, 70, (area.minZ() + area.maxZ()) / 2.0);
    }

    private static Player player(UUID uuid, boolean online) {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> uuid;
                                    case "isOnline" -> online;
                                    case "equals" -> proxy == arguments[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "toString" -> uuid.toString();
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected player access: "
                                                            + method.getName());
                                });
    }
}
