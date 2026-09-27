package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

@Setter
public final class RiptideRushHandler extends BaseListener {
    private RiptideRushArea area;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAcceptedMathMovement(PlayerMoveEvent event) {
        if (area == null || event.getTo() == null || area.notAreaPlayer(event.getPlayer())) return;
        area.recordMathMovement(event.getPlayer(), event.getFrom(), event.getTo());
    }

    RiptideRushHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void handleRoutedPlayerMoveLow(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (area.notAreaPlayer(player) || area.isIntroductionPhase() || event.getTo() == null) return;
        GameStageEnum stage = area.getGameStageEnum();
        if (stage == GameStageEnum.PREPARATION && RiptideCourseGenerator.isGenerating(player.getWorld())) return;
        if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN) {
            if (area.notInArea(event.getTo())) area.teleportPlayerToSpawnPoint(player);
            return;
        }
        if (stage != GameStageEnum.PROGRESS) return;
        if (area.isEliminated(player.getUniqueId())) return;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && activeParticipant(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && activeParticipant(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (activeParticipant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (activeParticipant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (activeParticipant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (activeParticipant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && area != null && area.isColorFloorParticipant(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && area != null && area.isColorFloorParticipant(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (area != null && area.isColorFloorParticipant(event.getPlayer())) event.setCancelled(true);
    }

    private boolean activeParticipant(Player player) {
        return area != null && !area.notAreaPlayer(player)
                && area.getGameStageEnum() != GameStageEnum.WAITING
                && player.getGameMode() != GameMode.SPECTATOR;
    }
}
