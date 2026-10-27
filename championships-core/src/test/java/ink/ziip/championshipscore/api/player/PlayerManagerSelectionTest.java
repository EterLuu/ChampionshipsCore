package ink.ziip.championshipscore.api.player;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidLookupException;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidSource;
import ink.ziip.championshipscore.database.player.PlayerDao;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

class PlayerManagerSelectionTest {
    private static class Lookup extends PlayerManager {
        UUID resolved;
        int lookups;

        Lookup() {
            super(null);
        }

        @Override
        public UUID resolveConfiguredUuid(String name) {
            lookups++;
            return resolved;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final List<Player> online = new ArrayList<>();
        final List<Runnable> queued = new ArrayList<>();
        final Lookup players = allocate(Lookup.class);
        final ConcurrentHashMap<String, UUID> ids = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();
        int synchronizedIdentities;

        Fixture() throws Exception {
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("runTaskAsynchronously"))
                                    queued.add((Runnable) a[1]);
                                else throw new AssertionError(m.getName());
                                return null;
                            });
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getOnlinePlayers" -> online;
                                        case "getScheduler" -> scheduler;
                                        default -> throw new AssertionError(m.getName());
                                    });
            field(Bukkit.class, "server").set(null, server);
            var core = allocate(ChampionshipsCore.class);
            set(core, "server", server);
            set(players, "plugin", core);
            set(players, "uuidSource", PlayerUuidSource.PROFILE_UUID);
            set(players, "cachedPlayerUUID", ids);
            set(players, "cachedPlayerName", names);
            set(
                    players,
                    "playerDao",
                    proxy(
                            PlayerDao.class,
                            (p, m, a) -> {
                                if (!m.getName().equals("synchronizeIdentity"))
                                    throw new AssertionError(m.getName());
                                synchronizedIdentities++;
                                return new PlayerIdentityMigrationResult(
                                        (String) a[0],
                                        (UUID) a[1],
                                        Set.of(),
                                        null,
                                        Set.of(),
                                        0,
                                        false,
                                        true,
                                        null);
                            }));
        }

        void online(UUID id, String name) {
            online.add(
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> name;
                                        default -> throw new AssertionError(m.getName());
                                    }));
        }

        @Override
        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previous);
        }
    }

    @Test
    void selectedOnlineNameCannotResolveToAReplacementAccount() throws Exception {
        try (var f = new Fixture()) {
            UUID selected = UUID.randomUUID(), replacement = UUID.randomUUID();
            f.online(replacement, "PlayerOne");
            var failure =
                    assertThrows(
                            CompletionException.class,
                            () ->
                                    f.players
                                            .resolvePlayerUUID("PlayerOne", selected)
                                            .toCompletableFuture()
                                            .join());
            assertEquals(
                    PlayerUuidLookupException.Reason.IDENTITY_CONFLICT,
                    ((PlayerUuidLookupException) failure.getCause()).reason());
            assertTrue(f.ids.isEmpty());
            assertTrue(f.names.isEmpty());
            assertEquals(0, f.synchronizedIdentities);
            assertEquals(0, f.players.lookups);
        }
    }

    @Test
    void selectedOfflineNameIsCheckedBeforeSynchronizingPersistentIdentity() throws Exception {
        try (var f = new Fixture()) {
            UUID selected = UUID.randomUUID();
            f.players.resolved = UUID.randomUUID();
            var result = f.players.resolvePlayerUUID("PlayerOne", selected).toCompletableFuture();
            assertFalse(result.isDone());
            f.queued.removeFirst().run();
            var failure = assertThrows(CompletionException.class, result::join);
            assertEquals(
                    PlayerUuidLookupException.Reason.IDENTITY_CONFLICT,
                    ((PlayerUuidLookupException) failure.getCause()).reason());
            assertEquals(0, f.synchronizedIdentities);
            assertTrue(f.ids.isEmpty());
            assertTrue(f.names.isEmpty());
        }
    }

    @Test
    void matchingSelectionsAndNameOnlyInputUseTheExistingIdentityPolicy() throws Exception {
        try (var f = new Fixture()) {
            UUID selected = UUID.randomUUID();
            f.players.resolved = selected;
            var offline = f.players.resolvePlayerUUID("PlayerOne", selected).toCompletableFuture();
            f.queued.removeFirst().run();
            assertEquals(selected, offline.join());
            assertEquals(1, f.synchronizedIdentities);
            assertEquals("PlayerOne", f.names.get(selected));
            f.online(selected, "PlayerOne");
            assertEquals(
                    selected,
                    f.players
                            .resolvePlayerUUID("playerone", selected)
                            .toCompletableFuture()
                            .join());
            assertEquals(
                    selected,
                    f.players.resolvePlayerUUID("PlayerOne").toCompletableFuture().join());
            assertEquals(1, f.players.lookups);
            assertEquals(1, f.synchronizedIdentities);
        }
    }
}
