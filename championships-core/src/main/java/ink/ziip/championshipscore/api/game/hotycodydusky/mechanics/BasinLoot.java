package ink.ziip.championshipscore.api.game.hotycodydusky.mechanics;

import ink.ziip.championshipscore.api.game.arena.GamePickup;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/** The tournament item pool for Hoty Cody Dusky. */
public enum BasinLoot {
    MUSHROOM(Material.RED_MUSHROOM, 20),
    CHORUS(Material.CHORUS_FRUIT, 20),
    PEARL(Material.ENDER_PEARL, 20),
    HORN(Material.MUSIC_DISC_CAT, 240),
    TRIDENT(Material.TRIDENT, 0),
    VEST(Material.LEATHER_CHESTPLATE, 0),
    KEY(Material.TRIAL_KEY, 0),
    COMPASS(Material.COMPASS, 40),
    SMOKE(Material.LINGERING_POTION, 20),
    BAIT(Material.HONEY_BOTTLE, 20),
    WIND(Material.WIND_CHARGE, 10),
    DECOY(Material.ARMOR_STAND, 10),
    CLOCK(Material.CLOCK, 20),
    TOTEM(Material.TOTEM_OF_UNDYING, 0);

    public static List<BasinLoot> pool(boolean extended) {
        return extended ? List.of(values()) : List.copyOf(java.util.EnumSet.range(MUSHROOM, DECOY));
    }

    public static final int SPAWN_INTERVAL = 5;
    public static final int LIMIT = 10;
    public static final int CHANCE_PERCENT = 75;
    private static final NamespacedKey TYPE = new NamespacedKey("championshipscore", "basin_loot");
    private final Material material;
    private final String messageKey;
    private final int cooldownTicks;

    public int cooldownTicks() {
        return cooldownTicks;
    }

    BasinLoot(Material material, int cooldownTicks) {
        this.material = material;
        messageKey = "hoty-items." + name().toLowerCase(Locale.ROOT);
        this.cooldownTicks = cooldownTicks;
    }

    public String title() {
        return MessageConfig.codeText(messageKey);
    }

    public String description() {
        return MessageConfig.codeText(messageKey + "-hint");
    }

    public ItemStack stack() {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(
                meta -> {
                    var usage =
                            LegacyText.component(description())
                                    .decoration(TextDecoration.ITALIC, false);
                    meta.displayName(GamePickup.heldName(LegacyText.component(title()), usage));
                    meta.lore(List.of(usage));
                    meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, name());
                });
        if (this == TRIDENT) {
            stack.unsetData(DataComponentTypes.MAX_DAMAGE);
            stack.unsetData(DataComponentTypes.DAMAGE);
            stack.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        if (cooldownTicks > 0)
            stack.setData(
                    DataComponentTypes.USE_COOLDOWN,
                    UseCooldown.useCooldown(cooldownTicks / 20f)
                            .cooldownGroup(
                                    new NamespacedKey(
                                            "championshipscore",
                                            "basin_" + name().toLowerCase(Locale.ROOT))));
        if (this == HORN) {
            stack.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
            stack.setData(DataComponentTypes.RARITY, org.bukkit.inventory.ItemRarity.COMMON);
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("goat_horn"));
            stack.setData(
                    DataComponentTypes.CONSUMABLE,
                    Consumable.consumable()
                            .consumeSeconds(0.05f)
                            .animation(ItemUseAnimation.TOOT_HORN)
                            .sound(Key.key("intentionally_empty"))
                            .hasConsumeParticles(false)
                            .addEffect(
                                    ConsumeEffect.playSoundConsumeEffect(
                                            Key.key("item.goat_horn.sound.1"))));
            stack.setData(
                    DataComponentTypes.USE_EFFECTS,
                    UseEffects.useEffects().canSprint(true).speedMultiplier(1f));
        }
        if (this == SMOKE)
            stack.editMeta(PotionMeta.class, meta -> meta.setColor(Color.fromRGB(0x8ac9e4)));
        if (this == CHORUS) {
            stack.unsetData(DataComponentTypes.FOOD);
            stack.setData(
                    DataComponentTypes.CONSUMABLE,
                    Consumable.consumable()
                            .consumeSeconds(0.05f)
                            .addEffect(ConsumeEffect.teleportRandomlyEffect(32f)));
        }
        if (this == BAIT || this == MUSHROOM) {
            stack.unsetData(DataComponentTypes.FOOD);
            stack.unsetData(DataComponentTypes.USE_REMAINDER);
            stack.setData(
                    DataComponentTypes.CONSUMABLE,
                    Consumable.consumable()
                            .consumeSeconds(this == BAIT ? 1.6f : 0.5f)
                            .animation(this == BAIT ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
                            .sound(
                                    Key.key(
                                            this == BAIT
                                                    ? "entity.generic.drink"
                                                    : "entity.generic.eat"))
                            .hasConsumeParticles(this == MUSHROOM));
        }
        return stack;
    }

    public static BasinLoot of(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String key = item.getPersistentDataContainer().get(TYPE, PersistentDataType.STRING);
        if (key == null) return null;
        for (var value : values()) if (value.name().equals(key)) return value;
        return null;
    }
}
