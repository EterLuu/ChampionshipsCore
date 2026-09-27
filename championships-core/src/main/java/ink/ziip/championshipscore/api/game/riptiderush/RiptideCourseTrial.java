package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Creeper;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

/** An isolated editor run: same raft, math crossing and floor mechanics, with no score/event participation. */
public final class RiptideCourseTrial implements Listener {
    private static final Map<UUID, RiptideCourseTrial> ACTIVE = new HashMap<>();
    private final PrepareSessionManager manager;
    private final PrepareSession session;
    private final Player player;
    private final RiptideRushConfig config;
    private final RiptideCourseGeometry geometry;
    private final List<RiptideCoursePlan.Level> floors;
    private final List<RiptideCoursePlan.Level> dodges;
    private final RiptideMathRun math;
    private final RiptidePassRun passRun;
    private final RiptideRhythmRun rhythmRun;
    private final RiptideCourseReveal courseReveal;
    private final List<RiptideMathRun.Answer> answers = new ArrayList<>();
    private final Location returnTo;
    private final GameMode oldMode;
    private final boolean oldFlight;
    private final boolean oldFlying;
    private final RiptideColorFloorInventory inventory;
    private final List<org.bukkit.potion.PotionEffect> oldEffects;
    private final int end;
    private int step;
    private int elapsed;
    private final RiptideFallCheck fallCheck = new RiptideFallCheck();
    private final RiptideDeparture departure = new RiptideDeparture();
    private int countdown = 60;
    private int floorIndex;
    private int dodgeIndex;
    private int dodgeTicks;
    private RiptideDodgeRun dodgeRun;
    private final List<LivingEntity> dodgeEntities = new ArrayList<>();
    private final Map<UUID, RiptideDodgeRun.Direction> dodgeDirections = new HashMap<>();
    private final Map<UUID, Double> dodgeSpeeds = new HashMap<>();
    private double budget;
    private RiptideColorFloorRun floor;
    private RiptideColorFloorPlatform platform;
    private BukkitTask task;
    private boolean stopped;
    private final Runnable onComplete;

    private RiptideCourseTrial(PrepareSessionManager manager, Player player, PrepareSession session,
                               RiptideCoursePlan plan, RiptideCoursePlan.Level only, Runnable onComplete) {
        this.onComplete = onComplete;
        this.manager = manager; this.player = player; this.session = session;
        config = (RiptideRushConfig) session.getTarget().config(); geometry = config.resolveGeometry();
        returnTo = player.getLocation().clone(); oldMode = player.getGameMode();
        oldFlight = player.getAllowFlight(); oldFlying = player.isFlying();
        inventory = RiptideColorFloorInventory.capture(player.getInventory());
        oldEffects = List.copyOf(player.getActivePotionEffects());
        var levels = plan.trialLevels(only);
        passRun = new RiptidePassRun(geometry, levels, config);
        rhythmRun = new RiptideRhythmRun(geometry, levels);
        step = only == null ? 0 : Math.max(0, levels.getFirst().step() - 24);
        end = only == null ? geometry.totalSteps() : Math.min(geometry.totalSteps(), levels.getLast().step() + geometry.halfLength() + 8);
        floors = levels.stream().filter(RiptideCoursePlan.Level::colorFloor).toList();
        dodges = levels.stream().filter(level -> level.type() == RiptideLevelType.DODGE).toList();
        RiptideRaftBlocks.move(geometry, 0, step, trail());
        player.closeInventory(); player.setGameMode(GameMode.ADVENTURE); player.setAllowFlight(false);
        for (var effect : oldEffects) player.removePotionEffect(effect.getType());
        player.getInventory().setStorageContents(new ItemStack[player.getInventory().getStorageContents().length]);
        player.getInventory().setItemInOffHand(null);
        player.teleport(geometry.centerAt(step));
        math = new RiptideMathRun(geometry, levels.stream().filter(l -> l.type() == RiptideLevelType.MATH && !l.isSideSweep())
                .map(l -> new RiptideMathRun.Gate(l.number(), l.step(), l.question(config.getMinimumOperand(), config.getMaximumOperand(),
                        RiptideDifficulty.stage(l.step(), geometry.totalSteps()))))
                .toList(), player.getLocation());
        courseReveal = new RiptideCourseReveal(config, geometry, levels);
    }

    public static boolean isActive(String worldName) {
        return ACTIVE.values().stream().anyMatch(t -> t.session.getTarget().worldName().equals(worldName));
    }
    public static void start(PrepareSessionManager manager, Player player, PrepareSession session,
                             RiptideCoursePlan plan, RiptideCoursePlan.Level only) {
        start(manager, player, session, plan, only, () -> { });
    }
    public static void start(PrepareSessionManager manager, Player player, PrepareSession session,
                             RiptideCoursePlan plan, RiptideCoursePlan.Level only, Runnable onComplete) {
        if (ACTIVE.containsKey(player.getUniqueId()) || isActive(session.getTarget().worldName())
                || !player.hasPermission("cc.admin") || manager.getSession(player) != session) return;
        var trial = new RiptideCourseTrial(manager, player, session, plan, only, onComplete);
        ACTIVE.put(player.getUniqueId(), trial);
        Bukkit.getPluginManager().registerEvents(trial, session.getPlugin());
        trial.task = Bukkit.getScheduler().runTaskTimer(session.getPlugin(), () -> {
            try { trial.tick(); }
            catch (RuntimeException error) {
                session.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "激流试玩失败", error);
                trial.finish("试玩终止：" + error.getMessage());
            }
        }, 1L, 1L);
        Utils.sendAdminInfo(player, "试玩将在3秒后开始；请自行跟上木筏，交换副手键（默认F）可退出，不记录成绩。");
    }

    public static void stop(Player player) {
        var trial = ACTIVE.get(player.getUniqueId());
        if (trial != null) trial.finish("试玩已退出");
    }
    public static void stopAll() { for (var trial : List.copyOf(ACTIVE.values())) trial.finish("试玩已结束"); }

    private void tick() {
        if (stopped) return;
        if (!player.isOnline() || manager.getSession(player) != session || !player.hasPermission("cc.admin")
                || !session.getTarget().worldName().equals(player.getWorld().getName())) { finish("试玩已退出"); return; }
        if (countdown > 0) {
            if (countdown % 20 == 0) player.sendTitle(Integer.toString(countdown / 20), "交换副手键（默认F）退出试玩", 0, 21, 0);
            countdown--; return;
        }
        elapsed++;
        Location feet = player.getLocation();
        if (fallCheck.sample(geometry, feet, step, config.getHorizontalPadding(), config.getFallDistance(),
                player.isInWater() || player.isInLava(), RiptideFallCheck.deckPresent(geometry, feet, step), Bukkit.getCurrentTick())) {
            finish("试玩结束：离开木筏或坠落"); return;
        }
        courseReveal.tick(step);
        answers.addAll(math.sample(player.getLocation()));
        for (var answer : answers) {
            player.resetTitle();
            if (answer.result() != RiptideMathRun.Result.CORRECT) {
                finish(answer.result() == RiptideMathRun.Result.WRONG ? "试玩结束：解题答案错误" : "试玩结束：未从解题门通道通过"); return;
            }
            player.sendActionBar(LegacyText.component(MessageConfig.RIPTIDE_RUSH_MATH_CORRECT));
        }
        answers.clear();
        boolean waitingToDepart = departure.tick();
        if (waitingToDepart) {
            // Use the same post-challenge stop as a scored round.
        } else if (passRun.active()) {
            var sideAnswers = passRun.tick(List.of(player));
            if (sideAnswers.stream().anyMatch(a -> !a.correct())) { finish("试玩结束：侧向解题答案颜色错误"); return; }
            if (!sideAnswers.isEmpty()) player.sendActionBar(LegacyText.component(MessageConfig.RIPTIDE_RUSH_MATH_CORRECT));
            if (!passRun.active()) departure.begin();
        } else if (floor != null) {
            if (floor.tick()) {
                if (!floor.matches(player.getLocation(), geometry, step)) { finish("试玩结束：踩色站错方块"); return; }
                if (floor.advance()) paintFloor();
                else { platform.restore(); platform = null; floor = null;
                    player.getInventory().setStorageContents(new ItemStack[player.getInventory().getStorageContents().length]);
                    player.getInventory().setItemInOffHand(null); player.resetTitle(); departure.begin(); }
            }
            if (floor != null && elapsed % 5 == 0) showFloor();
        } else if (dodgeTicks > 0) {
            tickDodge();
        } else {
            budget += RiptideCoursePlanner.speedAt(config, geometry, step) / 20D;
            while (budget >= 1 && step < end) {
                RiptideRaftBlocks.move(geometry, step, step + 1, trail()); step++; budget--;
                courseReveal.tick(step);
                if (floorIndex < floors.size() && step + geometry.halfLength() >= floors.get(floorIndex).step()) {
                    var stage = floors.get(floorIndex++);
                    platform = new RiptideColorFloorPlatform(geometry, step);
                    List<Material> authored = stage.template().blueprint() == null ? List.of() : stage.template().blueprint().floorMaterials();
                    floor = new RiptideColorFloorRun(geometry.raftWidth(), geometry.raftLength(), RiptideDifficulty.floorRoundTicks(step, geometry.totalSteps()),
                            new Random(stage.contentSeed()), RiptideColorFloorRun.Theme.valueOf(stage.variant()), authored,
                            RiptideDifficulty.floorMaterials(step, geometry.totalSteps()));
                    player.resetTitle(); paintFloor(); break;
                }
                if (dodgeIndex < dodges.size() && step + geometry.halfLength() >= dodges.get(dodgeIndex).step()) {
                    beginDodge(dodges.get(dodgeIndex++)); break;
                }
                if (passRun.beginReached(step)) break;
            }
        }
        if (dodgeTicks == 0) rhythmRun.tick(step, RiptideCoursePlanner.speedAt(config, geometry, step), List.of(player));
        if (step >= geometry.totalSteps()*.8)
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,40,0,false,false,true));
        if (passRun.active() || floor != null || dodgeTicks > 0 || departure.active() || waitingToDepart) math.suspendPreview();
        if (elapsed % 10 == 0 && floor == null && dodgeTicks == 0 && !passRun.active() && !departure.active() && !waitingToDepart) {
            var gate = math.preview(player.getLocation(), RiptideCoursePlanner.speedAt(config, geometry, step),
                    config.getMathPreviewBlocks(), elapsed, false);
            if (gate != null) {
                var display = RiptideQuestionDisplay.of(gate.question(), gate.level());
                player.sendTitle(display.title(), display.subtitle(), 0, 11, 0);
            }
            else player.resetTitle();

        }
        if (departure.active() || waitingToDepart)
            player.sendActionBar(LegacyText.component(departure.active() ? MessageConfig.RIPTIDE_RUSH_DEPARTURE_ACTIONBAR : ""));
        if (!departure.active() && !waitingToDepart && step >= end && floor == null && dodgeTicks == 0 && !passRun.active()) finish("试玩完成，用时 " + String.format(Locale.ROOT, "%.2f", elapsed / 20D) + " 秒");
        else if (elapsed >= config.getTimer() * 20) finish("试玩结束：时间到");
    }

    private Material trail() {
        Material material = Material.matchMaterial(config.getTrailMaterial());
        return material != null && material.isBlock() ? material : Material.AIR;
    }
    private void paintFloor() {
        platform.paint(floor.floor());
        RiptideColorFloorInventory.show(player.getInventory(), new ItemStack(floor.target(), 64)); showFloor();
    }
    private void showFloor() {
        player.showTitle(Title.title(LegacyText.component(MessageConfig.RIPTIDE_RUSH_FLOOR_TITLE
                        .replace("%seconds%", String.format(Locale.ROOT, "%.1f", floor.remainingTicks() / 20D))), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(350), Duration.ZERO)));
        player.sendActionBar(LegacyText.component(MessageConfig.RIPTIDE_RUSH_FLOOR_ACTIONBAR
                        .replace("%round%", Integer.toString(floor.roundNumber())).replace("%rounds%", Integer.toString(floor.roundCount())))
                .replaceText(b -> b.matchLiteral("%block%").replacement(Component.translatable(floor.target().translationKey()))));
    }

    private void beginDodge(RiptideCoursePlan.Level stage) {
        dodgeRun = new RiptideDodgeRun(stage.variant(), stage.contentSeed(), geometry.raftWidth(), geometry.raftLength());
        dodgeTicks = RiptideDodgeRun.DURATION_TICKS;
        player.resetTitle();
        player.sendTitle(MessageConfig.RIPTIDE_RUSH_DODGE_TITLE, "", 0, 20, 0);
    }

    private void tickDodge() {
        if (dodgeRun == null) { dodgeTicks = 0; return; }
        for (var spawn : dodgeRun.tick()) spawnDodge(spawn);
        for (LivingEntity entity : List.copyOf(dodgeEntities)) {
            if (!entity.isValid() || entity.isDead()) { dodgeEntities.remove(entity); continue; }
            var direction = dodgeDirections.get(entity.getUniqueId());
            if (direction != null) advanceDodgeEntity(entity, direction);
            if (entity.getBoundingBox().overlaps(player.getBoundingBox())) { finish("试玩结束：被冲刺生物撞到"); return; }
        }
        dodgeTicks--;
        if (dodgeRun.complete() || dodgeTicks <= 0) { clearDodge(); departure.begin(); }
        else if (elapsed % 10 == 0) player.sendActionBar(LegacyText.component(MessageConfig.RIPTIDE_RUSH_DODGE_ACTIONBAR.replace("%seconds%", String.format(Locale.ROOT, "%.1f", dodgeTicks / 20D))));
    }

    private void spawnDodge(RiptideDodgeRun.Spawn spawn) {
        var world = player.getWorld();
        Location center = geometry.centerAt(step).clone();
        double side = Math.max(2D, geometry.raftWidth() / 2D + 3D), length = Math.max(2D, geometry.raftLength() / 2D + 3D);
        Location location = center.clone().add(0D, 1.2D, 0D);
        switch (spawn.direction()) {
            case EAST -> location.add(-side, 0D, spawn.lateral()); case WEST -> location.add(side, 0D, spawn.lateral());
            case NORTH -> location.add(spawn.lateral(), 0D, length); case SOUTH -> location.add(spawn.lateral(), 0D, -length);
            case NORTHEAST -> location.add(-side, 0D, length); case SOUTHWEST -> location.add(side, 0D, -length);
            case DOWN -> location.add(spawn.lateral(), 7D, 0D);
        }
        EntityType type = switch (spawn.mob()) { case ZOMBIE -> EntityType.ZOMBIE; case HUSK -> EntityType.HUSK; case SKELETON -> EntityType.SKELETON; case SPIDER -> EntityType.SPIDER; case CREEPER -> EntityType.CREEPER; };
        Entity raw = world.spawnEntity(location, type); if (!(raw instanceof LivingEntity entity)) { raw.remove(); return; }
        entity.setAI(false); entity.setGravity(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setCollidable(false);
        if (entity instanceof Creeper creeper) creeper.setExplosionRadius(0);
        dodgeDirections.put(entity.getUniqueId(), spawn.direction());
        dodgeSpeeds.put(entity.getUniqueId(), RiptideDodgeRun.speed(spawn.mob(), Math.min(2, spawn.tick() / RiptideDodgeRun.WAVE_TICKS)));
        faceDodgeEntity(entity, spawn.direction()); dodgeEntities.add(entity);
    }
    private static RiptideDodgeRun.Mob entityType(LivingEntity e) { return switch (e.getType()) { case HUSK -> RiptideDodgeRun.Mob.HUSK; case SKELETON -> RiptideDodgeRun.Mob.SKELETON; case SPIDER -> RiptideDodgeRun.Mob.SPIDER; case CREEPER -> RiptideDodgeRun.Mob.CREEPER; default -> RiptideDodgeRun.Mob.ZOMBIE; }; }
    private static org.bukkit.util.Vector dodgeDirection(RiptideDodgeRun.Direction d) { return switch (d) { case EAST -> new org.bukkit.util.Vector(1,0,0); case WEST -> new org.bukkit.util.Vector(-1,0,0); case NORTH -> new org.bukkit.util.Vector(0,0,-1); case SOUTH -> new org.bukkit.util.Vector(0,0,1); case NORTHEAST -> new org.bukkit.util.Vector(1,0,-1).normalize(); case SOUTHWEST -> new org.bukkit.util.Vector(-1,0,1).normalize(); case DOWN -> new org.bukkit.util.Vector(0,-1,0); }; }
    private void advanceDodgeEntity(LivingEntity entity, RiptideDodgeRun.Direction direction) {
        var movement = dodgeDirection(direction).multiply(dodgeSpeeds.getOrDefault(entity.getUniqueId(), RiptideDodgeRun.speed(entityType(entity))));
        if (movement.lengthSquared() <= 0D) return;
        var next = entity.getLocation().clone().add(movement); next.setDirection(movement);
        entity.teleport(next); entity.setRotation(next.getYaw(), next.getPitch()); entity.setVelocity(new org.bukkit.util.Vector());
    }
    private static void faceDodgeEntity(LivingEntity entity, RiptideDodgeRun.Direction direction) {
        var facing = entity.getLocation().clone(); facing.setDirection(dodgeDirection(direction));
        entity.setRotation(facing.getYaw(), facing.getPitch());
    }
    private void clearDodge() { for (var entity : List.copyOf(dodgeEntities)) if (entity.isValid()) entity.remove(); dodgeEntities.clear(); dodgeDirections.clear(); dodgeSpeeds.clear(); dodgeRun = null; dodgeTicks = 0; }
    private void finish(String reason) {
        if (stopped) return;
        stopped = true; ACTIVE.remove(player.getUniqueId()); HandlerList.unregisterAll(this);
        if (task != null) task.cancel();
        try {
            clearDodge();
            passRun.close();
            rhythmRun.close();
            courseReveal.close();
            if (platform != null) platform.restore();
            RiptideRaftBlocks.move(geometry, step, 0, trail());
        } finally {
            for (var effect : List.copyOf(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
            player.addPotionEffects(oldEffects);
            inventory.restore(player.getInventory()); player.resetTitle(); player.sendActionBar(Component.empty());
            player.setGameMode(oldMode); player.setAllowFlight(oldFlight); player.setFlying(oldFlying && oldFlight);
            if (player.isOnline()) {
                player.teleport(returnTo); Utils.sendAdminInfo(player, reason);
                if (session.getPlugin().isEnabled() && manager.getSession(player) == session) onComplete.run();
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (e.getPlayer() != player || e.getTo() == null || countdown > 0) return;
        answers.addAll(math.sample(e.getFrom())); answers.addAll(math.sample(e.getTo()));
    }
    @EventHandler(priority = EventPriority.LOWEST) public void quit(PlayerQuitEvent e) { if (e.getPlayer() == player) stop(player); }
    @EventHandler public void damage(EntityDamageEvent e) { if (e.getEntity() == player) e.setCancelled(true); }
    @EventHandler public void food(FoodLevelChangeEvent e) { if (e.getEntity() == player) e.setCancelled(true); }
    @EventHandler public void drop(PlayerDropItemEvent e) { if (e.getPlayer() == player) e.setCancelled(true); }
    @EventHandler public void interact(PlayerInteractEvent e) { if (e.getPlayer() == player) e.setCancelled(true); }
    @EventHandler public void swap(PlayerSwapHandItemsEvent e) {
        if (e.getPlayer() != player) return;
        e.setCancelled(true);
        // Restore the saved inventory after the cancelled swap event has finished processing.
        Bukkit.getScheduler().runTask(session.getPlugin(), () -> finish("试玩已退出"));
    }
    @EventHandler public void click(InventoryClickEvent e) { if (e.getWhoClicked() == player) e.setCancelled(true); }
    @EventHandler public void drag(InventoryDragEvent e) { if (e.getWhoClicked() == player) e.setCancelled(true); }
}
