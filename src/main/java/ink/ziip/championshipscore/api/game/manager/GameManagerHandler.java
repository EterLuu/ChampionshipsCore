package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
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

import java.util.UUID;

public class GameManagerHandler extends BaseListener {

    protected GameManagerHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseArea baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.ADVENTURE);
            });

        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        BaseArea baseArea = null;
        if (championshipTeam != null) {
            baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
            if (baseArea != null) {
                baseArea.handlePlayerJoin(event);
                return;
            }
        }
        baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
        if (baseArea != null) {
            baseArea.handleSpectatorJoin(event);
            return;
        }

        World world = player.getWorld();
        if (!world.equals(CCConfig.LOBBY_LOCATION.getWorld())) {
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.teleport(CCConfig.LOBBY_LOCATION);
                player.setGameMode(GameMode.ADVENTURE);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        BaseArea baseArea = null;
        if (championshipTeam != null) {
            baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
            if (baseArea != null) {
                baseArea.handlePlayerQuit(event);
            }
        }
        baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
        if (baseArea != null) {

            plugin.getGameManager().leaveSpectating(player);
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
