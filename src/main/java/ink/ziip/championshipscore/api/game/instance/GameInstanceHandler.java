package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;

public class GameInstanceHandler extends BaseListener {
    private final BaseGameInstance baseArea;

    public GameInstanceHandler(ChampionshipsCore plugin, BaseGameInstance baseArea) {
        super(plugin);
        this.baseArea = baseArea;
    }

    private boolean isCountdownParticipant(Player player) {
        return baseArea.getGameStageEnum() == ink.ziip.championshipscore.api.object.stage.GameStageEnum.COUNTDOWN
                && !baseArea.notAreaPlayer(player);
    }

    private boolean isCountdownMovementFrozen(Player player) {
        return baseArea.freezeMovementDuringCountdown() && isCountdownParticipant(player);
    }

    private boolean isIntroductionParticipant(Player player) {
        return baseArea.isIntroductionPhase() && !baseArea.notAreaPlayer(player);
    }

    private boolean isProtectedParticipant(Player player) {
        return baseArea.isSpectator(player) || isIntroductionParticipant(player)
                || isCountdownParticipant(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItems(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamageByBlock(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isProtectedParticipant(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isProtectedParticipant(player)) {
                event.setCancelled(true);
            }
        }
        if (event.getDamager() instanceof Player player) {
            if (isProtectedParticipant(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isProtectedParticipant(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() != null && event.getTo().getY() < -50
                && isIntroductionParticipant(player)) {
            player.teleport(baseArea.getPreparationTeleportLocation(baseArea.getSpectatorSpawnLocation()));
            return;
        }
        if (isCountdownMovementFrozen(player)
                && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ())) {
            event.setCancelled(true);
            return;
        }
        if (baseArea.isSpectator(player) || isIntroductionParticipant(player)) {
            if (event.getTo() != null && !baseArea.isSpectatorLocationAllowed(event.getTo())) {
                player.teleport(isIntroductionParticipant(player)
                        ? baseArea.getPreparationTeleportLocation(baseArea.getSpectatorSpawnLocation())
                        : baseArea.getSpectatorSpawnLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isProtectedParticipant(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
        }
    }
}
