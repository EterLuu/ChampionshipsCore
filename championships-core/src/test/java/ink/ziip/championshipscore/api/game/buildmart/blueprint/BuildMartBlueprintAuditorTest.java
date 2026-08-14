package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildMartBlueprintAuditorTest {
    @Test
    void difficultyBandsHaveStableBoundaries() {
        assertEquals(1, BuildMartBlueprintAuditor.suggestedStars(0));
        assertEquals(1, BuildMartBlueprintAuditor.suggestedStars(19.9));
        assertEquals(2, BuildMartBlueprintAuditor.suggestedStars(20));
        assertEquals(2, BuildMartBlueprintAuditor.suggestedStars(39.9));
        assertEquals(3, BuildMartBlueprintAuditor.suggestedStars(40));
        assertEquals(3, BuildMartBlueprintAuditor.suggestedStars(59.9));
        assertEquals(4, BuildMartBlueprintAuditor.suggestedStars(60));
        assertEquals(4, BuildMartBlueprintAuditor.suggestedStars(79.9));
        assertEquals(5, BuildMartBlueprintAuditor.suggestedStars(80));
        assertEquals(5, BuildMartBlueprintAuditor.suggestedStars(100));
    }

    @Test
    void waxedStonecutPartsRequireTheMatchingWaxedOxidationBase() {
        assertEquals(Set.of(Material.WAXED_WEATHERED_COPPER), BuildMartBlueprintAuditor.copperSources(
                "waxed_weathered_cut_copper_stairs", Set.of(Material.WAXED_WEATHERED_COPPER)));
        assertNull(BuildMartBlueprintAuditor.copperSources(
                "waxed_weathered_cut_copper_stairs", Set.of(Material.WAXED_COPPER_BLOCK)));
    }

    @Test
    void waxedSpecialPartsAreNotGeneralizedFromWaxedCopperBlocks() {
        assertNull(BuildMartBlueprintAuditor.copperSources(
                "waxed_copper_trapdoor", Set.of(Material.WAXED_COPPER_BLOCK, Material.COPPER_BLOCK)));
        assertNull(BuildMartBlueprintAuditor.copperSources(
                "waxed_weathered_copper_trapdoor",
                Set.of(Material.WAXED_WEATHERED_COPPER, Material.COPPER_BLOCK, Material.HONEYCOMB)));
    }

    @Test
    void waxingRequiresHoneycombAndTheObtainableUnwaxedPart() {
        assertEquals(Set.of(Material.COPPER_BLOCK, Material.HONEYCOMB), BuildMartBlueprintAuditor.copperSources(
                "waxed_copper_bars", Set.of(Material.COPPER_BLOCK, Material.HONEYCOMB)));
        assertEquals(Set.of(Material.WEATHERED_COPPER_TRAPDOOR, Material.HONEYCOMB),
                BuildMartBlueprintAuditor.copperSources("waxed_weathered_copper_trapdoor",
                        Set.of(Material.WEATHERED_COPPER_TRAPDOOR, Material.HONEYCOMB)));
    }
}
