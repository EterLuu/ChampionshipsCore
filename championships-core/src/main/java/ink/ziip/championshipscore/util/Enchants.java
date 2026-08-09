package ink.ziip.championshipscore.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

/**
 * Modern lookup of vanilla enchantments by {@link io.papermc.paper.registry.keys.EnchantmentKeys
 * EnchantmentKeys} constant, replacing the deprecated {@code Enchantment.X} static fields and the
 * deprecated {@code org.bukkit.Registry.ENCHANTMENT} accessor. The enchantment registry is fetched once
 * via Paper's {@link RegistryAccess} / {@link RegistryKey} API and cached, so each lookup is a
 * one-liner; {@code getOrThrow} fails loudly on a bad key instead of silently returning {@code null}.
 */
public final class Enchants {
    private static final Registry<Enchantment> REGISTRY =
            RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

    private Enchants() {
    }

    /** The vanilla enchantment identified by {@code key} (e.g. {@code EnchantmentKeys.SHARPNESS}). */
    public static Enchantment get(TypedKey<Enchantment> key) {
        return REGISTRY.getOrThrow(key);
    }
}
