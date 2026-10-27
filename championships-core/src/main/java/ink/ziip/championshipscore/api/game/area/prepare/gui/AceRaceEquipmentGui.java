package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.gui.MenuId;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Selects the equipment available from one Ace Race checkpoint to the next. */
public final class AceRaceEquipmentGui {
    private AceRaceEquipmentGui() {}

    public static final class Holder extends PrepareMenu {
        final Consumer<AceRaceEquipment> callback;
        final Map<Integer, AceRaceEquipment> options = new HashMap<>();

        Holder(Player player, PrepareSession session, Consumer<AceRaceEquipment> callback) {
            super(
                    player,
                    session,
                    MenuId.ACE_RACE_EQUIPMENT,
                    9,
                    List.of(),
                    List.of(),
                    Map.of("none", 1, "elytra", 3, "trident", 5, "dolphins", 7),
                    Map.of());
            this.callback = callback;
        }
    }

    public static void open(
            @NotNull Player player,
            @NotNull PrepareSession session,
            @NotNull AceRaceEquipment current,
            @NotNull Consumer<AceRaceEquipment> callback) {
        Holder holder = new Holder(player, session, callback);
        holder.clear();
        for (AceRaceEquipment equipment : AceRaceEquipment.values()) {
            String id =
                    equipment == AceRaceEquipment.DOLPHINS_GRACE
                            ? "dolphins"
                            : equipment.configValue();
            int slot = holder.layout.control(id);
            holder.put(
                    id,
                    equipment == current ? "selected" : "idle",
                    Map.of("name", equipment.displayName()));
            holder.options.put(slot, equipment);
        }
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
        AceRaceEquipment equipment = holder.options.get(event.getRawSlot());
        if (equipment == null) return;
        player.closeInventory();
        holder.callback.accept(equipment);
        PrepareModeInventory.refresh(player, holder.session);
    }
}
