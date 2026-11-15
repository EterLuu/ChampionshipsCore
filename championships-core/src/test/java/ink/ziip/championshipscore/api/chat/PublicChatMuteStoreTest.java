package ink.ziip.championshipscore.api.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

class PublicChatMuteStoreTest {
    @TempDir Path directory;

    @Test
    void absoluteExpirySurvivesRestartAndElapsesWhileOffline() throws Exception {
        var clock = new MutableClock();
        var file = directory.resolve("mutes.yml");
        var store = new PublicChatMuteStore(file, clock);
        UUID id = UUID.randomUUID();
        store.mute(id, "Player", 60_000, "spam", "Console");
        clock.now += 30_000;
        var restarted = new PublicChatMuteStore(file, clock);
        restarted.load();
        assertEquals(61_000, restarted.activeMute(id).expiresAt());
        clock.now = 61_000;
        assertNull(restarted.activeMute(id));
        assertTrue(restarted.activeMutes().isEmpty());
        restarted.load();
        assertNull(restarted.activeMute(id));
    }

    @Test
    void permanentMutesAndUnmutePersistAndLaterActionsReplaceEarlierState() throws Exception {
        var clock = new MutableClock();
        var file = directory.resolve("mutes.yml");
        var store = new PublicChatMuteStore(file, clock);
        UUID id = UUID.randomUUID();
        store.mute(id, "Player", 60_000, "first reason", "Admin");
        store.mute(id, "Player", 0, "永久原因", "Console");
        clock.now += 1_000_000;
        var restarted = new PublicChatMuteStore(file, clock);
        restarted.load();
        assertTrue(restarted.activeMute(id).permanent());
        assertEquals("永久原因", restarted.activeMute(id).reason());
        restarted.mute(id, "RenamedPlayer", 5_000, "new reason", "Admin");
        assertFalse(restarted.activeMute(id).permanent());
        assertEquals("RenamedPlayer", restarted.activeMute(id).playerName());
        assertTrue(restarted.unmute(id));
        assertFalse(restarted.unmute(id));
        store.load();
        assertNull(store.activeMute(id));
    }

    @Test
    void failedWritesPreserveTheLastCommittedRuntimeState() throws Exception {
        var file = directory.resolve("mutes.yml");
        var store = new PublicChatMuteStore(file, new MutableClock());
        UUID id = UUID.randomUUID();
        var original = store.mute(id, "Player", 0, "original", "Admin");
        Files.move(file, directory.resolve("original.yml"));
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "cannot replace this directory");
        assertThrows(
                IOException.class,
                () -> store.mute(id, "Player", 60_000, "replacement", "Console"));
        assertEquals(original, store.activeMute(id));
        assertThrows(IOException.class, () -> store.unmute(id));
        assertEquals(original, store.activeMute(id));
    }

    @Test
    void malformedReloadDoesNotEraseCommittedMutes() throws Exception {
        var file = directory.resolve("mutes.yml");
        var store = new PublicChatMuteStore(file, new MutableClock());
        UUID id = UUID.randomUUID();
        var original = store.mute(id, "Player", 0, "original", "Admin");
        Files.writeString(file, "mutes:\n  " + id + ":\n    name: Player\n");
        assertThrows(IOException.class, store::load);
        assertEquals(original, store.activeMute(id));
        Files.writeString(
                file,
                "mutes:\n  "
                        + id
                        + ":\n    name: Player\n    reason: original\n"
                        + "    actor: Admin\n    created-at: 1000\n    expires-at: 0.5\n");
        assertThrows(IOException.class, store::load);
        assertEquals(original, store.activeMute(id));
    }

    private static final class MutableClock extends Clock {
        long now = 1_000;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now);
        }
    }
}
