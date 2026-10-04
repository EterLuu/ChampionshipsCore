package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import org.bukkit.Color;

/** Minecraft team palette and explicit RGB values used by equipment and displays. */
public final class TeamColors {
    private TeamColors() {}

    public static Color fromHex(String hexColor) {
        try {
            String value = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            if (value.length() != 6) throw new IllegalArgumentException("颜色必须为六位十六进制值");
            return Color.fromRGB(
                    Integer.parseInt(value.substring(0, 2), 16),
                    Integer.parseInt(value.substring(2, 4), 16),
                    Integer.parseInt(value.substring(4, 6), 16));
        } catch (Exception ignored) {
            return Color.fromBGR(0, 0, 0);
        }
    }

    public static String[] names() {
        return new String[] {
            "white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
        };
    }
}
