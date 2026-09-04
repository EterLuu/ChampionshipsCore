package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig.WindZone;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** List editor for the WorldEdit cuboids that act as Build Mart wind vents. */
public final class BuildMartWindZoneListStep extends PrepareStep {
    public BuildMartWindZoneListStep() {
        super("wind_zones", Component.text(GuiConfig.text("map-editor.menus.step-list.games.build-mart.items.wind-zones.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.build-mart.items.wind-zones.lore", 0)),
                Material.WIND_CHARGE, StepCaptureType.LIST);
    }

    private static BuildMartConfig cfg(SetupTarget target) {
        return (BuildMartConfig) target.config();
    }

    @Override
    public boolean requiresWorldEdit() {
        return true;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && !cfg(session.getTarget()).getWindZones().isEmpty();
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        WindZone zone = selection(session, player);
        if (zone == null) return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_WIND_SELECT_FIRST);
        BuildMartConfig config = cfg(session.getTarget());
        List<WindZone> zones = new ArrayList<>(config.getWindZones());
        zones.add(zone);
        config.setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_WIND_ADDED.replace("%count%", String.valueOf(zones.size())));
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        cfg(session.getTarget()).setWindZones(List.of());
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_WIND_CLEARED);
    }

    @Override
    public int listCount(@NotNull PrepareSession session) {
        return cfg(session.getTarget()).getWindZones().size();
    }

    @Override
    public @NotNull List<ListEntry> listEntries(@NotNull PrepareSession session) {
        List<WindZone> zones = cfg(session.getTarget()).getWindZones();
        List<ListEntry> entries = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) {
            WindZone zone = zones.get(i);
            Vector min = Vector.getMinimum(zone.pos1(), zone.pos2());
            Vector max = Vector.getMaximum(zone.pos1(), zone.pos2());
            entries.add(new ListEntry(GuiConfig.text(
                    "map-editor.menus.step-list.games.build-mart.items.wind-entry.title",
                    java.util.Map.of("number", i + 1)), List.of(
                    GuiConfig.line("map-editor.menus.step-list.games.build-mart.items.wind-entry.lore", 0,
                            java.util.Map.of("range", String.format(java.util.Locale.ROOT, "%s → %s", format(min), format(max)))),
                    GuiConfig.line("map-editor.menus.step-list.games.build-mart.items.wind-entry.lore", 1,
                            java.util.Map.of("top", formatY(max.getY() + 1))))));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        WindZone zone = selection(session, player);
        if (zone == null) return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_WIND_SELECT_FIRST);
        List<WindZone> zones = new ArrayList<>(cfg(session.getTarget()).getWindZones());
        if (index < 0 || index >= zones.size()) return null;
        zones.set(index, zone);
        cfg(session.getTarget()).setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_WIND_UPDATED.replace("%index%", String.valueOf(index + 1)));
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        List<WindZone> zones = new ArrayList<>(cfg(session.getTarget()).getWindZones());
        if (index < 0 || index >= zones.size() || newOrder < 1 || newOrder > zones.size())
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_SERIAL_NUMBER_BETWEEN.replace("%max%", String.valueOf(zones.size())));
        WindZone moved = zones.remove(index);
        zones.add(newOrder - 1, moved);
        cfg(session.getTarget()).setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_WIND_ADJUSTED.replace("%order%", String.valueOf(newOrder)));
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        List<WindZone> zones = new ArrayList<>(cfg(session.getTarget()).getWindZones());
        if (index < 0 || index >= zones.size()) return null;
        zones.remove(index);
        cfg(session.getTarget()).setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_WIND_ADJUSTED.replace("%order%", String.valueOf(index + 1)));
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text(GuiConfig.text("map-editor.menus.step-list.games.build-mart.items.wind-add.title"));
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text(GuiConfig.line("map-editor.menus.step-list.games.build-mart.items.wind-add.lore", 0));
    }

    private static WindZone selection(@NotNull PrepareSession session, @NotNull Player player) {
        try {
            Vector[] selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
            return new WindZone(selection[0], selection[1]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String format(Vector vector) {
        return vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ();
    }

    private static String formatY(double y) {
        return String.valueOf((int) Math.floor(y));
    }
}
