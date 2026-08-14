package ink.ziip.championshipscore.configuration.config.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Small renderer shared by every inventory that exposes its functional buttons through gui.yml. */
public final class ConfiguredGui {
    private ConfiguredGui() {
    }

    public static @NotNull ItemStack item(@NotNull String path, @NotNull Map<String, ?> placeholders,
                                          @NotNull Material fallbackMaterial,
                                          @NotNull Component fallbackTitle,
                                          @NotNull List<Component> fallbackLore, boolean fallbackGlint) {
        return item(path, null, placeholders, fallbackMaterial, fallbackTitle, fallbackLore, fallbackGlint);
    }

    public static @NotNull ItemStack item(@NotNull String path, @Nullable String state,
                                          @NotNull Map<String, ?> placeholders,
                                          @NotNull Material fallbackMaterial,
                                          @NotNull Component fallbackTitle,
                                          @NotNull List<Component> fallbackLore, boolean fallbackGlint) {
        GuiConfig.ItemSpec spec = GuiConfig.item(path, state, placeholders,
                new GuiConfig.ItemSpec(-1, fallbackMaterial, fallbackTitle, fallbackLore, fallbackGlint));
        ItemStack stack = new ItemStack(spec.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(spec.title());
            meta.lore(spec.lore());
            meta.setEnchantmentGlintOverride(spec.glint());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static @NotNull ItemStack item(@NotNull String path, @Nullable String state,
                                          @NotNull Map<String, ?> placeholders,
                                          @NotNull ItemStack fallback) {
        ItemMeta meta = fallback.getItemMeta();
        Component title = meta != null && meta.hasDisplayName() ? meta.displayName() : Component.empty();
        List<Component> lore = meta == null || meta.lore() == null ? List.of() : meta.lore();
        boolean glint = meta != null && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
        GuiConfig.ItemSpec spec = GuiConfig.item(path, state, placeholders,
                new GuiConfig.ItemSpec(-1, fallback.getType(), title, lore, glint));
        boolean sameType = spec.material() == fallback.getType();
        ItemStack rendered = sameType ? fallback.clone() : new ItemStack(spec.material(), fallback.getAmount());
        ItemMeta renderedMeta = rendered.getItemMeta();
        if (renderedMeta != null) {
            // Start from the fallback meta so persistent data, custom model data and item flags survive.
            if (meta != null && sameType) {
                renderedMeta = meta.clone();
            } else if (meta != null) {
                // Type-specific meta (for example SkullMeta) cannot be attached to every material.
                // Persistent click targets are the part that must always survive a configured type swap.
                meta.getPersistentDataContainer().copyTo(renderedMeta.getPersistentDataContainer(), true);
            }
            renderedMeta.displayName(spec.title());
            renderedMeta.lore(spec.lore());
            renderedMeta.setEnchantmentGlintOverride(spec.glint());
            rendered.setItemMeta(renderedMeta);
        }
        return rendered;
    }

    public static int slot(@NotNull String itemPath, int fallback) {
        return GuiConfig.integer(itemPath + ".slot", fallback);
    }
}
