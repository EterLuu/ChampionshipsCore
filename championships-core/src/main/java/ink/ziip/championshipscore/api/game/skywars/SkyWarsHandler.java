package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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
import org.bukkit.event.world.PortalCreateEvent;
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
        if (skyWarsArea.isIntroductionPhase()) {
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
                            .replace("%player%", Utils.formatPlayerName(player))
                            .replace("%killer%", Utils.formatPlayerName(spawner));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMountHappyGhast(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getMount() instanceof HappyGhast happyGhast)
                || !skyWarsArea.isTeamHappyGhast(happyGhast)
                || skyWarsArea.canRideTeamHappyGhast(player, happyGhast)) {
            return;
        }

        event.setCancelled(true);
        Utils.sendActionBar(player, "&#fff566空岛乱斗 &#bababa• &#ff6b26这不是你们队的乐魂");
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeamHappyGhastKilled(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof HappyGhast happyGhast)
                || skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS
                || !skyWarsArea.isTeamHappyGhast(happyGhast)
                || happyGhast.getHealth() > event.getFinalDamage()) {
            return;
        }

        UUID killerUuid = resolvePlayerDamager(event.getDamager());
        if (killerUuid != null && skyWarsArea.getParticipantUniqueIds().contains(killerUuid)) {
            skyWarsArea.recordHappyGhastKill(happyGhast, killerUuid);
        }
    }

    private UUID resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
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

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }
        if (skyWarsArea.isIntroductionPhase()) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                if (!skyWarsArea.isIntroductionPhase())
                    skyWarsArea.teleportPlayerToAssignedTeamSpawn(player);
                return;
            }
            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (skyWarsArea.isManagedSpectator(player)) {
                    if (location.getY() < -64) {
                        player.teleport(skyWarsArea.getSpectatorSpawnLocation());
                    }
                } else {
                    UUID uuid = player.getUniqueId();
                    if (!skyWarsArea.getDeathPlayer().contains(uuid)) {
                        Player assailant = player.getKiller();
                        UUID assailantUuid = assailant == null ? null : assailant.getUniqueId();
                        UUID happyGhastKiller = skyWarsArea.consumeHappyGhastKiller(player);
                        if (happyGhastKiller != null) {
                            assailantUuid = happyGhastKiller;
                        }

                        if (assailantUuid != null) {
                            ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                            ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailantUuid);

                            if (playerTeam == null || assailantTeam == null)
                                return;

                            if (playerTeam.equals(assailantTeam)) {
                                skyWarsArea.addDeathPlayer(player);
                                return;
                            }

                            String message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_VOID;

                            message = message
                                    .replace("%player%", Utils.formatPlayerName(player))
                                    .replace("%killer%", Utils.formatPlayerName(assailantUuid));

                            skyWarsArea.sendMessageToAllGamePlayers(message);
                            skyWarsArea.addPlayerPoints(assailantUuid, skyWarsArea.getKillPoints());

                            skyWarsArea.addDeathPlayer(player);
                        } else {

                            String message = MessageConfig.SKY_WARS_PLAYER_DEATH_BY_VOID;

                            message = message.replace("%player%", Utils.formatPlayerName(player));
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) {
            return;
        }
        if (skyWarsArea.notInArea(event.getBlocks().getFirst().getLocation())) {
            return;
        }
        if (event.getEntity() != null && event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                skyWarsArea.sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_PLAYER_CREATE_PORTAL
                        .replace("%player%", Utils.formatPlayerName(player)));
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityEnterNetherPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notInArea(player.getLocation())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEnterNetherPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notInArea(player.getLocation())) {
            return;
        }
        event.setCancelled(true);
    }
}
