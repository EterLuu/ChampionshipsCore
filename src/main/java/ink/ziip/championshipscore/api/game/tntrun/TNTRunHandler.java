package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

@Getter
@Setter
public class TNTRunHandler extends BaseListener {
    private TNTRunTeamArea tntRunTeamArea;

    protected TNTRunHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (tntRunTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tntRunTeamArea.notInArea(location)) {
            if (tntRunTeamArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                tntRunTeamArea.teleportPlayerToSpawnPoint(player);
            }
            if (tntRunTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (location.getY() < -64) {
                        player.teleport(getTntRunTeamArea().getSpectatorSpawnLocation());
                    }
                    return;
                }

                tntRunTeamArea.addDeathPlayer(player);
                ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (tntRunTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (tntRunTeamArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (tntRunTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (tntRunTeamArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null)
            return;

        if (!world.getName().equals(tntRunTeamArea.getWorldName()))
            return;

        for (Block block : event.blockList()) {
            if (block.getType() != Material.AIR && tntRunTeamArea.getBlockUnderLocation(block.getLocation().add(0, -1, 0), 0.3) != null) {
                event.setCancelled(true);
            }
        }
        event.setYield(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        World world = event.getBlock().getWorld();

        if (!world.getName().equals(tntRunTeamArea.getWorldName()))
            return;

        for (Block block : event.blockList()) {
            if (block.getType() != Material.AIR && tntRunTeamArea.getBlockUnderLocation(block.getLocation().add(0, -1, 0), 0.3) != null) {
                event.setCancelled(true);
            }
        }
        event.setYield(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (tntRunTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (tntRunTeamArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

//    @EventHandler(priority = EventPriority.LOWEST)
//    public void onPlayerInventoryInteract(InventoryInteractEvent event) {
//        if (event.getWhoClicked() instanceof Player player) {
//            if (tntRunTeamArea.notAreaPlayer(player)) {
//                return;
//            }
//
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler(priority = EventPriority.LOWEST)
//    public void onPlayerInventoryClick(InventoryClickEvent event) {
//        if (event.getWhoClicked() instanceof Player player) {
//            if (tntRunTeamArea.notAreaPlayer(player)) {
//                return;
//            }
//
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler(priority = EventPriority.LOWEST)
//    public void onPlayerInventoryDrag(InventoryDragEvent event) {
//        if (event.getWhoClicked() instanceof Player player) {
//            if (tntRunTeamArea.notAreaPlayer(player)) {
//                return;
//            }
//
//            event.setCancelled(true);
//        }
//    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (tntRunTeamArea.notAreaPlayer(player)) {
            return;
        }

        event.setCancelled(true);
    }
}
