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
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SkyWarsTeamArea extends BaseMultiTeamGameInstance {
    @Getter
    private final List<UUID> deathPlayer = new ArrayList<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    private final List<SkyWarsShrink> shrinkTimes = new ArrayList<>();
    private final Map<ChampionshipTeam, Location> teamSpawnLocations = new HashMap<>();
    private final Map<ChampionshipTeam, HappyGhast> teamHappyGhasts = new HashMap<>();
    @Getter
    private int timer;
    private BukkitTask startGameProgressTask;
    private BukkitTask borderCheckTask;
    private double radius;
    private double shrink;
    private double height;
    private double heightShrink;
    private double low;
    private double lowShrink;
    private final SkyWarsVariantRegistry variantRegistry;
    private SkyWarsVariant resolvedVariant;
    private SkyWarsMapGeometry mapGeometry;

    @Override
    public void resetArea() {
        deathPlayer.clear();
        teamDeathPlayers.clear();
        teamSpawnLocations.clear();
        teamHappyGhasts.clear();

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
        return mapGeometry().getBoundaryCenter();
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
        loadPublishedMapOrDraft(World.Environment.NORMAL);
        resolvedVariant = resolveVariant();
        mapGeometry = getGameConfig().resolveMapGeometry();
        reloadShrinkTimes();
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
        shrinkTimes.clear();
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
                        shrinkTimes.add(new SkyWarsShrink(start, end, toRadius, toHeight));
                    } catch (NumberFormatException e) {
                        logGame(Level.WARNING, "配置", "无效的边界收缩配置=" + key);
                    }
                } else {
                    logGame(Level.WARNING, "配置", "无效的边界收缩配置=" + key);
                }
            }
        }
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

        assignTeamSpawnPoints();
        teleportAllPlayersToAssignedTeamSpawns();

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

        startGameProgress();
    }

    protected void startGameProgress() {
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
                    clearGlassCages(player);
                    break;
                }
            }
        }

        int duration = variant().lifecycle().durationSeconds();
        startGameProgressTask = startRemainingTimer(duration, seconds -> {
            timer = seconds;
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
                if (spawnTeamHappyGhasts() > 0) {
                    sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_HAPPY_GHAST_SPAWNED);
                    playSoundToAllGamePlayers(Sound.BLOCK_BELL_USE, 1, 1.2F);
                }
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
            Location center = mapGeometry().getBoundaryCenter();

            for (UUID uuid : gamePlayersCopy) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) {
                    Location location = player.getLocation();
                    ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(player);

                    double distance = Math.hypot(center.getX() - location.getX(), center.getZ() - location.getZ());

                    if (radius - 10 < distance && distance < radius + 10) {
                        setParticles(player, !(radius <= 20));
                    }

                    if (location.getY() > height - 10 || location.getY() < low + 10) {
                        setHeightParticles(player, height);
                        setHeightParticles(player, low);
                    }

                    if (distance >= radius || location.getY() > height || location.getY() < low) {
                        scheduler.runTask(plugin, () -> player.damage(1));
                        championshipPlayer.setRedScreen();
                        championshipPlayer.sendActionBar(MessageConfig.SKY_WARS_OUT_OF_BORDER);
                    } else {
                        championshipPlayer.removeRedScreen();
                    }
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
        scheduler.runTaskAsynchronously(plugin, () -> {
            Location center = mapGeometry().getBoundaryCenter();
            Location location = player.getLocation();
            World world = location.getWorld();

            double x = center.getX();
            double z = center.getZ();
            double x1 = location.getX();
            double z1 = location.getZ();
            double y = location.getY();

            if (world != null) {

                double alpha = Math.atan2(z1 - z, x1 - x);

                for (double h = y - 3; h < y + 5; h++) {
                    double beta, endBeta, increment;
                    if (byAngle) {
                        beta = alpha - 0.0872;
                        endBeta = alpha + 0.0872;
                        increment = 0.01;
                    } else {
                        beta = 0;
                        endBeta = 20;
                        increment = 1;
                    }
                    for (; beta <= endBeta; beta += increment) {
                        double x2 = center.getX() + radius * Math.cos(beta);
                        double z2 = center.getZ() + radius * Math.sin(beta);
                        Location particleLoc = new Location(center.getWorld(), x2, h, z2);
                        player.spawnParticle(Particle.DUST, particleLoc, 1, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                    }
                }
            }
        });
    }

    private void setHeightParticles(Player player, double y) {
        scheduler.runTaskAsynchronously(plugin, () -> {
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world != null) {
                for (int radius = 1; radius < 5; radius++) {
                    for (double beta = 0; beta <= 20; beta += 1) {
                        double x2 = location.getX() + radius * Math.cos(beta);
                        double z2 = location.getZ() + radius * Math.sin(beta);
                        Location particleLoc = new Location(location.getWorld(), x2, y, z2);
                        player.spawnParticle(Particle.DUST, particleLoc, 1, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                    }
                }
            }
        });
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (borderCheckTask != null)
            borderCheckTask.cancel();

        removeSpawnedHappyGhasts();
        teamSpawnLocations.clear();

        calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(MessageConfig.SKY_WARS_GAME_END_TITLE, MessageConfig.SKY_WARS_GAME_END_SUBTITLE);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
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

    private void addTeamDeathPlayer(ChampionshipTeam championshipTeam, boolean addedPoints) {
        teamDeathPlayers.put(championshipTeam, teamDeathPlayers.getOrDefault(championshipTeam, 0) + 1);
        Integer deathPlayer = teamDeathPlayers.get(championshipTeam);
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
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                if (!restoreSharedPreGameParticipant(event.getEntity())) {
                    teleportPlayerToAssignedTeamSpawn(event.getEntity());
                    event.getEntity().setGameMode(GameMode.ADVENTURE);
                }
            });
            return;
        }

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(getSpectatorSpawnLocation());
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
            if (!restoreSharedPreGameParticipant(player)) {
                teleportPlayerToAssignedTeamSpawn(player);
                player.setGameMode(GameMode.ADVENTURE);
            }
            return;
        }

        if ((getGameStageEnum() == GameStageEnum.COUNTDOWN || getGameStageEnum() == GameStageEnum.PROGRESS)
                && !deathPlayer.contains(player.getUniqueId())) {
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
            Location spawn = team == null ? null : teamSpawnLocations.get(team);
            if (spawn != null) {
                player.teleport(spawn);
                player.setGameMode(GameMode.SURVIVAL);
                return;
            }
        }
        player.teleport(getSpectatorSpawnLocation());
        player.setGameMode(getGameStageEnum() == GameStageEnum.END ? GameMode.ADVENTURE : GameMode.SPECTATOR);
    }

    public int getPlayerBoarderDistance(Player player) {
        Location location = player.getLocation();
        Location center = mapGeometry().getBoundaryCenter();
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

    private void assignTeamSpawnPoints() {
        Iterator<String> spawnPointsI = mapGeometry().getTeamSpawns().iterator();

        Collections.shuffle(gameTeams);

        for (ChampionshipTeam championshipTeam : gameTeams) {
            if (!spawnPointsI.hasNext()) {
                spawnPointsI = mapGeometry().getTeamSpawns().iterator();
            }
            Location location = Utils.getLocation(spawnPointsI.next());
            // Record the raw spawn point (without player offsets) so the team's
            // happy ghast can be spawned exactly here later in the game.
            teamSpawnLocations.put(championshipTeam, location.clone());
        }
    }

    private void teleportAllPlayersToAssignedTeamSpawns() {
        for (ChampionshipTeam team : gameTeams) {
            Location location = teamSpawnLocations.get(team);
            if (location == null) continue;
            List<Player> onlinePlayers = team.getOnlinePlayers();
            for (int i = 0; i < onlinePlayers.size(); i++) {
                Player player = onlinePlayers.get(i);
                Location spawn = location.clone();
                spawn.setX(spawn.getX() + (i % 2 == 0 ? -1 : 1));
                spawn.setZ(spawn.getZ() + (i < 2 ? -1 : 1));
                player.teleport(spawn);
            }
        }
    }

    public void teleportPlayerToAssignedTeamSpawn(@NotNull Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        Location assigned = team == null ? null : teamSpawnLocations.get(team);
        Location fallback = assigned == null ? getSpectatorSpawnLocation() : assigned;
        player.teleport(getPreparationTeleportLocation(fallback));
    }

    /**
     * Spawn a stationary, no-AI happy ghast wearing the team-colored harness at
     * each team's spawn point. Triggered at the configured time (default: 2
     * minutes into the game). No AI is enough to keep it at the spawn point:
     * the happy ghast hovers via its FloatGoal (an AI goal, disabled by NoAI),
     * and gravity is only applied inside travel(), which NoAI skips - so it
     * neither falls nor drifts away. 50 HP.
     */
    private int spawnTeamHappyGhasts() {
        int spawned = 0;
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
            HappyGhast happyGhast = (HappyGhast) world.spawnEntity(spawnLocation, EntityType.HAPPY_GHAST);
            if (happyGhast == null) {
                logGame(Level.WARNING, "实体", "队伍=" + team.getName() + " 的快乐恶魂生成被取消");
                continue;
            }

            happyGhast.setAI(false);
            happyGhast.setPersistent(false);
            happyGhast.setAdult();

            // Team-colored harness in the BODY slot (same slot as llama carpet / wolf armor).
            Material harnessMaterial = Material.getMaterial(team.getColorName() + "_HARNESS");
            if (harnessMaterial != null) {
                EntityEquipment equipment = happyGhast.getEquipment();
                if (equipment != null) {
                    equipment.setItem(EquipmentSlot.BODY, new ItemStack(harnessMaterial));
                }
            } else {
                logGame(Level.WARNING, "实体", "队伍=" + team.getName() + " 颜色=" + team.getColorName()
                        + " 未找到对应挽具");
            }

            // 50 HP: raise max health first so setHealth(50) is not capped.
            AttributeInstance maxHealth = happyGhast.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(50.0);
                happyGhast.setHealth(50.0);
            }

            HappyGhast previousHappyGhast = teamHappyGhasts.put(team, happyGhast);
            if (previousHappyGhast != null && !previousHappyGhast.isDead()) {
                previousHappyGhast.remove();
            }

            logGame(Level.INFO, "实体", "队伍=" + team.getName() + " 已生成快乐恶魂，挽具=" + team.getColorName());
            spawned++;
        }
        return spawned;
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
        if (happyGhast != null && !happyGhast.isDead()) {
            happyGhast.setHealth(0);
            logGame(Level.INFO, "实体", "队伍=" + team.getName() + " 已淘汰，对应快乐恶魂死亡");
        }
    }

    private void removeSpawnedHappyGhasts() {
        for (HappyGhast happyGhast : teamHappyGhasts.values()) {
            if (happyGhast != null && !happyGhast.isDead()) {
                happyGhast.remove();
            }
        }
        teamHappyGhasts.clear();
    }

    private void damageAllPlayers() {
        Collections.shuffle(gamePlayers);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null) {
                if (!deathPlayer.contains(player.getUniqueId())) {
                    int level = player.getFoodLevel() - 1;
                    player.setFoodLevel(Math.max(level, 0));
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
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerInventory inventory = player.getInventory();
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null) {
                    inventory.setBoots(championshipTeam.getBoots());
                }
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
        return gameConfig.getConfiguredWorld();
    }
}
