package ink.ziip.championshipscore.platform.bukkit.bingo;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/** Maps special name-tag interactions to their Bingo objective parameter. */
public final class BingoNameTagObjective {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private BingoNameTagObjective() {
    }

    public static String match(ItemStack item, EntityType target) {
        if (item == null || item.getType() != Material.NAME_TAG || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return null;
        return match(target, PLAIN_TEXT.serialize(meta.displayName()));
    }

    static String match(EntityType target, String displayName) {
        if (target == null || displayName == null) return null;
        String normalized = displayName.toLowerCase(Locale.ROOT);
        return switch (target) {
            case SHEEP -> normalized.contains("jeb_") ? "SHEEP_JEB" : null;
            case IRON_GOLEM -> upsideDown(normalized) ? "IRON_GOLEM_DINNERBONE" : null;
            case GHAST -> upsideDown(normalized) ? "GHAST_DINNERBONE" : null;
            default -> null;
        };
    }

    private static boolean upsideDown(String name) {
        return name.contains("dinnerbone") || name.contains("grumm");
    }
}
