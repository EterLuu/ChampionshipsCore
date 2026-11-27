package ink.ziip.championshipscore.api.game.craftingfeast.mechanics;

import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;

import org.bukkit.*;
import org.bukkit.inventory.*;

import java.util.*;

/** Checks order reachability using the server's vanilla recipes; never registers a recipe. */
public final class FeastVanillaRecipes {
    private FeastVanillaRecipes() {}

    public static void validate(CraftingFeastConfig config) {
        Set<Material> available = EnumSet.noneOf(Material.class);
        config.supplyBarrels().forEach(s -> available.addAll(s.items().keySet()));
        config.resourceLocations()
                .forEach(
                        (material, locations) -> {
                            for (Location location : locations) {
                                if (location.getBlock().getType() != material)
                                    throw new IllegalArgumentException(
                                            "Missing Crafting Feast resource: " + location);
                                // Gravel and flint are both obtainable on this replenishing wall.
                                // Sampling a handful of breaks can miss either vanilla drop.
                                if (material == Material.GRAVEL) {
                                    available.add(Material.GRAVEL);
                                    available.add(Material.FLINT);
                                    continue;
                                }
                                for (Material tool :
                                        List.of(
                                                Material.NETHERITE_PICKAXE,
                                                Material.NETHERITE_AXE,
                                                Material.NETHERITE_SHOVEL))
                                    location.getBlock()
                                            .getDrops(new ItemStack(tool))
                                            .forEach(item -> available.add(item.getType()));
                            }
                        });
        config.workstationLocations()
                .forEach(
                        (material, locations) -> {
                            for (Location location : locations)
                                if (location.getBlock().getType() != material)
                                    throw new IllegalArgumentException(
                                            "Missing Crafting Feast workstation: " + location);
                        });
        for (var supply : config.supplyBarrels())
            if (supply.location().getBlock().getType() != Material.BARREL)
                throw new IllegalArgumentException(
                        "Missing Crafting Feast supply barrel: " + supply.location());
        Set<Material> stations = config.workstationLocations().keySet();
        List<Recipe> recipes = new ArrayList<>();
        Bukkit.recipeIterator()
                .forEachRemaining(
                        recipe -> {
                            if (recipe instanceof Keyed keyed
                                    && keyed.getKey()
                                            .getNamespace()
                                            .equals(NamespacedKey.MINECRAFT)) recipes.add(recipe);
                        });
        Set<Material> made = EnumSet.noneOf(Material.class);
        boolean changed;
        do {
            changed = false;
            for (Recipe recipe : recipes) {
                List<RecipeChoice> ingredients = ingredients(recipe, stations);
                if (ingredients.isEmpty()
                        || !ingredients.stream().allMatch(c -> matches(c, available))) continue;
                Material result = recipe.getResult().getType();
                made.add(result);
                changed |= available.add(result);
            }
        } while (changed);
        for (var request : config.allRequests())
            if (Collections.disjoint(request.answers(), request.clue() ? available : made))
                throw new IllegalArgumentException("Unreachable vanilla request: " + request.id());
    }

    private static boolean matches(RecipeChoice choice, Set<Material> available) {
        if (choice instanceof RecipeChoice.MaterialChoice materials)
            return materials.getChoices().stream().anyMatch(available::contains);
        return false;
    }

    private static List<RecipeChoice> ingredients(Recipe recipe, Set<Material> stations) {
        if (stations.contains(Material.CRAFTING_TABLE)) {
            if (recipe instanceof ShapedRecipe shaped)
                return shaped.getChoiceMap().values().stream().filter(Objects::nonNull).toList();
            if (recipe instanceof ShapelessRecipe shapeless) return shapeless.getChoiceList();
        }
        if (recipe instanceof FurnaceRecipe furnace && stations.contains(Material.FURNACE))
            return List.of(furnace.getInputChoice());
        if (recipe instanceof SmokingRecipe smoking && stations.contains(Material.SMOKER))
            return List.of(smoking.getInputChoice());
        if (recipe instanceof BlastingRecipe blasting && stations.contains(Material.BLAST_FURNACE))
            return List.of(blasting.getInputChoice());
        if (recipe instanceof StonecuttingRecipe cutting && stations.contains(Material.STONECUTTER))
            return List.of(cutting.getInputChoice());
        return List.of();
    }
}
