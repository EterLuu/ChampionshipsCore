package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
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
        if (plugin.getGameManager().isWaitingForNextRound(uuid)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.spigot().respawn();
                plugin.getGameManager().restoreNextRoundHold(player);
            });
            return;
        }
        BaseGameInstance baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
        if (baseArea != null) {
            if (baseArea.isSharedPreGameRecoveryPhase()) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.setDroppedExp(0);
                event.getDrops().clear();
                BaseGameInstance preGameArea = baseArea;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.spigot().respawn();
                    preGameArea.restoreSharedPreGameParticipant(player);
                });
                return;
            }
            baseArea.handlePlayerDeath(event);
            return;
        }
        baseArea = plugin.getGameManager().getPlayerSpectatorStatus(uuid);
        if (baseArea != null) {
            baseArea.handleSpectatorDeath(event);
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            if (!lobbyAvailable()) {
                warnLobbyUnavailable();
                return;
            }
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
        if (plugin.getGameManager().restoreNextRoundHold(player)) {
            return;
        }
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
        BaseGameInstance baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
        if (baseArea != null) {
            if (baseArea.restoreSharedPreGameParticipant(player))
                return;
            baseArea.handlePlayerJoin(event);
            return;
        }
        baseArea = plugin.getGameManager().getPlayerSpectatorStatus(uuid);
        if (baseArea != null) {
            baseArea.handleSpectatorJoin(event);
            return;
        }

        if (championshipTeam == null && plugin.getGameManager().spectateCurrentGame(player)) {
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.teleport(CCConfig.LOBBY_LOCATION);
                player.setGameMode(GameMode.ADVENTURE);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransitionDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getGameManager().isWaitingForNextRound(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        BaseGameInstance baseArea = plugin.getGameManager().getBasePlayerArea(uuid);
        if (baseArea != null)
            baseArea.handlePlayerQuit(event);
        baseArea = plugin.getGameManager().getPlayerSpectatorStatus(uuid);
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
