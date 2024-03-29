package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

@Setter
public class DragonEggCarnivalHandler extends BaseListener {
    private DragonEggCarnivalArea dragonEggCarnivalArea;

    protected DragonEggCarnivalHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (dragonEggCarnivalArea.notInArea(location)) {
            return;
        }

        if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (dragonEggCarnivalArea.getTimer() <= 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) {
            player.removeScoreboardTag("final");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (dragonEggCarnivalArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (dragonEggCarnivalArea.notInArea(location)) {
                return;
            }

            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
                return;
            }

            if (dragonEggCarnivalArea.getTimer() <= 0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (dragonEggCarnivalArea.notInArea(location)) {
            if (dragonEggCarnivalArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                player.teleport(dragonEggCarnivalArea.getSpectatorSpawnLocation());
            }
            if (dragonEggCarnivalArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (location.getY() <= -64) {
                        player.teleport(dragonEggCarnivalArea.getSpectatorSpawnLocation());
                    }
                    return;
                } else {
                    dragonEggCarnivalArea.teleportPlayerToSpawnLocation(player);
                    ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                    if (championshipTeam != null)
                        dragonEggCarnivalArea.sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_OUT_OF_BORDER.replace("%player%", championshipTeam.getColoredColor() + player.getName()));
                }
            }
            return;
        }

        if (dragonEggCarnivalArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (dragonEggCarnivalArea.getTimer() <= 0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickupDragonEgg(EntityPickupItemEvent event) {
        if (event.getItem().getItemStack().getType() != Material.DRAGON_EGG) {
            return;
        }

        LivingEntity livingEntity = event.getEntity();
        if (livingEntity instanceof Player player) {
            if (dragonEggCarnivalArea.notAreaPlayer(player)) {
                return;
            }
            if (dragonEggCarnivalArea.getTimer() >= 100)
                return;
            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS)
                return;

            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam != null) {
                player.getInventory().clear();
                dragonEggCarnivalArea.sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_PICK_UP_EGG.replace("%player%", championshipTeam.getColoredColor() + player.getName()));
                dragonEggCarnivalArea.endGameInForm(championshipTeam);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerKilledDragonEgg(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            Player player = dragon.getKiller();
            if (player == null)
                return;

            if (dragonEggCarnivalArea.notAreaPlayer(player))
                return;

            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS)
                return;

            if (dragonEggCarnivalArea.getTimer() < 100)
                return;

            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam != null) {
                dragonEggCarnivalArea.sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_KILLED_DRAGON.replace("%player%", championshipTeam.getColoredColor() + player.getName()));
                dragonEggCarnivalArea.endGameInForm(championshipTeam);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDragonEggFallIntoVoid(EntitySpawnEvent event) {
        Location location = event.getLocation();

        if (dragonEggCarnivalArea.notInArea(location)) {
            return;
        }

        if (event.getEntity() instanceof FallingBlock fallingBlock) {
            Location blockLocation = fallingBlock.getLocation();
            for (int i = 1; i < 60; i++) {
                Block thisBlock = blockLocation.add(0, -i, 0).getBlock();
                if (!thisBlock.isEmpty()) {
                    return;
                }
            }
            Block dragonEgg = dragonEggCarnivalArea.getGameConfig().getDragonEggSpawnPoint().getBlock();
            dragonEgg.setType(Material.DRAGON_EGG, true);
            dragonEggCarnivalArea.sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_RE_SPAWN_DRAGON_EGG);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEnterPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortalCreate(PortalCreateEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(dragonEggCarnivalArea.getWorldName())) {
            return;
        }

        event.setCancelled(true);
    }
}
