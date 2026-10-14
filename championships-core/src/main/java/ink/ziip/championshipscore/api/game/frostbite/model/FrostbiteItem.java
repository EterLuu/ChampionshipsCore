package ink.ziip.championshipscore.api.game.frostbite.model;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Material;

/** Arena power-ups. */
public enum FrostbiteItem {
    AVALANCHE(Material.SNOWBALL, "frostbite-item.avalanche-1", "frostbite-item.avalanche-2"),
    AXE(Material.IRON_AXE, "frostbite-item.axe-1", "frostbite-item.axe-2"),
    BLAZE(Material.CAMPFIRE, "frostbite-item.blaze-1", "frostbite-item.blaze-2"),
    BOW(Material.BOW, "frostbite-item.bow-1", "frostbite-item.bow-2"),
    BEACON(Material.MAGMA_BLOCK, "frostbite-item.beacon-1", "frostbite-item.beacon-2"),
    EXPLOSION(Material.TNT, "frostbite-item.explosion-1", "frostbite-item.explosion-2"),
    GLOW(Material.GLOW_INK_SAC, "frostbite-item.glow-1", "frostbite-item.glow-2"),
    HOT_ROD(Material.BLAZE_ROD, "frostbite-item.hot-rod-1", "frostbite-item.hot-rod-2"),
    ICICLE(Material.PRISMARINE_SHARD, "frostbite-item.icicle-1", "frostbite-item.icicle-2"),
    INVIS(Material.FERMENTED_SPIDER_EYE, "frostbite-item.invis-1", "frostbite-item.invis-2"),
    MYSTERY(Material.ENDER_CHEST, "frostbite-item.mystery-1", "frostbite-item.mystery-2"),
    PHOENIX(Material.TOTEM_OF_UNDYING, "frostbite-item.phoenix-1", "frostbite-item.phoenix-2"),
    SPEED(Material.SUGAR, "frostbite-item.speed-1", "frostbite-item.speed-2"),
    FROST_TRAP(Material.BLUE_ICE, "frostbite-item.frost-trap-1", "frostbite-item.frost-trap-2"),
    WHOABALL(Material.SNOWBALL, "frostbite-item.whoaball-1", "frostbite-item.whoaball-2");
    public final Material material;
    private final String title;
    private final String description;

    public String title() {
        return MessageConfig.codeText(title);
    }

    public String description() {
        return MessageConfig.codeText(description);
    }

    FrostbiteItem(Material material, String title, String description) {
        this.material = material;
        this.title = title;
        this.description = description;
    }
}
