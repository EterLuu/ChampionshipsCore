package ink.ziip.championshipscore.api.game.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

@Setter
public class SnowballShowdownHandler extends BaseListener {
    private SnowballShowdownTeamArea snowballShowdownTeamArea;

    protected SnowballShowdownHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (snowballShowdownTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (snowballShowdownTeamArea.notInArea(location)) {
            return;
        }

        if (snowballShowdownTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            ItemStack itemStack = event.getItem();
            if (itemStack != null) {
                if (event.getItem().getType() == Material.SNOWBALL) {
                    if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShootSnowBall(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball) {
            if (snowball.getShooter() instanceof Player player) {
                if (snowballShowdownTeamArea.notAreaPlayer(player)) {
                    return;
                }

                Location location = player.getLocation();
                if (snowballShowdownTeamArea.notInArea(location)) {
                    return;
                }

                snowball.setGravity(false);
                snowball.setVelocity(snowball.getVelocity().multiply(2));
                snowball.setTicksLived(200);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Snowball) {
                Projectile projectile = (Projectile) event.getDamager();
                ProjectileSource projectileSource = projectile.getShooter();
                if (!(projectileSource instanceof Player assailant))
                    return;

                ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
                if (assailantChampionshipTeam != null) {
                    if (assailantChampionshipTeam.equals(plugin.getTeamManager().getTeamByPlayer(player))) {
                        event.setCancelled(true);
                        return;
                    }
                    if (!snowballShowdownTeamArea.canBeDamaged(player)) {
                        event.setCancelled(true);
                        return;
                    }

                    snowballShowdownTeamArea.addShoot(assailant, player);
                }
                event.setDamage(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (snowballShowdownTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (snowballShowdownTeamArea.notInArea(location)) {
            if (snowballShowdownTeamArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                snowballShowdownTeamArea.teleportPlayerToSpawnLocation(player);
            }
            if (snowballShowdownTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (location.getY() < -64) {
                        player.teleport(snowballShowdownTeamArea.getGameConfig().getSpectatorSpawnPoint());
                    }
                    return;
                }
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (championshipTeam != null) {
                    snowballShowdownTeamArea.sendMessageToAllGamePlayers(MessageConfig.SNOWBALL_PLAYER_DEATH_BY_VOID.replace("%player%", championshipTeam.getColoredColor() + player.getName()));
                }
                snowballShowdownTeamArea.respawnPlayer(player);
            }
            return;
        }

        if (snowballShowdownTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (snowballShowdownTeamArea.getTimer() >= snowballShowdownTeamArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItems(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (snowballShowdownTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (snowballShowdownTeamArea.notInArea(location)) {
            return;
        }

        if (snowballShowdownTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (snowballShowdownTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (snowballShowdownTeamArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (snowballShowdownTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (snowballShowdownTeamArea.notInArea(location)) {
            return;
        }

        if (snowballShowdownTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (snowballShowdownTeamArea.getTimer() >= snowballShowdownTeamArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (snowballShowdownTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (snowballShowdownTeamArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }
}
