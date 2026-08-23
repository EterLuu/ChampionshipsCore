package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;

@Getter
@Setter
public class ParkourWarriorHandler extends BaseListener {
    private ParkourWarriorTeamArea parkourWarriorTeamArea;

    protected ParkourWarriorHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void handleRoutedPlayerMoveLow(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (parkourWarriorTeamArea.notAreaPlayer(player)) {
            return;
        }
        // A completed runner remains in the participant roster until settlement, but is no
        // longer a runner. In particular, do not let the out-of-bounds recovery below turn
        // a spectator back into adventure mode.
        if (parkourWarriorTeamArea.isFinishedPlayer(player.getUniqueId())) {
            return;
        }
        if (parkourWarriorTeamArea.isIntroductionPhase()) {
            return;
        }

        Location location = player.getLocation();
        if (parkourWarriorTeamArea.notInArea(location)) {
            GameStageEnum stage = parkourWarriorTeamArea.getGameStageEnum();
            // An out-of-bounds runner is always restored to the course. Spectator mode is
            // reserved for players who reach the final checkpoint.
            if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN
                    || stage == GameStageEnum.PROGRESS) {
                player.setGameMode(GameMode.ADVENTURE);
                player.setFlying(false);
                player.setAllowFlight(false);
                parkourWarriorTeamArea.teleportPlayerToSpawnPoint(player,
                        stage == GameStageEnum.PROGRESS);
            }

            return;
        }

        if (parkourWarriorTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (parkourWarriorTeamArea.getTimer() <= parkourWarriorTeamArea.getGameConfig().getTimer()) {
                if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.CREATIVE) {
                    parkourWarriorTeamArea.handlePlayerMove(player);
                }
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
        if (parkourWarriorTeamArea.isFinishedPlayer(player.getUniqueId())) {
            return;
        }

        Location location = player.getLocation();
        if (parkourWarriorTeamArea.notInArea(location)) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (parkourWarriorTeamArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (parkourWarriorTeamArea.getTimer() <= parkourWarriorTeamArea.getGameConfig().getTimer()) {
                    if (event.getItem() != null) {
                        if (event.getItem().getType() == Material.BARRIER) {
                            parkourWarriorTeamArea.backToMainSpawnPoint(player);
                        }
                    }
                }
            }
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
    public void onPlayerDamageCrystalEvent(EntityDamageEvent event) {
        if (event.getEntity() instanceof EnderCrystal enderCrystal) {
            Location location = enderCrystal.getLocation();
            if (parkourWarriorTeamArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamageCrystalEvent2(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof EnderCrystal enderCrystal) {
            Location location = enderCrystal.getLocation();
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityEnterNetherPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (parkourWarriorTeamArea.notInArea(player.getLocation())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEnterNetherPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (parkourWarriorTeamArea.notInArea(player.getLocation())) {
            return;
        }
        event.setCancelled(true);
    }

}
