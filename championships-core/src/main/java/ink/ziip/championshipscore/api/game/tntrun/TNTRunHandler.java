package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
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
import org.bukkit.event.player.*;

@Getter
@Setter
public class TNTRunHandler extends BaseListener {
    private TNTRunTeamArea tntRunTeamArea;

    protected TNTRunHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void handleRoutedPlayerMoveLow(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (tntRunTeamArea.notAreaPlayer(player)) {
            return;
        }
        if (tntRunTeamArea.isIntroductionPhase()) {
            return;
        }

        Location location = player.getLocation();
        GameStageEnum stage = tntRunTeamArea.getGameStageEnum();
        if (tntRunTeamArea.notInArea(location)) {
            if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN) {
                tntRunTeamArea.teleportPlayerToSpawnPoint(player);
                player.setFallDistance(0f);
                return;
            }
            if (stage == GameStageEnum.PROGRESS) {
                if (tntRunTeamArea.isManagedSpectator(player)) {
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
            return;
        }
        // Foot-block probing is intentionally handled by TNTRunTeamArea's tested async polling task;
        // this routed event remains responsible only for authoritative boundary/death handling.
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (tntRunTeamArea.notAreaPlayer(player)) {
                return;
            }

            GameStageEnum stage = tntRunTeamArea.getGameStageEnum();
            if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN) {
                event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (tntRunTeamArea.notAreaPlayer(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (tntRunTeamArea.notAreaPlayer(player)) {
            return;
        }

        event.setCancelled(true);
    }
}
