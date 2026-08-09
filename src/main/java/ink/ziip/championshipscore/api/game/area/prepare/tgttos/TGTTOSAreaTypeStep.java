package ink.ziip.championshipscore.api.game.area.prepare.tgttos;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.TGTTOSAreaTypeGui;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Selects the map's player equipment/game-mode profile from the prepare menu. */
public final class TGTTOSAreaTypeStep extends PrepareStep {
    public record Option(String value, Material icon, String name, String description) {
    }

    private static final List<Option> OPTIONS = List.of(
            new Option("BOAT", Material.OAK_BOAT, "BOAT", "橡木船 / 生存"),
            new Option("ROAD", Material.DIAMOND_PICKAXE, "ROAD", "钻石镐与队伍混凝土 / 生存"),
            new Option("NONE", Material.BARRIER, "NONE", "无物品 / 冒险"),
            new Option("ELYTRA", Material.ELYTRA, "ELYTRA", "不可破坏鞘翅 / 冒险")
    );

    public TGTTOSAreaTypeStep() {
        super("area_type", Component.text("地图装备类型"),
                Component.text("打开菜单选择开局模式与物品"), Material.CHEST, StepCaptureType.SELECT);
    }

    public static List<Option> options() {
        return OPTIONS;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        if (session == null) return false;
        return find(config(session).getAreaType()) != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        if (session == null) return null;
        Option option = find(config(session).getAreaType());
        return option == null ? "未设置" : option.name() + "（" + option.description() + "）";
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        TGTTOSAreaTypeGui.open(player, session, this);
    }

    public String select(@NotNull PrepareSession session, @NotNull Option option) {
        config(session).setAreaType(option.value());
        session.markDirty();
        return Utils.formatAdminSuccess("已设置地图装备类型：&#fff566" + option.name()
                + " &#696969• &#ededed" + option.description());
    }

    private static TGTTOSConfig config(PrepareSession session) {
        return (TGTTOSConfig) session.getTarget().config();
    }

    private static Option find(String value) {
        if (value == null) return null;
        for (Option option : OPTIONS) {
            if (option.value().equalsIgnoreCase(value)) return option;
        }
        return null;
    }
}
