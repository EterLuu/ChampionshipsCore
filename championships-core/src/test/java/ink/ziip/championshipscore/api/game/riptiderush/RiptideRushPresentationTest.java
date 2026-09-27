package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RiptideRushPresentationTest {
    @Test
    void movingAndPausedRaftNeverOverwritePlayerMovement() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ink/ziip/championshipscore/api/game/riptiderush/RiptideRushArea.java"));
        String movement = source.substring(source.indexOf("private void tickCourse()"),
                source.indexOf("private Material trailMaterial()"));
        assertFalse(movement.contains("setVelocity("));
        assertFalse(movement.contains("teleport("));
        assertFalse(movement.contains("applyForwardMotion"));
        assertTrue(movement.contains("shiftRaftOneBlock()"));
    }
}
