package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loans the current server's registered recipes for one match. Quit restores before player save.
 */
final class FeastRecipeBook implements AutoCloseable {
    private final Set<NamespacedKey> recipes = new HashSet<>();
    private final Map<UUID, Set<NamespacedKey>> granted = new HashMap<>();

    void enter(Player player) {
        UUID id = player.getUniqueId();
        if (granted.containsKey(id)) return;
        if (recipes.isEmpty())
            Bukkit.recipeIterator()
                    .forEachRemaining(
                            recipe -> {
                                if (recipe instanceof Keyed keyed) recipes.add(keyed.getKey());
                            });
        Set<NamespacedKey> missing = new HashSet<>(recipes);
        missing.removeAll(player.getDiscoveredRecipes());
        granted.put(id, missing);
        if (!missing.isEmpty()) player.discoverRecipes(missing);
    }

    void leave(Player player) {
        Set<NamespacedKey> missing = granted.remove(player.getUniqueId());
        if (missing != null && !missing.isEmpty()) player.undiscoverRecipes(missing);
    }

    @Override
    public void close() {
        for (UUID id : Set.copyOf(granted.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) leave(player);
        }
        granted.clear();
        recipes.clear();
    }
}
