package ink.ziip.championshipscore.api.game.area.prepare.tgttos;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
            new Option("BOAT", Material.OAK_BOAT, "BOAT", GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-001")),
            new Option("ROAD", Material.DIAMOND_PICKAXE, "ROAD", GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-002")),
            new Option("NONE", Material.BARRIER, "NONE", GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-003")),
            new Option("ELYTRA", Material.ELYTRA, "ELYTRA", GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-004"))
    );

    public TGTTOSAreaTypeStep() {
        super("area_type", Component.text(GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-005")),
                Component.text(GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-006")), Material.CHEST, StepCaptureType.SELECT);
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
        return option == null ? GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-007") : option.name() + "（" + option.description() + "）";
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        TGTTOSAreaTypeGui.open(player, session, this);
    }

    public String select(@NotNull PrepareSession session, @NotNull Option option) {
        config(session).setAreaType(option.value());
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-tgttos-tgttosareatypestep.text-008") + option.name()
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
