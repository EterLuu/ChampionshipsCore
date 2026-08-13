package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** The first screen players see when they enter the public-play lobby. */
final class DailyLobbyMenu {
    private static final int SIZE = 27;
    private static final int STATS_SLOT = 11;
    private static final int MATCH_SLOT = 13;
    private static final int PARTY_SLOT = 15;
    private static final int SPECTATE_SLOT = 17;
    private static final int CLOSE_SLOT = 22;
    private final DailyManager daily;

    DailyLobbyMenu(DailyManager daily) {
        this.daily = daily;
    }

    void open(Player player) {
        LobbyHolder holder = new LobbyHolder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text(GuiConfig.text("daily.menus.lobby.game-lobby"), NamedTextColor.DARK_AQUA)
                .decorate(TextDecoration.BOLD));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof LobbyHolder holder) refresh(holder);
        }
    }

    void click(Player player, int slot, LobbyHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == STATS_SLOT) {
            daily.openStatsMenu(player);
            clickSound(player, 1.15F);
        } else if (slot == MATCH_SLOT) {
            daily.openMatchMenu(player);
            clickSound(player, 1.2F);
        } else if (slot == PARTY_SLOT) {
            daily.openPartyMenu(player);
            clickSound(player, 1.1F);
        } else if (slot == SPECTATE_SLOT) {
            daily.openSpectateMenu(player);
            clickSound(player, 1.3F);
        }
    }

    private void refresh(LobbyHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 18) inventory.setItem(slot, border);
        }

        UUID viewer = holder.viewer;
        DailyPlayerSnapshot snapshot = daily.snapshot(viewer);
        inventory.setItem(4, item(Material.NETHER_STAR,
                Component.text(GuiConfig.text("daily.menus.lobby.lobby-status"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(
                        line(GuiConfig.text("daily.menus.lobby.current-status"), snapshot.queueState(), NamedTextColor.WHITE),
                        line(GuiConfig.text("daily.menus.lobby.current-game"), snapshot.selectedGame(), NamedTextColor.AQUA),
                        line(GuiConfig.text("daily.menus.lobby.number-of-people-traveling-together"), snapshot.partySize() + GuiConfig.text("daily.menus.lobby.player-count-suffix"), NamedTextColor.WHITE),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby.select-a-function-below-to-get-started"), NamedTextColor.GRAY)
                )));
        inventory.setItem(STATS_SLOT, item(Material.WRITABLE_BOOK,
                Component.text(GuiConfig.text("daily.menus.lobby.personal-record"), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby.check-your-games-wins-and-winning-percentage"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby.and-detailed-records-of-each-game"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby.click-to-view"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(MATCH_SLOT, item(Material.COMPASS,
                Component.text(GuiConfig.text("daily.menus.lobby.enter-matching"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby.choose-the-game-you-want-to-participate-in"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby.check-the-number-of-people-and-opening-progress-in-real-time"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby.click-to-enter"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(PARTY_SLOT, item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("daily.menus.lobby.team-function"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby.invite-friends-to-join-the-match"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby.accept-invitations-or-manage-partys"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby.click-to-open"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(SPECTATE_SLOT, item(Material.SPYGLASS,
                Component.text(GuiConfig.text("daily.menus.lobby.spectator-game"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby.view-currently-playing-games"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby.and-select-live-venues-to-spectate-on"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby.click-to-open"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text(GuiConfig.text("daily.menus.lobby.close"), NamedTextColor.RED), List.of()));
    }

    private static Component line(String label, String value, NamedTextColor color) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, color));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class LobbyHolder implements InventoryHolder {
        private final UUID viewer;
        private Inventory inventory;

        private LobbyHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
