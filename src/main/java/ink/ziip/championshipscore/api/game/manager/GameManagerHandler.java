package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.bingo.BingoTeamArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameManagerHandler extends BaseListener {

    protected GameManagerHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseArea baseArea = plugin.getGameManager().getBaseTeamArea(championshipTeam);
            if (baseArea instanceof BingoTeamArea) {
                plugin.getServer().getScheduler().runTask(plugin, () -> event.getEntity().spigot().respawn());
                return;
            }
            if (baseArea != null) {
                baseArea.handlePlayerDeath(event);
                return;
            }
        } else {
            BaseArea baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null) {
                baseArea.handleSpectatorDeath(event);
                return;
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            player.teleport(CCConfig.LOBBY_LOCATION);
            player.setGameMode(GameMode.ADVENTURE);

        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getGameManager().updatePlayer(player);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseArea baseArea = plugin.getGameManager().getBaseTeamArea(championshipTeam);
            if (baseArea != null) {
                baseArea.handlePlayerJoin(event);
                return;
            }
        } else {
            BaseArea baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null) {
                baseArea.handleSpectatorJoin(event);
                return;
            }
        }

        World world = player.getWorld();
        if (!world.equals(CCConfig.LOBBY_LOCATION)) {
            player.teleport(CCConfig.LOBBY_LOCATION);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getGameManager().updatePlayer(player);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseArea baseArea = plugin.getGameManager().getBaseTeamArea(championshipTeam);
            if (baseArea != null) {
                baseArea.handlePlayerQuit(event);
            }
        } else {
            BaseArea baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null) {
                baseArea.handleSpectatorQuit(event);
                plugin.getGameManager().leaveSpectating(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamGameEnd(TeamGameEndEvent event) {
        plugin.getGameManager().teamGameEndHandler(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSingleTeamGameEnd(SingleGameEndEvent event) {
        plugin.getGameManager().singleTeamGameEndHandler(event);
    }
}
