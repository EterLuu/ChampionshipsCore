package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PlayerListener extends BaseListener {
    protected PlayerListener(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.getPlayer(player).updatePlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.getPlayer(player).updatePlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShootSnowBall(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball) {
           snowball.setGravity(false);
           snowball.setVelocity(snowball.getVelocity().multiply(2));
           snowball.setTicksLived(200);
        }
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

        if (event.getDamager() instanceof Arrow || event.getDamager() instanceof ThrownPotion) {
            Projectile projectile = (Projectile) event.getDamager();
            ProjectileSource projectileSource = projectile.getShooter();
            if (!(projectileSource instanceof Player assailant))
                return;

            ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
            if (assailantChampionshipTeam != null) {
                if (assailantChampionshipTeam.equals(plugin.getTeamManager().getTeamByPlayer((Player) event.getEntity()))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHealedBySplashPotion(EntityRegainHealthEvent event) {
        // TODO
    }
}
