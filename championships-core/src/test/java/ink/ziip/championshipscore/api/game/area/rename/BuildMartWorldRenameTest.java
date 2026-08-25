package ink.ziip.championshipscore.api.game.area.rename;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartWorldRenameTest {
    @Test
    void onlyAnUnsharedDefaultWorldFollowsTheMapRegistration() {
        assertTrue(BuildMartWorldRename.ownsDefaultWorld("area", "buildmart_area", List.of()));
        assertFalse(BuildMartWorldRename.ownsDefaultWorld("area", "buildmart_area",
                List.of("buildmart_area")));
        assertFalse(BuildMartWorldRename.ownsDefaultWorld("area", "buildmart_shared", List.of()));
    }
}
