package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartMaterialManifest;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BuildMartBlueprintAuditorTest {
    @Test
    void submissionCoverageAcceptsProcessedMaterialsAndRejectsUnavailableMaterials() {
        var inventory =
                new BuildMartMaterialManifest.AuditInventory(
                        true, Map.of(Material.OAK_LOG, 1L, Material.COBBLESTONE, 1L), Map.of());
        assertTrue(
                BuildMartBlueprintAuditor.audit(blueprint(Material.OAK_STAIRS), inventory)
                        .fullyCovered());
        assertTrue(
                BuildMartBlueprintAuditor.audit(blueprint(Material.STONE), inventory)
                        .fullyCovered());
        var rejected =
                BuildMartBlueprintAuditor.audit(blueprint(Material.DIAMOND_BLOCK), inventory);
        assertFalse(rejected.fullyCovered());
        assertEquals(Set.of(Material.DIAMOND_BLOCK), rejected.uncoveredMaterials());
    }

    @ParameterizedTest(name = "score {0} -> {1} stars")
    @CsvSource({
        "0, 1", "19.9, 1", "20, 2", "39.9, 2", "40, 3", "59.9, 3", "60, 4", "79.9, 4", "80, 5",
        "100, 5"
    })
    void difficultyBandsHaveStableBoundaries(double score, int stars) {
        assertEquals(stars, BuildMartBlueprintAuditor.suggestedStars(score));
    }

    @Test
    void copperSourcesRequireMatchingWaxedBasesOrHoneycombWithTheUnwaxedPart() {
        record Case(String part, Set<Material> available, Set<Material> expected) {}
        var cases =
                List.of(
                        // Waxed stonecut parts need the matching waxed oxidation base.
                        new Case(
                                "waxed_weathered_cut_copper_stairs",
                                Set.of(Material.WAXED_WEATHERED_COPPER),
                                Set.of(Material.WAXED_WEATHERED_COPPER)),
                        new Case(
                                "waxed_weathered_cut_copper_stairs",
                                Set.of(Material.WAXED_COPPER_BLOCK),
                                null),
                        // Waxed special parts are not generalized from waxed copper blocks.
                        new Case(
                                "waxed_copper_trapdoor",
                                Set.of(Material.WAXED_COPPER_BLOCK, Material.COPPER_BLOCK),
                                null),
                        new Case(
                                "waxed_weathered_copper_trapdoor",
                                Set.of(
                                        Material.WAXED_WEATHERED_COPPER,
                                        Material.COPPER_BLOCK,
                                        Material.HONEYCOMB),
                                null),
                        // Waxing needs honeycomb plus the obtainable unwaxed part.
                        new Case(
                                "waxed_copper_bars",
                                Set.of(Material.COPPER_BLOCK, Material.HONEYCOMB),
                                Set.of(Material.COPPER_BLOCK, Material.HONEYCOMB)),
                        new Case(
                                "waxed_weathered_copper_trapdoor",
                                Set.of(Material.WEATHERED_COPPER_TRAPDOOR, Material.HONEYCOMB),
                                Set.of(Material.WEATHERED_COPPER_TRAPDOOR, Material.HONEYCOMB)));
        assertAll(
                cases.stream()
                        .map(
                                c ->
                                        (Executable)
                                                () ->
                                                        assertEquals(
                                                                c.expected(),
                                                                BuildMartBlueprintAuditor
                                                                        .copperSources(
                                                                                c.part(),
                                                                                c.available()),
                                                                c.toString())));
    }

    private static BuildMartBlueprint blueprint(Material material) {
        var data =
                (BlockData)
                        Proxy.newProxyInstance(
                                BlockData.class.getClassLoader(),
                                new Class<?>[] {BlockData.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getMaterial" -> material;
                                            case "getAsString" -> material.getKey().toString();
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        return new BuildMartBlueprint(
                "test", "test", 1, List.of(new BlueprintBlock(0, 0, 0, data)));
    }
}
