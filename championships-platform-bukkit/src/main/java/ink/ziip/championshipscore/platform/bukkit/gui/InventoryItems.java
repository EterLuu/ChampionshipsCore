package ink.ziip.championshipscore.platform.bukkit.gui;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.InventoryItemTemplate;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Stateless rendering of an immutable Core-owned item template on either Bukkit platform. */
public final class InventoryItems {
    private InventoryItems() {}

    public static Component text(String template, Map<String, String> values) {
        return LegacyText.component(MessageTemplate.render(template, values))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Use the receiving server's native registry during PREPARE, before a player opens the menu.
     */
    public static Material material(InventoryItemTemplate template) {
        var material = Material.getMaterial(template.material());
        if (material == null || !material.isItem() || material.isAir())
            throw new IllegalArgumentException(
                    "Invalid menu item material: " + template.material());
        return material;
    }

    public static ItemStack render(InventoryItemTemplate template, Map<String, String> values) {
        var item = new ItemStack(material(template));
        item.editMeta(
                meta -> {
                    meta.displayName(text(template.title(), values));
                    meta.lore(template.lore().stream().map(line -> text(line, values)).toList());
                    meta.setEnchantmentGlintOverride(template.glint());
                });
        return item;
    }
}
