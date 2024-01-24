package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

@Getter
@Setter
public class TGTTOSHandler extends BaseListener {
    private TGTTOSTeamArea tgttosTeamArea;

    protected TGTTOSHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (tgttosTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tgttosTeamArea.notInArea(location)) {
            return;
        }

        if (!tgttosTeamArea.getGameConfig().getAreaType().equals("ROAD") || tgttosTeamArea.getTimer() >= tgttosTeamArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        tgttosTeamArea.getBlockStates().add(event.getBlockPlaced().getState());
        event.getItemInHand().setAmount(64);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (tgttosTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tgttosTeamArea.notInArea(location)) {
            return;
        }

        if (!tgttosTeamArea.getBlockStates().contains(event.getBlock().getState()) || tgttosTeamArea.getTimer() >= tgttosTeamArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        event.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (tgttosTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (tgttosTeamArea.notInArea(location)) {
                return;
            }

            if (tgttosTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS || tgttosTeamArea.getTimer() >= tgttosTeamArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
                return;
            }

            event.setDamage(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (tgttosTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (tgttosTeamArea.notInArea(location)) {
                return;
            }

            if (tgttosTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
                return;
            }

            event.setDamage(0);

            if (event.getDamager() instanceof Player && event.getEntity() instanceof Chicken) {
                if (!tgttosTeamArea.getArrivedPlayers().contains(player.getUniqueId())) {
                    event.getEntity().remove();
                    tgttosTeamArea.playerArrivedAtEndPoint(player);
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (tgttosTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tgttosTeamArea.notInArea(location)) {
            tgttosTeamArea.teleportPlayerToSpawnPoint(player);
            if (tgttosTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (tgttosTeamArea.getGameConfig().getAreaType().equals("BOAT")) {
                    tgttosTeamArea.giveBoatToPlayer(player);
                }

                tgttosTeamArea.sendMessageToAllGamePlayers(MessageConfig.TGTTOS_FALL_INTO_VOID.replace("%player%", player.getName()));
            }
            return;
        }

        if (tgttosTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (tgttosTeamArea.getTimer() >= tgttosTeamArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (tgttosTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tgttosTeamArea.notInArea(location)) {
            return;
        }

        if (tgttosTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (tgttosTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (tgttosTeamArea.getTimer() >= tgttosTeamArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (tgttosTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tgttosTeamArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (tgttosTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (tgttosTeamArea.notInArea(location)) {
                return;
            }

            if (event.getCause() == EntityDamageEvent.DamageCause.FALL)
                event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (tgttosTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (tgttosTeamArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }
}
