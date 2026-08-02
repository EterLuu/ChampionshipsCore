package ink.ziip.championshipscore.api.game.skywars;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.skywars.SkyWarsShrink;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class SkyWarsTeamArea extends BaseMultiTeamGameInstance {
    @Getter
    private final List<UUID> deathPlayer = new CopyOnWriteArrayList<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    private volatile List<SkyWarsShrink> shrinkTimes = List.of();
    private final Map<ChampionshipTeam, Location> teamSpawnLocations = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, HappyGhast> teamHappyGhasts = new ConcurrentHashMap<>();
    @Getter
    private volatile int timer;
    private volatile ScheduledTask startGamePreparationTask;
    private volatile ScheduledTask startGameProgressTask;
    private volatile ScheduledTask borderCheckTask;
    private volatile double radius;
    private volatile double shrink;
    private volatile double height;
    private volatile double heightShrink;
    private volatile double low;
    private volatile double lowShrink;
    private final SkyWarsVariantRegistry variantRegistry;
    private volatile SkyWarsVariant resolvedVariant;
    private volatile SkyWarsMapGeometry mapGeometry;

    @Override
    public void resetArea() {
        deathPlayer.clear();
        teamDeathPlayers.clear();
        teamSpawnLocations.clear();
        teamHappyGhasts.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        borderCheckTask = null;

        preloadMap();
    }

    @Override
    public void resetGame() {
        cancelIntroduction();
        cancelFinalCountdown();
        resetBaseArea();
        playerPoints.clear();
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return mapGeometry().getSpectatorSpawn();
    }

    public SkyWarsTeamArea(ChampionshipsCore plugin, SkyWarsConfig skyWarsConfig, boolean firstTime, String areaName) {
        this(plugin, skyWarsConfig, firstTime, areaName, null);
    }

    public SkyWarsTeamArea(ChampionshipsCore plugin, SkyWarsConfig skyWarsConfig, boolean firstTime,
                           String areaName, SkyWarsVariantRegistry variantRegistry) {
        super(plugin, GameTypeEnum.SkyWars, new SkyWarsHandler(plugin), skyWarsConfig);
        this.variantRegistry = variantRegistry;

        getGameHandler().setSkyWarsArea(this);
        skyWarsConfig.setAreaName(areaName);

        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }

    }

    /** Preloads a clean arena at startup and immediately after each completed game. */
    public void preloadMap() {
        // loadMap teleports retained spectators after recreating the world. Do not let that teleport
        // resolve through a Location cached against the world instance which is about to be unloaded.
        mapGeometry = null;
        resolvedVariant = null;
        loadPublishedMapOrDraft(World.Environment.NORMAL).thenAccept(loaded -> {
            if (!loaded) return;
            resolvedVariant = resolveVariant();
            mapGeometry = getGameConfig().resolveMapGeometry();
            reloadShrinkTimes();
        });
    }

    private SkyWarsVariant variant() {
        if (resolvedVariant == null) {
            resolvedVariant = resolveVariant();
        }
        return resolvedVariant;
    }

    private SkyWarsVariant resolveVariant() {
        return variantRegistry == null
                ? getGameConfig().resolveInlineVariant()
                : variantRegistry.resolve(getGameConfig());
    }

    @Override
    protected List<List<String>> getIntroductionRules() {
        return variant().presentation().ruleSections();
    }

    private SkyWarsMapGeometry mapGeometry() {
        if (mapGeometry == null) {
            mapGeometry = getGameConfig().resolveMapGeometry();
        }
        return mapGeometry;
    }

    private void reloadShrinkTimes() {
        List<SkyWarsShrink> updated = new ArrayList<>();
        List<String> configuredShrinkTimes = variant().rules().boundary().shrinkSchedule();
        if (configuredShrinkTimes != null) {
            for (String key : configuredShrinkTimes) {
                String[] shrinkTimeSetting = key.split(":");
                if (shrinkTimeSetting.length == 4) {
                    try {
                        int start = Integer.parseInt(shrinkTimeSetting[0]);
                        int end = Integer.parseInt(shrinkTimeSetting[1]);
                        int toRadius = Integer.parseInt(shrinkTimeSetting[2]);
                        int toHeight = Integer.parseInt(shrinkTimeSetting[3]);
                        updated.add(new SkyWarsShrink(start, end, toRadius, toHeight));
                    } catch (NumberFormatException e) {
                        logGame(Level.WARNING, "配置", "无效的边界收缩配置=" + key);
                    }
                } else {
                    logGame(Level.WARNING, "配置", "无效的边界收缩配置=" + key);
                }
            }
        }
        shrinkTimes = List.copyOf(updated);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        teleportAllPlayers(mapGeometry().getPreparationSpawn());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.SKY_WARS_START_PREPARATION,
                MessageConfig.SKY_WARS_START_PREPARATION_TITLE, MessageConfig.SKY_WARS_START_PREPARATION_SUBTITLE);

        SkyWarsBoundaryRules boundary = variant().rules().boundary();
        setBorderShrinkTask(boundary.enableAtRemainingSeconds(),
                0,
                boundary.radius(),
                boundary.defaultHeight(),
                boundary.lowestHeight(),
                boundary.radius(),
                boundary.middleHeight()
        );

        timer = variant().lifecycle().preparationSeconds();
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            // changeLevelForAllGamePlayers(timer);
            showPreparationCountdown(timer);

            if (timer == 0) {
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
                startGameProgress();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllTeamPlayersToSpawnPoints();

        int offlinePlayers = 0;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                deathPlayer.add(uuid);
                offlinePlayers++;
            }
        }
        addPointsToAllSurvivePlayers(offlinePlayers * variant().scoring().playerEliminationSurvival());

        for (UUID uuid : deathPlayer) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam != null) {
                addTeamDeathPlayer(championshipTeam, false);
                logGame(Level.INFO, "玩家", "玩家=" + playerManager.getPlayerName(uuid) + " uuid=" + uuid
                        + " 状态=离线，计入淘汰");
            }
        }

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        startFinalCountdown(variant().lifecycle().countdownSeconds(),
                MessageConfig.SKY_WARS_GAME_START_SOON_TITLE,
                MessageConfig.SKY_WARS_GAME_START_TITLE, MessageConfig.SKY_WARS_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        if (variant().rules().glassCage()) {
            for (ChampionshipTeam championshipTeam : gameTeams) {
                for (Player player : championshipTeam.getOnlinePlayers()) {
                    scheduler.runEntity(player, () -> clearGlassCages(player));
                    break;
                }
            }
        }

        int duration = variant().lifecycle().durationSeconds();
        startGameProgressTask = startRemainingTimer(duration, seconds -> {
            timer = seconds;
            // changeLevelForAllGamePlayers(timer);
            String timerMessage = MessageConfig.SKY_WARS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer));
            sendActionBarToActiveGamePlayers(timerMessage);
            updateSpectatorTimerBossBar(timerMessage, timer, duration);

            if (timer == 0)
                return;

            if (timer == variant().rules().boundary().enableAtRemainingSeconds()) {
                startBorderShrink();
            }

            Integer spawnHappyGhastTime = variant().rules().spawnHappyGhastAtRemainingSeconds();
            if (spawnHappyGhastTime != null && timer == spawnHappyGhastTime) {
                spawnTeamHappyGhasts();
            }

            for (SkyWarsShrink skyWarsShrink : shrinkTimes) {
                if (timer == skyWarsShrink.getStartTime()) {
                    setBorderShrinkTask(skyWarsShrink.getStartTime(),
                            skyWarsShrink.getEndTime(),
                            radius,
                            height,
                            low,
                            skyWarsShrink.getToRadius(),
                            skyWarsShrink.getToHeight()
                    );
                    sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_BOARD_SHRINK);
                    playSoundToAllGamePlayers(Sound.BLOCK_ANVIL_USE, 1, 12F);
                }
                if (timer == skyWarsShrink.getEndTime()) {
                    lowShrink = 0;
                    heightShrink = 0;
                    shrink = 0;
                    sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_STOP_BOARD_SHRINK);
                    playSoundToAllGamePlayers(Sound.BLOCK_BELL_USE, 1, 12F);
                }
            }

            if (timer == variant().rules().disableHealthRegainAtRemainingSeconds()) {
                sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_DEDUCT_FOOD_LEVEL);
            }

            if (timer <= variant().rules().disableHealthRegainAtRemainingSeconds()) {
                damageAllPlayers();
            }

        }, this::endGame);
    }

    private void setBorderShrinkTask(int start, int end, double startRadius, double startHeight, double startLow, int toRadius, int toHeight) {
        radius = startRadius;
        shrink = (radius - toRadius) / (start - end);

        height = startHeight;
        low = startLow;

        heightShrink = (height - toHeight) / (start - end);
        lowShrink = heightShrink;
    }

    protected void startBorderShrink() {
        final List<UUID> gamePlayersCopy = new ArrayList<>(gamePlayers);
        borderCheckTask = scheduler.runTaskTimerAsynchronously(plugin, () -> {
            Location center = mapGeometry().getPreparationSpawn();

            for (UUID uuid : gamePlayersCopy) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) {
                    double boundaryRadius = radius;
                    double boundaryHigh = height;
                    double boundaryLow = low;
                    scheduler.runEntity(player, () -> {
                        Location location = player.getLocation();
                        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(player);
                        double distance = Math.hypot(
                                center.getX() - location.getX(), center.getZ() - location.getZ());

                        if (boundaryRadius - 10 < distance && distance < boundaryRadius + 10)
                            setParticles(player, boundaryRadius > 20);
                        if (location.getY() > boundaryHigh - 10 || location.getY() < boundaryLow + 10) {
                            setHeightParticles(player, boundaryHigh);
                            setHeightParticles(player, boundaryLow);
                        }

                        if (distance >= boundaryRadius || location.getY() > boundaryHigh
                                || location.getY() < boundaryLow) {
                            player.damage(1);
                            championshipPlayer.setRedScreen();
                            championshipPlayer.sendActionBar(MessageConfig.SKY_WARS_OUT_OF_BORDER);
                        } else {
                            championshipPlayer.removeRedScreen();
                        }
                    });
                }
            }
            height = height - heightShrink;
            low = low + lowShrink;
            radius = radius - shrink;
            if (radius < 0)
                radius = 0;
            int middleHeight = variant().rules().boundary().middleHeight();
            if (height < middleHeight)
                height = middleHeight;
            if (low > middleHeight)
                low = middleHeight;

        }, 0, 20L);
    }

    private void setParticles(Player player, boolean byAngle) {
        Location center = mapGeometry().getPreparationSpawn();
        Location location = player.getLocation();
        if (location.getWorld() != null) {
            double alpha = Math.atan2(location.getZ() - center.getZ(), location.getX() - center.getX());
            for (double h = location.getY() - 3; h < location.getY() + 5; h++) {
                double beta = byAngle ? alpha - 0.0872 : 0;
                double endBeta = byAngle ? alpha + 0.0872 : 20;
                double increment = byAngle ? 0.01 : 1;
                for (; beta <= endBeta; beta += increment) {
                    double x = center.getX() + radius * Math.cos(beta);
                    double z = center.getZ() + radius * Math.sin(beta);
                    player.spawnParticle(Particle.DUST, new Location(center.getWorld(), x, h, z), 1,
                            new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                }
            }
        }
    }

    private void setHeightParticles(Player player, double y) {
        Location location = player.getLocation();
        if (location.getWorld() != null) {
            for (int particleRadius = 1; particleRadius < 5; particleRadius++) {
                for (double beta = 0; beta <= 20; beta += 1) {
                    double x = location.getX() + particleRadius * Math.cos(beta);
                    double z = location.getZ() + particleRadius * Math.sin(beta);
                    player.spawnParticle(Particle.DUST, new Location(location.getWorld(), x, y, z), 1,
                            new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                }
            }
        }
    }

    @Override
    public synchronized void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (borderCheckTask != null)
            borderCheckTask.cancel();

        removeSpawnedHappyGhasts();
        teamSpawnLocations.clear();

        calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(MessageConfig.SKY_WARS_GAME_END_TITLE, MessageConfig.SKY_WARS_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        resetGame();
    }

    protected void calculatePoints() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, variant().scoring().survive());
            }
        }

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
    }

    private synchronized void addTeamDeathPlayer(ChampionshipTeam championshipTeam, boolean addedPoints) {
        Integer deathPlayer = teamDeathPlayers.merge(championshipTeam, 1, Integer::sum);
        logGame(Level.INFO, "淘汰", "队伍=" + championshipTeam.getName() + " 已淘汰人数=" + deathPlayer);
        if (deathPlayer != null) {
            if (deathPlayer == championshipTeam.getMembers().size()) {
                sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_WHOLE_TEAM_WAS_KILLED.replace("%team%", championshipTeam.getColoredName()));
                killTeamHappyGhast(championshipTeam);
                if (addedPoints)
                    addPointsToAllSurvivePlayers(variant().scoring().teamEliminationSurvival());
            }
        }
    }

    protected void addDeathPlayer(Player player) {
        addDeathPlayer(player.getUniqueId());
    }

    private synchronized void addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid))
            return;

        deathPlayer.add(uuid);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (championshipTeam != null) {
            addTeamDeathPlayer(championshipTeam, true);
        }
        addPointsToAllSurvivePlayers(variant().scoring().playerEliminationSurvival());
    }

    private void addPointsToAllSurvivePlayers(int points) {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, points);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            scheduler.runEntity(player, () -> {
                event.getEntity().spigot().respawn();
                event.getEntity().teleportAsync(getSpectatorSpawnLocation());
            });
            player.teleportAsync(getPreparationTeleportLocation(mapGeometry().getPreparationSpawn()));
            return;
        }

        scheduler.runEntity(player, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleportAsync(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId()))
            return;

        spawnTomb(player, event.getDrops());

        addDeathPlayer(player);

        Player assailant = player.getKiller();
        EntityDamageEvent entityDamageEvent = player.getLastDamageCause();

        if (assailant != null) {
            ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
            ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

            if (playerTeam == null || assailantTeam == null)
                return;

            String message = MessageConfig.SKY_WARS_KILL_PLAYER;

            if (entityDamageEvent != null) {
                EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                    message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_VOID;
                }
            }

            if (playerTeam.equals(assailantTeam)) {
                message = MessageConfig.SKY_WARS_KILL_TEAM_PLAYER;
                message = message
                        .replace("%player%", Utils.formatPlayerName(player))
                        .replace("%killer%", Utils.formatPlayerName(assailant));
                sendMessageToAllGamePlayers(message);
                return;
            }

            message = message
                    .replace("%player%", Utils.formatPlayerName(player))
                    .replace("%killer%", Utils.formatPlayerName(assailant));

            sendMessageToAllGamePlayers(message);

            addPlayerPoints(assailant.getUniqueId(), variant().scoring().kill());
        } else {

            String message = MessageConfig.SKY_WARS_PLAYER_DEATH;

            if (entityDamageEvent != null) {
                EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                    message = MessageConfig.SKY_WARS_PLAYER_DEATH_BY_VOID;
                }
            }

            message = message.replace("%player%", Utils.formatPlayerName(player));
            sendMessageToAllGamePlayers(message);
        }
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId()))
            return;

        sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_PLAYER_LEAVE
                .replace("%player%", Utils.formatPlayerName(player)));
        addDeathPlayer(player);
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleportAsync(getPreparationTeleportLocation(mapGeometry().getPreparationSpawn()));
            return;
        }

        player.teleportAsync(getSpectatorSpawnLocation());
        scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    public int getPlayerBoarderDistance(Player player) {
        Location location = player.getLocation();
        Location center = getSpectatorSpawnLocation();
        double distance = Math.hypot(center.getX() - location.getX(), center.getZ() - location.getZ());
        return (int) Math.abs(radius - distance);
    }

    public void spawnTomb(Player player, List<ItemStack> items) {
        if (player == null || !gamePlayers.contains(player.getUniqueId())) {
            return;
        }

        Location location = player.getLocation();
        World world = location.getWorld();

        if (world != null) {
            if (world.getBlockAt(location).getType() != Material.AIR) {
                for (int i = 1; i <= 5; i++) {
                    Location belowLocation = location.clone().add(0, i, 0);
                    if (world.getBlockAt(belowLocation).getType() == Material.AIR) {
                        location = belowLocation;
                        break;
                    }
                }
            }

            if (world.getBlockAt(location).getType() != Material.AIR) {
                return;
            }

            world.getBlockAt(location).setType(Material.CHEST);
            world.spawnParticle(Particle.DUST, location.clone().add(0.5, 0.5, 0.5), 100, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));

            Chest chest = (Chest) world.getBlockAt(location).getState();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    chest.getInventory().addItem(item);
                }
            }

            items.clear();
        }
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public int getSurvivedTeamNums() {
        int i = 0;
        for (ChampionshipTeam championshipTeam : teamDeathPlayers.keySet()) {
            if (teamDeathPlayers.get(championshipTeam) == championshipTeam.getMembers().size())
                i++;
        }
        return gameTeams.size() - i;
    }

    private void teleportAllTeamPlayersToSpawnPoints() {
        Iterator<String> spawnPointsI = mapGeometry().getTeamSpawns().iterator();

        Collections.shuffle(gameTeams);

        for (ChampionshipTeam championshipTeam : gameTeams) {
            Location location;
            if (spawnPointsI.hasNext()) {
                location = Utils.getLocation(spawnPointsI.next());
                for (int i = 0; i < championshipTeam.getOnlinePlayers().size(); i++) {
                    Player player = championshipTeam.getOnlinePlayers().get(i);
                    if (player != null) {
                        Location spawnLocation = location.clone();
                        spawnLocation.setX(spawnLocation.getX() + (i % 2 == 0 ? -1 : 1));
                        spawnLocation.setZ(spawnLocation.getZ() + (i < 2 ? -1 : 1));
                        player.teleportAsync(spawnLocation);
                    }
                }
            } else {
                spawnPointsI = mapGeometry().getTeamSpawns().iterator();
                location = Utils.getLocation(spawnPointsI.next());
                championshipTeam.teleportAllPlayers(location);
            }
            // Record the raw spawn point (without player offsets) so the team's
            // happy ghast can be spawned exactly here later in the game.
            teamSpawnLocations.put(championshipTeam, location.clone());
        }
    }

    /**
     * Spawn a stationary, no-AI happy ghast wearing the team-colored harness at
     * each team's spawn point. Triggered at the configured time (default: 2
     * minutes into the game). No AI is enough to keep it at the spawn point:
     * the happy ghast hovers via its FloatGoal (an AI goal, disabled by NoAI),
     * and gravity is only applied inside travel(), which NoAI skips - so it
     * neither falls nor drifts away. 50 HP.
     */
    private void spawnTeamHappyGhasts() {
        for (Map.Entry<ChampionshipTeam, Location> entry : teamSpawnLocations.entrySet()) {
            ChampionshipTeam team = entry.getKey();
            if (teamDeathPlayers.getOrDefault(team, 0) >= team.getMembers().size()) {
                continue;
            }

            Location baseLocation = entry.getValue();
            World world = baseLocation.getWorld();
            if (world == null) {
                logGame(Level.WARNING, "实体", "队伍=" + team.getName() + " 未生成快乐恶魂：出生点世界为空");
                continue;
            }

            Location spawnLocation = baseLocation.clone().add(0, 2, 0);
            scheduler.runAtLocation(spawnLocation, () -> {
                if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
                HappyGhast happyGhast = (HappyGhast) world.spawnEntity(spawnLocation, EntityType.HAPPY_GHAST);
                if (happyGhast == null) {
                    logGame(Level.WARNING, "实体", "队伍=" + team.getName() + " 的快乐恶魂生成被取消");
                    return;
                }

                happyGhast.setAI(false);
                happyGhast.setPersistent(false);
                happyGhast.setAdult();

                Material harnessMaterial = Material.getMaterial(team.getColorName() + "_HARNESS");
                if (harnessMaterial != null) {
                    EntityEquipment equipment = happyGhast.getEquipment();
                    if (equipment != null) equipment.setItem(EquipmentSlot.BODY, new ItemStack(harnessMaterial));
                } else {
                    logGame(Level.WARNING, "实体", "队伍=" + team.getName() + " 颜色=" + team.getColorName()
                            + " 未找到对应挽具");
                }

                AttributeInstance maxHealth = happyGhast.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(50.0);
                    happyGhast.setHealth(50.0);
                }

                HappyGhast previousHappyGhast = teamHappyGhasts.put(team, happyGhast);
                if (previousHappyGhast != null) scheduler.runEntity(previousHappyGhast, previousHappyGhast::remove);
                logGame(Level.INFO, "实体", "队伍=" + team.getName() + " 已生成快乐恶魂，挽具=" + team.getColorName());
            });
        }
    }

    public boolean isTeamHappyGhast(HappyGhast happyGhast) {
        return teamHappyGhasts.containsValue(happyGhast);
    }

    public boolean canRideTeamHappyGhast(Player player, HappyGhast happyGhast) {
        UUID playerId = player.getUniqueId();
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !gamePlayers.contains(playerId)
                || deathPlayer.contains(playerId)) {
            return false;
        }

        ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(playerId);
        return playerTeam != null && happyGhast.equals(teamHappyGhasts.get(playerTeam));
    }

    private void killTeamHappyGhast(ChampionshipTeam team) {
        HappyGhast happyGhast = teamHappyGhasts.remove(team);
        if (happyGhast != null) scheduler.runEntity(happyGhast, () -> {
            if (!happyGhast.isDead()) happyGhast.setHealth(0);
            logGame(Level.INFO, "实体", "队伍=" + team.getName() + " 已淘汰，对应快乐恶魂死亡");
        });
    }

    private void removeSpawnedHappyGhasts() {
        for (HappyGhast happyGhast : teamHappyGhasts.values()) {
            if (happyGhast != null) scheduler.runEntity(happyGhast, happyGhast::remove);
        }
        teamHappyGhasts.clear();
    }

    private void damageAllPlayers() {
        Collections.shuffle(gamePlayers);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null) {
                if (!deathPlayer.contains(player.getUniqueId())) {
                    scheduler.runEntity(player, () -> {
                        int level = player.getFoodLevel() - 1;
                        player.setFoodLevel(Math.max(level, 0));
                    });
                }
            }
        }
    }

    private void clearGlassCages(Player player) {
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(player.getWorld());

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            Location playerLocation = player.getLocation();
            int radius = 5;
            BlockVector3 pos1 = BukkitAdapter.asBlockVector(playerLocation.clone().add(-radius, -radius, -radius));
            BlockVector3 pos2 = BukkitAdapter.asBlockVector(playerLocation.clone().add(radius, radius, radius));

            Region region = new CuboidRegion(pos1, pos2);
            Set<BaseBlock> baseBlocks = new HashSet<>();
            baseBlocks.add(new BaseBlock(BukkitAdapter.asBlockState(new ItemStack(Material.GLASS))));
            editSession.replaceBlocks(region, baseBlocks, BlockTypes.AIR);
        } catch (Exception ignored) {
        }
    }

    private void giveItemToAllGamePlayers() {
//        ItemStack bread = new ItemStack(Material.BREAD);
//        bread.setAmount(8);
//
//        ItemStack sword = new ItemStack(Material.IRON_SWORD);
//        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
//        pickaxe.addEnchantment(Enchants.get(EnchantmentKeys.EFFICIENCY), 3);
//        ItemStack bow = new ItemStack(Material.BOW);
//        ItemStack arrows = new ItemStack(Material.ARROW);
//        arrows.setAmount(4);
//        ItemStack chestPlate = new ItemStack(Material.IRON_CHESTPLATE);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                scheduler.runEntity(player, () -> {
                    PlayerInventory inventory = player.getInventory();
//                inventory.addItem(bread.clone());
//                inventory.addItem(sword.clone());
//                inventory.addItem(pickaxe.clone());
//                inventory.addItem(bow.clone());
//                inventory.addItem(arrows.clone());
//                inventory.setChestplate(chestPlate);
                if (championshipTeam != null) {
//                    inventory.addItem(championshipTeam.getConcrete());
//                    inventory.addItem(championshipTeam.getConcrete());
//                    inventory.setLeggings(championshipTeam.getLeggings());
                    inventory.setBoots(championshipTeam.getBoots());
                }
                });
            }
        }
    }

    @Override
    public SkyWarsConfig getGameConfig() {
        return (SkyWarsConfig) gameConfig;
    }

    @Override
    public SkyWarsHandler getGameHandler() {
        return (SkyWarsHandler) gameHandler;
    }

    public String getWorldName() {
        return "skywars_" + gameConfig.getAreaName();
    }
}
