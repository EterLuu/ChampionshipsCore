package ink.ziip.championshipscore.api.game.bingo.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TaskGeneratorTest {
    @Test
    void wildernessBoundShapesShareTheirDyeColorWithExistingObjectives() {
        for (DyeColor color : DyeColor.values()) {
            for (String family :
                    new String[] {
                        "WOOL",
                        "WOOL_SLAB",
                        "WOOL_STAIRS",
                        "CUSHION",
                        "CONCRETE",
                        "CONCRETE_SLAB",
                        "CONCRETE_STAIRS",
                        "BED",
                        "DYE"
                    }) {
                Material material = Material.valueOf(color.name() + "_" + family);
                assertEquals(
                        "color:" + color.name(),
                        TaskGenerator.colorKeyFor(material),
                        material.name());
            }
        }
    }

    @Test
    void strawBedsAndPoplarFoliageDoNotClaimADyeColor() {
        for (Material material :
                new Material[] {
                    Material.STRAW_BED, Material.POPLAR_STAIRS, Material.RED_POPLAR_LEAVES,
                    Material.ORANGE_POPLAR_LEAVES, Material.YELLOW_POPLAR_LEAVES, Material.CANDLE
                }) {
            assertNull(TaskGenerator.colorKeyFor(material), material.name());
        }
    }
}
