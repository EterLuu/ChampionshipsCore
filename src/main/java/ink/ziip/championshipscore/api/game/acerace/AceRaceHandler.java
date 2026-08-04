package ink.ziip.championshipscore.api.game.acerace;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRiptideEvent;

/** Keeps the race non-destructive and turns a fall below the active height into respawn-point recovery. */
@Getter
@Setter
public class AceRaceHandler extends BaseListener {
    private AceRaceArea aceRaceArea;

    protected AceRaceHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (aceRaceArea.notAreaPlayer(player)) return;
        if (aceRaceArea.getGameStageEnum() == GameStageEnum.PROGRESS
                && player.getGameMode() != GameMode.SPECTATOR) {
            aceRaceArea.handlePlayerMove(event);
            return;
        }
        if (aceRaceArea.getGameStageEnum() == GameStageEnum.PREPARATION && aceRaceArea.notInArea(player.getLocation())) {
            player.teleport(aceRaceArea.getPreparationTeleportLocation(aceRaceArea.getGameConfig().getStartSpawnPoint()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        aceRaceArea.handleVisibilityJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !aceRaceArea.notAreaPlayer(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !aceRaceArea.notAreaPlayer(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !aceRaceArea.notAreaPlayer(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!aceRaceArea.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!aceRaceArea.notAreaPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (aceRaceArea.notAreaPlayer(event.getPlayer())) return;
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            // Protect buttons, containers, doors, etc. without cancelling use of the held item.
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        aceRaceArea.applyIntermediateRiptideBoost(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJump(PlayerJumpEvent event) {
        if (aceRaceArea.shouldSuppressLaunchPadJump(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
        }
    }
}
