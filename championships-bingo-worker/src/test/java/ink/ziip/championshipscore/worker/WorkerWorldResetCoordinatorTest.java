package ink.ziip.championshipscore.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerWorldResetCoordinatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesVersionedAtomicResetHandoff() throws Exception {
        Path world = Files.createDirectory(temporaryDirectory.resolve("bingo"));
        Path marker = temporaryDirectory.resolve(WorkerWorldResetCoordinator.RESET_MARKER_FILE);
        UUID resetId = UUID.fromString("15f444a9-444a-4187-9007-f89b7f21f341");

        Path retired = WorkerWorldResetCoordinator.writeResetMarker(marker, world, resetId);

        assertEquals(temporaryDirectory.resolve("bingo.cc-reset-" + resetId), retired);
        assertEquals(List.of("1", "bingo", "bingo.cc-reset-" + resetId), Files.readAllLines(marker));
        assertTrue(Files.isDirectory(world));
        try (var entries = Files.list(temporaryDirectory)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void rejectsMarkerOutsideWorldContainer() throws Exception {
        Path world = Files.createDirectory(temporaryDirectory.resolve("bingo"));
        Path otherDirectory = Files.createDirectory(temporaryDirectory.resolve("other"));

        assertThrows(IllegalArgumentException.class, () -> WorkerWorldResetCoordinator.writeResetMarker(
                otherDirectory.resolve(WorkerWorldResetCoordinator.RESET_MARKER_FILE), world, UUID.randomUUID()));
    }
}
