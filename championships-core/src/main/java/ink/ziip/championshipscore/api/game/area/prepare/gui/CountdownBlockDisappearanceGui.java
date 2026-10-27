package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.step.CountdownBlockDisappearanceStep;
import ink.ziip.championshipscore.api.gui.MenuId;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Editor GUI for the optional opening-countdown block disappearance. */
public final class CountdownBlockDisappearanceGui {
    private CountdownBlockDisappearanceGui() {}

    public static final class Holder extends PrepareMenu {
        final CountdownBlockDisappearanceStep step;
        final Map<Integer, CountdownBlockDisappearanceStep.Mode> modes = new HashMap<>();

        Holder(Player player, PrepareSession session, CountdownBlockDisappearanceStep step) {
            super(
                    player,
                    session,
                    MenuId.MAP_EDITOR_COUNTDOWN_BLOCKS,
                    27,
                    List.of(),
                    List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 19, 20, 21, 23, 24, 25, 26),
                    Map.of(
                            "selection",
                            10,
                            "random",
                            12,
                            "east-west",
                            13,
                            "north-south",
                            14,
                            "vertical",
                            15,
                            "direct",
                            16,
                            "clear",
                            17,
                            "back",
                            22),
                    Map.of());
            this.step = step;
        }
    }

    public static void open(
            @NotNull Player player,
            @NotNull PrepareSession session,
            @NotNull CountdownBlockDisappearanceStep step) {
        Holder holder = new Holder(player, session, step);
        holder.clear();
        var config = session.getTarget().config();
        Vector first = config.getCountdownBlockDisappearancePos1();
        Vector second = config.getCountdownBlockDisappearancePos2();
        boolean enabled = first != null && second != null;
        holder.put(
                "selection",
                enabled ? "enabled" : "disabled",
                Map.of("volume", enabled ? String.valueOf(volume(first, second)) : ""));
        var current =
                CountdownBlockDisappearanceStep.Mode.from(
                        config.getCountdownBlockDisappearanceMode());
        for (var mode : CountdownBlockDisappearanceStep.Mode.values()) {
            String id = mode.itemId();
            holder.put(
                    id,
                    mode == current ? "current" : "idle",
                    Map.of("enabled", enabled ? "1" : "0"));
            holder.modes.put(holder.layout.control(id), mode);
        }
        holder.put("clear", null, Map.of());
        holder.put("back", null, Map.of());
        player.openInventory(holder.inventory);
    }

    public static void handleClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
            @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != holder.inventory || !holder.available(manager, player))
            return;
        int slot = event.getRawSlot();
        if (slot == holder.layout.control("selection")) {
            String message = holder.step.captureSelection(holder.session, player);
            if (message != null) player.sendMessage(message);
            back(player, holder.session);
            return;
        }
        var mode = holder.modes.get(slot);
        if (mode != null) {
            player.sendMessage(holder.step.selectMode(holder.session, mode));
            back(player, holder.session);
            return;
        }
        if (slot == holder.layout.control("clear")) {
            player.sendMessage(holder.step.clearSelection(holder.session));
            back(player, holder.session);
            return;
        }
        if (slot == holder.layout.control("back")) back(player, holder.session);
    }

    private static long volume(Vector first, Vector second) {
        return ((long) Math.abs(first.getBlockX() - second.getBlockX()) + 1L)
                * ((long) Math.abs(first.getBlockY() - second.getBlockY()) + 1L)
                * ((long) Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L);
    }

    private static void back(Player player, PrepareSession session) {
        player.closeInventory();
        PrepareModeInventory.refresh(player, session);
    }
}
