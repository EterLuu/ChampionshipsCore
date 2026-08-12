package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Edits course-ordered markers which are bound to Ace Race progress segments at load time. */
public final class AceRaceRespawnPointListStep extends ListStep {
    public AceRaceRespawnPointListStep() {
        super("respawn_points", Component.text(GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-001")),
                Component.text(GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-002")),
                Material.RECOVERY_COMPASS,
                target -> cfg(target).ensureRespawnPoints(),
                (target, values) -> cfg(target).setRespawnPoints(values),
                target -> cfg(target).ensureRespawnPoints().isEmpty(),
                (target, value) -> cfg(target).addRespawnPoint(value),
                target -> cfg(target).clearRespawnPoints(),
                target -> cfg(target).ensureRespawnPoints().size());
    }

    private static AceRaceConfig cfg(SetupTarget target) {
        return (AceRaceConfig) target.config();
    }

    private static void reload(PrepareSession session) {
        AceRaceArea area = session.getPlugin().getGameManager().getAceRaceManager().getArea(session.getAreaName());
        if (area != null) area.loadCoursePoints();
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        String result = super.listAdd(session, player);
        reload(session);
        return result;
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        String result = super.listClear(session, player);
        reload(session);
        return result;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        String result = super.listEdit(session, player, index);
        reload(session);
        return result;
    }

    @Override
    public @NotNull List<ListEntry> listEntries(@NotNull PrepareSession session) {
        List<ListEntry> base = super.listEntries(session);
        List<ListEntry> entries = new ArrayList<>(base.size());
        for (int index = 0; index < base.size(); index++) {
            List<String> details = new ArrayList<>(base.get(index).details());
            details.add(bindingText(session, index));
            entries.add(new ListEntry(base.get(index).title(), details));
        }
        return entries;
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        AceRaceConfig config = cfg(session.getTarget());
        int count = config.ensureRespawnPoints().size();
        if (index < 0 || index >= count || newOrder < 1 || newOrder > count)
            return ink.ziip.championshipscore.util.Utils.formatAdminError(GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-003") + count + GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-004"));
        config.moveRespawnPoint(index, newOrder);
        session.markDirty();
        String result = ink.ziip.championshipscore.util.Utils.formatAdminSuccess(
                GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-005") + newOrder + GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-006"));
        reload(session);
        return result;
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        AceRaceConfig config = cfg(session.getTarget());
        if (index < 0 || index >= config.ensureRespawnPoints().size()) return null;
        config.removeRespawnPoint(index);
        session.markDirty();
        String result = ink.ziip.championshipscore.util.Utils.formatAdminSuccess(
                GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-007") + (index + 1) + GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-008"));
        reload(session);
        return result;
    }

    public String bindingText(@NotNull PrepareSession session, int index) {
        AceRaceArea area = area(session);
        if (area == null || area.getRespawnPointIndexForConfig(index) < 0) return GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-009");
        int binding = area.getRespawnPointBinding(index);
        return binding < 0 ? GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-010") : GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-011") + (binding + 1) + GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-012");
    }

    public String setBinding(@NotNull PrepareSession session, int index, int binding) {
        AceRaceArea area = area(session);
        if (area == null || !area.setRespawnPointBinding(index, binding)) return null;
        session.markDirty();
        return ink.ziip.championshipscore.util.Utils.formatAdminSuccess(
                GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-013") + (binding < 0 ? GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-014") : GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-015") + (binding + 1) + GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-012")) + "。");
    }

    public int currentBinding(@NotNull PrepareSession session, int index) {
        AceRaceArea area = area(session);
        return area == null ? -1 : area.getRespawnPointBinding(index);
    }

    public AceRaceArea area(@NotNull PrepareSession session) {
        return session.getPlugin().getGameManager().getAceRaceManager().getArea(session.getAreaName());
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text(GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-016"));
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text(GuiConfig.text("prepare-step-aceracerespawnpointliststep.text-017"));
    }
}
