package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.gui.MenuInventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Bukkit boundary for queue safety, menu selection and session settlement. */
final class DailyListener extends BaseListener {
    private final DailyManager daily;

    DailyListener(ChampionshipsCore plugin, DailyManager daily) {
        super(plugin);
        this.daily = daily;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenu(InventoryClickEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (!DailyManager.isDailyMenuHolder(holder)) {
            if (DailyLobbyItem.is(event.getCurrentItem()) || DailyLobbyItem.is(event.getCursor()))
                event.setCancelled(true);
            return;
        }
        Player player = MenuInventory.clickedPlayer(event);
        if (player == null) return;
        int slot = event.getRawSlot();
        if (holder instanceof DailyBingoVoteController.VoteHolder vote) {
            daily.bingoVote().click(player, slot, vote);
            return;
        }
        if (holder instanceof DailyLobbyMenu.LobbyHolder lobby) {
            daily.lobbyMenu().click(player, slot, lobby);
            return;
        }
        if (holder instanceof DailyGameMenu.MenuHolder match) {
            if (event.isRightClick()
                    && daily.matchMenu().isBingoSlot(match, slot)
                    && daily.reopenBingoVote(player)) {
                return;
            }
            daily.matchMenu().click(player, slot, match);
            return;
        }
        if (holder instanceof DailyStatsMenu.StatsHolder stats) {
            daily.statsMenu().click(player, slot, stats);
            return;
        }
        if (holder instanceof DailyStatsMenu.DetailHolder detail) {
            daily.statsMenu().click(player, slot, detail);
            return;
        }
        if (holder instanceof DailyPartyMenu.PartyHolder party) {
            daily.partyMenu().click(player, slot, party);
            return;
        }
        if (holder instanceof DailyLeaderboardMenu.LeaderboardHolder leaderboard) {
            daily.leaderboardMenu().click(player, slot, leaderboard);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (DailyManager.isDailyMenuHolder(holder)
                || DailyLobbyItem.is(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(DailyLobbyItem::is))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyItemUse(PlayerInteractEvent event) {
        if (!DailyLobbyItem.is(event.getItem())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND
                && (event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK))
            daily.openMenu(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLobbyItemDrop(PlayerDropItemEvent event) {
        if (DailyLobbyItem.is(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLobbyItemSwap(PlayerSwapHandItemsEvent event) {
        if (DailyLobbyItem.is(event.getMainHandItem()) || DailyLobbyItem.is(event.getOffHandItem()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLobbyItemDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(DailyLobbyItem::is);
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            Player player = event.getEntity();
                            if (player.isOnline()) daily.syncLobbyItem(player);
                        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && daily.isQueued(player.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        daily.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        daily.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(SingleGameEndEvent event) {
        daily.finish(event.getGameInstance());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeamEnd(TeamGameEndEvent event) {
        daily.finish(event.getGameInstance());
    }
}
