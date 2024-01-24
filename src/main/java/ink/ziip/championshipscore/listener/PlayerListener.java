package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.player.CCPlayerManager;
import ink.ziip.championshipscore.api.team.Team;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
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
        CCPlayerManager ccPlayerManager = ChampionshipsCore.getInstance().getCcPlayerManager();
        ccPlayerManager.getPlayer(player).updatePlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CCPlayerManager ccPlayerManager = ChampionshipsCore.getInstance().getCcPlayerManager();
        ccPlayerManager.getPlayer(player).updatePlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player assailant && event.getEntity() instanceof Player player) {
            Team assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
            if (assailantTeam != null) {
                if (assailantTeam.equals(plugin.getTeamManager().getTeamByPlayer(player))) {
                    event.setCancelled(true);
                }
            }
        }

        if (event.getDamager() instanceof Arrow || event.getDamager() instanceof ThrownPotion) {
            Projectile projectile = (Projectile) event.getDamager();
            ProjectileSource projectileSource = projectile.getShooter();
            if (!(projectileSource instanceof Player assailant))
                return;

            Team assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
            if (assailantTeam != null) {
                if (assailantTeam.equals(plugin.getTeamManager().getTeamByPlayer((Player) event.getEntity()))) {
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
