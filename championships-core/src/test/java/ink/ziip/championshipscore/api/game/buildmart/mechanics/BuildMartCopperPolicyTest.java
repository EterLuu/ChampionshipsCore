package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Map;

class BuildMartCopperPolicyTest {
    @Test
    void waxIsRemovedWithoutChangingStageShapeOrState() {
        assertEquals(
                "minecraft:weathered_copper_grate[waterlogged=false]",
                BuildMartCopperPolicy.withoutWax(
                        "minecraft:waxed_weathered_copper_grate[waterlogged=false]"));
        assertEquals("minecraft:stone", BuildMartCopperPolicy.withoutWax("minecraft:stone"));
    }

    @Test
    void blueprintSpecialsBecomePristineUnwaxedCopper() {
        var expected =
                Map.of(
                        "minecraft:waxed_oxidized_copper_trapdoor"
                            + "[facing=north,half=top,open=false,powered=false,waterlogged=false]",
                        "minecraft:copper_trapdoor"
                            + "[facing=north,half=top,open=false,powered=false,waterlogged=false]",
                        "minecraft:waxed_weathered_copper_bars"
                            + "[east=false,north=true,south=false,waterlogged=false,west=false]",
                        "minecraft:copper_bars"
                            + "[east=false,north=true,south=false,waterlogged=false,west=false]",
                        "minecraft:oxidized_lightning_rod[facing=up,powered=false,waterlogged=false]",
                        "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]",
                        "minecraft:waxed_weathered_copper_door"
                                + "[facing=east,half=lower,hinge=left,open=false,powered=false]",
                        "minecraft:copper_door"
                                + "[facing=east,half=lower,hinge=left,open=false,powered=false]",
                        "minecraft:exposed_copper_chain[axis=y,waterlogged=false]",
                        "minecraft:copper_chain[axis=y,waterlogged=false]",
                        "minecraft:oxidized_copper_chest[facing=north,type=single,waterlogged=false]",
                        "minecraft:copper_chest[facing=north,type=single,waterlogged=false]",
                        "minecraft:weathered_copper_golem_statue[pose=standing]",
                        "minecraft:copper_golem_statue[pose=standing]",
                        "minecraft:waxed_oxidized_copper_lantern[hanging=true,waterlogged=false]",
                        "minecraft:copper_lantern[hanging=true,waterlogged=false]");
        assertAll(
                expected.entrySet().stream()
                        .map(
                                entry ->
                                        (Executable)
                                                () ->
                                                        assertEquals(
                                                                entry.getValue(),
                                                                BuildMartCopperPolicy
                                                                        .normalizeBlueprint(
                                                                                entry.getKey()),
                                                                entry.getKey())));
    }

    @Test
    void onlyForwardOxidationOfTheSameShapeIsBlocked() {
        assertTrue(
                BuildMartCopperPolicy.isForwardOxidation(
                        Material.COPPER_BLOCK, Material.EXPOSED_COPPER));
        assertTrue(
                BuildMartCopperPolicy.isForwardOxidation(
                        Material.WEATHERED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_STAIRS));
        assertFalse(
                BuildMartCopperPolicy.isForwardOxidation(
                        Material.OXIDIZED_COPPER, Material.WEATHERED_COPPER));
        assertFalse(
                BuildMartCopperPolicy.isForwardOxidation(
                        Material.COPPER_BLOCK, Material.EXPOSED_CUT_COPPER));
        assertFalse(
                BuildMartCopperPolicy.isForwardOxidation(Material.STONE, Material.EXPOSED_COPPER));
    }
}
