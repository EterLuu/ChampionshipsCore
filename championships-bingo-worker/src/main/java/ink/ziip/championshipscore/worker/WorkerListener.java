package ink.ziip.championshipscore.worker;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import io.papermc.paper.event.entity.EntityCompostItemEvent;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoNameTagObjective;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoStarterKitService;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.platform.bukkit.text.PlayerPresentation;
import ink.ziip.championshipscore.platform.bukkit.text.TeamChatCommandParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.potion.PotionType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class WorkerListener implements Listener {
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

    private final WorkerMatchRegistry registry;
    private final PlatformScheduler scheduler;
    private final WorkerChatService chat;
    private UUID lastPumpkinPlacer;
    private Location lastPumpkinLocation;
    private long lastPumpkinPlacedAt;
    private final Map<UUID, Long> recentBrushUses = new ConcurrentHashMap<>();

    WorkerListener(Plugin plugin, WorkerMatchRegistry registry, WorkerChatService chat) {
        this.registry = registry;
        this.scheduler = new PlatformScheduler(plugin);
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        event.joinMessage(Component.translatable("multiplayer.player.joined",
                presentation.identity(player.getName())));
        registry.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        event.quitMessage(Component.translatable("multiplayer.player.left",
                presentation.identity(player.getName())));
        registry.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerPresentation presentation = registry.playerPresentation(player.getUniqueId());
        event.renderer((source, sourceDisplayName, message, viewer) ->
                presentation.chatLine(player.getName(), message));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void publishCrossServerChat(AsyncChatEvent event) {
        chat.publish(event.getPlayer(), event.message());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamMessageCommand(PlayerCommandPreprocessEvent event) {
        if (registry.nativeTeamMutationSupported()) return;
        String message = TeamChatCommandParser.messageBody(event.getMessage());
        if (message == null) return;
        event.setCancelled(true);
        if (message.isEmpty()) {
            registry.sendConfiguredMessage(event.getPlayer(), "chat.team.usage");
            return;
        }
        registry.sendTeamMessage(event.getPlayer(), Component.text(message));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            registry.requestObserve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (registry.isRunningPlayer(player.getUniqueId()) && event.getRecipe() != null) {
                Material result = event.getRecipe().getResult().getType();
                if (result != Material.AIR && result.isItem()) {
                    registry.recordEventDistinct(player, "craft_unique", result.name());
                }
            }
            registry.requestObserve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMutation(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            registry.requestObserve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!registry.isRunningPlayer(event.getPlayer().getUniqueId())) event.message(null);
        registry.observeAdvancement(event.getPlayer(), event.getAdvancement());
    }

    // ── EventTask signals and tracked counters ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!registry.isRunningPlayer(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        Material type = item.getType();
        if (type == Material.GOAT_HORN) {
            registry.observeEventSignal(player, "toot_goat_horn", "");
        } else if (type == Material.MILK_BUCKET) {
            if (!player.getActivePotionEffects().isEmpty()) {
                registry.observeEventSignal(player, "remove_effect_milk", "");
            }
        } else if (type == Material.POTION && item.getItemMeta() instanceof PotionMeta meta
                && meta.getBasePotionType() == PotionType.WATER) {
            registry.observeEventSignal(player, "drink", "WATER_BOTTLE");
        } else if (type.isEdible()) {
            registry.observeEventSignal(player, "eat", type.name());
            registry.recordEventDistinct(player, "eat_unique", type.name());
            if (SOUPS.contains(type)) registry.recordEventDistinct(player, "eat_all:SOUPS", type.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (!registry.isRunningPlayer(player.getUniqueId())) return;
        Material type = event.getBrokenItem().getType();
        String kind = isArmor(type) ? "ARMOR" : isTool(type) ? "TOOL" : null;
        if (kind != null) registry.observeEventSignal(player, "break_item", kind);
        registry.observeEventSignal(player, "break_item", type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEventDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!registry.isRunningPlayer(player.getUniqueId())) return;
        if (registry.clearsInventoryOnDeath(player)) event.getDrops().clear();
        String cause = resolveDeathCause(player);
        if (cause != null) registry.observeEventSignal(player, "die", cause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player && registry.isRunningPlayer(player.getUniqueId())) {
            registry.observeEventSignal(player, "tame", event.getEntityType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventLeash(PlayerLeashEntityEvent event) {
        if (registry.isRunningPlayer(event.getPlayer().getUniqueId())) {
            registry.observeEventSignal(event.getPlayer(), "leash", event.getEntity().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player) || !registry.isRunningPlayer(player.getUniqueId())) return;
        String species = event.getMother().getType().name();
        registry.observeEventSignal(player, "breed", species);
        registry.recordEventDistinct(player, "breed_unique", species);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!registry.isRunningPlayer(player.getUniqueId()) || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null) return;
        Material type = block.getType();
        if ((type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL)
                && item != null && item.getType() == Material.BRUSH) {
            recentBrushUses.put(player.getUniqueId(), System.currentTimeMillis());
        } else if (type == Material.COMPOSTER && isComposterFull(block)) {
            registry.observeEventSignal(player, "use", "COMPOSTER");
        } else if ((type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE)
                && item != null && item.getType().isEdible() && campfireHasFreeSlot(block)) {
            scheduler.runEntityLater(player, () -> checkCampfireFilled(block, player), 1L);
        } else if (type == Material.CAKE && player.getFoodLevel() < 20) {
            registry.observeEventSignal(player, "eat", "CAKE");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventCompost(EntityCompostItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !registry.isRunningPlayer(player.getUniqueId())) return;
        Material type = event.getItem().getType();
        if (type.isEdible()) registry.recordEventDistinct(player, "compost_unique", type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!registry.isRunningPlayer(player.getUniqueId())) return;
        Long brushedAt = recentBrushUses.remove(player.getUniqueId());
        if (brushedAt == null || System.currentTimeMillis() - brushedAt > 15_000L) return;
        Material type = event.getBlockState().getType();
        if (type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL) {
            registry.observeEventSignal(player, "use_brush", "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!registry.isRunningPlayer(player.getUniqueId())) return;
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType() == Material.GOLDEN_DANDELION
                && event.getRightClicked() instanceof Ageable ageable && !ageable.isAdult()) {
            registry.observeEventSignal(player, "use_golden_dandelion", "");
            return;
        }
        String objective = BingoNameTagObjective.match(item, event.getRightClicked().getType());
        if (objective != null) registry.observeEventSignal(player, "name", objective);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!registry.isRunningPlayer(player.getUniqueId())) return;
        Material placed = event.getBlockPlaced().getType();
        if (placed.name().endsWith("HANGING_SIGN")) {
            registry.observeEventSignal(player, "place", "HANGING_SIGN");
        } else if (placed == Material.CARVED_PUMPKIN) {
            lastPumpkinPlacer = player.getUniqueId();
            lastPumpkinLocation = event.getBlock().getLocation();
            lastPumpkinPlacedAt = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && registry.isRunningPlayer(player.getUniqueId())
                && event.getEntity().getType() == EntityType.PAINTING) {
            registry.observeEventSignal(player, "place", "PAINTING");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventShieldDisable(PlayerShieldDisableEvent event) {
        if (registry.isRunningPlayer(event.getPlayer().getUniqueId())) {
            registry.observeEventSignal(event.getPlayer(), "shield_disabled", "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Firework firework && firework.getShooter() instanceof Player player
                && registry.isRunningPlayer(player.getUniqueId())) {
            registry.observeEventSignal(player, "shoot_firework_crossbow", "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM) return;
        Player player = recentPumpkinPlacer(event.getLocation());
        if (player == null) player = nearestRunningPlayer(event.getEntity(), 8);
        if (player != null) registry.observeEventSignal(player, "construct_copper_golem", "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !registry.isRunningPlayer(player.getUniqueId())) return;
        EntityType type = event.getEntity().getType();
        if (type == EntityType.ENDERMAN || type == EntityType.ZOMBIFIED_PIGLIN) {
            registry.observeEventSignal(player, "enrage", type.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        Player player = crystalDamager(crystal);
        if (player == null) player = nearestRunningPlayer(crystal, 16);
        if (player != null) registry.observeEventSignal(player, "explode_end_crystal", "");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEventEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null || !registry.isRunningPlayer(player.getUniqueId())) return;
        LivingEntity living = event.getEntity();
        EntityType type = living.getType();
        if (Tag.ENTITY_TYPES_UNDEAD.isTagged(type)) {
            registry.recordEventCount(player, "kill_family:UNDEAD");
        } else if (Tag.ENTITY_TYPES_ARTHROPOD.isTagged(type)) {
            registry.recordEventCount(player, "kill_family:ARTHROPOD");
        }
        if (living instanceof Monster) registry.recordEventDistinct(player, "kill_unique:HOSTILE", type.name());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedDrop(PlayerDropItemEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedPlace(BlockPlaceEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedBreak(BlockBreakEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedInteract(PlayerInteractEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedInteractEntity(PlayerInteractEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)
                || !registry.isProtectedParticipant(player.getUniqueId())) return;
        event.setCancelled(true);
        event.setTarget(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedEntityBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedEntityInteract(EntityInteractEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedInsideBlock(EntityInsideBlockEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedGameEvent(GenericGameEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedArrowPickup(PlayerPickupArrowEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedVehicleCollision(VehicleEntityCollisionEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoatMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || from.getWorld() != to.getWorld()) return;
        double distance = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        if (!Double.isFinite(distance) || distance <= 0.0D) return;
        double centimeters = distance * 100.0D;
        for (org.bukkit.entity.Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player player) registry.recordBoatMovement(player, centimeters);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedArmorStand(PlayerArmorStandManipulateEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedBucketEmpty(PlayerBucketEmptyEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedBucketFill(PlayerBucketFillEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedBucketEntity(PlayerBucketEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedSwapHands(PlayerSwapHandItemsEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedLeash(PlayerLeashEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedShear(PlayerShearEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedUnleash(PlayerUnleashEntityEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedRaidTrigger(RaidTriggerEvent event) {
        if (registry.isProtectedParticipant(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && registry.isProtectedParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectedDamageByEntity(EntityDamageByEntityEvent event) {
        UUID attacker = attackerId(event.getDamager());
        if (attacker != null && registry.isProtectedParticipant(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFinalCountdownMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !registry.isFinalCountdownPlayer(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (!registry.isPlaying(event.getPlayer().getUniqueId())) return;
        NamespacedKey key = event.getAdvancement().getKey();
        if (key != null && BingoStarterKitService.conflictingAdvancementKeys().contains(key.getKey())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = registry.respawnLocation(event.getPlayer());
        if (location == null) return;
        event.setRespawnLocation(location);
        scheduler.runEntityLater(event.getPlayer(), () -> registry.restoreAfterRespawn(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCompass(PlayerInteractEvent event) {
        if (event.getItem() == null || !registry.canUseBingoUi(event.getPlayer().getUniqueId())) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (event.getHand() == EquipmentSlot.HAND
                && registry.handleSpectatorControl(event.getPlayer(), event.getItem(), rightClick)) {
            event.setCancelled(true);
            return;
        }
        org.bukkit.Material type = event.getItem().getType();
        if (type != org.bukkit.Material.COMPASS && type != org.bukkit.Material.FILLED_MAP) return;
        if (type == org.bukkit.Material.COMPASS
                && !registry.isRunningPlayer(event.getPlayer().getUniqueId())) return;
        Integer selectedTeam = type == org.bukkit.Material.FILLED_MAP
                ? registry.boundCardTeam(event.getItem()) : null;
        if (type == org.bukkit.Material.FILLED_MAP && selectedTeam == null) return;
        if (type == org.bukkit.Material.FILLED_MAP && !rightClick) return;
        if (action == Action.RIGHT_CLICK_BLOCK) return;
        if (type == org.bukkit.Material.FILLED_MAP && event.getHand() == EquipmentSlot.HAND
                && isOffhandWeapon(event.getPlayer().getInventory().getItemInOffHand().getType())) return;
        event.setCancelled(true);
        if (type == org.bukkit.Material.FILLED_MAP) {
            // Opening in the interaction callback can be overwritten by the client's use-item
            // acknowledgement on Folia. Defer exactly one entity tick, preserving ownership.
            scheduler.runEntityLater(event.getPlayer(),
                    () -> registry.openCard(event.getPlayer(), selectedTeam), 1L);
        } else {
            scheduler.runEntityLater(event.getPlayer(), () -> registry.openTeammates(event.getPlayer()), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!WorkerMenuService.isReadOnly(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID target = WorkerMenuService.teammateTarget(event.getView().getTopInventory(), event.getRawSlot());
        if (target != null) {
            player.closeInventory();
            registry.teleportToTeammate(player, target);
            return;
        }
        UUID spectatorTarget = WorkerMenuService.spectatorTarget(event.getView().getTopInventory(), event.getRawSlot());
        if (spectatorTarget != null) {
            player.closeInventory();
            registry.teleportToSpectatorTarget(player, spectatorTarget);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (WorkerMenuService.isReadOnly(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        org.bukkit.inventory.ItemStack item = event.getItemDrop().getItemStack();
        if (!registry.canUseBingoUi(event.getPlayer().getUniqueId())) return;
        if (item.getType() == org.bukkit.Material.COMPASS || registry.boundCardTeam(item) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBoundCardPickup(EntityPickupItemEvent event) {
        Integer teamId = registry.boundCardTeam(event.getItem().getItemStack());
        if (teamId == null) return;
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        boolean allowed = teamId == Integer.MIN_VALUE
                ? registry.canPickupSpectatorCard(player.getUniqueId())
                : registry.canPickupCard(player.getUniqueId(), teamId);
        if (!allowed) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        UUID attacker = attackerId(event.getDamager());
        if (attacker != null && registry.isRunningPlayer(victim.getUniqueId())
                && registry.sameTeam(attacker, victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        UUID thrower = shooterId(event.getPotion().getShooter());
        if (thrower == null || !registry.isRunningPlayer(thrower)) return;
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim
                && !victim.getUniqueId().equals(thrower)
                && registry.sameTeam(thrower, victim.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        UUID source = shooterId(cloud.getSource());
        if (source == null || !registry.isRunningPlayer(source)) return;
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim
                && !victim.getUniqueId().equals(source)
                && registry.sameTeam(source, victim.getUniqueId()));
    }

    private Player nearestRunningPlayer(Entity center, double radius) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : center.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player player) || !registry.isRunningPlayer(player.getUniqueId())) continue;
            double distance = player.getLocation().distanceSquared(center.getLocation());
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
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
        return player != null && registry.isRunningPlayer(player.getUniqueId()) ? player : null;
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
        if (!registry.isRunningPlayer(player.getUniqueId()) || !(block.getState() instanceof Campfire campfire)) return;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (item == null || item.getType().isAir()) return;
        }
        registry.observeEventSignal(player, "fill_campfire", "");
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

    private static UUID attackerId(Entity damager) {
        if (damager instanceof Player player) return player.getUniqueId();
        if (damager instanceof Projectile projectile) return shooterId(projectile.getShooter());
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Tameable pet && pet.isTamed()) return pet.getOwnerUniqueId();
        return null;
    }

    private static UUID shooterId(ProjectileSource source) {
        return source instanceof Player player ? player.getUniqueId() : null;
    }

    private static boolean isOffhandWeapon(org.bukkit.Material material) {
        return material == org.bukkit.Material.SHIELD || material == org.bukkit.Material.TRIDENT
                || material == org.bukkit.Material.BOW || material == org.bukkit.Material.CROSSBOW;
    }
}
