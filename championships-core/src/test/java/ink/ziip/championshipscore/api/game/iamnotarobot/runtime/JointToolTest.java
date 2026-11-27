package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class JointToolTest {
    @Test
    void pickConfiguresReachAndConcreteBreakingWithoutSpeedModifier() throws Exception {
        Path path =
                Path.of(
                        "src/main/java/ink/ziip/championshipscore/api/game/iamnotarobot/runtime/JointTool.java");
        if (!Files.exists(path)) path = Path.of("championships-core").resolve(path);
        String source = Files.readString(path);
        assertTrue(source.contains("new ItemStack(Material.IRON_PICKAXE)"));
        assertTrue(source.contains("meta.setUnbreakable(true)"));
        String reach =
                source.substring(
                        source.indexOf("Attribute.BLOCK_INTERACTION_RANGE"),
                        source.indexOf("pick.setData("));
        assertTrue(reach.contains("2.5,"));
        assertTrue(reach.contains("EquipmentSlotGroup.MAINHAND"));
        String predicate =
                source.substring(source.indexOf("pick.setData("), source.indexOf("return pick;"));
        assertTrue(predicate.contains("DataComponentTypes.CAN_BREAK"));
        assertTrue(predicate.contains("Material.GRAY_CONCRETE"));
        assertFalse(source.contains("Attribute.BLOCK_BREAK_SPEED"));
        assertFalse(source.contains("BREAK_SPEED_BONUS"));
    }
}
