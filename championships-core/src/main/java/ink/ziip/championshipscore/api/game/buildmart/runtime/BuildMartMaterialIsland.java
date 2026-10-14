package ink.ziip.championshipscore.api.game.buildmart.runtime;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Stable display/classification order for the 24 physical resource islands in Build Mart. */
public enum BuildMartMaterialIsland {
    WHITE(
            "white",
            MessageConfig.codeText("build-mart-material-island.white-1"),
            Material.WHITE_WOOL),
    ORANGE(
            "orange",
            MessageConfig.codeText("build-mart-material-island.orange-1"),
            Material.ORANGE_WOOL),
    MAGENTA(
            "magenta",
            MessageConfig.codeText("build-mart-material-island.magenta-1"),
            Material.MAGENTA_WOOL),
    LIGHT_BLUE(
            "light-blue",
            MessageConfig.codeText("build-mart-material-island.light-blue-1"),
            Material.LIGHT_BLUE_WOOL),
    YELLOW(
            "yellow",
            MessageConfig.codeText("build-mart-material-island.yellow-1"),
            Material.YELLOW_WOOL),
    LIME("lime", MessageConfig.codeText("build-mart-material-island.lime-1"), Material.LIME_WOOL),
    PINK("pink", MessageConfig.codeText("build-mart-material-island.pink-1"), Material.PINK_WOOL),
    GRAY("gray", MessageConfig.codeText("build-mart-material-island.gray-1"), Material.GRAY_WOOL),
    LIGHT_GRAY(
            "light-gray",
            MessageConfig.codeText("build-mart-material-island.light-gray-1"),
            Material.LIGHT_GRAY_WOOL),
    CYAN("cyan", MessageConfig.codeText("build-mart-material-island.cyan-1"), Material.CYAN_WOOL),
    PURPLE(
            "purple",
            MessageConfig.codeText("build-mart-material-island.purple-1"),
            Material.PURPLE_WOOL),
    BLUE("blue", MessageConfig.codeText("build-mart-material-island.blue-1"), Material.BLUE_WOOL),
    BROWN(
            "brown",
            MessageConfig.codeText("build-mart-material-island.brown-1"),
            Material.BROWN_WOOL),
    GREEN(
            "green",
            MessageConfig.codeText("build-mart-material-island.green-1"),
            Material.GREEN_WOOL),
    RED("red", MessageConfig.codeText("build-mart-material-island.red-1"), Material.RED_WOOL),
    BLACK(
            "black",
            MessageConfig.codeText("build-mart-material-island.black-1"),
            Material.BLACK_WOOL),

    PLANTS(
            "plants",
            MessageConfig.codeText("build-mart-material-island.plants-1"),
            Material.FLOWERING_AZALEA),
    NETHER(
            "nether",
            MessageConfig.codeText("build-mart-material-island.nether-1"),
            Material.NETHERRACK),
    TREES("trees", MessageConfig.codeText("build-mart-material-island.trees-1"), Material.OAK_LOG),
    BRICKS(
            "bricks",
            MessageConfig.codeText("build-mart-material-island.bricks-1"),
            Material.BRICKS),
    STONE("stone", MessageConfig.codeText("build-mart-material-island.stone-1"), Material.STONE),
    MINERALS(
            "minerals",
            MessageConfig.codeText("build-mart-material-island.minerals-1"),
            Material.DIAMOND_ORE),
    SAND_GRAVEL(
            "sand-gravel",
            MessageConfig.codeText("build-mart-material-island.sand-gravel-1"),
            Material.SAND),
    COPPER(
            "copper",
            MessageConfig.codeText("build-mart-material-island.copper-1"),
            Material.COPPER_BLOCK);

    private final String id;
    private final String displayName;
    private final Material icon;

    BuildMartMaterialIsland(
            @NotNull String id, @NotNull String displayName, @NotNull Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String titleKey() {
        return "map-editor.games.build-mart.menus.material-zones.items.island.states."
                + id
                + ".title";
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
