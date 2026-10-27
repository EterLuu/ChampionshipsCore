package ink.ziip.championshipscore.platform.bukkit.gui;

import ink.ziip.championshipscore.shared.presentation.PlayerListPresentation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A read-only player list bound to one connection, owner and admission snapshot. */
public final class PlayerListMenu {
    private PlayerListMenu() {}

    public record Target(UUID uuid, Map<String, String> values) {
        public Target {
            java.util.Objects.requireNonNull(uuid);
            values = Map.copyOf(values);
        }
    }

    public static void open(
            Player viewer,
            Object owner,
            PlayerListPresentation presentation,
            BooleanSupplier admitted,
            Supplier<List<Target>> targets,
            Consumer<UUID> select) {
        open(viewer, owner, presentation, Map.of(), admitted, targets, select);
    }

    public static void open(
            Player viewer,
            Object owner,
            PlayerListPresentation presentation,
            Map<String, String> values,
            BooleanSupplier admitted,
            Supplier<List<Target>> targets,
            Consumer<UUID> select) {
        if (!connected(viewer) || !admitted.getAsBoolean()) return;
        var holder = new Holder(viewer, owner, presentation, values, admitted, targets, select);
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        presentation.layout().size(),
                        InventoryItems.text(presentation.title(), values));
        holder.refresh();
        viewer.openInventory(holder.inventory);
    }

    public static boolean owns(Inventory inventory) {
        return inventory.getHolder(false) instanceof Holder;
    }

    public static boolean owns(Inventory inventory, Object owner) {
        return inventory.getHolder(false) instanceof Holder holder && holder.owner == owner;
    }

    /** Call only for a cancelled click; the original owner receives selection directly. */
    public static boolean click(Player player, Inventory inventory, int slot) {
        if (!(inventory.getHolder(false) instanceof Holder holder)) return false;
        if (holder.viewer != player
                || !connected(player)
                || player.getOpenInventory().getTopInventory() != inventory
                || !holder.admitted.getAsBoolean()) return true;
        String control = holder.controls.get(slot);
        if (control != null) {
            switch (control) {
                case "close" -> player.closeInventory();
                case "previous" -> {
                    holder.page--;
                    holder.refresh();
                }
                case "next" -> {
                    holder.page++;
                    holder.refresh();
                }
                default -> {}
            }
            return true;
        }
        UUID target = holder.rendered.get(slot);
        if (target != null
                && holder.targets.get().stream()
                        .anyMatch(candidate -> candidate.uuid().equals(target))) {
            player.closeInventory();
            if (connected(player) && holder.admitted.getAsBoolean()) holder.select.accept(target);
        }
        return true;
    }

    private static boolean connected(Player viewer) {
        return viewer.isOnline() && Bukkit.getPlayer(viewer.getUniqueId()) == viewer;
    }

    private static final class Holder implements InventoryHolder {
        private final Player viewer;
        private final Object owner;
        private final PlayerListPresentation presentation;
        private final Map<String, String> values;
        private final BooleanSupplier admitted;
        private final Supplier<List<Target>> targets;
        private final Consumer<UUID> select;
        private final Map<Integer, UUID> rendered = new HashMap<>();
        private final Map<Integer, String> controls = new HashMap<>();
        private Inventory inventory;
        private int page;

        private Holder(
                Player viewer,
                Object owner,
                PlayerListPresentation presentation,
                Map<String, String> values,
                BooleanSupplier admitted,
                Supplier<List<Target>> targets,
                Consumer<UUID> select) {
            this.viewer = viewer;
            this.owner = java.util.Objects.requireNonNull(owner);
            this.presentation = presentation;
            this.values = Map.copyOf(values);
            this.admitted = admitted;
            this.targets = targets;
            this.select = select;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void refresh() {
            inventory.clear();
            rendered.clear();
            controls.clear();
            var entries = List.copyOf(targets.get());
            var layout = presentation.layout();
            page = layout.clampPage(page, entries.size());
            var values = new HashMap<>(this.values);
            values.put("%page%", Integer.toString(page + 1));
            values.put("%pages%", Integer.toString(layout.pageCount(entries.size())));
            values.put("%count%", Integer.toString(entries.size()));
            for (int slot : layout.border())
                inventory.setItem(
                        slot, InventoryItems.render(presentation.items().get("border"), values));
            var slots = layout.pageSlots(page, entries.size());
            for (int i = 0; i < slots.size(); i++) {
                var entry = entries.get(page * layout.content().size() + i);
                var entryValues = new HashMap<>(this.values);
                entryValues.putAll(entry.values());
                inventory.setItem(
                        slots.get(i),
                        InventoryItems.render(presentation.items().get("player"), entryValues));
                rendered.put(slots.get(i), entry.uuid());
            }
            for (String control : PlayerListPresentation.CONTROLS) {
                if (control.equals("previous") && page == 0
                        || control.equals("next") && page + 1 >= layout.pageCount(entries.size()))
                    continue;
                int slot = layout.control(control);
                inventory.setItem(
                        slot, InventoryItems.render(presentation.items().get(control), values));
                controls.put(slot, control);
            }
        }
    }
}
