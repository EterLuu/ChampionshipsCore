package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.api.game.laser.mechanics.LaserGunMechanics;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared weapon items; existing laser-box message keys preserve installed custom translations. */
public final class LaserGunItems {
    private LaserGunItems() {}

    public static ItemStack gunItem(
            Plugin plugin, NamespacedKey itemKey, LaserGun gun, LaserGun selected) {
        ItemStack result = new ItemStack(gun.material());
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        String name = gunName(gun);
        meta.displayName(LegacyText.component(name));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyText.component(gunDescription(gun)));
        String controls =
                gun == LaserGun.SNIPER
                        ? "laser-box-area.item-15"
                        : gun == LaserGun.SMG || gun == LaserGun.MACHINE
                                ? "laser-box-area.item-16"
                                : "laser-box-area.item-8";
        lore.add(LegacyText.component(MessageConfig.codeText(controls)));
        lore.add(
                LegacyText.component(
                        configured(MessageConfig.LASER_BOX_GUN_MAGAZINE, "&#bababaLimited ammo")
                                .replace("%magazine%", Integer.toString(gun.magazine()))
                                .replace("%seconds%", formatSeconds(gun.reloadTicks()))));
        lore.add(
                LegacyText.component(
                        configured(MessageConfig.LASER_BOX_GUN_FIRE_RATE, "&#bababa%rate% shots/s")
                                .replace("%rate%", formatRate(gun.shotsPerSecond()))));
        if (selected == gun)
            lore.add(
                    LegacyText.component(
                            configured(
                                    MessageConfig.LASER_BOX_GUN_SELECTED, "&#a8ff78✓ Equipped")));
        meta.lore(lore);
        meta.getPersistentDataContainer()
                .set(itemKey, PersistentDataType.STRING, "gun:" + gun.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        result.setItemMeta(meta);
        LaserGunMechanics.configureGunItem(
                result, new NamespacedKey(plugin, "laser-fire-rate-" + gun.id()));
        return result;
    }

    public static String gunName(LaserGun gun) {
        return switch (gun) {
            case SNIPER -> configured(MessageConfig.LASER_BOX_GUN_SNIPER_NAME, "Sniper Rifle");
            case RIFLE -> configured(MessageConfig.LASER_BOX_GUN_RIFLE_NAME, "Assault Rifle");
            case SMG -> configured(MessageConfig.LASER_BOX_GUN_SMG_NAME, "SMG");
            case MACHINE -> configured(MessageConfig.LASER_BOX_GUN_MACHINE_NAME, "LMG");
            case SHOTGUN -> configured(MessageConfig.LASER_BOX_GUN_SHOTGUN_NAME, "Shotgun");
        };
    }

    private static String gunDescription(LaserGun gun) {
        return switch (gun) {
            case SNIPER ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_SNIPER_DESCRIPTION,
                            "Accurate long-range fire");
            case RIFLE ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_RIFLE_DESCRIPTION,
                            "A reliable all-round weapon");
            case SMG ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_SMG_DESCRIPTION, "Fast close-range fire");
            case MACHINE ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_MACHINE_DESCRIPTION, "Sustained firepower");
            case SHOTGUN ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_SHOTGUN_DESCRIPTION, "Close-range burst");
        };
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static String formatRate(double rate) {
        return java.math.BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
    }

    private static String configured(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public static void syncAmmo(Player player, NamespacedKey itemKey, int slot, int ammo) {
        ItemStack current = player.getInventory().getItem(slot);
        boolean tagged =
                current != null
                        && current.hasItemMeta()
                        && "ammo"
                                .equals(
                                        current.getItemMeta()
                                                .getPersistentDataContainer()
                                                .get(itemKey, PersistentDataType.STRING));
        if (ammo <= 0) {
            if (tagged) player.getInventory().setItem(slot, null);
            return;
        }
        if (!tagged || current.getType() != Material.SPECTRAL_ARROW) {
            ItemStack stack = new ItemStack(Material.SPECTRAL_ARROW, ammo);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(
                        LegacyText.component(
                                configured(MessageConfig.LASER_BOX_AMMO_ITEM, "&#fff566Ammo")));
                meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "ammo");
                stack.setItemMeta(meta);
            }
            player.getInventory().setItem(slot, stack);
        } else if (current.getAmount() != ammo) current.setAmount(ammo);
    }
}
