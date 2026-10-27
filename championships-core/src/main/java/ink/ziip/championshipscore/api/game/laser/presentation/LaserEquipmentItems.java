package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Configured utility items retain their game-owned markers and mechanical metadata. */
public final class LaserEquipmentItems {
    private static final Set<String> BOX_TYPES =
            Set.of("gun-selector", "grenade", "strike", "reveal", "shield");

    private LaserEquipmentItems() {}

    public static ItemStack box(String type, NamespacedKey marker) {
        if (!BOX_TYPES.contains(type))
            throw new IllegalArgumentException("Unknown LaserBox equipment: " + type);
        return create("laserbox", type, marker, false);
    }

    public static ItemStack showdownSelector(NamespacedKey marker) {
        return create("lasershowdown", "gun-selector", marker, true);
    }

    public static String name(String type) {
        return MessageConfig.codeText(
                switch (type) {
                    case "gun-selector" -> "laser-equipment.gun-selector";
                    case "grenade" -> "laser-equipment.grenade";
                    case "strike" -> "laser-equipment.strike";
                    case "reveal" -> "laser-equipment.reveal";
                    case "shield" -> "laser-equipment.shield";
                    default ->
                            throw new IllegalArgumentException("Unknown laser equipment: " + type);
                });
    }

    private static ItemStack create(
            String game, String type, NamespacedKey marker, boolean unbreakable) {
        ItemStack item =
                ConfiguredGui.item(
                        "laser.equipment." + game + "." + type,
                        Map.of("name", name(type)),
                        Material.PAPER,
                        Component.empty(),
                        List.of(),
                        false);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(marker, PersistentDataType.STRING, type);
        if (unbreakable) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        item.setItemMeta(meta);
        return item;
    }
}
