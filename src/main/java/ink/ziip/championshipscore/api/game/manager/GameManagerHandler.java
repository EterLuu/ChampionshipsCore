package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;
import java.util.logging.Level;

public class GameManagerHandler extends BaseListener {

    protected GameManagerHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    // Avoids spamming the log when the lobby location is misconfigured (e.g. a stale world_key).
    private boolean lobbyBrokenWarned = false;

    private boolean lobbyAvailable() {
        return CCConfig.LOBBY_LOCATION != null && CCConfig.LOBBY_LOCATION.getWorld() != null;
    }

    private void warnLobbyUnavailable() {
        if (lobbyBrokenWarned) return;
        lobbyBrokenWarned = true;
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameManager", "大厅",
                "大厅世界不可用，请检查 config.yml 的 lobby.location.world_key/world；修复并重载前将跳过大厅传送"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (championshipTeam != null) {
            BaseGameInstance baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
            if (baseArea != null) {
                baseArea.handlePlayerDeath(event);
                return;
            }
        } else {
            BaseGameInstance baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            if (baseArea != null) {
                baseArea.handleSpectatorDeath(event);
                return;
            }
        }

        FoliaScheduler.global(plugin).runEntity(player, () -> {
            event.getEntity().spigot().respawn();
            if (!lobbyAvailable()) {
                warnLobbyUnavailable();
                return;
            }
            player.teleportAsync(CCConfig.LOBBY_LOCATION).thenRun(() ->
                    FoliaScheduler.global(plugin).runEntity(player,
                            () -> player.setGameMode(GameMode.ADVENTURE)));

        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        BaseGameInstance baseArea = null;
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

        // Fallback lobby clear: this player is neither a participant nor a spectator in any game, so no
        // area is managing their inventory or effects. Strip both once on join so stale items/effects
        // carried over from a previous/crashed game don't follow them into the lobby. Participants and
        // spectators are dispatched above and never reach here, so active game state is never touched.
        player.getInventory().clear();
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(potionEffect.getType());
        }

        World world = player.getWorld();
        if (!lobbyAvailable()) {
            warnLobbyUnavailable();
        } else if (!world.equals(CCConfig.LOBBY_LOCATION.getWorld())) {
            player.teleportAsync(CCConfig.LOBBY_LOCATION).thenRun(() ->
                    FoliaScheduler.global(plugin).runEntity(player,
                            () -> player.setGameMode(GameMode.ADVENTURE)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        BaseGameInstance baseArea = null;
        if (championshipTeam != null) {
            baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
            if (baseArea != null) {
                baseArea.handlePlayerQuit(event);
            }
        }
        baseArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
        if (baseArea != null) {
            if (!baseArea.keepSpectatorAcrossReconnect()) {
                plugin.getGameManager().leaveSpectating(player);
            }
            // else: keep tracking so handleSpectatorJoin restores the spectator on reconnect; the area
            // releases them itself when its game ends (releaseAllSpectators).
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
