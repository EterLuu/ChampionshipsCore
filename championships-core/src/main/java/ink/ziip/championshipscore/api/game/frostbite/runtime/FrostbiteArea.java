package ink.ziip.championshipscore.api.game.frostbite.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.frostbite.config.FrostbiteConfig;
import ink.ziip.championshipscore.api.game.frostbite.mechanics.FrostbiteFrozenEquipment;
import ink.ziip.championshipscore.api.game.frostbite.mechanics.FrostbiteSupplyHints;
import ink.ziip.championshipscore.api.game.frostbite.model.FrostbiteItem;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.*;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.logging.Level;

/** Four spatially isolated solo arenas under one atomic CC round and settlement owner. */
public final class FrostbiteArea extends BaseMultiTeamGameInstance {
    private final Map<Integer, Integer> pickupArenaOffsets = new HashMap<>();
    private static final int AVALANCHE_ZONE_TICKS = 400;
    private static final int AVALANCHE_SLOWNESS_TICKS = 10;

    private record Shot(UUID owner, FrostbiteItem item, int arena, int expires) {}

    private record AvalancheCloud(UUID owner, int arena, AreaEffectCloud entity) {}

    private static final class Prop {
        final UUID owner;
        final FrostbiteItem type;
        final int arena;
        final Location location;
        final ArmorStand entity;
        final BlockDisplay display;
        final int expires;

        Prop(
                UUID owner,
                FrostbiteItem type,
                int arena,
                Location location,
                ArmorStand entity,
                BlockDisplay display,
                int expires) {
            this.owner = owner;
            this.type = type;
            this.arena = arena;
            this.location = location;
            this.entity = entity;
            this.display = display;
            this.expires = expires;
        }
    }

    private record Zone(
            UUID owner,
            FrostbiteItem type,
            int arena,
            Location center,
            int start,
            int until,
            List<BlockDisplay> displays) {}

    private final Map<UUID, Shot> shots = new HashMap<>();
    private final List<Prop> props = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, AvalancheCloud> avalancheClouds = new HashMap<>();
    private final Map<UUID, Location> freezeLocations = new HashMap<>();
    private final Map<UUID, Integer> meleeCooldown = new HashMap<>();
    private final Map<UUID, Integer> useCooldown = new HashMap<>();
    private final Map<UUID, Integer> invisibleUntil = new HashMap<>();

    /**
     * Viewer UUID -> players in the same arena currently highlighted after that viewer respawned.
     */
    private final Map<UUID, Set<UUID>> respawnGlowing = new HashMap<>();

    private final Map<UUID, Integer> respawnGlowGeneration = new HashMap<>();
    private final FrostbiteSupplyHints supplyHints = new FrostbiteSupplyHints();
    private final Map<UUID, Boolean> collisionBefore = new HashMap<>();
    private final List<ItemDisplay> pickupDisplays = new ArrayList<>();
    private final Map<UUID, Integer> fullPickupHintAt = new HashMap<>();
    private List<Location> pickups = List.of();
    private int[] pickupReady = new int[0];
    private final Random random = new Random();
    private final NamespacedKey itemKey;
    private FrostbiteRound round;
    private BukkitTask tickTask;
    private BukkitTask timerTask;
    private int tick;
    private int timer;
    private boolean ending;
    private boolean ownTeleport;

    public FrostbiteArea(
            ChampionshipsCore plugin, FrostbiteConfig config, boolean firstTime, String name) {
        super(plugin, GameTypeEnum.FrostbiteFrenzy, new FrostbiteHandler(plugin), config);
        itemKey = new NamespacedKey(plugin, "frostbite_item");
        getGameHandler().setArea(this);
        config.setAreaName(name);
        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    public void preloadMap() {
        loadPublishedMapOrDraft(World.Environment.NORMAL);
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams) {
        if (!validRoster(teams)) return false;
        return super.tryStartGame(teams);
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams, List<UUID> players) {
        if (!validRoster(teams)
                || players == null
                || !new HashSet<>(players)
                        .equals(
                                teams.stream()
                                        .flatMap(t -> t.getMembers().stream())
                                        .collect(java.util.stream.Collectors.toSet())))
            return false;
        return super.tryStartGame(teams, players);
    }

    private boolean validRoster(List<ChampionshipTeam> teams) {
        if (teams == null
                || teams.size() < 2
                || teams.size() > 16
                || teams.stream()
                        .anyMatch(
                                t ->
                                        t == null
                                                || t.getMembers().isEmpty()
                                                || t.getMembers().size() > 4)) {
            logGame(Level.WARNING, "参赛", "霜冻决斗需要2–16支队伍，每队需要1–4人");
            return false;
        }
        return true;
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        for (int a : getSelectedArenaIndices(4)) {
            locations.addAll(getGameConfig().spawns(a));
            for (String p : getGameConfig().getItemPoints())
                locations.add(getGameConfig().point(p, a));
        }
        return locations;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        startGameIntroduction(this::prepareRound);
    }

    private void prepareRound() {
        try {
            getGameConfig().validate();
            int index =
                    isEventRun()
                            ? plugin.getScheduleManager()
                                            .getFrostbiteScheduleManager()
                                            .getSubRound()
                                    - 1
                            : 0;
            var ordered =
                    gameTeams.stream()
                            .sorted(Comparator.comparingInt(ChampionshipTeam::getId))
                            .toList();
            round =
                    new FrostbiteRound(
                            ordered.stream()
                                    .map(t -> t.getMembers().stream().sorted().toList())
                                    .toList(),
                            index,
                            getSelectedArenaIndices(4));
            tick = 0;
            ending = false;
            resetPlayerHealthFoodEffectLevelInventory();
            changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
            for (UUID id : round.seats().keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    // Preserve roster-based arena assignments while absent members sit out this
                    // round.
                    round.leave(id);
                    continue;
                }
                collisionBefore.put(id, player.isCollidable());
                player.setCollidable(false);
                spawn(player, false);
                player.sendMessage(
                        LegacyText.component(
                                MessageConfig.FROSTBITE_ARENA
                                        .replace(
                                                "%arena%",
                                                Integer.toString(round.seats().get(id).arena() + 1))
                                        .replace(
                                                "%freeze%",
                                                Integer.toString(
                                                        getGameConfig().getFreezeSeconds()))));
            }
            createPickups();
            announceGamePreparation(
                    MessageConfig.FROSTBITE_START_PREPARATION,
                    MessageConfig.FROSTBITE_START_PREPARATION_TITLE,
                    MessageConfig.FROSTBITE_START_PREPARATION_SUBTITLE);
            startFinalCountdown(
                    MessageConfig.GAME_FROSTBITE,
                    MessageConfig.FROSTBITE_GAME_START_TITLE,
                    MessageConfig.FROSTBITE_GAME_START_SUBTITLE,
                    this::begin);
        } catch (RuntimeException failure) {
            logGame(Level.SEVERE, "准备", failure.getMessage());
            abortAndReset();
        }
    }

    private void begin() {
        round.seats().keySet().forEach(id -> round.heat(id, getGameConfig().getHeatSeconds() * 20));
        tickTask = scheduler.runTaskTimer(plugin, this::tickRound, 1, 1);
        timerTask =
                startRemainingTimer(
                        getGameConfig().getTimer(),
                        remaining -> {
                            timer = remaining;
                            updateGameTimerBossBar(
                                    MessageConfig.FROSTBITE_BOSS_BAR.replace(
                                            "%time%",
                                            remaining / 60
                                                    + ":"
                                                    + String.format(
                                                            java.util.Locale.ROOT,
                                                            "%02d",
                                                            remaining % 60)),
                                    remaining,
                                    getGameConfig().getTimer());
                        },
                        this::endGame);
    }

    public boolean participant(Player player) {
        return !notAreaPlayer(player) && getGameStageEnum() != GameStageEnum.WAITING;
    }

    public boolean playing(Player player) {
        return getGameStageEnum() == GameStageEnum.PROGRESS
                && round != null
                && round.active(player.getUniqueId())
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean acting(Player player) {
        return playing(player) && round.canAct(player.getUniqueId());
    }

    private int arena(UUID id) {
        return round.seats().get(id).arena();
    }

    private List<Player> opponents(UUID id, Location point, double radius) {
        return round.seats().keySet().stream()
                .filter(other -> round.enemies(id, other))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(this::playing)
                .filter(
                        p ->
                                p.getWorld().equals(point.getWorld())
                                        && p.getLocation().distanceSquared(point)
                                                <= radius * radius)
                .toList();
    }

    private void tickRound() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null) return;
        tick++;
        for (UUID id : round.expired(tick)) {
            if (!isGameplayParticipant(id)) continue;
            UUID killer = round.die(id);
            awardKill(killer, id);
            respawn(id);
        }
        for (UUID id : round.seats().keySet()) {
            if (!round.active(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                awardKill(round.leave(id), id);
                cleanupPlayer(id);
                continue;
            }
            if (!isGameplayParticipant(p)) continue;
            if (!getGameConfig().contains(p.getLocation(), arena(id))
                    || p.getLocation().getY() < getGameConfig().getArenaMin().getY() + 1
                    || p.isInWater()
                    || p.isInLava()) {
                awardKill(round.die(id), id);
                respawn(id);
                continue;
            }
            if (invisibleUntil.getOrDefault(id, Integer.MAX_VALUE) <= tick) reveal(p);
            updateHeatState(p);
            if (tick % 5 == 0) {
                var frozen = round.freezeState(id);
                if (frozen != null) {
                    p.setVelocity(new Vector());
                    p.setFreezeTicks(130);
                    p.sendActionBar(
                            LegacyText.component(
                                    MessageConfig.FROSTBITE_FROZEN_ACTIONBAR
                                            .replace(
                                                    "%seconds%",
                                                    String.format(
                                                            java.util.Locale.ROOT,
                                                            "%.1f",
                                                            (frozen.until() - tick) / 20D))
                                            .replace(
                                                    "%campfire%",
                                                    camp(id) != null ? " &6营火将自动返回" : "")));
                } else {
                    collect(p);
                    String hint = supplyHints.current(id, tick);
                    p.sendActionBar(
                            LegacyText.component(
                                    hint != null
                                            ? hint
                                            : MessageConfig.FROSTBITE_STATUS_ACTIONBAR
                                                    .replace(
                                                            "%kills%",
                                                            Integer.toString(round.kills(id)))
                                                    .replace(
                                                            "%arena%",
                                                            Integer.toString(arena(id) + 1))
                                                    .replace(
                                                            "%heated%",
                                                            round.heated(id, tick)
                                                                    ? " &6保温中"
                                                                    : "")));
                }
            }
        }
        for (var it = shots.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entry.getValue().expires <= tick) {
                if (entity != null) entity.remove();
                it.remove();
            }
        }
        for (var it = avalancheClouds.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!entry.getValue().entity().isValid()) it.remove();
        }
        tickProps();
        for (Zone zone : List.copyOf(zones)) {
            if (zone.until <= tick) {
                removeZone(zone);
                continue;
            }
            if (tick % 5 != 0) continue;
            if (zone.type == FrostbiteItem.FROST_TRAP) {
                for (Player p : opponents(zone.owner, zone.center, 2.5)) freeze(zone.owner, p);
            } else if (zone.type == FrostbiteItem.BEACON) {
                Player owner = Bukkit.getPlayer(zone.owner);
                if (owner != null
                        && acting(owner)
                        && owner.getWorld().equals(zone.center.getWorld())
                        && owner.getLocation().distanceSquared(zone.center) <= 16)
                    round.heat(zone.owner, tick + 30);
            }
            if (tick % 10 == 0)
                zone.center
                        .getWorld()
                        .spawnParticle(
                                zone.type == FrostbiteItem.BEACON
                                        ? Particle.FLAME
                                        : Particle.SNOWFLAKE,
                                zone.center,
                                10,
                                1.5,
                                .2,
                                1.5,
                                0);
        }
        if (tick % 2 == 0) animatePickups();
        if (tick % 20 == 0)
            for (int i = 0; i < pickups.size(); i++)
                if (pickupReady[i] > 0 && pickupReady[i] <= tick) {
                    pickupDisplays.get(i).setItemStack(new ItemStack(Material.GOLD_BLOCK));
                    pickupReady[i] = 0;
                }
        if (round.seats().keySet().stream().noneMatch(round::active)) endGame();
    }

    private void awardKill(UUID killer, UUID victim) {
        if (killer == null) return;
        addPlayerPoints(killer, getGameConfig().getPointsPerKill());
        Player victimPlayer = Bukkit.getPlayer(victim);
        String victimName =
                victimPlayer == null
                        ? CoreMessages.formatPlayerName(victim)
                        : CoreMessages.formatPlayerName(victimPlayer);
        Player killerPlayer = Bukkit.getPlayer(killer);
        String killerName =
                killerPlayer == null
                        ? CoreMessages.formatPlayerName(killer)
                        : CoreMessages.formatPlayerName(killerPlayer);
        sendMessageToAllGamePlayers(
                MessageConfig.FROSTBITE_KILL
                        .replace("%killer%", killerName)
                        .replace("%victim%", victimName));
        if (killerPlayer != null)
            killerPlayer.playSound(
                    killerPlayer.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1F, 1.2F);
        if (victimPlayer != null)
            victimPlayer.playSound(victimPlayer.getLocation(), Sound.BLOCK_GLASS_BREAK, 1F, .6F);
    }

    private void respawn(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        clearCombat(p);
        removeOwnedProps(id);
        spawn(p, true);
        showRespawnGlow(id);
        p.sendTitle(
                LegacyText.translateColorCodes(MessageConfig.FROSTBITE_RESPAWN_TITLE),
                LegacyText.translateColorCodes(MessageConfig.FROSTBITE_RESPAWN_SUBTITLE),
                0,
                20,
                5);
    }

    /**
     * Shows the respawned player the other active players in their isolated arena for three
     * seconds.
     */
    private void showRespawnGlow(UUID viewerId) {
        clearRespawnGlow(viewerId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || round == null || !round.active(viewerId)) return;
        Integer generation = respawnGlowGeneration.merge(viewerId, 1, Integer::sum);
        int viewerArena = arena(viewerId);
        Set<UUID> targets = new HashSet<>();
        for (UUID targetId : round.seats().keySet()) {
            if (targetId.equals(viewerId)
                    || !round.active(targetId)
                    || arena(targetId) != viewerArena) continue;
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !playing(target)) continue;
            plugin.getGlowingEntities().setGlowing(target, viewer);
            targets.add(targetId);
        }
        respawnGlowing.put(viewerId, targets);
        scheduler.runTaskLater(
                plugin,
                () -> {
                    if (generation.equals(respawnGlowGeneration.get(viewerId)))
                        clearRespawnGlow(viewerId);
                },
                60L);
    }

    private void clearRespawnGlow(UUID viewerId) {
        Set<UUID> targets = respawnGlowing.remove(viewerId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (targets != null && viewer != null) {
            for (UUID targetId : targets) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) plugin.getGlowingEntities().unsetGlowing(target, viewer);
            }
        }
    }

    private void spawn(Player p, boolean heat) {
        int a = arena(p.getUniqueId());
        List<Location> candidates = new ArrayList<>(getGameConfig().spawns(a));
        Collections.shuffle(candidates, random);
        List<Location> enemies =
                round.seats().keySet().stream()
                        .filter(id -> round.enemies(p.getUniqueId(), id))
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .filter(other -> getGameConfig().contains(other.getLocation(), a))
                        .map(Player::getLocation)
                        .toList();
        Location selected = null;
        double best = -1;
        for (Location candidate : candidates) {
            if (!safe(candidate)) continue;
            double nearest =
                    enemies.stream()
                            .mapToDouble(e -> e.distanceSquared(candidate))
                            .min()
                            .orElse(1E9);
            // Sequential spawns must also spread out before all players have entered this arena.
            if (nearest > best) {
                best = nearest;
                selected = candidate;
            }
        }
        if (selected == null) throw new IllegalStateException("场地没有安全重生点");
        teleport(p, selected);
        p.setVelocity(new Vector());
        p.setFallDistance(0);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setGameMode(GameMode.ADVENTURE);
        if (heat) round.heat(p.getUniqueId(), tick + getGameConfig().getHeatSeconds() * 20);
        equipTeamArmor(p);
        updateHeatState(p);
    }

    private boolean safe(Location l) {
        return l.getBlock().isPassable()
                && l.clone().add(0, 1, 0).getBlock().isPassable()
                && !l.clone().subtract(0, .15, 0).getBlock().isPassable()
                && !l.getBlock().isLiquid();
    }

    private void teleport(Player p, Location l) {
        ownTeleport = true;
        try {
            p.teleport(l);
        } finally {
            ownTeleport = false;
        }
    }

    public void move(PlayerMoveEvent e) {
        if (!playing(e.getPlayer()) || e.getTo() == null) return;
        Location frozen = freezeLocations.get(e.getPlayer().getUniqueId());
        if (frozen != null) {
            Location to = frozen.clone();
            to.setYaw(e.getTo().getYaw());
            to.setPitch(e.getTo().getPitch());
            e.setTo(to);
        }
    }

    public void teleportEvent(PlayerTeleportEvent e) {
        if (playing(e.getPlayer()) && !ownTeleport) e.setCancelled(true);
    }

    public void melee(Player attacker, Player victim) {
        if (!acting(attacker)
                || !playing(victim)
                || !round.enemies(attacker.getUniqueId(), victim.getUniqueId())) return;
        UUID id = attacker.getUniqueId();
        if (meleeCooldown.getOrDefault(id, 0) > tick) return;
        FrostbiteItem item = item(attacker.getInventory().getItemInMainHand());
        if (item == FrostbiteItem.AXE && attacker.getAttackCooldown() < .9F) return;
        meleeCooldown.put(id, tick + 8);
        reveal(attacker);
        if (item == FrostbiteItem.AXE) {
            UUID killer = round.instantKill(id, victim.getUniqueId(), tick);
            if (killer != null) {
                consume(attacker);
                awardKill(killer, victim.getUniqueId());
                respawn(victim.getUniqueId());
            }
        } else if (freeze(id, victim) && item == FrostbiteItem.ICICLE) {
            consume(attacker);
            giveRandom(attacker, null);
            giveRandom(attacker, item(attacker.getInventory().getItem(0)));
        }
    }

    private boolean freeze(UUID attacker, Player victim) {
        if (!playing(victim)
                || !round.freeze(
                        attacker,
                        victim.getUniqueId(),
                        tick,
                        getGameConfig().getFreezeSeconds() * 20)) return false;
        boolean phoenix = has(victim, FrostbiteItem.PHOENIX);
        Prop camp = camp(victim.getUniqueId());
        Location frozenAt = victim.getLocation().clone();
        clearCombat(victim);
        if (phoenix) {
            playPhoenixEffect(victim);
            round.thaw(victim.getUniqueId());
            equipTeamArmor(victim);
            updateHeatState(victim);
            victim.sendActionBar(LegacyText.component(MessageConfig.FROSTBITE_PHOENIX_CONSUMED));
            return true;
        }
        applyFrozenState(victim);
        if (camp != null) {
            frozenAt.getWorld()
                    .spawnParticle(
                            Particle.FLAME, frozenAt.clone().add(0, 1, 0), 40, .45, .7, .45, .03);
            frozenAt.getWorld()
                    .spawnParticle(
                            Particle.LAVA, frozenAt.clone().add(0, 1, 0), 8, .35, .5, .35, .02);
            remove(camp);
            Location returnLocation = camp.location.clone();
            thaw(victim);
            teleport(victim, returnLocation);
            victim.setFallDistance(0);
            victim.sendActionBar(LegacyText.component(MessageConfig.FROSTBITE_CAMPFIRE_RETURNED));
            return true;
        }
        Player attackerPlayer = Bukkit.getPlayer(attacker);
        if (attackerPlayer != null) {
            attackerPlayer.playSound(
                    attackerPlayer.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1F, 1.25F);
            attackerPlayer.sendActionBar(
                    LegacyText.component(
                            MessageConfig.FROSTBITE_FREEZE_SUCCESS.replace(
                                    "%player%", CoreMessages.formatPlayerName(victim))));
        }
        sendMessageToAllGamePlayers(
                MessageConfig.FROSTBITE_FREEZE
                        .replace("%attacker%", CoreMessages.formatPlayerName(attacker))
                        .replace("%victim%", CoreMessages.formatPlayerName(victim)));
        return true;
    }

    private void playPhoenixEffect(Player player) {
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        player.getWorld()
                .spawnParticle(
                        Particle.TOTEM_OF_UNDYING,
                        player.getLocation().add(0, 1, 0),
                        80,
                        .7,
                        1,
                        .7,
                        .1);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1F, 1F);
    }

    private void applyFrozenState(Player victim) {
        FrostbiteFrozenEquipment.apply(victim.getInventory());
        freezeLocations.put(victim.getUniqueId(), victim.getLocation());
        victim.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        PotionEffect.INFINITE_DURATION,
                        255,
                        true,
                        false));
        victim.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.WEAKNESS,
                        PotionEffect.INFINITE_DURATION,
                        255,
                        true,
                        false));
        victim.setVelocity(new Vector());
        victim.setFreezeTicks(130);
        victim.getWorld()
                .spawnParticle(
                        Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 25, .4, .7, .4, .02);
        victim.playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1, .7F);
        victim.sendActionBar(LegacyText.component(MessageConfig.FROSTBITE_FROZEN));
    }

    private void thaw(Player p) {
        round.thaw(p.getUniqueId());
        freezeLocations.remove(p.getUniqueId());
        p.setFreezeTicks(0);
        FrostbiteFrozenEquipment.clear(p.getInventory());
        equipTeamArmor(p);
        updateHeatState(p);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    private void clearCombat(Player p) {
        freezeLocations.remove(p.getUniqueId());
        p.setFreezeTicks(0);
        FrostbiteFrozenEquipment.clear(p.getInventory());
        p.setFireTicks(0);
        supplyHints.clear(p.getUniqueId());
        for (PotionEffect effect : p.getActivePotionEffects())
            p.removePotionEffect(effect.getType());
        reveal(p);
    }

    private void reveal(Player p) {
        invisibleUntil.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void equipTeamArmor(Player p) {
        ChampionshipTeam team =
                gameTeams.stream()
                        .sorted(Comparator.comparingInt(ChampionshipTeam::getId))
                        .toList()
                        .get(round.seats().get(p.getUniqueId()).team());
        p.getInventory().setHelmet(team.getHelmet());
        p.getInventory().setChestplate(null);
        p.getInventory().setLeggings(team.getLeggings());
        p.getInventory().setBoots(team.getBoots());
    }

    private void updateHeatState(Player p) {
        if (round.freezeState(p.getUniqueId()) != null) return;
        p.setFireTicks(round.heated(p.getUniqueId(), tick) ? 2 : 0);
    }

    public FrostbiteItem item(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String name =
                stack.getItemMeta()
                        .getPersistentDataContainer()
                        .get(itemKey, PersistentDataType.STRING);
        try {
            return name == null ? null : FrostbiteItem.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean has(Player p, FrostbiteItem item) {
        for (ItemStack s : p.getInventory().getStorageContents()) if (item(s) == item) return true;
        return false;
    }

    private int itemCount(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (item(s) != null) n++;
        return n;
    }

    private ItemStack stack(FrostbiteItem type) {
        ItemStack stack = new ItemStack(type.material);
        stack.editMeta(
                meta -> {
                    meta.displayName(LegacyText.component("&b" + itemTitle(type)));
                    meta.lore(List.of(LegacyText.component("&7" + itemDescription(type))));
                    meta.setUnbreakable(true);
                    meta.getPersistentDataContainer()
                            .set(itemKey, PersistentDataType.STRING, type.name());
                });
        return stack;
    }

    private static String itemTitle(FrostbiteItem type) {
        String value =
                switch (type) {
                    case AVALANCHE -> MessageConfig.FROSTBITE_ITEM_AVALANCHE_TITLE;
                    case AXE -> MessageConfig.FROSTBITE_ITEM_AXE_TITLE;
                    case BLAZE -> MessageConfig.FROSTBITE_ITEM_BLAZE_TITLE;
                    case BOW -> MessageConfig.FROSTBITE_ITEM_BOW_TITLE;
                    case BEACON -> MessageConfig.FROSTBITE_ITEM_BEACON_TITLE;
                    case EXPLOSION -> MessageConfig.FROSTBITE_ITEM_EXPLOSION_TITLE;
                    case GLOW -> MessageConfig.FROSTBITE_ITEM_GLOW_TITLE;
                    case HOT_ROD -> MessageConfig.FROSTBITE_ITEM_HOT_ROD_TITLE;
                    case ICICLE -> MessageConfig.FROSTBITE_ITEM_ICICLE_TITLE;
                    case INVIS -> MessageConfig.FROSTBITE_ITEM_INVIS_TITLE;
                    case MYSTERY -> MessageConfig.FROSTBITE_ITEM_MYSTERY_TITLE;
                    case PHOENIX -> MessageConfig.FROSTBITE_ITEM_PHOENIX_TITLE;
                    case SPEED -> MessageConfig.FROSTBITE_ITEM_SPEED_TITLE;
                    case FROST_TRAP -> MessageConfig.FROSTBITE_ITEM_FROST_TRAP_TITLE;
                    case WHOABALL -> MessageConfig.FROSTBITE_ITEM_WHOABALL_TITLE;
                };
        return value == null ? type.title : value;
    }

    private static String itemDescription(FrostbiteItem type) {
        String value =
                switch (type) {
                    case AVALANCHE -> MessageConfig.FROSTBITE_ITEM_AVALANCHE_DESCRIPTION;
                    case AXE -> MessageConfig.FROSTBITE_ITEM_AXE_DESCRIPTION;
                    case BLAZE -> MessageConfig.FROSTBITE_ITEM_BLAZE_DESCRIPTION;
                    case BOW -> MessageConfig.FROSTBITE_ITEM_BOW_DESCRIPTION;
                    case BEACON -> MessageConfig.FROSTBITE_ITEM_BEACON_DESCRIPTION;
                    case EXPLOSION -> MessageConfig.FROSTBITE_ITEM_EXPLOSION_DESCRIPTION;
                    case GLOW -> MessageConfig.FROSTBITE_ITEM_GLOW_DESCRIPTION;
                    case HOT_ROD -> MessageConfig.FROSTBITE_ITEM_HOT_ROD_DESCRIPTION;
                    case ICICLE -> MessageConfig.FROSTBITE_ITEM_ICICLE_DESCRIPTION;
                    case INVIS -> MessageConfig.FROSTBITE_ITEM_INVIS_DESCRIPTION;
                    case MYSTERY -> MessageConfig.FROSTBITE_ITEM_MYSTERY_DESCRIPTION;
                    case PHOENIX -> MessageConfig.FROSTBITE_ITEM_PHOENIX_DESCRIPTION;
                    case SPEED -> MessageConfig.FROSTBITE_ITEM_SPEED_DESCRIPTION;
                    case FROST_TRAP -> MessageConfig.FROSTBITE_ITEM_FROST_TRAP_DESCRIPTION;
                    case WHOABALL -> MessageConfig.FROSTBITE_ITEM_WHOABALL_DESCRIPTION;
                };
        return value == null ? type.description : value;
    }

    private void consume(Player p) {
        p.getInventory().setItemInMainHand(null);
    }

    private void giveRandom(Player p, FrostbiteItem exclude) {
        if (itemCount(p) >= 2) return;
        List<FrostbiteItem> pool =
                Arrays.stream(FrostbiteItem.values())
                        .filter(
                                i ->
                                        i != exclude
                                                && i != FrostbiteItem.ICICLE
                                                && (exclude == null || i != FrostbiteItem.MYSTERY))
                        .toList();
        FrostbiteItem type = pool.get(random.nextInt(pool.size()));
        // Icicles cannot recursively generate themselves or boxes; regular pickups add them
        // separately.
        give(p, type);
    }

    private void give(Player p, FrostbiteItem type) {
        int slot = p.getInventory().getItem(0) == null ? 0 : 1;
        p.getInventory().setItem(slot, stack(type));
        if (type == FrostbiteItem.BOW)
            p.getInventory().setItem(8, new ItemStack(Material.ARROW, 3));
        supplyHints.add(
                p.getUniqueId(), "&b" + itemTitle(type) + " &7" + itemDescription(type), tick);
        p.sendActionBar(LegacyText.component(supplyHints.current(p.getUniqueId(), tick)));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .5F, 1.5F);
    }

    public void use(Player p) {
        if (!acting(p) || useCooldown.getOrDefault(p.getUniqueId(), 0) > tick) return;
        FrostbiteItem type = item(p.getInventory().getItemInMainHand());
        if (type == null) return;
        if (type == FrostbiteItem.BOW
                || type == FrostbiteItem.AXE
                || type == FrostbiteItem.ICICLE
                || type == FrostbiteItem.PHOENIX) return;
        if ((type == FrostbiteItem.BLAZE
                        || type == FrostbiteItem.BEACON
                        || type == FrostbiteItem.FROST_TRAP)
                && !p.isOnGround()) return;
        useCooldown.put(p.getUniqueId(), tick + 5);
        consume(p);
        switch (type) {
            case HOT_ROD -> {
                round.heat(p.getUniqueId(), tick + 70);
                updateHeatState(p);
            }
            case SPEED ->
                    p.addPotionEffect(
                            new PotionEffect(PotionEffectType.SPEED, 240, 2, true, false));
            case INVIS -> {
                p.addPotionEffect(
                        new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, true, false));
                invisibleUntil.put(p.getUniqueId(), tick + 200);
            }
            case GLOW ->
                    opponents(p.getUniqueId(), p.getLocation(), 512).stream()
                            .min(
                                    Comparator.comparingDouble(
                                            o -> o.getLocation().distanceSquared(p.getLocation())))
                            .ifPresent(
                                    other ->
                                            other.addPotionEffect(
                                                    new PotionEffect(
                                                            PotionEffectType.GLOWING,
                                                            160,
                                                            0,
                                                            true,
                                                            false)));
            case BLAZE -> place(p, type);
            case MYSTERY -> giveRandom(p, FrostbiteItem.MYSTERY);
            case BEACON, FROST_TRAP ->
                    createZone(
                            p.getUniqueId(),
                            type,
                            arena(p.getUniqueId()),
                            p.getLocation(),
                            type == FrostbiteItem.BEACON ? 160 : 120);
            case AVALANCHE -> launch(p, type, p.getLocation().getDirection().multiply(.8));
            case WHOABALL -> launch(p, type, p.getLocation().getDirection().multiply(1.4));
            case EXPLOSION -> {
                p.getWorld()
                        .spawnParticle(
                                Particle.SNOWFLAKE,
                                p.getLocation().add(0, 1, 0),
                                100,
                                3,
                                .7,
                                3,
                                .06);
                for (Player enemy : opponents(p.getUniqueId(), p.getLocation(), 6))
                    freeze(p.getUniqueId(), enemy);
                round.selfFreeze(p.getUniqueId(), tick, getGameConfig().getFreezeSeconds() * 20);
                clearCombat(p);
                applyFrozenState(p);
            }
            default -> {}
        }
    }

    private void launch(Player p, FrostbiteItem type, Vector velocity) {
        Snowball ball = p.launchProjectile(Snowball.class, velocity);
        shots.put(
                ball.getUniqueId(),
                new Shot(p.getUniqueId(), type, arena(p.getUniqueId()), tick + 100));
    }

    public void shoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !participant(p)) return;
        if (!acting(p) || item(e.getBow()) != FrostbiteItem.BOW) {
            e.setCancelled(true);
            return;
        }
        shots.put(
                e.getProjectile().getUniqueId(),
                new Shot(p.getUniqueId(), FrostbiteItem.BOW, arena(p.getUniqueId()), tick + 100));
        if (e.getProjectile() instanceof AbstractArrow arrow)
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        // The bow event runs before vanilla finishes consuming its arrow. Clearing the bow here
        // when the stack is at one causes that third projectile to be discarded on Paper.
        if (p.getInventory().getItem(8) == null || p.getInventory().getItem(8).getAmount() <= 1)
            scheduler.runTask(
                    plugin,
                    () -> {
                        ItemStack arrows = p.getInventory().getItem(8);
                        if (arrows == null
                                || arrows.getType() != Material.ARROW
                                || arrows.getAmount() <= 0) consume(p);
                    });
        reveal(p);
    }

    public boolean ownedProjectile(Entity entity) {
        return shots.containsKey(entity.getUniqueId());
    }

    public void hit(ProjectileHitEvent e) {
        Shot shot = shots.remove(e.getEntity().getUniqueId());
        if (shot == null) return;
        e.setCancelled(true);
        Location l = e.getEntity().getLocation();
        e.getEntity().remove();
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !round.active(shot.owner)
                || !isGameplayParticipant(shot.owner)
                || !getGameConfig().contains(l, shot.arena)) return;
        switch (shot.item) {
            case BOW -> {
                if (e.getHitEntity() instanceof Player p) freeze(shot.owner, p);
            }
            case WHOABALL -> {
                l.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 100, 2.5, .5, 2.5, .05);
                for (Player p : opponents(shot.owner, l, 5)) freeze(shot.owner, p);
            }
            case AVALANCHE -> {
                Location ground =
                        e.getHitBlock() == null
                                ? l.clone()
                                : e.getHitBlock().getLocation().add(.5, 1, .5);
                if (e.getHitBlock() == null) {
                    ground.setX(Math.floor(ground.getX()) + .5);
                    ground.setY(Math.floor(ground.getY()));
                    ground.setZ(Math.floor(ground.getZ()) + .5);
                }
                createAvalancheCloud(shot.owner, shot.arena, ground);
            }
            default -> {}
        }
        l.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 15, .5, .5, .5, .02);
    }

    private void place(Player p, FrostbiteItem type) {
        if (type == FrostbiteItem.BLAZE) {
            Prop old = camp(p.getUniqueId());
            if (old != null) remove(old);
        }
        Location location = p.getLocation();
        ArmorStand stand =
                location.getWorld()
                        .spawn(
                                location,
                                ArmorStand.class,
                                s -> {
                                    s.setVisible(false);
                                    s.setGravity(false);
                                    s.setSmall(true);
                                    s.setInvulnerable(false);
                                    s.setPersistent(false);
                                });
        Location displayLocation = location.getBlock().getLocation();
        BlockDisplay display =
                location.getWorld()
                        .spawn(
                                displayLocation,
                                BlockDisplay.class,
                                d -> {
                                    d.setBlock(Bukkit.createBlockData(Material.CAMPFIRE));
                                    d.setRotation(0, 0);
                                    d.setPersistent(false);
                                });
        props.add(
                new Prop(
                        p.getUniqueId(),
                        type,
                        arena(p.getUniqueId()),
                        location,
                        stand,
                        display,
                        tick + 600));
    }

    private Prop camp(UUID owner) {
        return props.stream()
                .filter(p -> p.owner.equals(owner) && p.type == FrostbiteItem.BLAZE)
                .findFirst()
                .orElse(null);
    }

    private void remove(Prop p) {
        p.entity.remove();
        p.display.remove();
        props.remove(p);
    }

    private void removeOwnedProps(UUID id) {
        for (Prop p : List.copyOf(props)) if (p.owner.equals(id)) remove(p);
    }

    public boolean prop(Entity entity) {
        return props.stream().anyMatch(p -> p.entity.getUniqueId().equals(entity.getUniqueId()));
    }

    public void strikeProp(Player player, Entity entity) {
        if (!acting(player)) return;
        for (Prop p : List.copyOf(props))
            if (p.entity.getUniqueId().equals(entity.getUniqueId())
                    && round.enemies(player.getUniqueId(), p.owner)) {
                remove(p);
            }
    }

    private void tickProps() {
        for (Prop p : List.copyOf(props)) {
            if (tick >= p.expires || !p.entity.isValid() || !p.display.isValid()) {
                remove(p);
                continue;
            }
        }
    }

    private void createAvalancheCloud(UUID owner, int arena, Location center) {
        AreaEffectCloud cloud =
                center.getWorld()
                        .spawn(
                                center,
                                AreaEffectCloud.class,
                                effect -> {
                                    effect.setRadius(1.7F);
                                    effect.setDuration(AVALANCHE_ZONE_TICKS);
                                    effect.setWaitTime(0);
                                    effect.setReapplicationDelay(5);
                                    effect.setRadiusOnUse(0F);
                                    effect.setRadiusPerTick(0F);
                                    effect.addCustomEffect(
                                            new PotionEffect(
                                                    PotionEffectType.SLOWNESS,
                                                    AVALANCHE_SLOWNESS_TICKS,
                                                    2,
                                                    true,
                                                    false),
                                            true);
                                    effect.setParticle(Particle.ENTITY_EFFECT);
                                    effect.setPersistent(false);
                                });
        avalancheClouds.put(cloud.getUniqueId(), new AvalancheCloud(owner, arena, cloud));
    }

    public void areaEffectCloud(AreaEffectCloudApplyEvent event) {
        AvalancheCloud cloud = avalancheClouds.get(event.getEntity().getUniqueId());
        if (cloud == null || round == null) return;
        event.getAffectedEntities()
                .removeIf(
                        entity ->
                                !(entity instanceof Player player)
                                        || !playing(player)
                                        || !round.enemies(cloud.owner(), player.getUniqueId())
                                        || arena(player.getUniqueId()) != cloud.arena()
                                        || !cloud.entity().getWorld().equals(player.getWorld()));
    }

    private void createZone(
            UUID owner, FrostbiteItem type, int arena, Location center, int duration) {
        List<BlockDisplay> displays = new ArrayList<>();
        if (type == FrostbiteItem.BEACON) {
            displays.add(
                    center.getWorld()
                            .spawn(
                                    center.clone().add(-.5, 0, -.5),
                                    BlockDisplay.class,
                                    d -> {
                                        d.setBlock(Bukkit.createBlockData(Material.MAGMA_BLOCK));
                                        d.setPersistent(false);
                                    }));
        } else {
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) {
                    Location tile = center.clone().add(x - .5, 0, z - .5);
                    displays.add(
                            center.getWorld()
                                    .spawn(
                                            tile,
                                            BlockDisplay.class,
                                            d -> {
                                                Snow snow =
                                                        (Snow)
                                                                Bukkit.createBlockData(
                                                                        Material.SNOW);
                                                snow.setLayers(3);
                                                d.setBlock(snow);
                                                d.setPersistent(false);
                                            }));
                }
        }
        zones.add(new Zone(owner, type, arena, center.clone(), tick, tick + duration, displays));
    }

    private void removeZone(Zone zone) {
        zone.displays.forEach(Entity::remove);
        zones.remove(zone);
    }

    private void createPickups() {
        List<Location> points = new ArrayList<>();
        pickupArenaOffsets.clear();
        for (int a : getSelectedArenaIndices(4)) {
            pickupArenaOffsets.put(a, points.size());
            for (String text : getGameConfig().getItemPoints())
                points.add(getGameConfig().point(text, a));
        }
        pickups = List.copyOf(points);
        pickupReady = new int[points.size()];
        for (Location l : points)
            pickupDisplays.add(
                    l.getWorld()
                            .spawn(
                                    l.clone().add(0, .8, 0),
                                    ItemDisplay.class,
                                    d -> {
                                        d.setItemStack(new ItemStack(Material.GOLD_BLOCK));
                                        d.setPersistent(false);
                                        d.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new AxisAngle4f(
                                                                (float) Math.toRadians(45),
                                                                0F,
                                                                1F,
                                                                0F),
                                                        new Vector3f(.6F, .6F, .6F),
                                                        new AxisAngle4f()));
                                    }));
    }

    private void animatePickups() {
        float angle = (float) Math.toRadians(45 + tick * 2.4);
        for (int i = 0; i < pickupDisplays.size(); i++) {
            ItemDisplay display = pickupDisplays.get(i);
            if (!display.isValid() || pickupReady[i] > tick) continue;
            display.setTransformation(
                    new Transformation(
                            new Vector3f(0, (float) (Math.sin(tick * .12) * .12), 0),
                            new AxisAngle4f(angle, 0F, 1F, 0F),
                            new Vector3f(.6F, .6F, .6F),
                            new AxisAngle4f()));
        }
    }

    private void collect(Player p) {
        int a = arena(p.getUniqueId()), count = getGameConfig().getItemPoints().size();
        Integer offset = pickupArenaOffsets.get(a);
        if (offset == null) return;
        for (int i = offset; i < offset + count; i++)
            if (pickupReady[i] <= tick && p.getLocation().distanceSquared(pickups.get(i)) <= 2.25) {
                if (itemCount(p) > 0) {
                    if (fullPickupHintAt.getOrDefault(p.getUniqueId(), -100) + 100 <= tick) {
                        fullPickupHintAt.put(p.getUniqueId(), tick);
                        supplyHints.add(p.getUniqueId(), "&e已有道具，无法额外获得补给", tick);
                    }
                    return;
                }
                pickupReady[i] = tick + getGameConfig().getItemRespawnSeconds() * 20;
                if (random.nextInt(15) == 0) give(p, FrostbiteItem.ICICLE);
                else giveRandom(p, null);
                pickupDisplays.get(i).setItemStack(new ItemStack(Material.AIR));
                return;
            }
    }

    @Override
    public synchronized void endGame() {
        if (ending
                || getGameStageEnum() == GameStageEnum.WAITING
                || getGameStageEnum() == GameStageEnum.END) return;
        ending = true;
        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            stopRuntime();
            setGameStageEnum(GameStageEnum.END);
            beginPostGameSettlement();
            completePostGame(false);
            return;
        }
        for (UUID id : round.expired(tick)) {
            UUID killer = round.die(id);
            awardKill(killer, id);
        }
        stopRuntime();
        if (isSettlementAllowed()) {
            round.rankingRewards(getGameConfig().getRankPointsPerPlayer())
                    .forEach(
                            (id, points) -> {
                                if (points > 0) addPlayerPoints(id, points);
                            });
            sendMessageToAllGamePlayers(getTeamPointsRank());
            addPlayerPointsToDatabase();
        }
        setGameStageEnum(GameStageEnum.END);
        announceGameEnd(MessageConfig.FROSTBITE_END_TITLE, MessageConfig.FROSTBITE_END_SUBTITLE);
        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        publishGameEndEvent(new SingleGameEndEvent(this, List.copyOf(gameTeams)));
        finishPostGameAfterEndEvent();
    }

    private void cleanupPlayer(UUID id) {
        clearRespawnGlow(id);
        respawnGlowGeneration.remove(id);
        for (UUID viewerId : new HashSet<>(respawnGlowing.keySet())) {
            Set<UUID> targets = respawnGlowing.get(viewerId);
            if (targets != null && targets.remove(id)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                Player target = Bukkit.getPlayer(id);
                if (viewer != null && target != null)
                    plugin.getGlowingEntities().unsetGlowing(target, viewer);
                if (targets.isEmpty()) respawnGlowing.remove(viewerId);
            }
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            clearCombat(p);
            Boolean previous = collisionBefore.remove(id);
            if (previous != null) p.setCollidable(previous);
        } else {
            freezeLocations.remove(id);
            invisibleUntil.remove(id);
            collisionBefore.remove(id);
        }
        removeOwnedProps(id);
        meleeCooldown.remove(id);
        useCooldown.remove(id);
        supplyHints.clear(id);
        fullPickupHintAt.remove(id);
        for (Zone zone : List.copyOf(zones)) if (zone.owner.equals(id)) removeZone(zone);
        for (AvalancheCloud cloud : List.copyOf(avalancheClouds.values())) {
            if (cloud.owner().equals(id)) {
                cloud.entity().remove();
                avalancheClouds.remove(cloud.entity().getUniqueId());
            }
        }
    }

    private void stopRuntime() {
        if (tickTask != null) tickTask.cancel();
        if (timerTask != null) timerTask.cancel();
        tickTask = null;
        timerTask = null;
        for (UUID id : List.copyOf(gamePlayers)) cleanupPlayer(id);
        for (UUID id : shots.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        shots.clear();
        for (Prop p : List.copyOf(props)) remove(p);
        for (Zone zone : List.copyOf(zones)) removeZone(zone);
        for (AvalancheCloud cloud : avalancheClouds.values()) cloud.entity().remove();
        avalancheClouds.clear();
        pickupDisplays.forEach(Entity::remove);
        pickupDisplays.clear();
        pickups = List.of();
        pickupArenaOffsets.clear();
        pickupReady = new int[0];
        freezeLocations.clear();
        meleeCooldown.clear();
        useCooldown.clear();
        invisibleUntil.clear();
        supplyHints.clear();
        fullPickupHintAt.clear();
        respawnGlowing.clear();
        respawnGlowGeneration.clear();
    }

    @Override
    public void endGameFinally() {
        stopRuntime();
        ownTeleport = true;
        try {
            super.endGameFinally();
        } finally {
            ownTeleport = false;
        }
    }

    @Override
    public void resetArea() {
        stopRuntime();
        round = null;
        ending = false;
        timer = 0;
        preloadMap();
    }

    @Override
    public void dispose() {
        stopRuntime();
        super.dispose();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> abortAndReset() {
        if (Bukkit.isPrimaryThread() && isEventRun())
            plugin.getScheduleManager().endGameSchedule(GameTypeEnum.FrostbiteFrenzy);
        return super.abortAndReset();
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent e) {
        if (playing(e.getPlayer()))
            awardKill(round.leave(e.getPlayer().getUniqueId()), e.getPlayer().getUniqueId());
        cleanupPlayer(e.getPlayer().getUniqueId());
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (notAreaPlayer(p)) return;
        if (round != null && getGameStageEnum() == GameStageEnum.PROGRESS) {
            awardKill(round.leave(p.getUniqueId()), p.getUniqueId());
            cleanupPlayer(p.getUniqueId());
            teleport(p, getSpectatorSpawnLocation());
            p.setGameMode(GameMode.SPECTATOR);
        } else {
            teleport(p, getSpectatorSpawnLocation());
            p.setGameMode(GameMode.ADVENTURE);
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent e) {
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        if (playing(e.getEntity())) {
            awardKill(round.leave(e.getEntity().getUniqueId()), e.getEntity().getUniqueId());
            cleanupPlayer(e.getEntity().getUniqueId());
        }
        // Native death is exceptional (commands/other plugins); respawn as spectator, never a free
        // re-entry.
    }

    public void respawnEvent(PlayerRespawnEvent e) {
        if (!participant(e.getPlayer())) return;
        e.setRespawnLocation(getSpectatorSpawnLocation());
        e.getPlayer().setGameMode(GameMode.SPECTATOR);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location l = getGameConfig().getSpectatorSpawnPoint();
        if (l != null) {
            l = l.clone();
            l.setWorld(Bukkit.getWorld(getWorldName()));
            return l;
        }
        World world = Bukkit.getWorld(getWorldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }

    @Override
    protected Collection<Player> getOnlineParticipantSpectators() {
        return gamePlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(p -> p.getGameMode() == GameMode.SPECTATOR)
                .toList();
    }

    @Override
    public int getTimer() {
        return timer;
    }

    public int getPlayerKills(UUID id) {
        return round == null ? 0 : round.kills(id);
    }

    public String getPlayerStateKey(UUID id) {
        if (getGameStageEnum() == GameStageEnum.END) return "ended";
        if (round != null && !round.active(id)) return "spectator";
        if (getGameStageEnum() != GameStageEnum.PROGRESS || round == null) return "waiting";
        if (round.freezeState(id) != null) return "frozen";
        return round.heated(id, tick) ? "heated" : "active";
    }

    @Override
    public FrostbiteConfig getGameConfig() {
        return (FrostbiteConfig) gameConfig;
    }

    @Override
    public FrostbiteHandler getGameHandler() {
        return (FrostbiteHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
