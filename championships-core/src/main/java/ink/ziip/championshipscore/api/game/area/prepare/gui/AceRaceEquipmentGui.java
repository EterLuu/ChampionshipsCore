package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.acerace.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/** Selects the equipment available from one Ace Race checkpoint to the next. */
public final class AceRaceEquipmentGui {
    private AceRaceEquipmentGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        final Consumer<AceRaceEquipment> callback;
        Inventory inventory;

        Holder(@NotNull PrepareSession session, @NotNull Consumer<AceRaceEquipment> callback) {
            this.session = session;
            this.callback = callback;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull PrepareSession session,
                            @NotNull AceRaceEquipment current,
                            @NotNull Consumer<AceRaceEquipment> callback) {
        Holder holder = new Holder(session, callback);
        holder.inventory = Bukkit.createInventory(holder, 9,
                Component.text(GuiConfig.text("map-editor.games.ace-race.menus.equipment.select-equipment-for-next-stage")).decoration(TextDecoration.ITALIC, false));
        holder.inventory.setItem(1, option(Material.BARRIER, AceRaceEquipment.NONE, current));
        holder.inventory.setItem(3, option(Material.ELYTRA, AceRaceEquipment.ELYTRA, current));
        holder.inventory.setItem(5, option(Material.TRIDENT, AceRaceEquipment.TRIDENT, current));
        holder.inventory.setItem(7, option(Material.HEART_OF_THE_SEA, AceRaceEquipment.DOLPHINS_GRACE, current));
        player.openInventory(holder.inventory);
    }

    public static void handleClick(@NotNull PrepareSessionManager manager,
                                   @NotNull InventoryClickEvent event, @NotNull Player player,
                                   @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        AceRaceEquipment equipment = switch (event.getRawSlot()) {
            case 1 -> AceRaceEquipment.NONE;
            case 3 -> AceRaceEquipment.ELYTRA;
            case 5 -> AceRaceEquipment.TRIDENT;
            case 7 -> AceRaceEquipment.DOLPHINS_GRACE;
            default -> null;
        };
        if (equipment == null) return;
        player.closeInventory();
        holder.callback.accept(equipment);
        PrepareModeInventory.refresh(player, session);
    }

    private static @NotNull ItemStack option(@NotNull Material material,
                                             @NotNull AceRaceEquipment equipment,
                                             @NotNull AceRaceEquipment current) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(equipment.displayName()).color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(equipment == current ? GuiConfig.text("map-editor.games.ace-race.menus.equipment.current-selection") : GuiConfig.text("map-editor.games.ace-race.menus.equipment.click-to-select"))
                    .color(equipment == current ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        });
        return item;
    }
}
