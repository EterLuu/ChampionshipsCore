package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Per-area bingo listener: defers item/statistic progress checks on inventory events (pickup, craft,
 * click) to the next tick (inventory events fire before the item lands), forwards advancement-done
 * events, and enforces friendly-fire-off between teammates while the round runs.
 */
@Getter
@Setter
public class BingoHandler extends BaseListener {
    private BingoArea bingoArea;

    protected BingoHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    private boolean running() {
        return bingoArea != null && bingoArea.getGameStageEnum() == GameStageEnum.PROGRESS;
    }

    /** Inventory events fire before the item lands; re-scan one tick later once the inventory settles. */
    private void scheduleProgressCheck(Player player) {
        if (player == null || bingoArea == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> bingoArea.checkPlayerProgress(player), 1L);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!running()) return;
        if (event.getEntity() instanceof Player player && !bingoArea.notAreaPlayer(player)) {
            scheduleProgressCheck(player);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!running()) return;
        if (event.getWhoClicked() instanceof Player player && !bingoArea.notAreaPlayer(player)) {
            scheduleProgressCheck(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!running()) return;
        if (event.getWhoClicked() instanceof Player player && !bingoArea.notAreaPlayer(player)) {
            scheduleProgressCheck(player);
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!running()) return;
        Player player = event.getPlayer();
        if (!bingoArea.notAreaPlayer(player)) {
            bingoArea.onAdvancement(player, event.getAdvancement());
        }
    }

    // ── friendly fire off ──────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!running()) return;
        if (!(event.getEntity() instanceof Player victim) || bingoArea.notAreaPlayer(victim)) return;

        UUID attackerId = resolveAttackerId(event.getDamager());
        if (attackerId == null || attackerId.equals(victim.getUniqueId())) return;
        if (sameTeam(victim, attackerId)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!running()) return;
        UUID throwerId = resolveProjectileShooterId(event.getPotion().getShooter());
        if (throwerId == null) return;
        event.getAffectedEntities().removeIf(le -> le instanceof Player victim
                && !victim.getUniqueId().equals(throwerId)
                && sameTeam(victim, throwerId));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        if (!running()) return;
        AreaEffectCloud cloud = event.getEntity();
        UUID sourceId = resolveProjectileShooterId(cloud.getSource());
        if (sourceId == null) return;
        event.getAffectedEntities().removeIf(le -> le instanceof Player victim
                && !victim.getUniqueId().equals(sourceId)
                && sameTeam(victim, sourceId));
    }

    private boolean sameTeam(Player victim, UUID attackerId) {
        if (bingoArea.notAreaPlayer(victim)) return false;
        ChampionshipTeam victimTeam = plugin.getTeamManager().getTeamByPlayer(victim);
        if (victimTeam == null) return false;
        ChampionshipTeam attackerTeam = plugin.getTeamManager().getTeamByPlayer(attackerId);
        return victimTeam.equals(attackerTeam);
    }

    private static UUID resolveAttackerId(Entity damager) {
        if (damager instanceof Player player) return player.getUniqueId();
        if (damager instanceof Projectile projectile) {
            return resolveProjectileShooterId(projectile.getShooter());
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player igniter) {
            return igniter.getUniqueId();
        }
        if (damager instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null) {
            return pet.getOwnerUniqueId();
        }
        return null;
    }

    private static UUID resolveProjectileShooterId(ProjectileSource shooter) {
        return shooter instanceof Player player ? player.getUniqueId() : null;
    }
}
