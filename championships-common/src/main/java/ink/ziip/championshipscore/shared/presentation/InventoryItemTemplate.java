package ink.ziip.championshipscore.shared.presentation;

import java.util.List;
import java.util.Objects;

/** Immutable legacy-text item template; Bukkit material lookup belongs to the platform renderer. */
public record InventoryItemTemplate(
        String material, String title, List<String> lore, boolean glint) {
    public InventoryItemTemplate {
        Objects.requireNonNull(material);
        Objects.requireNonNull(title);
        lore = List.copyOf(lore);
        if (material.isBlank()) throw new IllegalArgumentException("Empty item material");
    }
}
