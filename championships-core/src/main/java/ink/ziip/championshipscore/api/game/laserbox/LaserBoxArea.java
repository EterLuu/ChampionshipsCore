package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** One paired run. All combat, particles and cleanup are owned by this instance on the server thread. */
public final class LaserBoxArea extends BasePairedGameInstance {
    private static final double LASER_PARTICLE_SPACING = 1.0D;
    private static final int REVEAL_DURATION_TICKS = 70;
    private static final int SHIELD_HITS = 1;
    private static final Particle.DustOptions LASER_PARTICLE =
            new Particle.DustOptions(Color.fromRGB(0xE8FFFF), 0.35F);
    private final NamespacedKey itemKey;
    private final Map<UUID, Integer> protection = new HashMap<>();
    private final Map<UUID, Grenade> grenades = new HashMap<>();
    private final Map<UUID, SmokeCloud> smokeClouds = new HashMap<>();
    private final Map<Block, BlockData> smokeOriginals = new HashMap<>();
    private final Map<Block, Set<UUID>> smokeOwners = new HashMap<>();
    private final List<Strike> strikes = new ArrayList<>();
    private final Map<UUID, Item> drops = new HashMap<>();
    /** Players currently receiving LaserBox's movement speed effect. */
    private final Set<UUID> movementSpeed = new HashSet<>();
    private final Map<Integer, Item> supplies = new HashMap<>();
    private final ArrayDeque<String> supplyPool = new ArrayDeque<>();
    private LaserBoxRound round;
    private BukkitTask tickTask;
    private int timer;
    private int strikeCooldown;
    private List<Location> supplyLocations = List.of();
    private final int copyIndex;
    private LaserBoxGeometry geometry;
    private final Map<Scoreboard, Team> hiddenNameTagTeams = new IdentityHashMap<>();
    private final Map<Scoreboard, Map<String, String>> previousNameTagTeams = new IdentityHashMap<>();
    private record Grenade(Snowball entity, UUID owner, boolean right, int expires) {}
    private record SmokeCloud(UUID id, Location location, UUID owner, boolean right, int start, int expires, List<Block> cells) {}
    private record Strike(Location location, UUID owner, boolean right, int expires) {}

    public LaserBoxArea(ChampionshipsCore plugin, LaserBoxConfig config) {
        this(plugin, config, 0, true);
    }
    LaserBoxArea(ChampionshipsCore plugin, LaserBoxConfig config, int copyIndex, boolean initializeConfig) {
        super(plugin, GameTypeEnum.LaserBox, new LaserBoxHandler(plugin), config);
        if (initializeConfig) config.initializeConfiguration(plugin.getFolder());
        this.copyIndex = copyIndex;
        if (config.getAreaPos1() != null && config.getAreaPos2() != null
                && config.getRightSpawnPoint() != null && config.getLeftSpawnPoint() != null
                && config.getSpectatorSpawnPoint() != null) {
            try {
                this.geometry = new ReplicatedSpatialLayout<>(LaserBoxGeometry.from(config),
                        config.getCopyGrid(), Math.max(1, config.getCopyCount())).geometry(copyIndex);
            } catch (RuntimeException ignored) {
                // Validation during start reports incomplete map geometry to the administrator.
            }
        }
        itemKey = new NamespacedKey(plugin, "laserbox-item");
        getGameHandler().setArea(this);
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
    }
    @Override public LaserBoxConfig getGameConfig() { return (LaserBoxConfig) gameConfig; }
    @Override public int getCopyIndex() { return copyIndex; }
    @Override public LaserBoxHandler getGameHandler() { return (LaserBoxHandler) gameHandler; }
    @Override public String getWorldName() { return getGameConfig().getConfiguredWorld(); }
    @Override public int getTimer() { return timer; }
    public int getRightProgress() { return round == null ? LaserBoxRound.INITIAL_SCORE : round.right(); }
    public int getLeftProgress() { return round == null ? LaserBoxRound.INITIAL_SCORE : round.left(); }
    public int getTransfer() { return round == null ? LaserBoxRound.INITIAL_TRANSFER : round.transfer(); }
    public int getPlayerKills(UUID player) { return round == null ? 0 : round.kills(player); }
    @Override public Location getSpectatorSpawnLocation() { return geometry == null ? getGameConfig().bind(getGameConfig().getSpectatorSpawnPoint()) : geometry.spectatorSpawn(); }
    @Override public boolean notInArea(Location location) {
        if (location == null || geometry == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())) return true;
        return !location.toVector().isInAABB(geometry.boundaryMin(), geometry.boundaryMax().clone().add(new Vector(1, 1, 1)));
    }
    @Override public boolean tryStartGame(ChampionshipTeam right, ChampionshipTeam left) {
        try {
            getGameConfig().validate();
            geometry = new ReplicatedSpatialLayout<>(LaserBoxGeometry.from(getGameConfig()),
                    getGameConfig().getCopyGrid(), getGameConfig().getCopyCount()).geometry(copyIndex);
        }
        catch (RuntimeException failure) { logGame(Level.WARNING,"启动",failure.getMessage()); return false; }
        return super.tryStartGame(right, left);
    }
    @Override protected Collection<Location> getStartPreloadLocations() {
        return List.of(spawn(true), spawn(false), getSpectatorSpawnLocation());
    }
    private Location spawn(boolean right) {
        if (geometry == null) return getGameConfig().bind(right ? getGameConfig().getRightSpawnPoint() : getGameConfig().getLeftSpawnPoint());
        return right ? geometry.rightSpawn() : geometry.leftSpawn();
    }
    private boolean right(UUID id) { return rightChampionshipTeam != null && rightChampionshipTeam.getMembers().contains(id); }
    private List<Player> players() {
        return getParticipantUniqueIds().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }
    private boolean live() { return getGameStageEnum() == GameStageEnum.PROGRESS && round != null && !round.finished(); }
    boolean active(Player player) {
        return live() && !notAreaPlayer(player) && !notInArea(player.getLocation())
                && player.getGameMode() == GameMode.ADVENTURE && round.ready(player.getUniqueId());
    }
    @Override public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        startGameIntroduction(() -> {
            resetPlayerHealthFoodEffectLevelInventory();
            for (Player player : players()) spawnPlayer(player, false);
            startFinalCountdown(gameTypeEnum.toString(), gameTypeEnum.toString(), "", this::begin);
        });
    }
    private void begin() {
        if (geometry == null) {
            geometry = new ReplicatedSpatialLayout<>(LaserBoxGeometry.from(getGameConfig()),
                    getGameConfig().getCopyGrid(), getGameConfig().getCopyCount()).geometry(copyIndex);
        }
        round = new LaserBoxRound();
        hideNameTags();
        timer = getGameConfig().getTimer();
        supplyLocations = geometry.supplyPoints().stream()
                .map(vector -> vector.toLocation(Bukkit.getWorld(getWorldName()))).toList();
        for (Player player : players()) spawnPlayer(player, false);
        updateHud();
        tickTask = scheduler.runTaskTimer(plugin, this::tick, 1, 1);
    }
    private void spawnPlayer(Player player, boolean protect) {
        UUID id = player.getUniqueId();
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20); player.setFoodLevel(20); player.setFireTicks(0); player.setFallDistance(0);
        player.setVelocity(new Vector());
        player.teleport(spawn(right(id)));
        player.getInventory().setItem(0, item("laser"));
        ChampionshipTeam team = right(id) ? rightChampionshipTeam : leftChampionshipTeam;
        player.getInventory().setHelmet(team.getHelmet().clone());
        player.getInventory().setChestplate(null);
        player.getInventory().setBoots(team.getBoots().clone());
        player.setLevel(0); player.setExp(0);
        syncMovementSpeed(player);
        if (protect && round != null) {
            protection.put(id, round.tick() + 80);
            round.protect(id);
            syncChestplate(player);
            plugin.getGameManager().getSpectatorManager().resumeParticipant(player, this);
        }
    }

    /** Active players receive Speed I while holding an item and Speed II with both hands empty. */
    private void syncMovementSpeed(Player player) {
        UUID id = player.getUniqueId();
        if (!active(player)) { clearMovementSpeed(player); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean empty = (held == null || held.getType().isAir())
                && (offHand == null || offHand.getType().isAir());
        int amplifier = empty ? 1 : 0;
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (!movementSpeed.contains(id) || current == null || current.getAmplifier() != amplifier
                || current.getDuration() < 20) {
            // Remove the previous level so a weaker effect takes over immediately too.
            if (current != null && current.getAmplifier() != amplifier)
                player.removePotionEffect(PotionEffectType.SPEED);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, amplifier, true, false, false));
        }
        movementSpeed.add(id);
    }

    private void clearMovementSpeed(Player player) {
        if (movementSpeed.remove(player.getUniqueId())) player.removePotionEffect(PotionEffectType.SPEED);
    }
    ItemStack item(String type) {
        Material material = switch(type) {
            case "laser" -> Material.COPPER_HOE;
            case "grenade" -> Material.SLIME_BALL;
            case "strike" -> Material.BLAZE_ROD;
            case "reveal" -> Material.ENDER_EYE;
            default -> Material.PRISMARINE_SHARD;
        };
        ItemStack result = new ItemStack(material);
        var meta = result.getItemMeta();
        String name = switch(type) {
            case "laser" -> "激光枪 · 光束发射器";
            case "grenade" -> "烟幕弹 · 视线封锁";
            case "strike" -> "定点爆破 · 范围打击";
            case "reveal" -> "追踪脉冲 · 敌方显形";
            default -> "护盾 · 1次拦截";
        };
        String usage = type.equals("shield") ? "拾取后立即生效" : "右键使用";
        String function = switch (type) {
            case "laser" -> "沿准星方向发射激光";
            case "grenade" -> "落点生成持续烟幕，遮挡激光";
            case "strike" -> "标记最近两名敌人的位置并造成范围打击";
            case "reveal" -> "短暂显示敌方位置";
            default -> "阻挡 1 次激光";
        };
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(usage, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(function, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
        result.setItemMeta(meta); return result;
    }
    String type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }
    void use(Player player) {
        if (!active(player)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        String type = type(held);
        if (type == null) return;
        UUID id = player.getUniqueId();
        if (type.equals("laser")) {
            if (round.shoot(id)) fire(player.getEyeLocation(), player.getEyeLocation().getDirection(), id, right(id), true);
            return;
        }
        if (type.equals("strike") && strikeCooldown > round.tick()) return;
        if (!Set.of("grenade","strike","reveal").contains(type)) return;
        held.setAmount(held.getAmount() - 1);
        switch(type) {
            case "grenade" -> {
                Snowball projectile = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(1.1));
                grenades.put(projectile.getUniqueId(), new Grenade(projectile,id,right(id),round.tick()+400));
            }
            case "strike" -> {
                Location origin = player.getLocation();
                List<Player> targets = players().stream()
                        .filter(enemy -> active(enemy) && right(enemy.getUniqueId()) != right(id))
                        .sorted(Comparator.comparingDouble(enemy -> enemy.getLocation().distanceSquared(origin)))
                        .limit(2)
                        .toList();
                for (Player enemy : targets) {
                    strikes.add(new Strike(enemy.getLocation().clone(), id, right(id), round.tick()+15));
                    enemy.playSound(enemy.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
                }
                strikeCooldown = round.tick()+15;
            }
            case "reveal" -> {
                for (Player enemy : players()) if (active(enemy) && right(enemy.getUniqueId()) != right(id))
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                            REVEAL_DURATION_TICKS, 0, false, false));
            }
        }
    }
    private void fire(Location eye, Vector direction, UUID owner, boolean onRight, boolean intercept) {
        if (!live() || direction.lengthSquared() < 0.0001) return;
        direction = direction.clone().normalize();
        World world = eye.getWorld();
        RayTraceResult block = world.rayTraceBlocks(eye,direction,50,FluidCollisionMode.NEVER,true);
        double distance = block == null ? 50 : block.getHitPosition().distance(eye.toVector());
        RayTraceResult result = world.rayTraceEntities(eye,direction,distance,0.15,
                e -> e instanceof Player p && active(p) && !p.getUniqueId().equals(owner)
                        || intercept && grenades.containsKey(e.getUniqueId()));
        if (result != null) distance = result.getHitPosition().distance(eye.toVector());
        Set<UUID> viewerIds = new LinkedHashSet<>(getParticipantUniqueIds());
        viewerIds.addAll(spectators);
        List<Player> viewers = viewerIds.stream().map(Bukkit::getPlayer).filter(Objects::nonNull)
                .filter(player -> player.getWorld().equals(world)).toList();
        for (double d = 0; d <= distance; d += LASER_PARTICLE_SPACING)
            world.spawnParticle(Particle.DUST, viewers, null,
                    eye.getX() + direction.getX() * d,
                    eye.getY() + direction.getY() * d,
                    eye.getZ() + direction.getZ() * d,
                    1, 0, 0, 0, 0, LASER_PARTICLE, false);
        world.playSound(eye,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,1,2);
        if (result == null) return;
        Entity hit = result.getHitEntity();
        if (hit instanceof Player victim && right(victim.getUniqueId()) != onRight) hit(victim,owner,onRight,false,false);
        else if (hit != null && intercept) {
            Grenade grenade = grenades.remove(hit.getUniqueId());
            if (grenade == null) return;
            Location point = hit.getLocation(); hit.remove();
            players().stream().filter(p -> active(p) && right(p.getUniqueId()) != onRight)
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(point)))
                    .ifPresent(p -> fire(point,p.getEyeLocation().toVector().subtract(point.toVector()),owner,onRight,false));
        }
    }
    private void hit(Player victim, UUID owner, boolean onRight, boolean gas, boolean strike) {
        if (!active(victim) || right(victim.getUniqueId()) == onRight) return;
        UUID id = victim.getUniqueId();
        if (round.protectedFromHits(id)) return;
        if (gas && inSmoke(victim)) return;
        if (strike && round.shield(id) > 0) {
            round.destroyShield(id); syncChestplate(victim); victim.setLevel(0); return;
        }
        UUID killer = owner != null && getParticipantUniqueIds().contains(owner) ? owner : null;
        if (!round.hit(id,right(id),gas,killer)) {
            syncChestplate(victim); victim.setLevel(round.shield(id));
            victim.playSound(victim.getLocation(),Sound.ITEM_SHIELD_BLOCK,1,1); return;
        }
        if (killer != null) {
            ChampionshipTeam killerTeam = right(killer) ? rightChampionshipTeam : leftChampionshipTeam;
            LaserBoxKillFeedback.burst(victim, Utils.hex2rgb(killerTeam.getColorCode()), itemKey);
        }
        for (ItemStack stack : victim.getInventory().getContents()) {
            String kind = type(stack);
            if (kind != null && !kind.equals("laser")) trackDrop(victim.getWorld().dropItem(victim.getLocation(), stack.clone()));
        }
        victim.getInventory().clear(); victim.setLevel(0);
        victim.removePotionEffect(PotionEffectType.GLOWING);
        victim.setGameMode(GameMode.SPECTATOR);
        clearMovementSpeed(victim);
        if (killer != null) {
            addPlayerPoints(killer,getGameConfig().getKillPoints());
            Player player = Bukkit.getPlayer(killer);
            LaserBoxRound feedbackRound = round;
            if (player != null) LaserBoxKillFeedback.play(player, feedbackRound.kills(killer), scheduler, plugin,
                    () -> round == feedbackRound && !notAreaPlayer(player));
        }
        sendMessageToAllGamePlayers("&e" + (owner == null ? "场地" : plugin.getPlayerManager().getPlayerName(owner))
                + " &7击中 " + Utils.formatPlayerName(victim));
        if (round.right() == 0 || round.left() == 0) endGame();
    }
    void landed(Entity projectile) {
        Grenade grenade = grenades.remove(projectile.getUniqueId());
        if (grenade == null) return;
        Location point = projectile.getLocation().clone(); projectile.remove();
        if (live() && !notInArea(point)) createSmoke(point, grenade.owner(), grenade.right());
    }

    private void createSmoke(Location center, UUID owner, boolean right) {
        List<Block> cells = new ArrayList<>();
        Block origin = center.getBlock();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        queue.add(origin); seen.add(origin);
        int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty() && cells.size() < 180) {
            Block block = queue.remove();
            int dx = block.getX() - origin.getX(), dy = block.getY() - origin.getY(), dz = block.getZ() - origin.getZ();
            if (dx * dx / 9D + dy * dy / 4D + dz * dz / 9D > 1.0D) continue;
            if (!block.isPassable() || block.isLiquid()) continue;
            cells.add(block);
            for (int[] direction : directions) {
                Block next = block.getRelative(direction[0], direction[1], direction[2]);
                if (seen.add(next)) queue.add(next);
            }
        }
        UUID id = UUID.randomUUID();
        smokeClouds.put(id, new SmokeCloud(id, center.clone(), owner, right, round.tick(), round.tick() + 400, cells));
    }

    private boolean inSmoke(Player player) {
        Location location = player.getLocation();
        for (Block block : smokeOwners.keySet()) {
            if (block.getWorld().equals(location.getWorld()) && block.getLocation().distanceSquared(location) <= 6.25D)
                return true;
        }
        return false;
    }

    private void tickSmoke(int tick) {
        for (SmokeCloud cloud : List.copyOf(smokeClouds.values())) {
            int age = tick - cloud.start();
            if (age >= cloud.expires() - cloud.start()) { removeSmoke(cloud); continue; }
            int count = Math.min(cloud.cells().size(), Math.max(1, age * 3));
            for (int i = 0; i < count; i++) {
                Block block = cloud.cells().get(i);
                if ((!block.isPassable() && !smokeOwners.containsKey(block)) || block.isLiquid()) continue;
                smokeOwners.computeIfAbsent(block, ignored -> {
                    smokeOriginals.put(block, block.getBlockData().clone());
                    block.setType(Material.POWDER_SNOW, false);
                    return new HashSet<>();
                }).add(cloud.id());
            }
            if (tick % 5 == 0) cloud.location().getWorld().spawnParticle(Particle.SNOWFLAKE,
                    cloud.location().clone().add(0, 1, 0), 12, 1.5, 1, 1.5, .01);
        }
    }

    private void removeSmoke(SmokeCloud cloud) {
        smokeClouds.remove(cloud.id());
        for (Block block : cloud.cells()) {
            Set<UUID> owners = smokeOwners.get(block);
            if (owners == null) continue;
            owners.remove(cloud.id());
            if (!owners.isEmpty()) continue;
            if (block.getType() == Material.POWDER_SNOW) {
                BlockData original = smokeOriginals.remove(block);
                if (original != null) block.setBlockData(original, false);
            } else smokeOriginals.remove(block);
            smokeOwners.remove(block);
        }
    }

    private void clearSmoke() {
        for (SmokeCloud cloud : List.copyOf(smokeClouds.values())) removeSmoke(cloud);
        smokeClouds.clear(); smokeOwners.clear(); smokeOriginals.clear();
    }
    boolean owns(Entity entity) {
        return grenades.containsKey(entity.getUniqueId()) || drops.containsKey(entity.getUniqueId())
                || LaserBoxKillFeedback.isKillFirework(entity, itemKey);
    }
    private void trackDrop(Item item) {
        item.setGlowing(true); item.setInvulnerable(true); item.setPickupDelay(0);
        drops.put(item.getUniqueId(),item);
    }
    void pickup(Player player, Item drop) {
        if (!active(player) || !drops.containsKey(drop.getUniqueId())) return;
        String type = type(drop.getItemStack());
        if ("shield".equals(type)) {
            if (round.shield(player.getUniqueId()) > 0) return;
            round.shield(player.getUniqueId(), SHIELD_HITS); player.setLevel(SHIELD_HITS);
        } else if (!player.getInventory().addItem(drop.getItemStack().clone()).isEmpty()) return;
        drops.remove(drop.getUniqueId()); supplies.values().removeIf(i -> i.getUniqueId().equals(drop.getUniqueId()));
        drop.remove(); player.playSound(player.getLocation(),Sound.ENTITY_ITEM_PICKUP,1,1);
        syncChestplate(player);
        player.sendActionBar(itemHint(type));
    }

    private void syncChestplate(Player player) {
        UUID id = player.getUniqueId();
        Material material = round != null && round.protectedFromHits(id) ? Material.DIAMOND_CHESTPLATE
                : round != null && round.shield(id) > 0 ? Material.IRON_CHESTPLATE : null;
        ItemStack chestplate = player.getInventory().getChestplate();
        if (material != null) {
            if (chestplate == null || chestplate.getType() != material)
                player.getInventory().setChestplate(new ItemStack(material));
        } else if (chestplate != null && (chestplate.getType() == Material.IRON_CHESTPLATE
                || chestplate.getType() == Material.DIAMOND_CHESTPLATE)) {
            player.getInventory().setChestplate(null);
        }
    }
    private Component itemHint(String type) {
        String usage = type.equals("shield") ? "立即触发" : "右键使用";
        String function = switch (type) {
            case "grenade" -> "生成烟幕，遮挡激光";
            case "strike" -> "轰击最近两名敌人的当前位置";
            case "reveal" -> "短暂显示敌方位置";
            default -> "阻挡 1 次激光";
        };
        String message = "shield".equals(type)
                ? "护盾 · " + function
                : "获得 " + itemName(type) + "  ·  " + usage + "  ·  " + function;
        return Component.text(message,
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
    private String itemName(String type) {
        return switch (type) {
            case "grenade" -> "烟幕弹";
            case "strike" -> "定点爆破";
            case "reveal" -> "追踪脉冲";
            default -> "护盾充能";
        };
    }
    private void tick() {
        if (!live()) return;
        round.advance();
        if (round.tick() % 5 == 0) enforceNameTagsHidden();
        for (Player player : players()) {
            UUID id = player.getUniqueId();
            if (round.respawn(id)) spawnPlayer(player,true);
            else if (round.respawning(id) && round.tick()%20 == 0)
                player.sendTitle("§c被击中", "§e"+(round.respawnRemaining(id)+19)/20+" 秒后重生",0,25,0);
            if (protection.containsKey(id) && protection.get(id) <= round.tick()) protection.remove(id);
            syncChestplate(player);
            syncMovementSpeed(player);
        }
        for (Grenade g : List.copyOf(grenades.values())) if (g.expires() <= round.tick() || !g.entity().isValid()) {
            grenades.remove(g.entity().getUniqueId()); g.entity().remove();
        }
        for (Strike strike : List.copyOf(strikes)) {
            strike.location().getWorld().spawnParticle(Particle.ELECTRIC_SPARK, strike.location(), 8, 1, 0.1, 1, 0);
            if (strike.expires() > round.tick()) continue;
            strikes.remove(strike);
            for (Player player : players()) if (player.getWorld().equals(strike.location().getWorld())
                    && player.getLocation().distanceSquared(strike.location()) <= 2.2*2.2)
                hit(player,strike.owner(),strike.right(),false,true);
            if (!live()) return;
        }
        tickSmoke(round.tick());
        drops.values().removeIf(item -> !item.isValid()); supplies.values().removeIf(item -> !item.isValid());
        if (round.tick()%400 == 0) {
            List<Integer> free = new ArrayList<>();
            for (int i=0;i<supplyLocations.size();i++) if (!supplies.containsKey(i)) free.add(i);
            if (!free.isEmpty()) {
                var random=ThreadLocalRandom.current(); int slot=free.get(random.nextInt(free.size()));
                if (supplyPool.isEmpty()) {
                    List<String> kinds = new ArrayList<>(List.of("shield", "grenade", "strike", "reveal"));
                    Collections.shuffle(kinds, random);
                    supplyPool.addAll(kinds);
                }
                String kind=supplyPool.removeFirst();
                Location location=supplyLocations.get(slot);
                Item item=location.getWorld().dropItem(location,item(kind)); item.setVelocity(new Vector()); item.setGravity(false);
                trackDrop(item); supplies.put(slot,item);
            }
        }
        if (round.tick()%20 == 0) {
            timer = Math.max(0,getGameConfig().getTimer()-round.tick()/20);
            updateHud();
            boolean rightOnline=players().stream().anyMatch(p -> right(p.getUniqueId()));
            boolean leftOnline=players().stream().anyMatch(p -> !right(p.getUniqueId()));
            if (!rightOnline || !leftOnline) { if (rightOnline!=leftOnline) round.forfeit(!rightOnline); endGame(); }
            else if (timer == 0) endGame();
        }
    }
    private void updateHud() {
        String title = rightChampionshipTeam.getColoredName()+" &f"+getRightProgress()
                +" &7 : &f"+getLeftProgress()+" "+leftChampionshipTeam.getColoredName()
                +" &8| &e"+String.format("%02d:%02d",timer/60,timer%60);
        updateGameTimerBossBar(title,timer,getGameConfig().getTimer());
    }
    @Override public void endGame() {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.WAITING || stage == GameStageEnum.END) return;
        // Stop commands can arrive before the live round exists. Finish the lifecycle here so
        // a preparation/countdown task cannot leave the instance half-started.
        if (stage != GameStageEnum.PROGRESS || round == null) {
            cleanup();
            setGameStageEnum(GameStageEnum.END);
            beginPostGameSettlement();
            completePostGame(false);
            return;
        }
        if (!round.finish()) return;
        cleanup();
        if (isSettlementAllowed()) {
            ChampionshipTeam winner=round.right()>round.left()?rightChampionshipTeam:round.left()>round.right()?leftChampionshipTeam:null;
            if (winner != null) addPlayerPointsToAllTeamMembers(winner,getGameConfig().getWinPoints());
            else { addPlayerPointsToAllTeamMembers(rightChampionshipTeam,getGameConfig().getDrawPoints());
                addPlayerPointsToAllTeamMembers(leftChampionshipTeam,getGameConfig().getDrawPoints()); }
            addPlayerPointsToDatabase();
            sendMessageToAllGamePlayers(winner==null?"&eLaserBox 平局":winner.getColoredName()+" &e赢得 LaserBox！");
        }
        announceGameEnd("激光方盒结束",getRightProgress()+" : "+getLeftProgress());
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        publishGameEndEvent(new TeamGameEndEvent(rightChampionshipTeam,leftChampionshipTeam,this));
        finishPostGameAfterEndEvent();
    }
    private void cleanup() {
        if (tickTask != null) tickTask.cancel(); tickTask=null;
        grenades.values().forEach(g -> g.entity().remove()); grenades.clear(); clearSmoke(); strikes.clear();
        drops.values().forEach(Entity::remove); drops.clear(); supplies.clear(); supplyPool.clear(); protection.clear(); strikeCooldown=0;
        for (Player player : players()) {
            clearMovementSpeed(player);
            ItemStack chestplate = player.getInventory().getChestplate();
            if (chestplate != null && (chestplate.getType() == Material.IRON_CHESTPLATE
                    || chestplate.getType() == Material.DIAMOND_CHESTPLATE))
                player.getInventory().setChestplate(null);
        }
        movementSpeed.clear();
        restoreNameTags();
    }
    private void hideNameTags() {
        restoreNameTags();
        List<Player> participants = players();
        if (participants.isEmpty()) return;
        String teamId = "lbhide" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        // Scoreboard visibility is viewer-local. Some plugins keep per-player scoreboards, so
        // applying the hidden team only to the main scoreboard leaves those viewers unchanged.
        for (Player viewer : participants) {
            Scoreboard scoreboard = viewer.getScoreboard();
            ensureNameTagsHidden(scoreboard, participants, teamId);
        }
    }
    /** Reassert the hidden team because scoreboard plugins may reassign entries after a tick. */
    private void enforceNameTagsHidden() {
        if (hiddenNameTagTeams.isEmpty()) return;
        String teamId = "lbhide" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        List<Player> participants = players();
        for (Player viewer : participants) ensureNameTagsHidden(viewer.getScoreboard(), participants, teamId);
    }
    private void ensureNameTagsHidden(Scoreboard scoreboard, List<Player> participants, String teamId) {
        Team hidden = hiddenNameTagTeams.get(scoreboard);
        if (hidden == null) {
            hidden = scoreboard.registerNewTeam(teamId);
            hiddenNameTagTeams.put(scoreboard, hidden);
            previousNameTagTeams.put(scoreboard, new LinkedHashMap<>());
        }
        hidden.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        Map<String, String> previous = previousNameTagTeams.get(scoreboard);
        for (Player player : participants) {
            String entry = player.getName();
            Team current = scoreboard.getEntryTeam(entry);
            if (current == hidden) continue;
            if (!previous.containsKey(entry)) previous.put(entry, current == null ? null : current.getName());
            if (current != null) current.removeEntry(entry);
            hidden.addEntry(entry);
        }
    }
    private void restoreNameTags() {
        for (Map.Entry<Scoreboard, Team> hiddenEntry : hiddenNameTagTeams.entrySet()) {
            Scoreboard scoreboard = hiddenEntry.getKey();
            Team hidden = hiddenEntry.getValue();
            Map<String, String> previous = previousNameTagTeams.getOrDefault(scoreboard, Map.of());
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                if (scoreboard.getEntryTeam(entry.getKey()) != hidden) continue;
                hidden.removeEntry(entry.getKey());
                if (entry.getValue() == null) continue;
                Team original = scoreboard.getTeam(entry.getValue());
                if (original != null) original.addEntry(entry.getKey());
            }
            try { hidden.unregister(); } catch (IllegalStateException ignored) { }
        }
        hiddenNameTagTeams.clear();
        previousNameTagTeams.clear();
    }
    @Override public void resetArea() { cleanup(); round=null; timer=0; }
    @Override public void dispose() { cleanup(); super.dispose(); }
    @Override public void handlePlayerQuit(PlayerQuitEvent event) {
        if (notAreaPlayer(event.getPlayer()) || round==null) return;
        clearMovementSpeed(event.getPlayer());
        UUID id=event.getPlayer().getUniqueId(); round.disconnect(id); protection.remove(id);
        grenades.values().removeIf(g -> { if (!g.owner().equals(id)) return false; g.entity().remove(); return true; });
        for (SmokeCloud cloud : List.copyOf(smokeClouds.values())) if (cloud.owner().equals(id)) removeSmoke(cloud);
        strikes.removeIf(s -> s.owner().equals(id));
    }
    @Override public void handlePlayerJoin(PlayerJoinEvent event) {
        Player p=event.getPlayer(); if (notAreaPlayer(p)) return;
        clearMovementSpeed(p);
        if (isIntroductionPhase()) { p.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation())); return; }
        if (live()) { round.disconnect(p.getUniqueId()); p.getInventory().clear(); p.setGameMode(GameMode.SPECTATOR); p.teleport(getSpectatorSpawnLocation()); }
        else if (getGameStageEnum()==GameStageEnum.COUNTDOWN || getGameStageEnum()==GameStageEnum.PREPARATION) spawnPlayer(p,false);
    }
    @Override public void handlePlayerDeath(PlayerDeathEvent event) {
        if (notAreaPlayer(event.getEntity())) return;
        event.getDrops().clear(); event.setDroppedExp(0); event.deathMessage(null);
        Player player=event.getEntity();
        if (live()) round.disconnect(player.getUniqueId());
        clearMovementSpeed(player);
        player.getInventory().setChestplate(null);
        player.setRespawnLocation(getSpectatorSpawnLocation(),true);
    }
    void boundary(Player player) {
        if (!live() || notAreaPlayer(player) || !notInArea(player.getLocation())) return;
        if (round.respawning(player.getUniqueId())) { player.teleport(getSpectatorSpawnLocation()); return; }
        hit(player,null,!right(player.getUniqueId()),true,false);
        player.teleport(spawn(right(player.getUniqueId())));
    }
}
