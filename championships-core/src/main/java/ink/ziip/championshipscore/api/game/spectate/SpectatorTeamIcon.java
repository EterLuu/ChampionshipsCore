package ink.ziip.championshipscore.api.game.spectate;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Pure team-colour to inventory-icon mapping. */
final class SpectatorTeamIcon {
    private SpectatorTeamIcon() {
    }

    static @NotNull Material wool(@Nullable String colorName) {
        if (colorName == null) return Material.WHITE_WOOL;
        Material material = Material.getMaterial(colorName.toUpperCase(Locale.ROOT) + "_WOOL");
        return material == null ? Material.WHITE_WOOL : material;
    }
}
