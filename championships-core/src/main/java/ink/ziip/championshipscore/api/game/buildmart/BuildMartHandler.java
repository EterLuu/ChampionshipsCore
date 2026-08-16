package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
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

    /** Copper is a stable building material in Build Mart and never advances through oxidation stages. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCopperOxidation(BlockFormEvent event) {
        if (buildMartArea == null || !event.getBlock().getWorld().getName().equals(buildMartArea.getWorldName())) {
            return;
        }
        if (BuildMartCopperPolicy.isForwardOxidation(event.getBlock().getType(), event.getNewState().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && buildMartArea != null
                && !buildMartArea.notAreaPlayer(player) && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMovementDamage(EntityDamageEvent event) {
        if ((event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL)
                && event.getEntity() instanceof Player player && buildMartArea != null
                && !buildMartArea.notAreaPlayer(player)) {
            event.setCancelled(true);
        }
    }

    private boolean boundaryActive() {
        if (buildMartArea == null || buildMartArea.isIntroductionPhase()) return false;
        GameStageEnum stage = buildMartArea.getGameStageEnum();
        return stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN
                || stage == GameStageEnum.PROGRESS;
    }

    @Override
    public void handleRoutedPlayerMoveNormal(PlayerMoveEvent event) {
        boolean gameRunning = running();
        if (!gameRunning && !boundaryActive()) return;
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;
        // Only act once per block step to keep this off the hot path of every sub-pixel move.
        if (to.getWorld() != null && to.getWorld().equals(from.getWorld())
                && to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ())
            return;

        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        BuildMartConfig config = buildMartArea.getGameConfig();

        // The playable space is the disjoint hub/base union. Returning to the hub keeps players from
        // escaping through the gaps between the separated team bases.
        if (to.getWorld() == null || !to.getWorld().getName().equals(buildMartArea.getWorldName())
                || !config.isInPlayableArea(to)) {
            Location hub = config.getHubPortalPoint();
            if (hub != null) event.setTo(hub);
            return;
        }

        // Formal preparation uses the same boundary but remains otherwise passive until the live round.
        if (!gameRunning) return;

        // ── flight rule: fly in the build area, no fly in the hub ──────────────────────────────────
        applyFlight(player, config, to);
    }

    /** Build Mart owns participant portal routing; vanilla destination lookup must never run. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (!portalActive(player) || !isBuildMartPortal(player, event.getFrom())) return;
        event.setCancelled(true);
        routePortal(player, event.getFrom());
    }

    private boolean portalActive(Player player) {
        return buildMartArea != null && (running() || boundaryActive())
                && !buildMartArea.notAreaPlayer(player);
    }

    /**
     * Only real Nether portal blocks in the player's own base or the shared hub participate. The two
     * configured points are landing locations, so admins never need separate outbound/inbound spawns.
     */
    private boolean isBuildMartPortal(Player player, Location from) {
        if (from == null || from.getBlock().getType() != Material.NETHER_PORTAL) return false;
        BuildMartConfig config = buildMartArea.getGameConfig();
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        Integer seat = team == null ? null : buildMartArea.seatOf(team);
        return config.isInHub(from) || seat != null && config.isInBase(from, seat);
    }

    private void routePortal(Player player, Location from) {
        BuildMartConfig config = buildMartArea.getGameConfig();
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        Integer seat = team == null ? null : buildMartArea.seatOf(team);
        BuildMartBase base = seat == null ? null : buildMartArea.cachedBaseForSeat(seat);
        if (base == null || onCooldown(player)) return;

        if (config.isInBase(from, seat)) {
            Location target = config.getHubPortalPoint();
            if (target != null) triggerPortal(player, target);
            return;
        }
        if (config.isInHub(from)) {
            Location target = base.getPortalPoint();
            if (target != null) triggerPortal(player, target);
        }
    }

    private void applyFlight(Player player, BuildMartConfig config, Location to) {
        if (buildMartArea.isManagedSpectator(player) || player.getGameMode() == GameMode.CREATIVE) return;
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
        long cooldownMillis = Math.max(50L, buildMartArea.getGameConfig().getPortalCooldownMillis());
        player.setPortalCooldown((int) Math.min(Integer.MAX_VALUE, (cooldownMillis + 49L) / 50L));
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

    /** Allows work blocks in a team's base while restricting structural controls outside build volumes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBaseBlockInteract(PlayerInteractEvent event) {
        if (!running() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.PHYSICAL) return;
        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || !isAnyTeamBase(clicked.getLocation())) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null || !isOwnTeamBase(team, clicked.getLocation())) {
            denyInteraction(event);
            return;
        }
        if (buildMartArea.isBuildZoneBlock(team, clicked.getWorld(),
                clicked.getX(), clicked.getY(), clicked.getZ())) return;

        Material type = clicked.getType();
        boolean restricted = event.getAction() == Action.PHYSICAL
                ? Tag.PRESSURE_PLATES.isTagged(type)
                : isRestrictedBaseControl(type);
        if (restricted) denyInteraction(event);
    }

    private boolean isOwnTeamBase(ChampionshipTeam team, Location location) {
        Integer seat = buildMartArea.seatOf(team);
        return seat != null && buildMartArea.getGameConfig().isInBase(location, seat);
    }

    private static boolean isRestrictedBaseControl(Material type) {
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type) || Tag.BUTTONS.isTagged(type)
                || Tag.PRESSURE_PLATES.isTagged(type) || type == Material.LEVER;
    }

    private static void denyInteraction(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    private boolean isAnyTeamBase(Location location) {
        BuildMartConfig config = buildMartArea.getGameConfig();
        for (int seat = 0; seat < config.getBaseCount(); seat++) {
            if (config.isInBase(location, seat)) return true;
        }
        return false;
    }

    // ── build zone protection + validation ──────────────────────────────────────────────────────

    /**
     * No placement in the hub. Builds are not auto-validated on placement; players commit a plot
     * explicitly via the physical submit buttons (see {@link BuildMartArea#submitSlot}).
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!running()) return;
        Player player = event.getPlayer();
        if (buildMartArea.notAreaPlayer(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null || !buildMartArea.isBuildZoneBlock(team, event.getBlock().getWorld(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ())) {
            event.setCancelled(true);
        }
    }

    /**
     * Allows breaks only in material/build plots, protects reference builds and submit buttons, and routes
     * allowed survival drops straight into the breaker's inventory. A full inventory does not create a world drop.
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
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (!buildMartArea.isMaterialZoneBlock(block.getWorld(), block.getX(), block.getY(), block.getZ())
                && (team == null || !buildMartArea.isBuildZoneBlock(team, block.getWorld(), block.getX(), block.getY(), block.getZ()))) {
            event.setCancelled(true);
            return;
        }
        // Creative admins keep vanilla break behaviour; participants run SURVIVAL during a round.
        if (player.getGameMode() == GameMode.CREATIVE) return;
        // Suppress the vanilla ground drop and give the broken block's drops directly to the breaker.
        event.setDropItems(false);
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        for (ItemStack drop : drops) {
            player.getInventory().addItem(drop);
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
