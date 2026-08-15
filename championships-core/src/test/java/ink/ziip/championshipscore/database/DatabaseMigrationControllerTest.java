package ink.ziip.championshipscore.database;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationControllerTest {
    @Test
    void registryVersionsAreGaplessUniqueAndAscending() {
        Set<Integer> seen = new HashSet<>();
        int previous = 0;
        for (DatabaseMigration migration : DatabaseMigrationController.registry()) {
            assertEquals(previous + 1, migration.version(),
                    "versions must be appended without gaps: " + migration);
            assertTrue(seen.add(migration.version()), "duplicate version: " + migration.version());
            assertTrue(!migration.name().isBlank());
            previous = migration.version();
        }
        assertTrue(previous >= 7, "baseline plus post-controller migrations expected");
    }
}
