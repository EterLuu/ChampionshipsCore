package ink.ziip.championshipscore.api.game.frostbite.presentation;

import ink.ziip.championshipscore.api.game.frostbite.model.FrostbiteItem;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

/** Renders configured power-up text while retaining gameplay material and persistent identity. */
public final class FrostbiteItemPresentation {
    private FrostbiteItemPresentation() {}

    public static ItemStack create(FrostbiteItem type, NamespacedKey itemKey) {
        ItemStack stack = new ItemStack(type.material);
        stack.editMeta(
                meta -> {
                    meta.displayName(
                            LegacyText.component(
                                    MessageConfig.codeText(
                                            "frostbite-item.display-name", itemTitle(type))));
                    meta.lore(
                            MessageConfig.FROSTBITE_ITEM_LORE.stream()
                                    .map(
                                            line ->
                                                    LegacyText.component(
                                                            MessageTemplate.render(
                                                                    line,
                                                                    Map.of(
                                                                            "%title%",
                                                                            itemTitle(type),
                                                                            "%description%",
                                                                            itemDescription(
                                                                                    type)))))
                                    .toList());
                    meta.setUnbreakable(true);
                    meta.getPersistentDataContainer()
                            .set(itemKey, PersistentDataType.STRING, type.name());
                    if (type == FrostbiteItem.BOW)
                        meta.getPersistentDataContainer()
                                .set(
                                        new NamespacedKey("championshipscore", "frostbite_bow_id"),
                                        PersistentDataType.STRING,
                                        UUID.randomUUID().toString());
                });
        return stack;
    }

    private static String itemTitle(FrostbiteItem type) {
        String value =
                switch (type) {
                    case AVALANCHE -> MessageConfig.FROSTBITE_ITEM_AVALANCHE_TITLE;
                    case AXE -> MessageConfig.FROSTBITE_ITEM_AXE_TITLE;
                    case BLAZE -> MessageConfig.FROSTBITE_ITEM_BLAZE_TITLE;
                    case BOW -> MessageConfig.FROSTBITE_ITEM_BOW_TITLE;
                    case BEACON -> MessageConfig.FROSTBITE_ITEM_BEACON_TITLE;
                    case EXPLOSION -> MessageConfig.FROSTBITE_ITEM_EXPLOSION_TITLE;
                    case GLOW -> MessageConfig.FROSTBITE_ITEM_GLOW_TITLE;
                    case HOT_ROD -> MessageConfig.FROSTBITE_ITEM_HOT_ROD_TITLE;
                    case ICICLE -> MessageConfig.FROSTBITE_ITEM_ICICLE_TITLE;
                    case INVIS -> MessageConfig.FROSTBITE_ITEM_INVIS_TITLE;
                    case MYSTERY -> MessageConfig.FROSTBITE_ITEM_MYSTERY_TITLE;
                    case PHOENIX -> MessageConfig.FROSTBITE_ITEM_PHOENIX_TITLE;
                    case SPEED -> MessageConfig.FROSTBITE_ITEM_SPEED_TITLE;
                    case FROST_TRAP -> MessageConfig.FROSTBITE_ITEM_FROST_TRAP_TITLE;
                    case WHOABALL -> MessageConfig.FROSTBITE_ITEM_WHOABALL_TITLE;
                };
        return value == null ? type.title() : value;
    }

    private static String itemDescription(FrostbiteItem type) {
        String value =
                switch (type) {
                    case AVALANCHE -> MessageConfig.FROSTBITE_ITEM_AVALANCHE_DESCRIPTION;
                    case AXE -> MessageConfig.FROSTBITE_ITEM_AXE_DESCRIPTION;
                    case BLAZE -> MessageConfig.FROSTBITE_ITEM_BLAZE_DESCRIPTION;
                    case BOW -> MessageConfig.FROSTBITE_ITEM_BOW_DESCRIPTION;
                    case BEACON -> MessageConfig.FROSTBITE_ITEM_BEACON_DESCRIPTION;
                    case EXPLOSION -> MessageConfig.FROSTBITE_ITEM_EXPLOSION_DESCRIPTION;
                    case GLOW -> MessageConfig.FROSTBITE_ITEM_GLOW_DESCRIPTION;
                    case HOT_ROD -> MessageConfig.FROSTBITE_ITEM_HOT_ROD_DESCRIPTION;
                    case ICICLE -> MessageConfig.FROSTBITE_ITEM_ICICLE_DESCRIPTION;
                    case INVIS -> MessageConfig.FROSTBITE_ITEM_INVIS_DESCRIPTION;
                    case MYSTERY -> MessageConfig.FROSTBITE_ITEM_MYSTERY_DESCRIPTION;
                    case PHOENIX -> MessageConfig.FROSTBITE_ITEM_PHOENIX_DESCRIPTION;
                    case SPEED -> MessageConfig.FROSTBITE_ITEM_SPEED_DESCRIPTION;
                    case FROST_TRAP -> MessageConfig.FROSTBITE_ITEM_FROST_TRAP_DESCRIPTION;
                    case WHOABALL -> MessageConfig.FROSTBITE_ITEM_WHOABALL_DESCRIPTION;
                };
        return value == null ? type.description() : value;
    }

    public static String pickupHint(FrostbiteItem type) {
        return MessageConfig.codeText(
                "frostbite-item.pickup-hint", itemTitle(type), itemDescription(type));
    }
}
