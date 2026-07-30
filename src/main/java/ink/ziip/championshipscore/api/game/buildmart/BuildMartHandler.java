package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-instance Build Mart listener for portals, build-zone flight, block protection, submissions, and
 * player lifecycle events.
 */
@Getter
@Setter
public class BuildMartHandler extends BaseListener {
    private BuildMartArea buildMartArea;

    /** Last portal trigger time per player, to debounce the base↔hub teleports. */
    private final Map<UUID, Long> lastPortal = new ConcurrentHashMap<>();

    protected BuildMartHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    private boolean running() {
        return buildMartArea != null && buildMartArea.getGameStageEnum() == GameStageEnum.PROGRESS;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!running()) return;
        Location to = event.getTo();
        Location from = event.getFrom();
        // Only act once per block step to keep this off the hot path of every sub-pixel move.
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ())
            return;

        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        BuildMartConfig config = buildMartArea.getGameConfig();
        Location spawn = buildMartArea.getSpectatorSpawnLocation();
        if (spawn.getWorld() == null || to.getWorld() == null || !to.getWorld().getName().equals(spawn.getWorld().getName()))
            return;

        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);

        // ── portals ─────────────────────────────────────────────────────────────────────────────
        if (!onCooldown(player)) {
            Integer seat = team == null ? null : buildMartArea.seatOf(team);
            BuildMartBase base = seat == null ? null : buildMartArea.cachedBaseForSeat(seat);
            // base → hub
            if (base != null && base.getPortalToHub() != null && base.getPortalToHub().contains(to.toVector())) {
                Location hub = config.getHubSpawnPoint();
                if (hub != null) {
                    triggerPortal(player, hub);
                    return;
                }
            }
            // hub → base
            if (base != null && base.getSpawn() != null && config.isInHubReturn(to)) {
                triggerPortal(player, base.getSpawn());
                return;
            }
        }

        // ── flight rule: fly in the build area, no fly in the hub ──────────────────────────────────
        applyFlight(player, config, to);
    }

    private void applyFlight(Player player, BuildMartConfig config, Location to) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        boolean inHub = config.isInHub(to);
        if (inHub) {
            if (player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        } else if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    private boolean onCooldown(Player player) {
        Long last = lastPortal.get(player.getUniqueId());
        long cd = buildMartArea.getGameConfig().getPortalCooldownMillis();
        return last != null && System.currentTimeMillis() - last < cd;
    }

    private void triggerPortal(Player player, Location target) {
        lastPortal.put(player.getUniqueId(), System.currentTimeMillis());
        player.teleport(target);
        // Re-evaluate flight at the destination on the next tick (after the teleport settles).
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                applyFlight(player, buildMartArea.getGameConfig(), player.getLocation()), 1L);
    }

    // ── physical submit buttons ────────────────────────────────────────────────────────────────

    /**
     * Right-clicking a team's submit button commits that plot. Normal plots submit on the first click; the
     * golden plot needs a confirming second click (handled in the area). The interact is cancelled so the
     * button neither fires redstone nor consumes a held block.
     */
    @EventHandler
    public void onSubmitButton(PlayerInteractEvent event) {
        if (!running()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        String slotId = buildMartArea.submitSlotIdAt(team, clicked.getLocation());
        if (slotId == null) return;
        event.setCancelled(true);
        buildMartArea.handleSubmitClick(player, slotId);
    }

    // ── build zone protection + validation ──────────────────────────────────────────────────────

    /**
     * No placement in the hub. Builds are no longer auto-validated on placement; players commit a plot
     * explicitly via the physical submit buttons (see {@link BuildMartArea#submitSlot}).
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!running()) return;
        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        if (buildMartArea.getGameConfig().isInHub(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Protects reference builds and submit buttons from being broken, and routes every other break straight
     * into the breaker's inventory: the block emits no ground item, and its drops (computed for the held
     * tool) are handed to the player. Overflow when the inventory is full falls back to a natural drop at the
     * block so nothing is lost.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!running()) return;
        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        Block block = event.getBlock();
        if (buildMartArea.isSubmitButtonBlock(block.getWorld(), block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            return;
        }
        if (buildMartArea.isProtectedReferenceBlock(block.getWorld(), block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            return;
        }
        // Creative admins keep vanilla break behaviour; participants run SURVIVAL during a round.
        if (player.getGameMode() == GameMode.CREATIVE) return;
        // Suppress the vanilla ground drop and give the broken block's drops directly to the breaker.
        event.setDropItems(false);
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
            for (ItemStack leftover : overflow.values()) {
                block.getWorld().dropItemNaturally(block.getLocation(), leftover);
            }
        }
    }

    /** No PvP / friendly fire in Build Mart: cancel any player-on-player damage. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!running()) return;
        if (!(event.getEntity() instanceof Player victim) || buildMartArea.notAreaPlayer(victim)) return;
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player) {
            event.setCancelled(true);
        }
    }

    public void clearCooldowns() {
        lastPortal.clear();
    }
}
