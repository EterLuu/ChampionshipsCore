package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PlayerListener extends BaseListener {
    protected PlayerListener(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (player.hasPermission("cc.refuge")) {
            event.setFormat(Utils.translateColorCodes(CCConfig.CHAT_REFUGEE));
            event.setMessage(Utils.translateColorCodes("&f" + event.getMessage()));
        } else if (championshipTeam == null) {
            event.setFormat(Utils.translateColorCodes(CCConfig.CHAT_SPECTATOR));
        } else {
            event.setFormat(Utils.translateColorCodes(CCConfig.CHAT_PLAYER.replace("%team%", championshipTeam.getColoredName())));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.getPlayer(player).updatePlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent event) {
        if (Bukkit.getOnlinePlayers().size() >= CCConfig.MAX_PLAYERS) {
            if (CCConfig.WHITELIST.contains(event.getName()))
                return;

            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(event.getUniqueId());
            if (championshipTeam == null) {
                event.setKickMessage(MessageConfig.SERVER_FULL);
                event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_FULL);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.getPlayer(player).updatePlayer();
        playerManager.updatePlayer(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player assailant && event.getEntity() instanceof Player player) {
            ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
            if (assailantChampionshipTeam != null) {
                if (assailantChampionshipTeam.equals(plugin.getTeamManager().getTeamByPlayer(player))) {
                    event.setCancelled(true);
                }
            }
        }

        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Arrow) {
                Projectile projectile = (Projectile) event.getDamager();
                ProjectileSource projectileSource = projectile.getShooter();
                if (!(projectileSource instanceof Player assailant))
                    return;

                ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
                if (assailantChampionshipTeam != null) {
                    if (assailantChampionshipTeam.equals(plugin.getTeamManager().getTeamByPlayer(player))) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHealedBySplashPotion(EntityRegainHealthEvent event) {
        // TODO
    }
}
