package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Semantic level categories. Visual pass obstacles are generator details, not persisted game rules. */
public enum RiptideLevelType {
    MATH("解题", "算术或观察题，左右选择答案；错误的一侧出局", Material.RED_CONCRETE),
    PASS("穿越", "穿过自动生成的障碍，没有额外判定", Material.IRON_BARS),
    COLOR_FLOOR("踩色", "木筏停留原地，完成逐轮踩色挑战", Material.LIME_CONCRETE),
    DODGE("躲避", "木筏停留原地，按提示躲避同一方向随机刷新的冲刺生物", Material.SWEET_BERRIES),
    RHYTHM("节奏", "木筏持续前进，抓住闸门节拍通过，离筏或掉落出局", Material.IRON_TRAPDOOR);

    private final String displayName;
    private final String description;
    private final Material icon;

    RiptideLevelType(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    public @NotNull String description() {
        return description;
    }

    public @NotNull Material icon() {
        return icon;
    }

    public static @NotNull RiptideLevelType parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("level type is missing");
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
