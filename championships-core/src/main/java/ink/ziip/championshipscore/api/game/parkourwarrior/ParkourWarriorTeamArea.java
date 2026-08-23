package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.CCSelection;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWCheckPointTypeEnum;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWCheckpoint;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWFinalCheckPointTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.format.TextDecoration;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class ParkourWarriorTeamArea extends BaseMultiTeamGameInstance {
    private final Map<UUID, Location> playerSpawnLocations = new HashMap<>();
    private final Map<UUID, Integer> playerLastSubCheckpoint = new HashMap<>();
    private final Map<UUID, PKWCheckpoint> playerLastCheckpoint = new HashMap<>();
    private final Map<UUID, Map<PKWCheckpoint, Integer>> playerCheckpointProgress = new HashMap<>();
    private final Set<UUID> finishedPlayers = new HashSet<>();
    private final List<PKWCheckpoint> checkpoints = new ArrayList<>();
    private final Map<ChampionshipTeam, Double> gamePointsMultiplier = new HashMap<>();
    private final Map<UUID, Double> playerFinalPointMultiplier = new HashMap<>();
    private final String visibilityOwner = "game:parkour-warrior:" + UUID.randomUUID();
    private final int copyIndex;

    @Getter
    private int timer;
    private BukkitTask startGameProgressTask;
    private boolean timedOut;
    /** PROGRESS start of the current run; the zero point for DAILY fastest-finish records. */
    private long progressStartNanos;

    public ParkourWarriorTeamArea(ChampionshipsCore plugin, ParkourWarriorConfig parkourWarriorConfig) {
        this(plugin, parkourWarriorConfig, 0, true);
    }

    ParkourWarriorTeamArea(ChampionshipsCore plugin, ParkourWarriorConfig parkourWarriorConfig,
                           int copyIndex, boolean initializeConfig) {
        super(plugin, GameTypeEnum.ParkourWarrior, new ParkourWarriorHandler(plugin), parkourWarriorConfig);
        if (initializeConfig)
            getGameConfig().initializeConfiguration(plugin.getFolder());

        this.copyIndex = copyIndex;

        getGameHandler().setParkourWarriorTeamArea(this);
        getGameHandler().register();

        loadCheckpoints();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public int getCopyIndex() {
        return copyIndex;
    }

    @Override
    public void resetGame() {
        plugin.getVisibilityManager().releaseAll(getGamePlayers(), visibilityOwner);
        cancelIntroduction();
        cancelFinalCountdown();
        resetBaseArea();
        playerPoints.clear();
        playerCheckpointProgress.clear();
        playerLastSubCheckpoint.clear();
        playerLastCheckpoint.clear();
        playerSpawnLocations.clear();
        finishedPlayers.clear();
        gamePointsMultiplier.clear();
        playerFinalPointMultiplier.clear();
        timedOut = false;
        progressStartNanos = 0L;

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public void loadCheckpoints() {
        checkpoints.clear();
        if (getSpectatorSpawnLocation() == null)
            return;

        ConfigurationSection section = getGameConfig().getCheckpoints();

        if (section == null)
            return;

        List<String> checkpointNames = new ArrayList<>(section.getKeys(false));
        Map<String, Integer> fallbackOrder = new HashMap<>();
        for (int i = 0; i < checkpointNames.size(); i++) fallbackOrder.put(checkpointNames.get(i), i + 1);
        checkpointNames.sort(Comparator.comparingInt(name -> section.getConfigurationSection(name) == null
                ? fallbackOrder.getOrDefault(name, Integer.MAX_VALUE)
                : section.getConfigurationSection(name).getInt("order", fallbackOrder.get(name))));
        for (String checkpointName : checkpointNames) {
            ConfigurationSection checkpointSection = section.getConfigurationSection(checkpointName);
            List<CCSelection> subCheckpoints = new ArrayList<>();

            if (checkpointSection != null) {
                for (String subCheckpoint : Objects.requireNonNull(checkpointSection.getConfigurationSection("sub-checkpoints")).getKeys(false)) {
                    Vector pos1 = checkpointSection.getVector("sub-checkpoints." + subCheckpoint + ".pos1");
                    Vector pos2 = checkpointSection.getVector("sub-checkpoints." + subCheckpoint + ".pos2");

                    subCheckpoints.add(new CCSelection(pos1, pos2, getSpectatorSpawnLocation().getWorld()));
                }

                PKWCheckPointTypeEnum pkwCheckPointType = PKWCheckPointTypeEnum.valueOf(checkpointSection.getString("type"));

                PKWFinalCheckPointTypeEnum pkwFinalCheckPointType = null;
                try {
                    pkwFinalCheckPointType = PKWFinalCheckPointTypeEnum.valueOf(checkpointSection.getString("final-check-point-type"));
                } catch (NullPointerException ignored) {
                }

                String name = checkpointSection.getString("name");

                PKWCheckpoint checkpoint = new PKWCheckpoint(
                        checkpointSection,
                        name,
                        checkpointName,
                        pkwCheckPointType,
                        subCheckpoints,
                        new CCSelection(checkpointSection.getVector("enter.pos1"), checkpointSection.getVector("enter.pos2"), getSpectatorSpawnLocation().getWorld()),
                        Utils.getLocation(checkpointSection.getConfigurationSection("spawn")),
                        pkwFinalCheckPointType == null ? PKWFinalCheckPointTypeEnum.none : pkwFinalCheckPointType
                );

                this.checkpoints.add(checkpoint);
            }
        }
    }

    public synchronized void handlePlayerMove(Player player) {
        UUID uuid = player.getUniqueId();
        // Completion is final for this run. Keep this guard at the score-state boundary as
        // well as in the listener so a completed player can never advance a branch again.
        if (finishedPlayers.contains(uuid)) return;

        Location currentLocation = player.getLocation();
        String name = Utils.formatPlayerName(player);
        PKWCheckpoint lastCheckpoint = playerLastCheckpoint.get(uuid);
        int lastSubCheckpoint = playerLastSubCheckpoint.get(uuid);

        // Enter first
        for (PKWCheckpoint checkpoint : checkpoints) {

            if (checkpoint.equals(lastCheckpoint)) {
                // Player is already at this checkpoint
                continue;
            }

            if (checkpoint.getEnter().isInside(currentLocation)) {
                Map<PKWCheckpoint, Integer> checkpointProgress = playerCheckpointProgress.get(uuid);
                if (checkpointProgress != null && checkpointProgress.containsKey(checkpoint)) {
                    if (checkpointProgress.get(checkpoint) == checkpoint.getSubCheckpoints().size() - 1) {
                        // Player has already entered this checkpoint and finished it
                        return;
                    }
                }
                checkpointProgress.put(checkpoint, -1);

                sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_ENTERED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName()));
                player.sendMessage(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_ENTERED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName()));

                float yaw = 0f;
                if (checkpoint.getType() == PKWCheckPointTypeEnum.sub)
                    yaw = 90f;
                playerSpawnLocations.put(uuid, checkpoint.getEnter().getLocation(yaw));
                playerLastSubCheckpoint.put(uuid, -1);
                playerLastCheckpoint.put(uuid, checkpoint);

                if (checkpoint.getType() == PKWCheckPointTypeEnum.sub || checkpoint.getType() == PKWCheckPointTypeEnum.fin) {
                    giveBackToolToPlayer(player);
                } else {
                    player.getInventory().remove(Material.BARRIER);
                }

                player.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);

                return;
            }
        }

        // Sub checkpoint second
        for (PKWCheckpoint checkpoint : checkpoints) {
            // Skip if the player is not at current checkpoint
            if (!checkpoint.equals(lastCheckpoint)) {
                continue;
            }

            for (int i = 0; i < checkpoint.getSubCheckpoints().size(); i++) {
                if (i == lastSubCheckpoint) {
                    // Player is already at this sub checkpoint
                    continue;
                }

                CCSelection subCheckpoint = checkpoint.getSubCheckpoints().get(i);

                if (subCheckpoint.isInside(currentLocation)) {
                    int index = checkpoint.getSubCheckpoints().indexOf(subCheckpoint);
                    Map<PKWCheckpoint, Integer> checkpointProgress = playerCheckpointProgress.get(uuid);

                    if (checkpointProgress.get(checkpoint) >= index) {
                        continue;
                    }


                    checkpointProgress.put(checkpoint, index);

                    float yaw = 0f;
                    if (checkpoint.getType() == PKWCheckPointTypeEnum.sub)
                        yaw = 90f;
                    playerSpawnLocations.put(uuid, subCheckpoint.getLocation(yaw));
                    playerLastSubCheckpoint.put(uuid, index);
                    playerLastCheckpoint.put(uuid, checkpoint);

                    if (getRunMode() == GameRunMode.DAILY)
                        plugin.getDailyManager().statsManager().recordParkourWarriorProgress(
                                this, uuid, countStars(uuid), false, -1L);

                    player.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);

                    ChampionshipTeam championshipTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);

                    sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_ARRIVED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName())
                            .replace("%sub-checkpoint%", String.valueOf(i + 1)));
                    player.sendMessage(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_ARRIVED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName())
                            .replace("%sub-checkpoint%", String.valueOf(i + 1)));

                    if (index == checkpoint.getSubCheckpoints().size() - 1) {
                        if (checkpoint.getType() == PKWCheckPointTypeEnum.main) {
                            // Do nothing, player has completed the main checkpoint

                        } else if (checkpoint.getType() == PKWCheckPointTypeEnum.sub) {

                            // Sub checkpoint is the last one
                            playerSpawnLocations.put(uuid, checkpoint.getSpawn());
                            teleportPlayerToSpawnPoint(player, false);

                        } else if (checkpoint.getType() == PKWCheckPointTypeEnum.fin) {

                            // End checkpoint logic
                            if (!finishedPlayers.add(uuid)) return;
                            if (getRunMode() == GameRunMode.DAILY) {
                                long durationMillis = progressStartNanos > 0L
                                        ? Math.max(0L, (System.nanoTime() - progressStartNanos) / 1_000_000L) : -1L;
                                plugin.getDailyManager().statsManager().recordParkourWarriorProgress(
                                        this, uuid, countStars(uuid), true, durationMillis);
                            }
                            player.setGameMode(GameMode.SPECTATOR);
                            hideAndShowPlayer(player);

                            double finalMultiplier = checkpoint.getPointMultiplier(getGameConfig());
                            if (getRunMode() == GameRunMode.DAILY)
                                playerFinalPointMultiplier.put(uuid,
                                        dailyFinalPointMultiplier(checkpoint.getFinalCheckPointType()));
                            else if (championshipTeam != null)
                                gamePointsMultiplier.put(championshipTeam,
                                        gamePointsMultiplier.getOrDefault(championshipTeam, 0d) + finalMultiplier);

                            sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_END_CHECKPOINT_COMPLETED
                                    .replace("%player%", Utils.formatPlayerName(player)));
                            player.sendMessage(MessageConfig.PARKOUR_WARRIOR_END_CHECKPOINT_COMPLETED
                                    .replace("%player%", Utils.formatPlayerName(player)));

                            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                            // Formal matches retain the complete roster. DAILY runs treat a disconnected
                            // player as unable to finish, so that UUID must not hold the remaining runners
                            // in PROGRESS.
                            boolean allRequiredPlayersFinished = getRunMode() == GameRunMode.DAILY
                                    ? getGamePlayers().stream().allMatch(participant ->
                                    finishedPlayers.contains(participant) || Bukkit.getPlayer(participant) == null)
                                    : finishedPlayers.containsAll(getGamePlayers());
                            if (getGameStageEnum() == GameStageEnum.PROGRESS
                                    && !getGamePlayers().isEmpty()
                                    && allRequiredPlayersFinished) {
                                endGame();
                            }

                            return;
                        }
                        sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_COMPLETED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName()));
                        player.sendMessage(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_COMPLETED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName()));

                        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                        player.getInventory().remove(Material.BARRIER);
                    }
                    return;
                }
            }
        }
    }

    public int getPlayerCheckpoints(UUID uuid, PKWCheckPointTypeEnum type, String checkpointName) {
        int count = 0;

        Map<PKWCheckpoint, Integer> singlePlayerCheckpointProgress = playerCheckpointProgress.get(uuid);
        if (singlePlayerCheckpointProgress == null)
            return 0;
        for (Map.Entry<PKWCheckpoint, Integer> entry : singlePlayerCheckpointProgress.entrySet()) {
            PKWCheckPointTypeEnum checkpointType = entry.getKey().getType();
            if (checkpointType == PKWCheckPointTypeEnum.main && type == PKWCheckPointTypeEnum.main) {
                int subCheckpointIndex = entry.getValue();
                if (subCheckpointIndex == entry.getKey().getSubCheckpoints().size() - 1) {
                    count++;
                }
            } else if (checkpointType == PKWCheckPointTypeEnum.sub && type == PKWCheckPointTypeEnum.sub && entry.getKey().getOriginName().equals(checkpointName)) {
                return entry.getValue() + 1;
            }
        }

        return count;
    }

    public int getPlayerSubCheckpoints(UUID uuid, PKWCheckPointTypeEnum type, int startIndex) {
        int count = 0;

        Map<PKWCheckpoint, Integer> singlePlayerCheckpointProgress = playerCheckpointProgress.get(uuid);
        if (singlePlayerCheckpointProgress == null)
            return 0;
        for (Map.Entry<PKWCheckpoint, Integer> entry : singlePlayerCheckpointProgress.entrySet()) {
            PKWCheckPointTypeEnum checkpointType = entry.getKey().getType();
            if (checkpointType == PKWCheckPointTypeEnum.main && type == PKWCheckPointTypeEnum.main) {
                int subCheckpointIndex = entry.getValue();
                count += subCheckpointIndex + 1;
            } else if (checkpointType == PKWCheckPointTypeEnum.sub && type == PKWCheckPointTypeEnum.sub) {
                int subCheckpointIndex = entry.getValue();
                if (subCheckpointIndex >= startIndex) {
                    count++;
                }
            }
        }

        return count;
    }

    public boolean isFinishedPlayer(@NotNull UUID uuid) {
        return finishedPlayers.contains(uuid);
    }

    static boolean shouldRestoreFinishedPlayerAsSpectator(GameStageEnum stage, boolean finished) {
        return stage == GameStageEnum.PROGRESS && finished;
    }

    public void backToMainSpawnPoint(Player player) {
        UUID uuid = player.getUniqueId();
        if (finishedPlayers.contains(uuid)) return;
        PKWCheckpoint pkwCheckpoint = playerLastCheckpoint.get(uuid);

        Location location = pkwCheckpoint.getSpawn();
        playerSpawnLocations.put(uuid, location);
        playerLastSubCheckpoint.put(uuid, -1);
        playerLastCheckpoint.put(uuid, null);

        player.getInventory().remove(Material.BARRIER);
        player.teleport(location);
    }

    public void giveBackToolToPlayer(Player player) {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null)
            meta.displayName(Utils.toComponent(MessageConfig.PARKOUR_WARRIOR_KITS_BACK_TOOL_NAME)
                    .decoration(TextDecoration.ITALIC, false));
        barrier.setItemMeta(meta);

        player.getInventory().setItem(8, barrier);
    }

    public void calculatePoints() {
        Map<UUID, Integer> player2Stars = new HashMap<>();
        Map<UUID, Integer> player3Stars = new HashMap<>();
        Map<UUID, Integer> player4Stars = new HashMap<>();
        Map<UUID, Integer> player5Stars = new HashMap<>();

        for (UUID uuid : getGamePlayers()) {
            Map<PKWCheckpoint, Integer> singlePlayerCheckpointProgress = playerCheckpointProgress.get(uuid);
            for (Map.Entry<PKWCheckpoint, Integer> entry : singlePlayerCheckpointProgress.entrySet()) {
                PKWCheckPointTypeEnum checkpointType = entry.getKey().getType();
                if (checkpointType == PKWCheckPointTypeEnum.main) {
                    player2Stars.put(uuid, player2Stars.getOrDefault(uuid, 0) + entry.getValue() + 1);
                } else if (checkpointType == PKWCheckPointTypeEnum.sub) {
                    int subCheckpointIndex = entry.getValue();
                    if (subCheckpointIndex == 0) {
                        player3Stars.put(uuid, player3Stars.getOrDefault(uuid, 0) + 1);
                    } else if (subCheckpointIndex == 1) {
                        player3Stars.put(uuid, player3Stars.getOrDefault(uuid, 0) + 1);
                        player4Stars.put(uuid, player4Stars.getOrDefault(uuid, 0) + 1);
                    } else if (subCheckpointIndex == 2) {
                        player3Stars.put(uuid, player3Stars.getOrDefault(uuid, 0) + 1);
                        player4Stars.put(uuid, player4Stars.getOrDefault(uuid, 0) + 1);
                        player5Stars.put(uuid, player5Stars.getOrDefault(uuid, 0) + 1);
                    }
                }
            }
        }

        for (UUID uuid : getGamePlayers()) {
            int finalPoints;
            int ascend = getGameConfig().getPointsGradient();
            int points2 = player2Stars.getOrDefault(uuid, 0);
            int points3 = player3Stars.getOrDefault(uuid, 0);
            int points4 = player4Stars.getOrDefault(uuid, 0);
            int points5 = player5Stars.getOrDefault(uuid, 0);
            finalPoints = points2 * getGameConfig().getPoints2() +
                    ascend * acc(points3 == 0 ? 0 : (points3 - 1)) + (points3 == 0 ? 0 : getGameConfig().getPoints3()) * points3 +
                    ascend * acc(points4 == 0 ? 0 : (points4 - 1)) + (points4 == 0 ? 0 : getGameConfig().getPoints4()) * points4 +
                    ascend * acc(points5 == 0 ? 0 : (points5 - 1)) + (points5 == 0 ? 0 : getGameConfig().getPoints5()) * points5;

            ChampionshipTeam championshipTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam != null) {
                double multiplier = getRunMode() == GameRunMode.DAILY
                        ? playerFinalPointMultiplier.getOrDefault(uuid, 1d)
                        * dailyCompletionMultiplier(finishedPlayers.contains(uuid), timedOut)
                        : gamePointsMultiplier.getOrDefault(championshipTeam, 0d) + 1;

                BigDecimal finalPointsBD = BigDecimal.valueOf(finalPoints * multiplier).setScale(2, RoundingMode.HALF_UP);

                addPlayerPoints(uuid, finalPointsBD.doubleValue());
            }
        }
    }

    private int acc(int num) {
        int sum = 0;
        for (int i = 1; i <= num; i++) {
            sum += i;
        }
        return sum;
    }

    static double dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum difficulty) {
        if (difficulty == null) return 1D;
        return switch (difficulty) {
            case normal -> 1.5D;
            case hard -> 2.5D;
            case none, easy -> 1D;
        };
    }

    static double dailyCompletionMultiplier(boolean finished, boolean timedOut) {
        return timedOut && !finished ? 0.5D : 1D;
    }

    /** Star total from live checkpoint progress; the same 2/3/4/5-star weights as settlement scoring. */
    private long countStars(UUID uuid) {
        Map<PKWCheckpoint, Integer> progress = playerCheckpointProgress.get(uuid);
        if (progress == null)
            return 0L;
        long stars = 0L;
        for (Map.Entry<PKWCheckpoint, Integer> entry : progress.entrySet()) {
            if (entry.getKey().getType() == PKWCheckPointTypeEnum.main) {
                stars += 2L * (entry.getValue() + 1);
            } else if (entry.getKey().getType() == PKWCheckPointTypeEnum.sub) {
                int subCheckpointIndex = entry.getValue();
                if (subCheckpointIndex >= 0)
                    stars += 3L;
                if (subCheckpointIndex >= 1)
                    stars += 4L;
                if (subCheckpointIndex >= 2)
                    stars += 5L;
            }
        }
        return stars;
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Players may enter from map editing/creative mode. Changing to ADVENTURE does not
        // clear Bukkit's allowFlight flag, so explicitly revoke flight before the run starts.
        disableFlightForGamePlayers();

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {
        finishedPlayers.clear();
        playerFinalPointMultiplier.clear();

        for (UUID uuid : getGamePlayers()) {
            playerLastCheckpoint.put(uuid, null);
            playerSpawnLocations.put(uuid, getGameConfig().getPlayerSpawnPoint());
            playerLastSubCheckpoint.put(uuid, 0);
            playerCheckpointProgress.put(uuid, new HashMap<>());
        }

        teleportAllPlayers(getGameConfig().getPlayerSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        disableFlightForGamePlayers();

        resetPlayerHealthFoodEffectLevelInventory();

        giveBootToAllPlayers();

        for (UUID uuid : getGamePlayers())
            plugin.getVisibilityManager().seeTeammates(uuid, visibilityOwner,
                    "Parkour Warrior 进行中只能看见本队玩家");

        plugin.getTeamManager().setCollision(false);

        announceGamePreparation(MessageConfig.PARKOUR_WARRIOR_START_PREPARATION,
                MessageConfig.PARKOUR_WARRIOR_START_PREPARATION_TITLE, MessageConfig.PARKOUR_WARRIOR_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    protected void startGameProgress() {
        startFinalCountdown(MessageConfig.PARKOUR_WARRIOR_GAME_START_SOON_TITLE,
                MessageConfig.PARKOUR_WARRIOR_GAME_START_TITLE, MessageConfig.PARKOUR_WARRIOR_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        timedOut = false;
        progressStartNanos = System.nanoTime();
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            int suddenDeathWarning = timer - getGameConfig().getSuddenDeath();
            if (suddenDeathWarning >= 1 && suddenDeathWarning <= 10) {
                sendActionBarToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_SUDDEN_DEATH_START_COUNT_DOWN
                        .replace("%time%", String.valueOf(suddenDeathWarning)));
            }
            if (timer == getGameConfig().getSuddenDeath()) {
                sendActionBarToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_START_SUDDEN_DEATH);
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_START_SUDDEN_DEATH_TITLE,
                        MessageConfig.PARKOUR_WARRIOR_START_SUDDEN_DEATH_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_BELL_USE, 1, 12F);
            }

            String timerTemplate = timer <= getGameConfig().getSuddenDeath()
                    ? MessageConfig.PARKOUR_WARRIOR_SUDDEN_DEATH_COUNT_DOWN
                    : MessageConfig.PARKOUR_WARRIOR_ACTION_BAR_COUNT_DOWN;
            updateGameTimerBossBar(timerTemplate
                    .replace("%time%", Utils.formatMinutesSeconds(timer)), timer, getGameConfig().getTimer());
        }, () -> {
            timedOut = true;
            endGame();
        });
    }

    private void giveBootToAllPlayers() {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            Player player = championshipPlayer.getPlayer();
            if (player != null) {
                ChampionshipTeam championshipTeam = championshipPlayer.getChampionshipTeam();
                if (championshipTeam != null) {
                    championshipPlayer.getPlayer().getInventory().setBoots(championshipTeam.getBoots());
                }
            }
        }
    }

    public void hideOnlinePlayers(Player player) {
        if (player != null) plugin.getVisibilityManager().seeTeammates(player.getUniqueId(), visibilityOwner,
                "Parkour Warrior 进行中只能看见本队玩家");
    }

    public void showOnlinePlayers(Player player) {
        if (player != null) plugin.getVisibilityManager().release(player.getUniqueId(), visibilityOwner);
    }

    public void hideAndShowPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean activeRunner = getGamePlayers().contains(uuid) && !finishedPlayers.contains(uuid);
        if (activeRunner) hideOnlinePlayers(player);
        else showOnlinePlayers(player);
        plugin.getVisibilityManager().reconcilePlayer(uuid);
    }

    @Override
    public void endGame() {
        // END guard keeps the timer and the all-finished early end from settling twice.
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        if (isSettlementAllowed()) calculatePoints();

        cleanInventoryForAllGamePlayers();

        // Concurrent DAILY copies share the global collision switch; only the last running copy may
        // restore it, otherwise players of the still-running copy would start pushing each other.
        boolean anotherCopyRunning = plugin.getGameManager().getParkourWarriorManager()
                .getRuntimeInstances().stream()
                .anyMatch(area -> area != this
                        && area.getGameStageEnum() != GameStageEnum.WAITING
                        && area.getGameStageEnum() != GameStageEnum.END);
        if (!anotherCopyRunning)
            plugin.getTeamManager().setCollision(true);

        announceGameEnd(MessageConfig.PARKOUR_WARRIOR_GAME_END_TITLE,
                MessageConfig.PARKOUR_WARRIOR_GAME_END_SUBTITLE);

        plugin.getVisibilityManager().releaseAll(getGamePlayers(), visibilityOwner);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        if (isSettlementAllowed()) sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
    }

    public void teleportPlayerToSpawnPoint(Player player, boolean broadcast) {
        player.teleport(playerSpawnLocations.getOrDefault(player.getUniqueId(), getGameConfig().getPlayerSpawnPoint()));
        if (broadcast) {
            String name = Utils.formatPlayerName(player);
            sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_FALL_INTO_VOID.replace("%player%", name));
            player.sendMessage(MessageConfig.PARKOUR_WARRIOR_FALL_INTO_VOID.replace("%player%", name));
        }
    }

    @Override
    public void resetArea() {
        startGameProgressTask = null;
    }

    @Override
    public ParkourWarriorConfig getGameConfig() {
        return (ParkourWarriorConfig) gameConfig;
    }

    @Override
    public ParkourWarriorHandler getGameHandler() {
        return (ParkourWarriorHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {

    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {

    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        GameStageEnum stage = getGameStageEnum();
        if (shouldRestoreFinishedPlayerAsSpectator(stage, finishedPlayers.contains(player.getUniqueId()))) {
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR);
            hideAndShowPlayer(player);
            return;
        }
        if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN
                || stage == GameStageEnum.PROGRESS) {
            player.teleport(playerSpawnLocations.getOrDefault(player.getUniqueId(),
                    getGameConfig().getPlayerSpawnPoint()));
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
            if (team != null) player.getInventory().setBoots(team.getBoots());
            if (stage == GameStageEnum.PROGRESS) hideAndShowPlayer(player);
            return;
        }
        player.teleport(CCConfig.LOBBY_LOCATION);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private void disableFlightForGamePlayers() {
        for (UUID uuid : getGamePlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }
}
