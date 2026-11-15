package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the LaserBox domain; persisted by MessageConfig. */
public final class LaserBoxMessages {
    private LaserBoxMessages() {}

    @ConfigOption(path = "laserbox.start-preparation")
    public static String LASER_BOX_START_PREPARATION;

    @ConfigOption(path = "laserbox.start-preparation-title")
    public static String LASER_BOX_START_PREPARATION_TITLE;

    @ConfigOption(path = "laserbox.start-preparation-subtitle")
    public static String LASER_BOX_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "laserbox.game-start-title")
    public static String LASER_BOX_GAME_START_TITLE;

    @ConfigOption(path = "laserbox.game-start-subtitle")
    public static String LASER_BOX_GAME_START_SUBTITLE;

    @ConfigOption(path = "laserbox.boss-bar")
    public static String LASER_BOX_BOSS_BAR;

    @ConfigOption(path = "laserbox.hit")
    public static String LASER_BOX_HIT;

    @ConfigOption(path = "laserbox.special-attack-name")
    public static String LASER_BOX_SPECIAL_ATTACK_NAME;

    @ConfigOption(path = "laserbox.headshot", nullable = true)
    public static String LASER_BOX_HEADSHOT;

    @ConfigOption(path = "laserbox.respawn-title")
    public static String LASER_BOX_RESPAWN_TITLE;

    @ConfigOption(path = "laserbox.respawn-subtitle")
    public static String LASER_BOX_RESPAWN_SUBTITLE;

    @ConfigOption(path = "laserbox.winner")
    public static String LASER_BOX_WINNER;

    @ConfigOption(path = "laserbox.draw")
    public static String LASER_BOX_DRAW;

    @ConfigOption(path = "laserbox.end-title")
    public static String LASER_BOX_END_TITLE;

    @ConfigOption(path = "laserbox.end-subtitle")
    public static String LASER_BOX_END_SUBTITLE;

    @ConfigOption(path = "laserbox.item-shield")
    public static String LASER_BOX_ITEM_SHIELD;

    @ConfigOption(path = "laserbox.item-grenade")
    public static String LASER_BOX_ITEM_GRENADE;

    @ConfigOption(path = "laserbox.item-strike")
    public static String LASER_BOX_ITEM_STRIKE;

    @ConfigOption(path = "laserbox.item-reveal")
    public static String LASER_BOX_ITEM_REVEAL;

    @ConfigOption(path = "laserbox.gun-selection-title", nullable = true)
    public static String LASER_BOX_GUN_SELECTION_TITLE;

    @ConfigOption(path = "laserbox.gun-selection-subtitle", nullable = true)
    public static String LASER_BOX_GUN_SELECTION_SUBTITLE;

    @ConfigOption(path = "laserbox.gun-selection-chosen", nullable = true)
    public static String LASER_BOX_GUN_SELECTION_CHOSEN;

    @ConfigOption(path = "laserbox.gun-selection-default", nullable = true)
    public static String LASER_BOX_GUN_SELECTION_DEFAULT;

    @ConfigOption(path = "laserbox.ammo", nullable = true)
    public static String LASER_BOX_AMMO;

    @ConfigOption(path = "laserbox.reload", nullable = true)
    public static String LASER_BOX_RELOAD;

    @ConfigOption(path = "laserbox.reload-complete", nullable = true)
    public static String LASER_BOX_RELOAD_COMPLETE;

    @ConfigOption(path = "laserbox.no-ammo", nullable = true)
    public static String LASER_BOX_NO_AMMO;

    @ConfigOption(path = "laserbox.ammo-item", nullable = true)
    public static String LASER_BOX_AMMO_ITEM;

    @ConfigOption(path = "laserbox.gun-magazine", nullable = true)
    public static String LASER_BOX_GUN_MAGAZINE;

    @ConfigOption(path = "laserbox.gun-fire-rate", nullable = true)
    public static String LASER_BOX_GUN_FIRE_RATE;

    @ConfigOption(path = "laserbox.gun-selected", nullable = true)
    public static String LASER_BOX_GUN_SELECTED;

    @ConfigOption(path = "laserbox.guns.sniper.name", nullable = true)
    public static String LASER_BOX_GUN_SNIPER_NAME;

    @ConfigOption(path = "laserbox.guns.sniper.description", nullable = true)
    public static String LASER_BOX_GUN_SNIPER_DESCRIPTION;

    @ConfigOption(path = "laserbox.guns.rifle.name", nullable = true)
    public static String LASER_BOX_GUN_RIFLE_NAME;

    @ConfigOption(path = "laserbox.guns.rifle.description", nullable = true)
    public static String LASER_BOX_GUN_RIFLE_DESCRIPTION;

    @ConfigOption(path = "laserbox.guns.smg.name", nullable = true)
    public static String LASER_BOX_GUN_SMG_NAME;

    @ConfigOption(path = "laserbox.guns.smg.description", nullable = true)
    public static String LASER_BOX_GUN_SMG_DESCRIPTION;

    @ConfigOption(path = "laserbox.guns.machine.name", nullable = true)
    public static String LASER_BOX_GUN_MACHINE_NAME;

    @ConfigOption(path = "laserbox.guns.machine.description", nullable = true)
    public static String LASER_BOX_GUN_MACHINE_DESCRIPTION;

    @ConfigOption(path = "laserbox.guns.shotgun.name", nullable = true)
    public static String LASER_BOX_GUN_SHOTGUN_NAME;

    @ConfigOption(path = "laserbox.guns.shotgun.description", nullable = true)
    public static String LASER_BOX_GUN_SHOTGUN_DESCRIPTION;
}
