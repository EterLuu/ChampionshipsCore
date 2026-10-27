package ink.ziip.championshipscore.api.game.bingo.mechanics;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.runtime.BingoArea;
import ink.ziip.championshipscore.api.gui.PlayerListMenuConfig;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.gui.PlayerListMenu;
import ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Compass controls use the original Bingo round, team and current gameplay admission. */
public final class BingoCompassListener extends BaseListener {
    public BingoCompassListener(ChampionshipsCore plugin) {
        super(plugin);
    }

    private BingoArea bingoAreaOf(Player player) {
        var area = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        return area instanceof BingoArea bingo ? bingo : null;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        BingoArea area = bingoAreaOf(player);
        if (area == null
                || event.getItem() == null
                || event.getItem().getType() != Material.COMPASS) return;
        var team = plugin.getTeamManager().getTeamByPlayer(player);
        BingoRound round = area.getRound();
        long generation = area.captureGameGeneration();
        if (!area.canUseTeammateCompass(player, team, round, generation)) return;
        event.setCancelled(true);
        openTeleportMenu(player, area, team, round, generation);
    }

    private List<Player> teammates(
            Player viewer,
            BingoArea area,
            ChampionshipTeam team,
            BingoRound round,
            long generation) {
        return team.getMembers().stream()
                .filter(id -> !id.equals(viewer.getUniqueId()))
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .filter(target -> area.canUseTeammateCompass(target, team, round, generation))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void openTeleportMenu(
            Player player,
            BingoArea area,
            ChampionshipTeam team,
            BingoRound round,
            long generation) {
        if (teammates(player, area, team, round, generation).isEmpty()) {
            player.sendMessage(noTeammatesMessage());
            return;
        }
        PlayerListMenu.open(
                player,
                area,
                PlayerListMenuConfig.teammates(),
                () -> area.canUseTeammateCompass(player, team, round, generation),
                () ->
                        teammates(player, area, team, round, generation).stream()
                                .map(
                                        target ->
                                                new PlayerListMenu.Target(
                                                        target.getUniqueId(),
                                                        Map.of("%player%", target.getName())))
                                .toList(),
                target -> area.teleportToTeammate(player, target, team, round, generation));
    }

    static Component noTeammatesMessage() {
        return ComponentTemplates.render(
                MessageConfig.codeText("bingo-compass-listener.no-teammates"),
                Map.of(
                        "%message%",
                        LegacyText.component(MessageConfig.MAP_EDITOR_BINGO_NO_TEAMMATES)));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        var inventory = event.getView().getTopInventory();
        if (!PlayerListMenu.owns(inventory)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != inventory) return;
        BingoArea area = bingoAreaOf(player);
        if (area != null && PlayerListMenu.owns(inventory, area))
            PlayerListMenu.click(player, inventory, event.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (PlayerListMenu.owns(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        BingoArea area = bingoAreaOf(event.getPlayer());
        if (area != null) area.cancelTeammateTeleports(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        BingoArea area = bingoAreaOf(event.getPlayer());
        if (area != null
                && area.canUseTeammateCompass(
                        event.getPlayer(),
                        plugin.getTeamManager().getTeamByPlayer(event.getPlayer()),
                        area.getRound(),
                        area.captureGameGeneration())
                && event.getItemDrop().getItemStack().getType() == Material.COMPASS)
            event.setCancelled(true);
    }
}
