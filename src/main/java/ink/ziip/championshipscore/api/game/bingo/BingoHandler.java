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
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.projectiles.ProjectileSource;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;

import java.util.UUID;

/**
 * Per-area bingo listener: defers item/statistic progress checks on inventory events (pickup, craft,
 * click) to the next tick (inventory events fire before the item lands), forwards advancement-done
 * events, and keeps same-team friendly fire off during a round. The first-3-minutes PvP grace is
 * enforced at the world level via {@code world.setPVP} (see {@link BingoArea}), not here.
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
        // Silence the vanilla "made the advancement" chat broadcast for anything earned outside a running
        // round (e.g. the kit-granted advancements at round start). The bingo world's
        // SHOW_ADVANCEMENT_MESSAGES gamerule already suppresses this; null the message too as a fallback.
        if (!running()) {
            event.message(null);
            return;
        }
        Player player = event.getPlayer();
        if (!bingoArea.notAreaPlayer(player)) {
            bingoArea.onAdvancement(player, event.getAdvancement());
        }
    }

    // Kit-granted advancements (Suit Up, Getting an Upgrade, Sky's the Limit) would pop an on-screen
    // toast at round start when the kit is handed out, and again on the first inventory change during
    // play (the kit items remain, so the next inventory_changed re-grants them). The chat broadcast is
    // suppressed via the SHOW_ADVANCEMENT_MESSAGES gamerule; the toast needs the criterion grant itself
    // cancelled. These advancements are also kept off the card by BingoStarterKit#trivialises, so
    // cancelling their grant affects no card task.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (bingoArea == null) return;
        Player player = event.getPlayer();
        if (bingoArea.notAreaPlayer(player)) return;
        GameStageEnum stage = bingoArea.getGameStageEnum();
        // PREPARATION covers the kit hand-out at round start; PROGRESS covers the first inventory change
        // re-granting kit advancements during play.
        if (stage != GameStageEnum.PREPARATION && stage != GameStageEnum.PROGRESS) return;
        NamespacedKey key = event.getAdvancement().getKey();
        if (key != null && BingoStarterKit.conflictingAdvancementKeys().contains(key.getKey())) {
            event.setCancelled(true);
        }
    }

    // ── death respawn ──────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (bingoArea == null) return;
        Player player = event.getPlayer();
        if (bingoArea.notAreaPlayer(player)) return;
        GameStageEnum stage = bingoArea.getGameStageEnum();
        if (stage != GameStageEnum.PREPARATION && stage != GameStageEnum.PROGRESS) return;
        // Vanilla respawns a bedless player at the main-world spawn (the lobby); redirect into the
        // bingo world so a death mid-round doesn't drop the player out of the running game.
        Location respawn = bingoArea.getRespawnLocation();
        if (respawn != null && respawn.getWorld() != null) {
            event.setRespawnLocation(respawn);
        }
        if (stage == GameStageEnum.PROGRESS) {
            // KEEP_INVENTORY is on so the kit/card survive a death, but re-issue them as a safety net
            // in case the gamerule is ever off or the items were lost. hasKit gates the kit so the
            // non-stacking tools never duplicate.
            plugin.getServer().getScheduler().runTask(plugin, () -> bingoArea.ensureKitAndCard(player));
        }
    }

    // ── elytra glide ────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!running()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (bingoArea.notAreaPlayer(player)) return;
        // Slow Falling cancels elytra flight; drop the permanent Slow Falling while gliding and restore
        // it when gliding ends. See BingoArea#onGlideToggle / BingoPermanentEffects.
        bingoArea.onGlideToggle(player, event.isGliding());
    }

    // ── friendly fire off ──────────────────────────────────────────────────────────────────────
    // The first-3-minutes PvP grace is enforced at the world level (BingoArea toggles world.setPVP),
    // so this handler only needs to keep same-team friendly fire off once PvP turns on.

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
