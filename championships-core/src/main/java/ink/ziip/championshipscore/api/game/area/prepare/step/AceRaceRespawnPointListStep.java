package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
        super("respawn_points", Component.text(GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-points.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.respawn-points.lore", 0)),
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
            return ink.ziip.championshipscore.util.Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_SERIAL_NUMBER_BETWEEN.replace("%max%", String.valueOf(count)));
        config.moveRespawnPoint(index, newOrder);
        session.markDirty();
        String result = ink.ziip.championshipscore.util.Utils.formatAdminSuccess(
                MessageConfig.MAP_EDITOR_STEP_POINT_ADJUSTED_TO.replace("%order%", String.valueOf(newOrder)));
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
                MessageConfig.MAP_EDITOR_STEP_DELETED.replace("%order%", String.valueOf(index + 1)));
        reload(session);
        return result;
    }

    public String bindingText(@NotNull PrepareSession session, int index) {
        AceRaceArea area = area(session);
        if (area == null || area.getRespawnPointIndexForConfig(index) < 0) return GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-binding.states.not-loaded.title");
        int binding = area.getRespawnPointBinding(index);
        return binding < 0 ? GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-binding.states.after-start.title") : GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-binding.states.bound.title").replace("%line%", String.valueOf(binding + 1));
    }

    public String setBinding(@NotNull PrepareSession session, int index, int binding) {
        AceRaceArea area = area(session);
        if (area == null || !area.setRespawnPointBinding(index, binding)) return null;
        session.markDirty();
        return ink.ziip.championshipscore.util.Utils.formatAdminSuccess(
                MessageConfig.MAP_EDITOR_ACE_RESPAWN_BOUND.replace("%binding%",
                binding < 0 ? GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-binding.states.after-start.title")
                        : GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-binding.states.bound.title").replace("%line%", String.valueOf(binding + 1))));
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
        return Component.text(GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.respawn-add.title"));
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text(GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.respawn-add.lore", 0));
    }
}
