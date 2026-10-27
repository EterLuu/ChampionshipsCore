package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

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
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, SIZE, "", List.of());
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof LobbyHolder holder) refresh(holder);
        }
    }

    void click(Player player, int slot, LobbyHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("close")) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("statistics")) {
            daily.openStatsMenu(player);
            clickSound(player, 1.15F);
        } else if (slot == holder.layout.control("matchmaking")) {
            daily.openMatchMenu(player);
            clickSound(player, 1.2F);
        } else if (slot == holder.layout.control("party")) {
            daily.openPartyMenu(player);
            clickSound(player, 1.1F);
        } else if (slot == holder.layout.control("spectate")) {
            daily.openSpectateMenu(player);
            clickSound(player, 1.3F);
        }
    }

    private void refresh(LobbyHolder holder) {
        Inventory inventory = holder.inventory;
        holder.clear();

        DailyPlayerSnapshot snapshot = daily.snapshot(holder.viewer);
        inventory.setItem(
                holder.layout.control("status"),
                holder.item(
                        "status",
                        null,
                        Map.of(
                                "queue",
                                snapshot.queueState(),
                                "game",
                                snapshot.selectedGame(),
                                "party_size",
                                snapshot.partySize())));
        inventory.setItem(
                holder.layout.control("statistics"), holder.item("statistics", null, Map.of()));
        inventory.setItem(
                holder.layout.control("matchmaking"), holder.item("matchmaking", null, Map.of()));
        inventory.setItem(holder.layout.control("party"), holder.item("party", null, Map.of()));
        inventory.setItem(
                holder.layout.control("spectate"), holder.item("spectate", null, Map.of()));
        inventory.setItem(holder.layout.control("close"), holder.item("close", null, Map.of()));
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class LobbyHolder extends DailyMenuPage {
        private LobbyHolder(UUID viewer) {
            super(
                    viewer,
                    MenuId.DAILY_LOBBY,
                    SIZE,
                    List.of(),
                    List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 18, 19, 20, 21, 22, 23, 24, 25, 26),
                    Map.of(
                            "status",
                            4,
                            "statistics",
                            STATS_SLOT,
                            "matchmaking",
                            MATCH_SLOT,
                            "party",
                            PARTY_SLOT,
                            "spectate",
                            SPECTATE_SLOT,
                            "close",
                            CLOSE_SLOT));
        }
    }
}
