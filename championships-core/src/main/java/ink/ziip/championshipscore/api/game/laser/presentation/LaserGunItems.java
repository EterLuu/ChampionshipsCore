package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.api.game.laser.mechanics.LaserGunMechanics;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Shared weapon items; existing laser-box message keys preserve installed custom translations. */
public final class LaserGunItems {
    private LaserGunItems() {}

    /** Reads only this game's item marker; unrelated item metadata never identifies a weapon. */
    public static String type(ItemStack item, NamespacedKey itemKey) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
    }

    public static ItemStack gunItem(
            Plugin plugin, NamespacedKey itemKey, LaserGun gun, LaserGun selected) {
        ItemStack result = new ItemStack(gun.material());
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        applyPresentation(
                meta,
                "laser.equipment.guns." + gun.id(),
                selected == gun ? "selected" : "idle",
                presentation(gun));
        meta.getPersistentDataContainer()
                .set(itemKey, PersistentDataType.STRING, "gun:" + gun.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        result.setItemMeta(meta);
        LaserGunMechanics.configureGunItem(
                result, new NamespacedKey(plugin, "laser-fire-rate-" + gun.id()));
        return result;
    }

    /** Values shared by equipped weapons and the configurable selection view. */
    static Map<String, String> presentation(LaserGun gun) {
        String controls =
                switch (gun) {
                    case SNIPER -> "laser-gun-controls.sniper";
                    case SMG, MACHINE -> "laser-gun-controls.automatic";
                    default -> "laser-gun-controls.single";
                };
        return Map.of(
                "name", gunName(gun),
                "description", gunDescription(gun),
                "controls", MessageConfig.codeText(controls),
                "magazine-summary",
                        MessageTemplate.render(
                                text(MessageConfig.LASER_BOX_GUN_MAGAZINE),
                                Map.of(
                                        "%magazine%",
                                        Integer.toString(gun.magazine()),
                                        "%seconds%",
                                        formatSeconds(gun.reloadTicks()))),
                "fire-rate-summary",
                        MessageTemplate.render(
                                text(MessageConfig.LASER_BOX_GUN_FIRE_RATE),
                                Map.of("%rate%", formatRate(gun.shotsPerSecond()))),
                "selected-label", text(MessageConfig.LASER_BOX_GUN_SELECTED));
    }

    public static String gunName(LaserGun gun) {
        return text(
                switch (gun) {
                    case SNIPER -> MessageConfig.LASER_BOX_GUN_SNIPER_NAME;
                    case RIFLE -> MessageConfig.LASER_BOX_GUN_RIFLE_NAME;
                    case SMG -> MessageConfig.LASER_BOX_GUN_SMG_NAME;
                    case MACHINE -> MessageConfig.LASER_BOX_GUN_MACHINE_NAME;
                    case SHOTGUN -> MessageConfig.LASER_BOX_GUN_SHOTGUN_NAME;
                });
    }

    private static String gunDescription(LaserGun gun) {
        return text(
                switch (gun) {
                    case SNIPER -> MessageConfig.LASER_BOX_GUN_SNIPER_DESCRIPTION;
                    case RIFLE -> MessageConfig.LASER_BOX_GUN_RIFLE_DESCRIPTION;
                    case SMG -> MessageConfig.LASER_BOX_GUN_SMG_DESCRIPTION;
                    case MACHINE -> MessageConfig.LASER_BOX_GUN_MACHINE_DESCRIPTION;
                    case SHOTGUN -> MessageConfig.LASER_BOX_GUN_SHOTGUN_DESCRIPTION;
                });
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static String formatRate(double rate) {
        return java.math.BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
    }

    static String text(String value) {
        return value == null ? "" : value;
    }

    private static void applyPresentation(
            ItemMeta meta, String path, String state, Map<String, String> values) {
        var template = GuiConfig.itemTemplate(path, state);
        var replacements = new HashMap<String, String>();
        values.forEach((key, value) -> replacements.put("%" + key + "%", value));
        // Equipped weapons retain their native italic default; selection menus set their own style.
        meta.displayName(
                LegacyText.component(MessageTemplate.render(template.title(), replacements)));
        meta.lore(
                template.lore().stream()
                        .map(
                                line ->
                                        LegacyText.component(
                                                MessageTemplate.render(line, replacements)))
                        .toList());
        if (template.glint()) meta.setEnchantmentGlintOverride(true);
    }

    public static void showCombatStatus(
            Player player, LaserGun gun, int ammo, int reloadTicks, int protectionTicks) {
        if (gun == null && protectionTicks <= 0) return;
        String text =
                gun == null
                        ? ""
                        : reloadTicks > 0
                                ? text(MessageConfig.LASER_BOX_RELOAD)
                                : text(MessageConfig.LASER_BOX_AMMO);
        if (gun != null)
            text =
                    MessageTemplate.render(
                            text,
                            Map.of(
                                    "%gun%", gunName(gun),
                                    "%ammo%", Integer.toString(ammo),
                                    "%magazine%", Integer.toString(gun.magazine()),
                                    "%seconds%", formatSeconds(reloadTicks)));
        if (protectionTicks > 0)
            text +=
                    MessageConfig.codeText(
                            "laser-combat.protection", formatSeconds(protectionTicks));
        player.sendActionBar(LegacyText.component(text));
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
                applyPresentation(
                        meta,
                        "laser.equipment.ammo",
                        null,
                        Map.of("name", text(MessageConfig.LASER_BOX_AMMO_ITEM)));
                meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "ammo");
                stack.setItemMeta(meta);
            }
            player.getInventory().setItem(slot, stack);
        } else if (current.getAmount() != ammo) current.setAmount(ammo);
    }
}
