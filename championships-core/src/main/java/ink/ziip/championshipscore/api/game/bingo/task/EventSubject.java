package ink.ziip.championshipscore.api.game.bingo.task;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

/**
 * A typed member of an {@link EventTask} subject set. The original {@code unique_collect}/{@code
 * all_collect} families only needed {@link Material}; the biome-visit and mob-family triggers need
 * non-material subjects, so the set is generalised here while the item-only {@code members} field is
 * kept for the existing triggers.
 */
public record EventSubject(Kind kind, String key) {

    public enum Kind {
        MATERIAL,
        ENTITY_TYPE,
        BIOME
    }

    public static EventSubject material(Material material) {
        return new EventSubject(Kind.MATERIAL, material.name());
    }

    public static EventSubject material(String materialName) {
        return new EventSubject(Kind.MATERIAL, materialName);
    }

    public static EventSubject entityType(EntityType entityType) {
        return new EventSubject(Kind.ENTITY_TYPE, entityType.name());
    }

    public static EventSubject entityType(String entityTypeName) {
        return new EventSubject(Kind.ENTITY_TYPE, entityTypeName);
    }

    public static EventSubject biome(Biome biome) {
        return new EventSubject(Kind.BIOME, biome.getKey().getKey());
    }

    public static EventSubject biome(String biomeName) {
        return new EventSubject(Kind.BIOME, biomeName.toLowerCase(java.util.Locale.ROOT));
    }

    public Material materialOrNull() {
        try {
            return kind == Kind.MATERIAL ? Material.valueOf(key) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public EntityType entityTypeOrNull() {
        try {
            return kind == Kind.ENTITY_TYPE ? EntityType.valueOf(key) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Biome biomeOrNull() {
        try {
            return kind == Kind.BIOME ? Registry.BIOME.get(NamespacedKey.minecraft(key)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
