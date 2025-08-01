package ink.ziip.championshipscore.api.game.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.UUID;

@Getter
@Setter
public class HotyCodyDuskyHandler extends BaseListener {
    private HotyCodyDuskyTeamArea hotyCodyDuskyArea;

    protected HotyCodyDuskyHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (hotyCodyDuskyArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (hotyCodyDuskyArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryInteract(InventoryInteractEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (hotyCodyDuskyArea.notAreaPlayer(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public synchronized void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (hotyCodyDuskyArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (hotyCodyDuskyArea.notInArea(location)) {
                return;
            }

            if (hotyCodyDuskyArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
                return;
            }

            if (event.getDamager() instanceof Player damager) {
                UUID damagerUUID = damager.getUniqueId();
                UUID playerUUID = player.getUniqueId();
                if (hotyCodyDuskyArea.getCodyHolder() != damagerUUID) {
                    event.setDamage(0);
                } else {
                    if (!hotyCodyDuskyArea.changeCodyHolder(playerUUID)) {
                        event.setDamage(0);
                    }
                }
            }
        }
    }
}
