package ink.ziip.championshipscore.api.game.buildmart;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartCopperPolicyTest {
    @Test
    void waxIsRemovedWithoutChangingStageShapeOrState() {
        assertEquals("minecraft:weathered_copper_grate[waterlogged=false]",
                BuildMartCopperPolicy.withoutWax("minecraft:waxed_weathered_copper_grate[waterlogged=false]"));
        assertEquals("minecraft:stone", BuildMartCopperPolicy.withoutWax("minecraft:stone"));
    }

    @Test
    void blueprintSpecialsBecomePristineUnwaxedCopper() {
        assertEquals("minecraft:copper_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]",
                BuildMartCopperPolicy.normalizeBlueprint("minecraft:waxed_oxidized_copper_trapdoor"
                        + "[facing=north,half=top,open=false,powered=false,waterlogged=false]"));
        assertEquals("minecraft:copper_bars[east=false,north=true,south=false,waterlogged=false,west=false]",
                BuildMartCopperPolicy.normalizeBlueprint("minecraft:waxed_weathered_copper_bars"
                        + "[east=false,north=true,south=false,waterlogged=false,west=false]"));
        assertEquals("minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]",
                BuildMartCopperPolicy.normalizeBlueprint(
                        "minecraft:oxidized_lightning_rod[facing=up,powered=false,waterlogged=false]"));
        assertEquals("minecraft:copper_door[facing=east,half=lower,hinge=left,open=false,powered=false]",
                BuildMartCopperPolicy.normalizeBlueprint("minecraft:waxed_weathered_copper_door"
                        + "[facing=east,half=lower,hinge=left,open=false,powered=false]"));
        assertEquals("minecraft:copper_chain[axis=y,waterlogged=false]",
                BuildMartCopperPolicy.normalizeBlueprint(
                        "minecraft:exposed_copper_chain[axis=y,waterlogged=false]"));
        assertEquals("minecraft:copper_chest[facing=north,type=single,waterlogged=false]",
                BuildMartCopperPolicy.normalizeBlueprint(
                        "minecraft:oxidized_copper_chest[facing=north,type=single,waterlogged=false]"));
        assertEquals("minecraft:copper_golem_statue[pose=standing]",
                BuildMartCopperPolicy.normalizeBlueprint(
                        "minecraft:weathered_copper_golem_statue[pose=standing]"));
        assertEquals("minecraft:copper_lantern[hanging=true,waterlogged=false]",
                BuildMartCopperPolicy.normalizeBlueprint(
                        "minecraft:waxed_oxidized_copper_lantern[hanging=true,waterlogged=false]"));
    }

    @Test
    void onlyForwardOxidationOfTheSameShapeIsBlocked() {
        assertTrue(BuildMartCopperPolicy.isForwardOxidation(Material.COPPER_BLOCK, Material.EXPOSED_COPPER));
        assertTrue(BuildMartCopperPolicy.isForwardOxidation(
                Material.WEATHERED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_STAIRS));
        assertFalse(BuildMartCopperPolicy.isForwardOxidation(Material.OXIDIZED_COPPER, Material.WEATHERED_COPPER));
        assertFalse(BuildMartCopperPolicy.isForwardOxidation(Material.COPPER_BLOCK, Material.EXPOSED_CUT_COPPER));
        assertFalse(BuildMartCopperPolicy.isForwardOxidation(Material.STONE, Material.EXPOSED_COPPER));
    }
}
