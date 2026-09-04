package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Stable display/classification order for the 24 physical resource islands in Build Mart. */
public enum BuildMartMaterialIsland {

    WHITE("white", "白色岛", Material.WHITE_WOOL),
    ORANGE("orange", "橙色岛", Material.ORANGE_WOOL),
    MAGENTA("magenta", "品红色岛", Material.MAGENTA_WOOL),
    LIGHT_BLUE("light-blue", "淡蓝色岛", Material.LIGHT_BLUE_WOOL),
    YELLOW("yellow", "黄色岛", Material.YELLOW_WOOL),
    LIME("lime", "黄绿色岛", Material.LIME_WOOL),
    PINK("pink", "粉红色岛", Material.PINK_WOOL),
    GRAY("gray", "灰色岛", Material.GRAY_WOOL),
    LIGHT_GRAY("light-gray", "淡灰色岛", Material.LIGHT_GRAY_WOOL),
    CYAN("cyan", "青色岛", Material.CYAN_WOOL),
    PURPLE("purple", "紫色岛", Material.PURPLE_WOOL),
    BLUE("blue", "蓝色岛", Material.BLUE_WOOL),
    BROWN("brown", "棕色岛", Material.BROWN_WOOL),
    GREEN("green", "绿色岛", Material.GREEN_WOOL),
    RED("red", "红色岛", Material.RED_WOOL),
    BLACK("black", "黑色岛", Material.BLACK_WOOL),

    PLANTS("plants", "植物类", Material.FLOWERING_AZALEA),
    NETHER("nether", "下界类", Material.NETHERRACK),
    TREES("trees", "树木类", Material.OAK_LOG),
    BRICKS("bricks", "砖块类", Material.BRICKS),
    STONE("stone", "石头类", Material.STONE),
    MINERALS("minerals", "矿物类", Material.DIAMOND_ORE),
    SAND_GRAVEL("sand-gravel", "沙砾类", Material.SAND),
    COPPER("copper", "铜类", Material.COPPER_BLOCK);

    private final String id;
    private final String displayName;
    private final Material icon;

    BuildMartMaterialIsland(@NotNull String id, @NotNull String displayName, @NotNull Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String titleKey() {
        return "map-editor.games.build-mart.menus.material-zones.items.island.states." + id + ".title";
    }

    public @NotNull String displayName() {
        return GuiConfig.text(titleKey());
    }

    public @NotNull Material icon() {
        return icon;
    }

    public static @Nullable BuildMartMaterialIsland byId(@Nullable String id) {
        if (id == null) return null;
        for (BuildMartMaterialIsland island : values()) {
            if (island.id.equalsIgnoreCase(id)) return island;
        }
        return null;
    }
}
