package ink.ziip.championshipscore.api.game.craftingfeast.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Map;

public record FeastSupply(String name, Location location, Map<Material, Integer> items) {
    public FeastSupply {
        items = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(items));
        if (name == null
                || name.isBlank()
                || items.isEmpty()
                || items.size() > 27
                || items.entrySet().stream()
                        .anyMatch(
                                e ->
                                        !e.getKey().isItem()
                                                || e.getKey().isAir()
                                                || e.getValue() < 1
                                                || e.getValue() > e.getKey().getMaxStackSize()))
            throw new IllegalArgumentException("Invalid Crafting Feast supply: " + name);
    }
}
