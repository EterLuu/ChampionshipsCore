package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

@Getter
@Setter
public class ParkourWarriorHandler extends BaseListener {
    private ParkourWarriorTeamArea parkourWarriorTeamArea;

    protected ParkourWarriorHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (parkourWarriorTeamArea.notAreaPlayer(player)) {
            return;
        }

        if (parkourWarriorTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (parkourWarriorTeamArea.getTimer() >= parkourWarriorTeamArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
            }
        }

        Location location = player.getLocation();
        if (parkourWarriorTeamArea.notInArea(location)) {
            parkourWarriorTeamArea.teleportPlayerToSpawnPoint(player, true);
            return;
        }

        if (parkourWarriorTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (parkourWarriorTeamArea.getTimer() <= parkourWarriorTeamArea.getGameConfig().getTimer()) {
                parkourWarriorTeamArea.handlePlayerMove(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (parkourWarriorTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (parkourWarriorTeamArea.notInArea(location)) {
                return;
            }

            event.setDamage(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (parkourWarriorTeamArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourWarriorTeamArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (parkourWarriorTeamArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (parkourWarriorTeamArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryMove(InventoryInteractEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (parkourWarriorTeamArea.notAreaPlayer(player)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!parkourWarriorTeamArea.notAreaPlayer(player)) {
            parkourWarriorTeamArea.hideAndShowPlayer(player);
        }
        if (parkourWarriorTeamArea.isSpectator(player)) {
            parkourWarriorTeamArea.hideAndShowPlayer(player);
        }
    }
}
