package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import lombok.Getter;
import lombok.Setter;
import io.papermc.paper.event.entity.EntityCompostItemEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-area bingo listener: defers item/statistic progress checks on inventory events (pickup, craft,
 * click) to the next tick (inventory events fire before the item lands), forwards advancement-done
 * events, and keeps same-team friendly fire off during a round. The first-3-minutes PvP grace is
 * enforced at the world level via {@code world.setPVP} (see {@link BingoArea}), not here.
 */
@Getter
@Setter
public class BingoHandler extends BaseListener {
    private static final Set<Material> SOUPS = Set.of(
            Material.BEETROOT_SOUP, Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.SUSPICIOUS_STEW);
    private static final Set<String> TOOL_MATERIALS = Set.of(
            "WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "COPPER");
    private static final Set<String> TOOL_TYPES = Set.of("PICKAXE", "AXE", "SHOVEL", "HOE", "SWORD");
    private static final Set<Material> MISC_TOOLS = Set.of(
            Material.FISHING_ROD, Material.FLINT_AND_STEEL, Material.SHEARS, Material.BRUSH,
            Material.CARROT_ON_A_STICK, Material.WARPED_FUNGUS_ON_A_STICK);
    private static final Set<String> ARMOR_MATERIALS = Set.of(
            "LEATHER", "COPPER", "GOLDEN", "CHAINMAIL", "IRON", "DIAMOND");
    private static final Set<String> ARMOR_TYPES = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private BingoArea bingoArea;
    private final PlatformScheduler platformScheduler;
    private UUID lastPumpkinPlacer;
    private Location lastPumpkinLocation;
    private long lastPumpkinPlacedAt;
    private final Map<UUID, Long> recentBrushUses = new ConcurrentHashMap<>();

    protected BingoHandler(ChampionshipsCore plugin) {
        super(plugin);
        this.platformScheduler = new PlatformScheduler(plugin);
    }

    private boolean running() {
        return bingoArea != null && bingoArea.getGameStageEnum() == GameStageEnum.PROGRESS;
    }

    /** Inventory events fire before the item lands; re-scan one tick later once the inventory settles. */
    private void scheduleProgressCheck(Player player) {
        if (player == null || bingoArea == null) return;
        platformScheduler.runEntityLater(player, () -> bingoArea.checkPlayerProgress(player), 1L);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!running()) return;
        if (event.getEntity() instanceof Player player && !bingoArea.notAreaPlayer(player)) {
            scheduleProgressCheck(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!running()) return;
        if (event.getWhoClicked() instanceof Player player && !bingoArea.notAreaPlayer(player)) {
            if (event.getRecipe() != null) {
                Material result = event.getRecipe().getResult().getType();
                if (result != Material.AIR && result.isItem()) {
                    bingoArea.recordEventDistinct(player, "craft_unique", result.name());
                }
            }
            scheduleProgressCheck(player);
        }
    }

    // ── EventTask signals and tracked counters ──────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Material type = item.getType();
        if (type == Material.GOAT_HORN) {
            bingoArea.onEventSignal(player, "toot_goat_horn", "");
        } else if (type == Material.MILK_BUCKET) {
            if (!player.getActivePotionEffects().isEmpty()) {
                bingoArea.onEventSignal(player, "remove_effect_milk", "");
            }
        } else if (type == Material.POTION && item.getItemMeta() instanceof PotionMeta meta
                && meta.getBasePotionType() == PotionType.WATER) {
            bingoArea.onEventSignal(player, "drink", "WATER_BOTTLE");
        } else if (type.isEdible()) {
            bingoArea.onEventSignal(player, "eat", type.name());
            bingoArea.recordEventDistinct(player, "eat_unique", type.name());
            if (SOUPS.contains(type)) bingoArea.recordEventDistinct(player, "eat_all:SOUPS", type.name());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())) return;
        Material type = event.getBrokenItem().getType();
        String kind = isArmor(type) ? "ARMOR" : isTool(type) ? "TOOL" : null;
        if (kind != null) bingoArea.onEventSignal(event.getPlayer(), "break_item", kind);
        bingoArea.onEventSignal(event.getPlayer(), "break_item", type.name());
    }

    @EventHandler
    public void onEventTaskDeath(PlayerDeathEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getEntity())) return;
        String cause = resolveDeathCause(event.getEntity());
        if (cause != null) bingoArea.onEventSignal(event.getEntity(), "die", cause);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!running() || !(event.getOwner() instanceof Player player) || bingoArea.notAreaPlayer(player)) return;
        bingoArea.onEventSignal(player, "tame", event.getEntityType().name());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())) return;
        bingoArea.onEventSignal(event.getPlayer(), "leash", event.getEntity().getType().name());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!running() || !(event.getBreeder() instanceof Player player) || bingoArea.notAreaPlayer(player)) return;
        String species = event.getMother().getType().name();
        bingoArea.onEventSignal(player, "breed", species);
        bingoArea.recordEventDistinct(player, "breed_unique", species);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEventTaskInteract(PlayerInteractEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null) return;
        Material type = block.getType();
        if ((type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL)
                && item != null && item.getType() == Material.BRUSH) {
            recentBrushUses.put(player.getUniqueId(), System.currentTimeMillis());
        } else if (type == Material.COMPOSTER && isComposterFull(block)) {
            bingoArea.onEventSignal(player, "use", "COMPOSTER");
        } else if ((type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE)
                && item != null && item.getType().isEdible() && campfireHasFreeSlot(block)) {
            platformScheduler.runEntityLater(player, () -> checkCampfireFilled(block, player), 1L);
        } else if (type == Material.CAKE && player.getFoodLevel() < 20) {
            bingoArea.onEventSignal(player, "eat", "CAKE");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCompostItem(EntityCompostItemEvent event) {
        if (!running() || !(event.getEntity() instanceof Player player) || bingoArea.notAreaPlayer(player)) return;
        Material type = event.getItem().getType();
        if (type.isEdible()) bingoArea.recordEventDistinct(player, "compost_unique", type.name());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())) return;
        Long brushedAt = recentBrushUses.remove(event.getPlayer().getUniqueId());
        if (brushedAt == null || System.currentTimeMillis() - brushedAt > 15_000L) return;
        Material type = event.getBlockState().getType();
        if (type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL) {
            bingoArea.onEventSignal(event.getPlayer(), "use_brush", "");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEventTaskInteractEntity(PlayerInteractEntityEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType() == Material.GOLDEN_DANDELION
                && event.getRightClicked() instanceof Ageable ageable && !ageable.isAdult()) {
            bingoArea.onEventSignal(player, "use_golden_dandelion", "");
            return;
        }
        if (item.getType() != Material.NAME_TAG || !item.hasItemMeta()) return;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String normalized = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        EntityType target = event.getRightClicked().getType();
        if (target == EntityType.SHEEP && normalized.contains("jeb_")) {
            bingoArea.onEventSignal(player, "name", "SHEEP_JEB");
        } else if (target == EntityType.IRON_GOLEM
                && (normalized.contains("dinnerbone") || normalized.contains("grumm"))) {
            bingoArea.onEventSignal(player, "name", "IRON_GOLEM_DINNERBONE");
        } else if (target == EntityType.GHAST
                && (normalized.contains("dinnerbone") || normalized.contains("grumm"))) {
            bingoArea.onEventSignal(player, "name", "GHAST_DINNERBONE");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!running() || event.getPlayer() == null || bingoArea.notAreaPlayer(event.getPlayer())) return;
        if (event.getEntity().getType() == EntityType.PAINTING) {
            bingoArea.onEventSignal(event.getPlayer(), "place", "PAINTING");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEventTaskBlockPlace(BlockPlaceEvent event) {
        if (!running() || bingoArea.notAreaPlayer(event.getPlayer())) return;
        Material placed = event.getBlockPlaced().getType();
        if (placed.name().endsWith("HANGING_SIGN")) {
            bingoArea.onEventSignal(event.getPlayer(), "place", "HANGING_SIGN");
        } else if (placed == Material.CARVED_PUMPKIN) {
            lastPumpkinPlacer = event.getPlayer().getUniqueId();
            lastPumpkinLocation = event.getBlock().getLocation();
            lastPumpkinPlacedAt = System.currentTimeMillis();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent event) {
        if (running() && !bingoArea.notAreaPlayer(event.getPlayer())) {
            bingoArea.onEventSignal(event.getPlayer(), "shield_disabled", "");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEventTaskProjectileLaunch(ProjectileLaunchEvent event) {
        if (!running() || !(event.getEntity() instanceof Firework firework)
                || !(firework.getShooter() instanceof Player player) || bingoArea.notAreaPlayer(player)) return;
        bingoArea.onEventSignal(player, "shoot_firework_crossbow", "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!running() || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM) return;
        Player player = recentPumpkinPlacer(event.getLocation());
        if (player == null) player = nearestAreaPlayer(event.getEntity(), 8.0);
        if (player != null) bingoArea.onEventSignal(player, "construct_copper_golem", "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!running() || !(event.getTarget() instanceof Player player) || bingoArea.notAreaPlayer(player)) return;
        EntityType type = event.getEntity().getType();
        if (type == EntityType.ENDERMAN || type == EntityType.ZOMBIFIED_PIGLIN) {
            bingoArea.onEventSignal(player, "enrage", type.name());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!running() || !(event.getEntity() instanceof EnderCrystal crystal)) return;
        Player player = crystalDamager(crystal);
        if (player == null) player = nearestAreaPlayer(crystal, 16.0);
        if (player != null) bingoArea.onEventSignal(player, "explode_end_crystal", "");
    }

    @EventHandler
    public void onEventTaskEntityDeath(EntityDeathEvent event) {
        if (!running() || !(event.getEntity().getKiller() instanceof Player player)
                || bingoArea.notAreaPlayer(player)) return;
        LivingEntity living = event.getEntity();
        EntityType type = living.getType();
        if (Tag.ENTITY_TYPES_UNDEAD.isTagged(type)) {
            bingoArea.recordEventCount(player, "kill_family:UNDEAD");
        } else if (Tag.ENTITY_TYPES_ARTHROPOD.isTagged(type)) {
            bingoArea.recordEventCount(player, "kill_family:ARTHROPOD");
        }
        if (living instanceof Monster) {
            bingoArea.recordEventDistinct(player, "kill_unique:HOSTILE", type.name());
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
            platformScheduler.runEntity(player, () -> bingoArea.ensureKitAndCard(player));
        }
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

    private Player nearestAreaPlayer(Entity center, double radius) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : center.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player player) || !player.isOnline() || bingoArea.notAreaPlayer(player)) continue;
            double distance = player.getLocation().distanceSquared(center.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private Player recentPumpkinPlacer(Location spawnLocation) {
        if (lastPumpkinPlacer == null || lastPumpkinLocation == null
                || System.currentTimeMillis() - lastPumpkinPlacedAt > 5_000L
                || !lastPumpkinLocation.getWorld().equals(spawnLocation.getWorld())
                || lastPumpkinLocation.distanceSquared(spawnLocation) > 64.0) return null;
        Player player = Bukkit.getPlayer(lastPumpkinPlacer);
        return player != null && player.isOnline() && !bingoArea.notAreaPlayer(player) ? player : null;
    }

    private static Player crystalDamager(EnderCrystal crystal) {
        EntityDamageEvent last = crystal.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent damage)) return null;
        if (damage.getDamager() instanceof Player player) return player;
        if (damage.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private static boolean isComposterFull(Block block) {
        return block.getBlockData() instanceof Levelled levelled
                && levelled.getLevel() >= levelled.getMaximumLevel();
    }

    private static boolean campfireHasFreeSlot(Block block) {
        if (!(block.getState() instanceof Campfire campfire)) return false;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (item == null || item.getType().isAir()) return true;
        }
        return false;
    }

    private void checkCampfireFilled(Block block, Player player) {
        if (!running() || !player.isOnline() || bingoArea.notAreaPlayer(player)
                || !(block.getState() instanceof Campfire campfire)) return;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (item == null || item.getType().isAir()) return;
        }
        bingoArea.onEventSignal(player, "fill_campfire", "");
    }

    private static boolean isArmor(Material type) {
        String[] parts = type.name().split("_", 2);
        return parts.length == 2 && ARMOR_MATERIALS.contains(parts[0]) && ARMOR_TYPES.contains(parts[1]);
    }

    private static boolean isTool(Material type) {
        if (MISC_TOOLS.contains(type)) return true;
        String[] parts = type.name().split("_", 2);
        return parts.length == 2 && TOOL_MATERIALS.contains(parts[0]) && TOOL_TYPES.contains(parts[1]);
    }

    private static String resolveDeathCause(Player player) {
        EntityDamageEvent last = player.getLastDamageCause();
        if (last == null) return null;
        if (last instanceof EntityDamageByEntityEvent damage) {
            Entity damager = damage.getDamager();
            switch (damager.getType()) {
                case IRON_GOLEM: return "IRON_GOLEM";
                case POLAR_BEAR: return "POLAR_BEAR";
                case WARDEN: return "WARDEN";
                case BEE: return "BEE";
                case FIREWORK_ROCKET: return "FIREWORK";
                case TNT_MINECART: return "TNT_MINECART";
                case TRIDENT: return "TRIDENT";
                case FALLING_BLOCK:
                    if (damager instanceof FallingBlock falling) {
                        Material material = falling.getBlockData().getMaterial();
                        if (material.name().contains("ANVIL")) return "ANVIL";
                        if (material == Material.POINTED_DRIPSTONE) return "FALLING_STALACTITE";
                    }
                    break;
                default:
                    break;
            }
        }
        return switch (last.getCause()) {
            case DROWNING -> "DROWNING";
            case VOID -> "VOID";
            case FREEZE -> "FREEZE";
            case MAGIC -> "MAGIC";
            case CONTACT -> contactBlockParam(player);
            default -> null;
        };
    }

    private static String contactBlockParam(Player player) {
        Location location = player.getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Material material = location.clone().add(dx, dy, dz).getBlock().getType();
                    if (material == Material.CACTUS) return "CACTUS";
                    if (material == Material.SWEET_BERRY_BUSH) return "BERRY_BUSH";
                }
            }
        }
        return null;
    }
}
