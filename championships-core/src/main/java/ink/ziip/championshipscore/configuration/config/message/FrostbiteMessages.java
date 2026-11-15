package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Frostbite domain; persisted by MessageConfig. */
public final class FrostbiteMessages {
    private FrostbiteMessages() {}

    @ConfigOption(path = "frostbite.start-preparation")
    public static String FROSTBITE_START_PREPARATION;

    @ConfigOption(path = "frostbite.start-preparation-title")
    public static String FROSTBITE_START_PREPARATION_TITLE;

    @ConfigOption(path = "frostbite.start-preparation-subtitle")
    public static String FROSTBITE_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "frostbite.game-start-title")
    public static String FROSTBITE_GAME_START_TITLE;

    @ConfigOption(path = "frostbite.game-start-subtitle")
    public static String FROSTBITE_GAME_START_SUBTITLE;

    @ConfigOption(path = "frostbite.arena")
    public static String FROSTBITE_ARENA;

    @ConfigOption(path = "frostbite.frozen-actionbar")
    public static String FROSTBITE_FROZEN_ACTIONBAR;

    @ConfigOption(path = "frostbite.status-actionbar")
    public static String FROSTBITE_STATUS_ACTIONBAR;

    @ConfigOption(path = "frostbite.kill")
    public static String FROSTBITE_KILL;

    @ConfigOption(path = "frostbite.respawn-title")
    public static String FROSTBITE_RESPAWN_TITLE;

    @ConfigOption(path = "frostbite.respawn-subtitle")
    public static String FROSTBITE_RESPAWN_SUBTITLE;

    @ConfigOption(path = "frostbite.boss-bar")
    public static String FROSTBITE_BOSS_BAR;

    @ConfigOption(path = "frostbite.end-title")
    public static String FROSTBITE_END_TITLE;

    @ConfigOption(path = "frostbite.end-subtitle")
    public static String FROSTBITE_END_SUBTITLE;

    @ConfigOption(path = "frostbite.phoenix-consumed")
    public static String FROSTBITE_PHOENIX_CONSUMED;

    @ConfigOption(path = "frostbite.campfire-returned")
    public static String FROSTBITE_CAMPFIRE_RETURNED;

    @ConfigOption(path = "frostbite.freeze-success")
    public static String FROSTBITE_FREEZE_SUCCESS;

    @ConfigOption(path = "frostbite.freeze")
    public static String FROSTBITE_FREEZE;

    @ConfigOption(path = "frostbite.frozen")
    public static String FROSTBITE_FROZEN;

    @ConfigOption(path = "frostbite.item-presentation.lore")
    public static java.util.List<String> FROSTBITE_ITEM_LORE =
            MessageConfig.defaultStringList("frostbite.item-presentation.lore");

    @ConfigOption(path = "frostbite.item.avalanche.title")
    public static String FROSTBITE_ITEM_AVALANCHE_TITLE;

    @ConfigOption(path = "frostbite.item.avalanche.description")
    public static String FROSTBITE_ITEM_AVALANCHE_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.axe.title")
    public static String FROSTBITE_ITEM_AXE_TITLE;

    @ConfigOption(path = "frostbite.item.axe.description")
    public static String FROSTBITE_ITEM_AXE_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.blaze.title")
    public static String FROSTBITE_ITEM_BLAZE_TITLE;

    @ConfigOption(path = "frostbite.item.blaze.description")
    public static String FROSTBITE_ITEM_BLAZE_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.bow.title")
    public static String FROSTBITE_ITEM_BOW_TITLE;

    @ConfigOption(path = "frostbite.item.bow.description")
    public static String FROSTBITE_ITEM_BOW_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.beacon.title")
    public static String FROSTBITE_ITEM_BEACON_TITLE;

    @ConfigOption(path = "frostbite.item.beacon.description")
    public static String FROSTBITE_ITEM_BEACON_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.explosion.title")
    public static String FROSTBITE_ITEM_EXPLOSION_TITLE;

    @ConfigOption(path = "frostbite.item.explosion.description")
    public static String FROSTBITE_ITEM_EXPLOSION_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.glow.title")
    public static String FROSTBITE_ITEM_GLOW_TITLE;

    @ConfigOption(path = "frostbite.item.glow.description")
    public static String FROSTBITE_ITEM_GLOW_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.hot_rod.title")
    public static String FROSTBITE_ITEM_HOT_ROD_TITLE;

    @ConfigOption(path = "frostbite.item.hot_rod.description")
    public static String FROSTBITE_ITEM_HOT_ROD_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.icicle.title")
    public static String FROSTBITE_ITEM_ICICLE_TITLE;

    @ConfigOption(path = "frostbite.item.icicle.description")
    public static String FROSTBITE_ITEM_ICICLE_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.invis.title")
    public static String FROSTBITE_ITEM_INVIS_TITLE;

    @ConfigOption(path = "frostbite.item.invis.description")
    public static String FROSTBITE_ITEM_INVIS_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.mystery.title")
    public static String FROSTBITE_ITEM_MYSTERY_TITLE;

    @ConfigOption(path = "frostbite.item.mystery.description")
    public static String FROSTBITE_ITEM_MYSTERY_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.phoenix.title")
    public static String FROSTBITE_ITEM_PHOENIX_TITLE;

    @ConfigOption(path = "frostbite.item.phoenix.description")
    public static String FROSTBITE_ITEM_PHOENIX_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.speed.title")
    public static String FROSTBITE_ITEM_SPEED_TITLE;

    @ConfigOption(path = "frostbite.item.speed.description")
    public static String FROSTBITE_ITEM_SPEED_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.frost_trap.title")
    public static String FROSTBITE_ITEM_FROST_TRAP_TITLE;

    @ConfigOption(path = "frostbite.item.frost_trap.description")
    public static String FROSTBITE_ITEM_FROST_TRAP_DESCRIPTION;

    @ConfigOption(path = "frostbite.item.whoaball.title")
    public static String FROSTBITE_ITEM_WHOABALL_TITLE;

    @ConfigOption(path = "frostbite.item.whoaball.description")
    public static String FROSTBITE_ITEM_WHOABALL_DESCRIPTION;
}
