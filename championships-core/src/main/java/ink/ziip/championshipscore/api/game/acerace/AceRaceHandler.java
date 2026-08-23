package ink.ziip.championshipscore.api.game.acerace;

import io.papermc.paper.event.entity.EntityAttemptSpinAttackEvent;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.gui.ListStepGui;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.entity.EnderCrystal;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
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

    @Override
    public void handleRoutedPlayerMoveLow(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (aceRaceArea.notAreaPlayer(player)) return;
        if (aceRaceArea.isIntroductionPhase()) return;
        if (aceRaceArea.getGameStageEnum() == GameStageEnum.PROGRESS
                && !aceRaceArea.isManagedSpectator(player)) {
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
        if (event.getEntity() instanceof EnderCrystal crystal
                && aceRaceArea.mapEditPreviewRespawnIndex(crystal) >= 0) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !aceRaceArea.notAreaPlayer(player)) event.setCancelled(true);
        if (event.getEntity() instanceof EnderCrystal crystal
                && aceRaceArea.mapEditPreviewRespawnIndex(crystal) >= 0) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreviewCrystalInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof EnderCrystal crystal)) return;
        int index = aceRaceArea.mapEditPreviewRespawnIndex(crystal);
        if (index < 0) return;
        event.setCancelled(true);
        PrepareSession session = plugin.getPrepareSessionManager().getSession(event.getPlayer());
        if (session == null || session.getGameType() != ink.ziip.championshipscore.api.object.game.GameTypeEnum.AceRace
                || !java.util.Objects.equals(session.getAreaName(), aceRaceArea.getGameConfig().getAreaName())) return;
        ListStepGui.openEdit(event.getPlayer(), session, new AceRaceRespawnPointListStep(), index);
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
        aceRaceArea.handleRiptideStart(event.getPlayer());
    }

    /** Prevents a riptiding racer from attacking another player and losing momentum on contact. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpinAttackContact(EntityAttemptSpinAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getTarget() instanceof Player)) return;
        if (aceRaceArea.getGameStageEnum() == GameStageEnum.PROGRESS && !aceRaceArea.notAreaPlayer(player)) {
            event.setCancelled(true);
            aceRaceArea.handleRiptideStart(player);
        }
    }
}
