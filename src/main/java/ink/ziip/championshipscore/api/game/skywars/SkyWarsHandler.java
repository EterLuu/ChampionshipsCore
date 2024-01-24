package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkyWarsHandler extends BaseListener {
    private final SkyWarsTeamArea skyWarsArea;

    protected SkyWarsHandler(ChampionshipsCore plugin, SkyWarsTeamArea skyWarsArea) {
        super(plugin);
        this.skyWarsArea = skyWarsArea;
    }


    @EventHandler(priority = EventPriority.LOWEST)
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

        if (skyWarsArea.getTimer() >= skyWarsArea.getSkyWarsConfig().getTimer()) {
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

        if (skyWarsArea.getTimer() >= skyWarsArea.getSkyWarsConfig().getTimer()) {
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

        if (skyWarsArea.getTimer() >= skyWarsArea.getSkyWarsConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlockPlaced();
        if (block.getType().toString().endsWith("_CONCRETE")) {
            event.getItemInHand().setAmount(64);
        }

        skyWarsArea.getBlockStates().add(event.getBlockPlaced().getState());
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

        if (skyWarsArea.getTimer() >= skyWarsArea.getSkyWarsConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        BlockState blockState = event.getBlock().getState();
        if (!skyWarsArea.getBlockStates().contains(blockState)) {
            event.setCancelled(true);
            return;
        }

        skyWarsArea.getBlockStates().remove(blockState);
        event.getBlock().getDrops().clear();
    }

//    @EventHandler(priority = EventPriority.LOWEST)
//    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
//        if (event.getEntity() instanceof Player player) {
//            if (skyWarsArea.notAreaPlayer(player)) {
//                return;
//            }
//
//            Location location = player.getLocation();
//            if (skyWarsArea.notInArea(location)) {
//                return;
//            }
//
//            if (skyWarsArea.getTimer() >= skyWarsArea.getSkyWarsConfig().getTimer()) {
//                return;
//            }
//
//            if (skyWarsArea.getTimer() <= skyWarsArea.getSkyWarsConfig().getTimeDisableHealthRegain())
//                event.setCancelled(true);
//        }
//    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            // TODO Player leave area
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getSkyWarsConfig().getTimer()) {
            event.setCancelled(true);
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
