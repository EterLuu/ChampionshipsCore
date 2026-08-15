package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
        super("wind_zones", Component.text(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.air-outlet-area")),
                Component.text(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.use-worldedit-selection-to-add-multiple-blower-areas-updrafts-will-be-generated-above-each-area")),
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
        if (zone == null) return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.please-use-worldedit-to-select-the-blower-area-first"));
        BuildMartConfig config = cfg(session.getTarget());
        List<WindZone> zones = new ArrayList<>(config.getWindZones());
        zones.add(zone);
        config.setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.the-blower-area-has-been-added-currently") + zones.size() + GuiConfig.text("map-editor.copy.colored-item-count-suffix"));
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        cfg(session.getTarget()).setWindZones(List.of());
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.the-blower-area-list-has-been-cleared"));
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
            entries.add(new ListEntry(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.air-outlet") + (i + 1), List.of(
                    GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.area") + format(min) + " → " + format(max),
                    GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.rising-range-y") + formatY(max.getY() + 1) + GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.through-200-suffix"))));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        WindZone zone = selection(session, player);
        if (zone == null) return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.please-use-worldedit-to-reselect-the-blower-area-first"));
        List<WindZone> zones = new ArrayList<>(cfg(session.getTarget()).getWindZones());
        if (index < 0 || index >= zones.size()) return null;
        zones.set(index, zone);
        cfg(session.getTarget()).setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.updated") + (index + 1) + GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.wind-zone-count-suffix"));
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        List<WindZone> zones = new ArrayList<>(cfg(session.getTarget()).getWindZones());
        if (index < 0 || index >= zones.size() || newOrder < 1 || newOrder > zones.size())
            return Utils.formatAdminError(GuiConfig.text("map-editor.copy.the-serial-number-must-be-between-1-and") + zones.size() + GuiConfig.text("map-editor.copy.range-end-suffix"));
        WindZone moved = zones.remove(index);
        zones.add(newOrder - 1, moved);
        cfg(session.getTarget()).setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.the-air-blower-has-been-adjusted-to-the") + newOrder + GuiConfig.text("map-editor.copy.item-suffix"));
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        List<WindZone> zones = new ArrayList<>(cfg(session.getTarget()).getWindZones());
        if (index < 0 || index >= zones.size()) return null;
        zones.remove(index);
        cfg(session.getTarget()).setWindZones(zones);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.deleted") + (index + 1) + GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.wind-zone-count-suffix"));
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.add-blower-area"));
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text(GuiConfig.text("map-editor.games.build-mart.steps.wind-zones.use-worldedit-to-select-an-area-and-click"));
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
