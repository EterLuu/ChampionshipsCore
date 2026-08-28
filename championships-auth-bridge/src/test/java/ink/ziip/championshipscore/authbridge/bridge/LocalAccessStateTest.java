package ink.ziip.championshipscore.authbridge.bridge;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalAccessStateTest {
    @Test
    void retainsOnlyAuthorizedIdentitiesAndVersions() throws Exception {
        File file = Files.createTempFile("authbridge-state", ".yml").toFile();
        try {
            LocalAccessState state = new LocalAccessState(file);
            UUID allowedUuid = UUID.randomUUID();
            UUID removedUuid = UUID.randomUUID();
            state.recordIdentity("Allowed", UUID.randomUUID().toString(), allowedUuid.toString());
            state.recordIdentity("Removed", UUID.randomUUID().toString(), removedUuid.toString());
            state.setAuthVersion("Allowed", 4);
            state.setAuthVersion("Removed", 7);

            state.retainIdentities(Set.of("allowed"));

            assertEquals(allowedUuid, state.expectedUuid("ALLOWED"));
            assertNull(state.expectedUuid("Removed"));
            assertEquals(4, state.authVersion("Allowed"));
            assertEquals(0, state.authVersion("Removed"));
            assertEquals(allowedUuid, new LocalAccessState(file).expectedUuid("Allowed"));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}
