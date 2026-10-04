package ink.ziip.championshipscore.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatabaseMigrationControllerTest {
    @Test
    void registryVersionsAreGaplessUniqueAndAscending() {
        int previous = 0;
        for (DatabaseMigration migration : DatabaseMigrationController.registry()) {
            assertEquals(
                    previous + 1,
                    migration.version(),
                    "versions must be appended without gaps: " + migration);
            assertFalse(migration.name().isBlank());
            previous = migration.version();
        }
        assertTrue(previous >= 7, "baseline plus post-controller migrations expected");
    }
}
