package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
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
import org.bukkit.scheduler.BukkitRunnable;

public class GameManagerHandler extends BaseListener {

    protected GameManagerHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseTeamArea baseTeamArea = plugin.getGameManager().getBaseTeamArea(championshipTeam);
            if (baseTeamArea != null) {
                baseTeamArea.handlePlayerDeath(event);
                return;
            }
        } else {
            BaseArea baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null)
                baseArea.handleSpectatorDeath(event);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                event.getEntity().spigot().respawn();
                player.teleport(CCConfig.LOBBY_LOCATION);
                player.setGameMode(GameMode.ADVENTURE);
            }
        }.runTask(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getGameManager().updatePlayer(player);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseTeamArea baseTeamArea = plugin.getGameManager().getBaseTeamArea(championshipTeam);
            if (baseTeamArea != null) {
                baseTeamArea.handlePlayerJoin(event);
                return;
            }
        } else {
            BaseArea baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null) {
                // TODO spectator join
                return;
            } else {
                if (player.isOp())
                    return;
                World world = player.getWorld();
                if (!world.equals(CCConfig.LOBBY_LOCATION)) {
                    player.teleport(CCConfig.LOBBY_LOCATION);
                    player.setGameMode(GameMode.ADVENTURE);
                }
                return;
            }
        }

        player.teleport(CCConfig.LOBBY_LOCATION);
        player.setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getGameManager().updatePlayer(player);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseTeamArea baseTeamArea = plugin.getGameManager().getBaseTeamArea(championshipTeam);
            if (baseTeamArea != null) {
                baseTeamArea.handlePlayerQuit(event);
                return;
            }
        } else {
            BaseArea baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null)
                // TODO spectator quit
                return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamGameEnd(TeamGameEndEvent event) {
        plugin.getGameManager().teamGameEndHandler(event);
    }
}