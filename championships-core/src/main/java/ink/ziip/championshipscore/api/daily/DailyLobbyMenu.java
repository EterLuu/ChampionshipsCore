package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;

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
import java.util.Map;
import java.util.UUID;

/** The first screen players see when they enter the public-play lobby. */
final class DailyLobbyMenu {
    private static final String MENU_PATH = MenuId.DAILY_LOBBY.path();
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
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, SIZE,
                "&3&l" + GuiConfig.text("daily.menus.lobby-screen.copy.game-lobby"), List.of(11, 13, 15, 17));
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
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
        if (slot == slot("close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (slot == slot("statistics", STATS_SLOT)) {
            daily.openStatsMenu(player);
            clickSound(player, 1.15F);
        } else if (slot == slot("matchmaking", MATCH_SLOT)) {
            daily.openMatchMenu(player);
            clickSound(player, 1.2F);
        } else if (slot == slot("party", PARTY_SLOT)) {
            daily.openPartyMenu(player);
            clickSound(player, 1.1F);
        } else if (slot == slot("spectate", SPECTATE_SLOT)) {
            daily.openSpectateMenu(player);
            clickSound(player, 1.3F);
        }
    }

    private void refresh(LobbyHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        ItemStack border = configured("border", Map.of(), Material.BLACK_STAINED_GLASS_PANE,
                Component.text(" "), List.of());
        for (int slot : GuiConfig.slots(MENU_PATH + ".layout.border",
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 18, 19, 20, 21, 22, 23, 24, 25, 26)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);

        UUID viewer = holder.viewer;
        DailyPlayerSnapshot snapshot = daily.snapshot(viewer);
        inventory.setItem(slot("status", 4), configured("status", Map.of(
                        "queue", snapshot.queueState(), "game", snapshot.selectedGame(),
                        "party_size", snapshot.partySize()), Material.NETHER_STAR,
                Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.lobby-status"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(
                        line(GuiConfig.text("daily.menus.lobby-screen.copy.current-status"), snapshot.queueState(), NamedTextColor.WHITE),
                        line(GuiConfig.text("daily.menus.lobby-screen.copy.current-game"), snapshot.selectedGame(), NamedTextColor.AQUA),
                        line(GuiConfig.text("daily.menus.lobby-screen.copy.number-of-people-traveling-together"), snapshot.partySize() + GuiConfig.text("daily.menus.lobby-screen.copy.player-count-suffix"), NamedTextColor.WHITE),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.select-a-function-below-to-get-started"), NamedTextColor.GRAY)
                )));
        inventory.setItem(slot("statistics", STATS_SLOT), configured("statistics", Map.of(), Material.WRITABLE_BOOK,
                Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.personal-record"), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.check-your-game-sessions-and-detailed-records"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.and-detailed-records-of-each-game"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.click-to-view"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(slot("matchmaking", MATCH_SLOT), configured("matchmaking", Map.of(), Material.COMPASS,
                Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.enter-matching"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.choose-the-game-you-want-to-participate-in"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.check-the-number-of-people-and-opening-progress-in-real-time"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.click-to-enter"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(slot("party", PARTY_SLOT), configured("party", Map.of(), Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.team-function"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.invite-friends-to-join-the-match"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.accept-invitations-or-manage-partys"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.click-to-open"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(slot("spectate", SPECTATE_SLOT), configured("spectate", Map.of(), Material.SPYGLASS,
                Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.spectator-game"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.view-currently-playing-games"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.and-select-live-venues-to-spectate-on"), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.click-to-open"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )));
        inventory.setItem(slot("close", CLOSE_SLOT), configured("close", Map.of(), Material.BARRIER,
                Component.text(GuiConfig.text("daily.menus.lobby-screen.copy.close"), NamedTextColor.RED), List.of()));
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
    }

    private static ItemStack configured(String key, Map<String, ?> placeholders, Material material,
                                        Component name, List<Component> lore) {
        return ConfiguredGui.item(MENU_PATH + ".items." + key, placeholders, material, name, lore, false);
    }

    private static Component line(String label, String value, NamedTextColor color) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, color));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore);
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
