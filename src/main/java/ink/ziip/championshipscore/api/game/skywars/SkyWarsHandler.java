package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

@Getter
@Setter
public class SkyWarsHandler extends BaseListener {
    private SkyWarsTeamArea skyWarsArea;

    protected SkyWarsHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
            }

            if (event.getDamager() instanceof Creeper creeper) {
                Player spawner = Bukkit.getPlayer(creeper.getName());
                if (spawner == null)
                    return;
                if (skyWarsArea.notAreaPlayer(spawner))
                    return;
                if (player.getHealth() <= event.getDamage() && !skyWarsArea.getDeathPlayer().contains(player.getUniqueId())) {
                    skyWarsArea.addDeathPlayer(player);
                    String message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_CREEPER;
                    ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                    ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(spawner);
                    if (playerTeam == null || assailantTeam == null)
                        return;
                    message = message
                            .replace("%player%", playerTeam.getColoredColor() + player.getName())
                            .replace("%killer%", assailantTeam.getColoredColor() + spawner.getName());
                    skyWarsArea.sendMessageToAllGamePlayers(message);

                    if (!playerTeam.equals(assailantTeam)) {
                        skyWarsArea.addPlayerPoints(spawner.getUniqueId(), 40);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedDamageByBlock(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedDamageByBlock(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItems(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (event.getCaught() instanceof Player caught) {
            caught.damage(0.00001, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return;
            }

            if (event.getDamager() instanceof Snowball projectile) {
                ProjectileSource projectileSource = projectile.getShooter();
                if (!(projectileSource instanceof Player))
                    return;

                event.setDamage(0.0001);
            }

            if (event.getDamager() instanceof Player damager) {
                Material material = damager.getInventory().getItemInMainHand().getType();
                String name = material.toString();
                if (name.contains("AXE")) {
                    if (event.getDamage() > 7) {
                        event.setDamage(7);
                    }
                }
                if (event.getDamageSource().getDamageType() == DamageType.TRIDENT) {
                    event.setDamage(7);
                }
                if (event.getDamageSource().getDamageType() == DamageType.ARROW) {
                    if (event.getDamage() > 7) {
                        event.setDamage(7);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                player.teleport(skyWarsArea.getSpectatorSpawnLocation());
            }
            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (location.getY() < -64) {
                        player.teleport(skyWarsArea.getSpectatorSpawnLocation());
                    }
                } else {
                    UUID uuid = player.getUniqueId();
                    if (!skyWarsArea.getDeathPlayer().contains(uuid)) {
                        Player assailant = player.getKiller();

                        if (assailant != null) {
                            ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                            ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

                            if (playerTeam == null || assailantTeam == null)
                                return;

                            if (playerTeam.equals(assailantTeam)) {
                                return;
                            }

                            String message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_VOID;

                            message = message
                                    .replace("%player%", playerTeam.getColoredColor() + player.getName())
                                    .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());

                            skyWarsArea.sendMessageToAllGamePlayers(message);
                            skyWarsArea.addPlayerPoints(assailant.getUniqueId(), 40);

                            skyWarsArea.addDeathPlayer(player);
                        } else {

                            String message = MessageConfig.SKY_WARS_PLAYER_DEATH_BY_VOID;

                            message = message.replace("%player%", player.getName());
                            skyWarsArea.sendMessageToAllGamePlayers(message);
                            skyWarsArea.addDeathPlayer(player);
                        }
                    }
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                        player.setGameMode(GameMode.SPECTATOR);
                    });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }
}
