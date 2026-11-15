package ink.ziip.championshipscore.redis;

import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.database.sync.DatabaseSyncDomain;
import ink.ziip.championshipscore.support.TestObjects;

import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class RedisManagerLifecycleTest {
    @Test
    void outageReconciliationKeepsPendingInvalidations() throws Exception {
        RedisManager manager = new RedisManager(null);
        set(manager, "configuredEnabled", true);
        manager.publishDatabaseChange("pending", DatabaseSyncDomain.RANK);
        Queue<?> pending = (Queue<?>) field(manager, "pendingPublications");
        assertEquals(1, pending.size());
        var flush = RedisManager.class.getDeclaredMethod("flushPendingPublications");
        flush.setAccessible(true);
        flush.invoke(manager);
        assertEquals(1, pending.size(), "No connected publisher must not consume the queue");
    }

    @Test
    void unloadFailsReadinessAndRejectsFurtherPublications() throws Exception {
        RedisManager manager = new RedisManager(null);
        set(manager, "configuredEnabled", true);
        var waiting = manager.whenReady().toCompletableFuture();
        manager.publishDatabaseChange("before-stop", DatabaseSyncDomain.RANK);
        manager.unload();
        assertTrue(waiting.isCompletedExceptionally());
        assertFalse(manager.isReady());
        manager.publishDatabaseChange("after-stop", DatabaseSyncDomain.RANK);
        assertTrue(((Queue<?>) field(manager, "pendingPublications")).isEmpty());
    }

    @Test
    void oldConnectionCompletionCannotResetNewAttempt() throws Exception {
        RedisManager manager = new RedisManager(null);
        ((AtomicLong) field(manager, "lifecycleGeneration")).set(2);
        AtomicBoolean connecting = (AtomicBoolean) field(manager, "connecting");
        connecting.set(true);
        var finish =
                java.util.Arrays.stream(RedisManager.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("finishConnectionAttempt"))
                        .findFirst()
                        .orElseThrow();
        finish.setAccessible(true);
        finish.invoke(manager, 1L, null, null, null, null);
        assertTrue(connecting.get());
        assertFalse(manager.isReady());
        assertFalse(manager.whenReady().toCompletableFuture().isDone());
        manager.unload();
    }

    private static Object field(RedisManager manager, String name) throws Exception {
        return TestObjects.field(RedisManager.class, name).get(manager);
    }
}
