package ink.ziip.championshipscore.api.game.decarnival;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Enchants;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.boss.DragonBattle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DragonEggCarnivalArea extends BasePairedGameInstance {
    static final int VANILLA_PLATFORM_X = 100;
    static final int MIRRORED_PLATFORM_X = -100;
    static final int PLATFORM_FLOOR_Y = 48;
    static final int PLATFORM_RADIUS = 2;
    static final int PLATFORM_AIR_HEIGHT = 3;
    static final int ADVANCEMENTS_TO_WIN = 2;
    static final int CRYSTAL_PEARLS_PER_PLAYER = 2;
    static final Set<String> VICTORY_ADVANCEMENTS = Set.of(
            "end/kill_dragon",
            "end/dragon_egg",
            "end/enter_end_gateway"
    );

    private enum RewardEnchant {
        SHARPNESS("锋利"),
        PROTECTION("保护"),
        POWER("力量");

        private final String displayName;

        RewardEnchant(String displayName) {
            this.displayName = displayName;
        }
    }

    @Getter
    private int timer;
    private BukkitTask startGameProgressTask;
    private BukkitTask dragonReadyTask;
    @Getter
    private int rightTeamPoints;
    @Getter
    private int leftTeamPoints;
    private ChampionshipTeam winningTeam;

    private final Map<UUID, Integer> deathCounts = new HashMap<>();
    private final Set<UUID> kitIssued = new HashSet<>();
    private final Map<UUID, Long> respawnReadyAt = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Set<UUID> creditedCrystals = new HashSet<>();
    private final Map<ChampionshipTeam, Integer> teamCrystalCounts = new HashMap<>();
    private final Map<UUID, Integer> deliveredCrystalRewards = new HashMap<>();
    private final Map<ChampionshipTeam, EnumMap<RewardEnchant, Integer>> teamEnchantments = new HashMap<>();
    private final Map<ChampionshipTeam, Set<String>> teamAdvancements = new HashMap<>();
    private final Map<ChampionshipTeam, Double> teamDragonDamage = new HashMap<>();
    private final Map<UUID, Double> playerDragonDamage = new HashMap<>();

    public DragonEggCarnivalArea(ChampionshipsCore plugin, DragonEggCarnivalConfig dragonEggCarnivalConfig,
                                 boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.DragonEggCarnival, new DragonEggCarnivalHandler(plugin), dragonEggCarnivalConfig);
        getGameHandler().setDragonEggCarnivalArea(this);
        dragonEggCarnivalConfig.setAreaName(areaName);

        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    public void preloadMap() {
        loadPublishedMapOrDraft(World.Environment.THE_END);
    }

    @Override
    public void resetArea() {
        timer = 0;
        rightTeamPoints = 0;
        leftTeamPoints = 0;
        winningTeam = null;
        startGameProgressTask = null;
        if (dragonReadyTask != null) dragonReadyTask.cancel();
        dragonReadyTask = null;
        cancelRespawnTasks();
        deathCounts.clear();
        kitIssued.clear();
        respawnReadyAt.clear();
        creditedCrystals.clear();
        teamCrystalCounts.clear();
        deliveredCrystalRewards.clear();
        teamEnchantments.clear();
        teamAdvancements.clear();
        teamDragonDamage.clear();
        playerDragonDamage.clear();
        getGameHandler().resetMatchState();
        preloadMap();
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        World world = fightWorld();
        if (world == null) return List.of();
        return List.of(
                new Location(world, VANILLA_PLATFORM_X, PLATFORM_FLOOR_Y + 1D, 0D),
                new Location(world, MIRRORED_PLATFORM_X, PLATFORM_FLOOR_Y + 1D, 0D),
                new Location(world, 0.5D, 80D, 0.5D));
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        configureFightWorld();
        prepareBothSpawnPlatforms();
        ensureVanillaDragon();
        startGameIntroduction(this::startFormalPreparation);
    }

    private void startFormalPreparation() {
        configureFightWorld();
        prepareBothSpawnPlatforms();
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        teleportTeamsToPlatforms();

        announceGamePreparation(MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION,
                MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION_TITLE,
                MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION_SUBTITLE);
        startGameProgress();
    }

    protected void startGameProgress() {
        prepareBothSpawnPlatforms();
        resetPlayerHealthFoodEffectLevelInventory();
        revokeVictoryAdvancements();
        giveStartingKits();
        teleportTeamsToPlatforms();
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        addFinalTagToTeam(rightChampionshipTeam);
        addFinalTagToTeam(leftChampionshipTeam);
        waitForDragonThenStartCountdown();
    }

    private void waitForDragonThenStartCountdown() {
        if (isVanillaDragonReady()) {
            startFightCountdown();
            return;
        }
        if (dragonReadyTask != null) dragonReadyTask.cancel();
        dragonReadyTask = scheduler.runTaskTimer(plugin, () -> {
            if (getGameStageEnum() != GameStageEnum.PREPARATION) {
                dragonReadyTask.cancel();
                dragonReadyTask = null;
                return;
            }
            if (!isVanillaDragonReady()) return;
            dragonReadyTask.cancel();
            dragonReadyTask = null;
            startFightCountdown();
        }, 0L, 20L);
    }

    private boolean isVanillaDragonReady() {
        World world = fightWorld();
        DragonBattle battle = world == null ? null : world.getEnderDragonBattle();
        EnderDragon dragon = battle == null ? null : battle.getEnderDragon();
        return dragon != null && dragon.isValid() && !dragon.isDead()
                && battle.getRespawnPhase() == DragonBattle.RespawnPhase.NONE;
    }

    private void startFightCountdown() {
        startFinalCountdown(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_TITLE,
                MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_TITLE,
                MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        timer = 0;
        updateFightBossBar();
        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {
            timer++;
            updateFightBossBar();
        }, 20L, 20L);
    }

    private void updateFightBossBar() {
        updateGameTimerBossBar(MessageConfig.DRAGON_EGG_CARNIVAL_ACTION_BAR_COUNT_DOWN
                .replace("%time%", String.valueOf(timer)), 1D);
    }

    private void configureFightWorld() {
        World world = fightWorld();
        if (world == null) return;
        world.setSpawnFlags(true, false);
        world.setGameRule(GameRules.SPAWN_MOBS, true);
        world.setGameRule(GameRules.SPAWN_MONSTERS, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
    }

    private void ensureVanillaDragon() {
        World world = fightWorld();
        if (world == null) return;

        DragonBattle battle = world.getEnderDragonBattle();
        EnderDragon dragon = battle == null ? null : battle.getEnderDragon();
        if (dragon != null && dragon.isValid() && !dragon.isDead()) {
            battle.setPreviouslyKilled(false);
            battle.resetCrystals();
            return;
        }
        if (battle != null && battle.getRespawnPhase() != DragonBattle.RespawnPhase.NONE) return;

        // Published arena snapshots may contain a completed End fight. Starting the vanilla respawn
        // ritual re-generates every spike crystal and, unlike a custom-spawned dragon, attaches the new
        // dragon to DragonBattle so its death creates the egg, exit portal and End gateway normally.
        for (EnderDragon stale : world.getEntitiesByClass(EnderDragon.class)) stale.remove();
        removeExistingCentralGateways(world);
        removeExistingDragonEggs(world);
        if (battle != null && startVanillaRespawnRitual(world, battle)) {
            battle.setPreviouslyKilled(false);
            return;
        }

        // A malformed End map should still remain playable, although prepare validation/logs should
        // normally prevent reaching this fallback.
        world.spawnEntity(new Location(world, 0.5D, 80D, 0.5D), EntityType.ENDER_DRAGON);
    }

    private boolean startVanillaRespawnRitual(@NotNull World world, @NotNull DragonBattle battle) {
        battle.generateEndPortal(true);
        Location portal = battle.getEndPortalLocation();
        if (portal == null) {
            portal = new Location(world, 0D, world.getHighestBlockYAt(0, 0), 0D);
        }

        List<EnderCrystal> ritualCrystals = new ArrayList<>();
        double crystalY = portal.getBlockY() + 4D;
        for (int[] offset : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            Location location = new Location(world, portal.getBlockX() + 0.5D + offset[0], crystalY,
                    portal.getBlockZ() + 0.5D + offset[1]);
            ritualCrystals.add((EnderCrystal) world.spawnEntity(location, EntityType.END_CRYSTAL));
        }

        battle.setPreviouslyKilled(true);
        boolean started = battle.initiateRespawn(ritualCrystals);
        if (!started) for (EnderCrystal crystal : ritualCrystals) crystal.remove();
        return started;
    }

    private void removeExistingCentralGateways(@NotNull World world) {
        for (int gateway = 0; gateway < 20; gateway++) {
            double angle = 2D * (-Math.PI + Math.PI / 20D * gateway);
            int centerX = (int) Math.floor(96D * Math.cos(angle));
            int centerZ = (int) Math.floor(96D * Math.sin(angle));
            for (int x = centerX - 4; x <= centerX + 4; x++) {
                for (int z = centerZ - 4; z <= centerZ + 4; z++) {
                    for (int y = 65; y <= 85; y++) {
                        if (world.getBlockAt(x, y, z).getType() == Material.END_GATEWAY) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
    }

    private void removeExistingDragonEggs(@NotNull World world) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 48; y <= 96; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.DRAGON_EGG) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    public void recordAdvancement(@NotNull Player player, @NotNull String advancementKey) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || isManagedSpectator(player)
                || !VICTORY_ADVANCEMENTS.contains(advancementKey)) return;
        ChampionshipTeam team = teamOf(player);
        if (team == null) return;

        Set<String> completed = teamAdvancements.computeIfAbsent(team, ignored -> new LinkedHashSet<>());
        if (!completed.add(advancementKey)) return;

        boolean firstOfAdvancement = teamAdvancements.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(team))
                .noneMatch(entry -> entry.getValue().contains(advancementKey));
        if (firstOfAdvancement && getRunMode() == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY) {
            plugin.getDailyManager().statsManager().recordDragonFirstAdvancement(this, team, advancementKey);
        }

        updateTeamProgress(team, completed.size());
        sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_ADVANCEMENT
                .replace("%team%", team.getColoredName())
                .replace("%advancement%", advancementDisplayName(advancementKey))
                .replace("%count%", String.valueOf(completed.size())));

        if (completed.size() >= ADVANCEMENTS_TO_WIN) {
            winningTeam = team;
            if (getRunMode() == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY)
                addPlayerPointsToAllTeamMembers(team, 1);
            endGame();
        }
    }

    public void recordCrystalDestroyed(@NotNull Player player, @NotNull UUID crystalId) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || !creditedCrystals.add(crystalId)) return;
        ChampionshipTeam team = teamOf(player);
        if (team == null) return;

        teamCrystalCounts.merge(team, 1, Integer::sum);
        RewardEnchant reward = RewardEnchant.values()[ThreadLocalRandom.current().nextInt(RewardEnchant.values().length)];
        EnumMap<RewardEnchant, Integer> levels = teamEnchantments.computeIfAbsent(team,
                ignored -> new EnumMap<>(RewardEnchant.class));
        int level = levels.merge(reward, 1, Integer::sum);
        for (Player member : team.getOnlinePlayers()) syncCrystalRewards(member, team);

        sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_CRYSTAL_REWARD
                .replace("%team%", team.getColoredName())
                .replace("%enchantment%", reward.displayName)
                .replace("%level%", String.valueOf(level)));
    }

    public void recordDragonDamage(@NotNull Player player, double finalDamage, double dragonMaxHealth) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || finalDamage <= 0D || dragonMaxHealth <= 0D) return;
        ChampionshipTeam team = teamOf(player);
        ChampionshipTeam opponent = opposingTeam(team);
        if (team == null || opponent == null) return;

        double before = teamDragonDamage.getOrDefault(team, 0D);
        double after = before + finalDamage;
        teamDragonDamage.put(team, after);
        double playerTotal = playerDragonDamage.merge(player.getUniqueId(), finalDamage, Double::sum);
        if (getRunMode() == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY) {
            plugin.getDailyManager().statsManager().recordDragonDamage(this, player.getUniqueId(), playerTotal);
        }
        int crossed = crossedDragonDamageThresholds(before, after, dragonMaxHealth);
        if (crossed <= 0) return;

        applyDragonPressure(opponent);
        sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_PRESSURE
                .replace("%team%", team.getColoredName())
                .replace("%rival%", opponent.getColoredName()));
    }

    static int crossedDragonDamageThresholds(double before, double after, double maxHealth) {
        if (maxHealth <= 0D || after <= before) return 0;
        double threshold = maxHealth * 0.2D;
        return Math.max(0, (int) Math.floor(after / threshold) - (int) Math.floor(before / threshold));
    }

    static int respawnDelaySeconds(int deathNumber) {
        return Math.min(30, Math.max(1, deathNumber) * 5);
    }

    static int platformCenterX(boolean vanillaSide) {
        return vanillaSide ? VANILLA_PLATFORM_X : MIRRORED_PLATFORM_X;
    }

    private void applyDragonPressure(@NotNull ChampionshipTeam team) {
        List<PotionEffectType> effects = List.of(PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE,
                PotionEffectType.WEAKNESS, PotionEffectType.HUNGER);
        for (Player player : team.getOnlinePlayers()) {
            for (PotionEffectType type : effects) player.addPotionEffect(new PotionEffect(type, 8 * 20, 0));
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            scheduler.runTask(plugin, () -> {
                player.spigot().respawn();
                scheduler.runTask(plugin, () -> teleportPlayerToSpawnLocation(player));
            });
            return;
        }

        int deathNumber = deathCounts.merge(player.getUniqueId(), 1, Integer::sum);
        int delaySeconds = respawnDelaySeconds(deathNumber);
        long readyAt = System.currentTimeMillis() + delaySeconds * 1000L;
        respawnReadyAt.put(player.getUniqueId(), readyAt);
        scheduleRespawnRelease(player.getUniqueId(), delaySeconds * 20L);

        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            scheduler.runTask(plugin, () -> enterRespawnCooldown(player, delaySeconds));
        });

        announceDeath(player);
    }

    private void enterRespawnCooldown(@NotNull Player player, int delaySeconds) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || !respawnReadyAt.containsKey(player.getUniqueId())) return;
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(getCentralCooldownLocation());
        Utils.sendActionBar(player, MessageConfig.DRAGON_EGG_CARNIVAL_RESPAWN_COUNTDOWN
                .replace("%time%", String.valueOf(delaySeconds)));
    }

    private void scheduleRespawnRelease(@NotNull UUID playerId, long delayTicks) {
        BukkitTask previous = respawnTasks.remove(playerId);
        if (previous != null) previous.cancel();
        respawnTasks.put(playerId, scheduler.runTaskLater(plugin, () -> releaseRespawn(playerId), Math.max(1L, delayTicks)));
    }

    private void releaseRespawn(@NotNull UUID playerId) {
        respawnTasks.remove(playerId);
        Long readyAt = respawnReadyAt.get(playerId);
        if (readyAt == null || getGameStageEnum() != GameStageEnum.PROGRESS) return;
        long remaining = readyAt - System.currentTimeMillis();
        if (remaining > 50L) {
            scheduleRespawnRelease(playerId, Math.max(1L, (remaining + 49L) / 50L));
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        respawnReadyAt.remove(playerId);
        plugin.getGameManager().getSpectatorManager().resumeParticipant(player, this);
        teleportPlayerToTeamPlatform(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0F);
        applyRespawnBuffs(player);
        Utils.sendActionBar(player, MessageConfig.DRAGON_EGG_CARNIVAL_RESPAWNED);
    }

    private void applyRespawnBuffs(@NotNull Player player) {
        for (PotionEffectType type : List.of(PotionEffectType.STRENGTH, PotionEffectType.HASTE,
                PotionEffectType.SPEED, PotionEffectType.RESISTANCE, PotionEffectType.REGENERATION)) {
            player.addPotionEffect(new PotionEffect(type, 10 * 20, 1));
        }
    }

    private void announceDeath(@NotNull Player player) {
        Player killer = player.getKiller();
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        boolean voidDeath = lastDamage != null && lastDamage.getCause() == EntityDamageEvent.DamageCause.VOID;
        String message;
        if (killer != null && !killer.equals(player)) {
            message = voidDeath ? MessageConfig.DRAGON_EGG_CARNIVAL_KILL_PLAYER_BY_VOID
                    : MessageConfig.DRAGON_EGG_CARNIVAL_KILL_PLAYER;
            message = message.replace("%killer%", Utils.formatPlayerName(killer));
        } else {
            message = voidDeath ? MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_DEATH_BY_VOID
                    : MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_DEATH;
        }
        sendMessageToAllGamePlayers(message.replace("%player%", Utils.formatPlayerName(player)));
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player) || getGameStageEnum() != GameStageEnum.PROGRESS) return;
        sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_LEAVE
                .replace("%player%", Utils.formatPlayerName(player)));
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            ChampionshipTeam team = teamOf(player);
            if (team != null) {
                if (!kitIssued.contains(player.getUniqueId())) {
                    revokeVictoryAdvancements(player);
                    giveStartingKit(player, team);
                }
                syncCrystalRewards(player, team);
            }
            Long readyAt = respawnReadyAt.get(player.getUniqueId());
            if (readyAt != null) {
                long remaining = readyAt - System.currentTimeMillis();
                if (remaining <= 0L) releaseRespawn(player.getUniqueId());
                else {
                    enterRespawnCooldown(player, (int) Math.ceil(remaining / 1000D));
                    scheduleRespawnRelease(player.getUniqueId(), Math.max(1L, (remaining + 49L) / 50L));
                }
            } else {
                teleportPlayerToTeamPlatform(player);
                player.setGameMode(GameMode.SURVIVAL);
            }
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION || getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            if ((getGameStageEnum() == GameStageEnum.COUNTDOWN || dragonReadyTask != null)
                    && !kitIssued.contains(player.getUniqueId())) {
                ChampionshipTeam team = teamOf(player);
                if (team != null) {
                    revokeVictoryAdvancements(player);
                    giveStartingKit(player, team);
                    syncCrystalRewards(player, team);
                }
            }
            teleportPlayerToSpawnLocation(player);
            return;
        }
        if (getGameStageEnum() == GameStageEnum.STOPPING || getGameStageEnum() == GameStageEnum.WAITING
                || getGameStageEnum() == GameStageEnum.END) {
            player.getInventory().clear();
            player.teleport(CCConfig.LOBBY_LOCATION);
            scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
            return;
        }

        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    public void replenishTeamConcrete(@NotNull Player player, @NotNull ItemStack placedStack) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        ChampionshipTeam team = teamOf(player);
        if (team == null || team.getConcrete() == null || placedStack.getType() != team.getConcrete().getType()) return;
        placedStack.setAmount(64);
    }

    public void teleportPlayerToSpawnLocation(@NotNull Player player) {
        if (isIntroductionPhase()) {
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            return;
        }
        if (teamOf(player) != null) {
            teleportPlayerToTeamPlatform(player);
            scheduler.runTask(plugin, () -> player.setGameMode(
                    getGameStageEnum() == GameStageEnum.PREPARATION ? GameMode.ADVENTURE : GameMode.SURVIVAL));
            return;
        }
        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    private void teleportTeamsToPlatforms() {
        teleportTeamToPlatform(rightChampionshipTeam);
        teleportTeamToPlatform(leftChampionshipTeam);
    }

    private void teleportTeamToPlatform(@Nullable ChampionshipTeam team) {
        if (team == null) return;
        prepareSpawnPlatform(team.equals(rightChampionshipTeam));
        Location spawn = platformSpawn(team.equals(rightChampionshipTeam));
        for (Player player : team.getOnlinePlayers()) player.teleport(spawn);
    }

    private void teleportPlayerToTeamPlatform(@NotNull Player player) {
        ChampionshipTeam team = teamOf(player);
        if (team == null) return;
        boolean vanillaSide = team.equals(rightChampionshipTeam);
        prepareSpawnPlatform(vanillaSide);
        player.teleport(platformSpawn(vanillaSide));
    }

    private void prepareBothSpawnPlatforms() {
        prepareSpawnPlatform(true);
        prepareSpawnPlatform(false);
    }

    private void prepareSpawnPlatform(boolean vanillaSide) {
        World world = fightWorld();
        if (world == null) return;
        int centerX = platformCenterX(vanillaSide);
        for (int x = centerX - PLATFORM_RADIUS; x <= centerX + PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                world.getBlockAt(x, PLATFORM_FLOOR_Y, z).setType(Material.OBSIDIAN, false);
                for (int y = PLATFORM_FLOOR_Y + 1; y <= PLATFORM_FLOOR_Y + PLATFORM_AIR_HEIGHT; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private Location platformSpawn(boolean vanillaSide) {
        World world = fightWorld();
        if (world == null) return getSpectatorSpawnLocation();
        float yaw = vanillaSide ? 90F : -90F;
        return new Location(world, platformCenterX(vanillaSide), PLATFORM_FLOOR_Y + 1D, 0D, yaw, 0F);
    }

    private Location getCentralCooldownLocation() {
        World world = fightWorld();
        if (world == null) return getSpectatorSpawnLocation();
        Location spectator = getSpectatorSpawnLocation();
        double y = spectator == null ? 80D : spectator.getY();
        return new Location(world, 0.5D, y, 0.5D, 0F, 35F);
    }

    private void giveStartingKits() {
        giveStartingKit(rightChampionshipTeam);
        giveStartingKit(leftChampionshipTeam);
    }

    private void giveStartingKit(@Nullable ChampionshipTeam team) {
        if (team == null) return;
        for (Player player : team.getOnlinePlayers()) giveStartingKit(player, team);
    }

    private void giveStartingKit(@NotNull Player player, @NotNull ChampionshipTeam team) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[]{
                new ItemStack(Material.DIAMOND_BOOTS),
                new ItemStack(Material.DIAMOND_LEGGINGS),
                new ItemStack(Material.DIAMOND_CHESTPLATE),
                new ItemStack(Material.DIAMOND_HELMET)
        });

        ItemStack sword = unbreakable(Material.DIAMOND_SWORD);
        ItemStack bow = unbreakable(Material.BOW);
        bow.addEnchantment(Enchants.get(EnchantmentKeys.POWER), 1);
        bow.addEnchantment(Enchants.get(EnchantmentKeys.INFINITY), 1);

        inventory.setItem(0, sword);
        inventory.setItem(1, bow);
        inventory.setItem(2, unbreakable(Material.DIAMOND_PICKAXE));
        inventory.setItem(3, unbreakable(Material.DIAMOND_AXE));
        inventory.setItem(4, team.getConcrete());
        inventory.setItem(5, stack(Material.ENDER_PEARL, 6));
        inventory.setItem(6, new ItemStack(Material.WATER_BUCKET));
        inventory.setItem(7, new ItemStack(Material.OAK_BOAT));
        inventory.setItem(8, stack(Material.COOKED_CHICKEN, 64));
        inventory.setItem(9, new ItemStack(Material.ARROW));
        inventory.setItemInOffHand(unbreakable(Material.SHIELD));
        inventory.setHeldItemSlot(0);
        deliveredCrystalRewards.put(player.getUniqueId(), 0);
        kitIssued.add(player.getUniqueId());
        player.updateInventory();
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack item = new ItemStack(material);
        item.setAmount(amount);
        return item;
    }

    private static ItemStack unbreakable(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void syncCrystalRewards(@NotNull Player player, @NotNull ChampionshipTeam team) {
        int teamCount = teamCrystalCounts.getOrDefault(team, 0);
        int delivered = deliveredCrystalRewards.getOrDefault(player.getUniqueId(), 0);
        int pearls = Math.max(0, teamCount - delivered) * CRYSTAL_PEARLS_PER_PLAYER;
        while (pearls > 0) {
            int amount = Math.min(64, pearls);
            player.getInventory().addItem(stack(Material.ENDER_PEARL, amount));
            pearls -= amount;
        }
        deliveredCrystalRewards.put(player.getUniqueId(), teamCount);
        applyTeamEnchantments(player, team);
        player.updateInventory();
    }

    private void applyTeamEnchantments(@NotNull Player player, @NotNull ChampionshipTeam team) {
        EnumMap<RewardEnchant, Integer> levels = teamEnchantments.get(team);
        if (levels == null) return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            switch (item.getType()) {
                case DIAMOND_SWORD, DIAMOND_AXE -> applyRewardEnchant(item, RewardEnchant.SHARPNESS,
                        Enchants.get(EnchantmentKeys.SHARPNESS), levels);
                case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS ->
                        applyRewardEnchant(item, RewardEnchant.PROTECTION,
                                Enchants.get(EnchantmentKeys.PROTECTION), levels);
                case BOW -> applyRewardEnchant(item, RewardEnchant.POWER,
                        Enchants.get(EnchantmentKeys.POWER), levels);
                default -> {
                }
            }
        }
    }

    private static void applyRewardEnchant(@NotNull ItemStack item, @NotNull RewardEnchant reward,
                                           @NotNull Enchantment enchantment,
                                           @NotNull EnumMap<RewardEnchant, Integer> levels) {
        int level = levels.getOrDefault(reward, 0);
        if (level > 0) item.addUnsafeEnchantment(enchantment, level);
    }

    private void revokeVictoryAdvancements() {
        for (UUID uuid : getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) revokeVictoryAdvancements(player);
        }
    }

    private void revokeVictoryAdvancements(@NotNull Player player) {
        for (String key : VICTORY_ADVANCEMENTS) {
            Advancement advancement = Bukkit.getAdvancement(NamespacedKey.minecraft(key));
            if (advancement == null) continue;
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : List.copyOf(progress.getAwardedCriteria())) progress.revokeCriteria(criterion);
        }
    }

    private void updateTeamProgress(@NotNull ChampionshipTeam team, int count) {
        if (team.equals(rightChampionshipTeam)) rightTeamPoints = count;
        if (team.equals(leftChampionshipTeam)) leftTeamPoints = count;
    }

    @Nullable
    private ChampionshipTeam teamOf(@NotNull Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return null;
        if (team.equals(rightChampionshipTeam) || team.equals(leftChampionshipTeam)) return team;
        return null;
    }

    @Nullable
    private ChampionshipTeam opposingTeam(@Nullable ChampionshipTeam team) {
        if (team == null) return null;
        if (team.equals(rightChampionshipTeam)) return leftChampionshipTeam;
        if (team.equals(leftChampionshipTeam)) return rightChampionshipTeam;
        return null;
    }

    private static String advancementDisplayName(String key) {
        return switch (key) {
            case "end/kill_dragon" -> "解放末地";
            case "end/dragon_egg" -> "下一世代";
            case "end/enter_end_gateway" -> "远程折跃";
            default -> key;
        };
    }

    private void addFinalTagToTeam(@Nullable ChampionshipTeam team) {
        if (team != null) for (Player player : team.getOnlinePlayers()) player.addScoreboardTag("final");
    }

    private void removeFinalTagFromTeam(@Nullable ChampionshipTeam team) {
        if (team != null) for (Player player : team.getOnlinePlayers()) player.removeScoreboardTag("final");
    }

    private void cancelRespawnTasks() {
        for (BukkitTask task : respawnTasks.values()) task.cancel();
        respawnTasks.clear();
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return gameConfig.getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;

        if (startGameProgressTask != null) startGameProgressTask.cancel();
        if (dragonReadyTask != null) dragonReadyTask.cancel();
        startGameProgressTask = null;
        dragonReadyTask = null;
        cancelFinalCountdown();
        cancelRespawnTasks();
        respawnReadyAt.clear();

        removeFinalTagFromTeam(rightChampionshipTeam);
        removeFinalTagFromTeam(leftChampionshipTeam);
        announceGameEnd(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END_TITLE,
                MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END_SUBTITLE);
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        if (isSettlementAllowed() && winningTeam != null) {
            Utils.sendMessageToAllPlayers(MessageConfig.DRAGON_EGG_CARNIVAL_WIN
                    .replace("%team%", winningTeam.getColoredName()));
        }
        publishGameEndEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));
        finishPostGameAfterEndEvent();
    }

    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }

    @Nullable
    private World fightWorld() {
        return Bukkit.getWorld(getWorldName());
    }

    @Override
    public DragonEggCarnivalConfig getGameConfig() {
        return (DragonEggCarnivalConfig) gameConfig;
    }

    @Override
    public DragonEggCarnivalHandler getGameHandler() {
        return (DragonEggCarnivalHandler) gameHandler;
    }
}
