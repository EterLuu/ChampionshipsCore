package ink.ziip.championshipscore.api.rank;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

class PendingPointTransactionStoreTest {
    @TempDir Path directory;
    private final Logger logger = Logger.getLogger("point-store-test");

    private PlayerPointEntry entry(UUID transaction, double points) {
        return PlayerPointEntry.builder()
                .transactionId(transaction)
                .uuid(UUID.randomUUID())
                .username("Player")
                .teamId(1)
                .team("Red")
                .rivalId(1)
                .rival("Red")
                .game(GameTypeEnum.RiptideRush)
                .area("raft")
                .round("scc")
                .points(points)
                .time("2026-09-19")
                .build();
    }

    @Test
    void batchSurvivesRestartAndAcknowledgesAllTogether() {
        Path file = directory.resolve("pending.yml");
        var store = new PendingPointTransactionStore(file, logger);
        var a = entry(UUID.randomUUID(), 104);
        var b = entry(UUID.randomUUID(), 70);
        assertTrue(store.stageAll(List.of(a, b)));
        var restarted = new PendingPointTransactionStore(file, logger);
        assertEquals(
                Set.of(a.getTransactionId(), b.getTransactionId()),
                new HashSet<>(
                        restarted.load().stream()
                                .map(PlayerPointEntry::getTransactionId)
                                .toList()));
        restarted.completeAll(List.of(a.getTransactionId(), b.getTransactionId()));
        assertTrue(new PendingPointTransactionStore(file, logger).load().isEmpty());
    }

    @Test
    void failedDiskWriteRetainsFrozenBatchAndRetriesWhenStorageRecovers() throws Exception {
        Path parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        Path file = parent.resolve("pending.yml");
        var store = new PendingPointTransactionStore(file, logger);
        var original = entry(UUID.randomUUID(), 108);
        assertFalse(store.stageAll(List.of(original)));
        assertTrue(
                store.snapshotForRetry().isEmpty(),
                "Do not send an undurable batch to the database");
        Files.delete(parent);
        assertEquals(List.of(original), store.snapshotForRetry());
        assertTrue(store.stageAll(List.of(entry(original.getTransactionId(), 999))));
        var recovered = new PendingPointTransactionStore(file, logger).load();
        assertEquals(1, recovered.size());
        assertEquals(108, recovered.getFirst().getPoints());
        assertEquals(original.getUuid(), recovered.getFirst().getUuid());
    }

    @Test
    void invalidBatchDoesNotLeavePartiallyUnstagedEntries() {
        var store = new PendingPointTransactionStore(directory.resolve("pending.yml"), logger);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.stageAll(List.of(entry(UUID.randomUUID(), 4), entry(null, 70))));
        assertTrue(store.snapshotForRetry().isEmpty());
    }

    @Test
    void failedAcknowledgementPreservesIdempotentRetry() throws Exception {
        Path file = directory.resolve("pending.yml");
        var store = new PendingPointTransactionStore(file, logger);
        var original = entry(UUID.randomUUID(), 104);
        assertTrue(store.stage(original));
        Path temporary = directory.resolve("pending.yml.tmp");
        Files.createDirectory(temporary);
        Files.writeString(temporary.resolve("block"), "x");
        store.complete(original.getTransactionId());
        Files.delete(temporary.resolve("block"));
        Files.delete(temporary);
        assertEquals(List.of(original), store.snapshotForRetry());
        store.complete(original.getTransactionId());
        assertTrue(store.snapshotForRetry().isEmpty());
    }
}
