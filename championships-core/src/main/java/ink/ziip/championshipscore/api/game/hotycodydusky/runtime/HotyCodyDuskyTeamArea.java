package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.hotycodydusky.config.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import lombok.Getter;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HotyCodyDuskyTeamArea extends BaseMultiTeamGameInstance {
    @Getter private final List<UUID> deathPlayer = new ArrayList<>();
    private final Map<UUID, Long> playerDeadTimes = new HashMap<>();
    private final Map<UUID, Long> playerCodyChangeTimes = new HashMap<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    @Getter private int timer;
    private BukkitTask startGameProgressTask;
    private final Map<Integer, BukkitTask> codySelectionTasks = new HashMap<>();
    private final Map<Integer, UUID> codyHolders = new HashMap<>();
    private final Map<UUID, Integer> playerArenas = new HashMap<>();
    private long holderGeneration;

    public HotyCodyDuskyTeamArea(
            ChampionshipsCore plugin, HotyCodyDuskyConfig hotyCodyDuskyConfig) {
        super(
                plugin,
                GameTypeEnum.HotyCodyDusky,
                new HotyCodyDuskyHandler(plugin),
                hotyCodyDuskyConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setHotyCodyDuskyArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    private void cancelCodySelections() {
        holderGeneration++;
        codySelectionTasks.values().forEach(BukkitTask::cancel);
        codySelectionTasks.clear();
    }

    @Override
    public void dispose() {
        cancelCodySelections();
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        codyHolders.clear();
        playerArenas.clear();
        super.dispose();
    }

    public int arenaOf(UUID player) {
        return playerArenas.getOrDefault(player, -1);
    }

    public UUID getCodyHolder(UUID player) {
        return codyHolders.get(arenaOf(player));
    }

    public List<UUID> getCodyHolders() {
        return List.copyOf(codyHolders.values());
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> points = new ArrayList<>();
        for (int arena : getSelectedArenaIndices(getGameConfig().getCopies())) {
            Location spawn = getGameConfig().spawn(arena);
            if (spawn != null) points.add(spawn);
        }
        return points;
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())) return true;
        var boxes = getGameConfig().getCopyBoxes();
        if (boxes.isEmpty()) return true;
        return getSelectedArenaIndices(boxes.size()).stream()
                .noneMatch(index -> boxes.get(index).contains(location.toVector()));
    }

    public boolean isInsideAssignedArena(Player player) {
        int arena = arenaOf(player.getUniqueId());
        var boxes = getGameConfig().getCopyBoxes();
        return arena >= 0
                && arena < boxes.size()
                && player.getWorld().getName().equals(getWorldName())
                && boxes.get(arena).contains(player.getLocation().toVector());
    }

    private Location playerSpawn(UUID id) {
        int arena = arenaOf(id);
        return getGameConfig()
                .spawn(
                        arena < 0
                                ? getSelectedArenaIndices(getGameConfig().getCopies()).getFirst()
                                : arena);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        if (startGameProgressTask != null) startGameProgressTask.cancel();
        cancelCodySelections();

        if (isSettlementAllowed()) calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(
                MessageConfig.HOTY_CODY_DUSKY_GAME_END_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_GAME_END_SUBTITLE);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    protected void calculatePoints() {
        List<UUID> alivePlayers = new ArrayList<>(gamePlayers);
        alivePlayers.removeAll(deathPlayer);
        ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.HotyCodyDuskyScoring
                .rankingBonuses(playerDeadTimes, alivePlayers, System.currentTimeMillis())
                .forEach(this::addPlayerPoints);

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
    }

    @Override
    public void resetArea() {
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        deathPlayer.clear();
        playerDeadTimes.clear();
        playerCodyChangeTimes.clear();
        teamDeathPlayers.clear();
        codyHolders.clear();
        playerArenas.clear();
        cancelCodySelections();

        startGameProgressTask = null;
    }

    @Override
    public HotyCodyDuskyConfig getGameConfig() {
        return (HotyCodyDuskyConfig) gameConfig;
    }

    @Override
    public HotyCodyDuskyHandler getGameHandler() {
        return (HotyCodyDuskyHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point
        // and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        playerArenas.clear();
        var roster =
                gameTeams.stream()
                        .map(
                                team ->
                                        team.getMembers().stream()
                                                .filter(gamePlayers::contains)
                                                .sorted()
                                                .toList())
                        .toList();
        playerArenas.putAll(
                ink.ziip.championshipscore.api.game.start.StartAllocation.spread(
                        roster, getSelectedArenaIndices(getGameConfig().getCopies())));
        for (UUID id : gamePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.teleport(playerSpawn(id));
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(
                MessageConfig.HOTY_CODY_DUSKY_START_PREPARATION,
                MessageConfig.HOTY_CODY_DUSKY_START_PREPARATION_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    protected void startGameProgress() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                deathPlayer.add(uuid);
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null) {
                    addTeamDeathPlayer(championshipTeam);
                    logGame(
                            Level.INFO,
                            "玩家",
                            "玩家="
                                    + playerManager.getPlayerName(uuid)
                                    + " uuid="
                                    + uuid
                                    + " 状态=离线，计入淘汰");
                }
            }
        }

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        startFinalCountdown(
                MessageConfig.HOTY_CODY_DUSKY_GAME_START_SOON_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_GAME_START_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        timer = getGameConfig().getTimer();
        for (int arena : getSelectedArenaIndices(getGameConfig().getCopies()))
            selectCodyHolder(arena, true);
        startGameProgressTask =
                startRemainingTimer(
                        getGameConfig().getTimer(),
                        seconds -> {
                            timer = seconds;
                            updateGameTimerBossBar(
                                    MessageConfig.HOTY_CODY_DUSKY_ACTION_BAR_COUNT_DOWN
                                            .replace("%time%", String.valueOf(timer))
                                            .replace(
                                                    "%holder%",
                                                    codyHolders.isEmpty()
                                                            ? "待定"
                                                            : codyHolders.values().stream()
                                                                    .map(
                                                                            CoreMessages
                                                                                    ::formatPlayerName)
                                                                    .collect(
                                                                            java.util.stream
                                                                                    .Collectors
                                                                                    .joining("、"))),
                                    timer,
                                    getGameConfig().getTimer());

                            int elapsed = getGameConfig().getTimer() - timer;
                            if (timer > 0 && elapsed > 0 && elapsed % 3 == 0) {
                                for (UUID holder : List.copyOf(codyHolders.values())) {
                                    Player player = Bukkit.getPlayer(holder);
                                    if (player != null
                                            && isGameplayParticipant(player)
                                            && isInsideAssignedArena(player)) {
                                        player.setHealth(Math.max(0, player.getHealth() - 2));
                                        player.playSound(player, Sound.ENTITY_PLAYER_HURT, 1, 1);
                                    }
                                }
                            }
                        },
                        this::endGame);
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public int getSurvivedTeamNums() {
        int i = 0;
        for (ChampionshipTeam championshipTeam : teamDeathPlayers.keySet()) {
            if (teamDeathPlayers.get(championshipTeam) == championshipTeam.getMembers().size()) i++;
        }
        return gameTeams.size() - i;
    }

    protected boolean changeCodyHolder(int type, UUID holder) {
        if (type != 2 || holder == null || !canHoldCody(holder)) return false;
        return changeCodyHolder(holder, false);
    }

    private boolean canHoldCody(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return getGameStageEnum() == GameStageEnum.PROGRESS
                && !deathPlayer.contains(id)
                && player != null
                && isGameplayParticipant(player)
                && isInsideAssignedArena(player);
    }

    private void selectCodyHolder(int arena, boolean first) {
        UUID codyHolder = codyHolders.get(arena);
        if (gameStageEnum != GameStageEnum.PROGRESS) {
            return;
        }

        if (gamePlayers.isEmpty()) {
            return;
        }

        List<UUID> alivePlayers = new ArrayList<>(gamePlayers);
        alivePlayers.removeAll(deathPlayer);
        alivePlayers.removeIf(id -> arenaOf(id) != arena || !canHoldCody(id));

        if (alivePlayers.isEmpty()) {
            codyHolders.remove(arena);
            return;
        }

        long now = System.currentTimeMillis();
        List<UUID> eligible =
                alivePlayers.stream()
                        .filter(this::isGameplayParticipant)
                        .filter(uuid -> !uuid.equals(codyHolder))
                        .filter(uuid -> now - playerCodyChangeTimes.getOrDefault(uuid, 0L) >= 1500L)
                        .toList();
        if (eligible.isEmpty()) {
            long remainingMillis =
                    alivePlayers.stream()
                            .mapToLong(
                                    uuid ->
                                            Math.max(
                                                    1L,
                                                    1500L
                                                            - (now
                                                                    - playerCodyChangeTimes
                                                                            .getOrDefault(
                                                                                    uuid, 0L))))
                            .min()
                            .orElse(50L);
            BukkitTask previous = codySelectionTasks.remove(arena);
            if (previous != null) previous.cancel();
            long generation = holderGeneration;
            long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
            codySelectionTasks.put(
                    arena,
                    scheduler.runTaskLater(
                            plugin,
                            () -> {
                                if (generation != holderGeneration) return;
                                codySelectionTasks.remove(arena);
                                selectCodyHolder(arena, first);
                            },
                            delayTicks));
            return;
        }
        UUID holder =
                eligible.get(
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(eligible.size()));
        if (!changeCodyHolder(holder, first)) return;
        addPlayerPoints(holder, 10);
    }

    private boolean changeCodyHolder(UUID to, boolean first) {
        if (!canHoldCody(to)) return false;
        UUID codyHolder = getCodyHolder(to);
        long lastChangeTime = playerCodyChangeTimes.getOrDefault(to, 0L);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChangeTime < 1500) {
            return false;
        }
        if (codyHolder != null) {
            if (codyHolder.equals(to)) {
                return false;
            }
            playerCodyChangeTimes.put(codyHolder, System.currentTimeMillis());
        }
        if (codyHolder == null)
            sendActionBarToAllGamePlayers(
                    MessageConfig.HOTY_CODY_DUSKY_PLAYER_RECEIVED_CODY.replace(
                            "%player%", CoreMessages.formatPlayerName(to)));
        else
            sendActionBarToAllGamePlayers(
                    MessageConfig.HOTY_CODY_DUSKY_GIVE_CODY_TO_PLAYER
                            .replace("%to%", CoreMessages.formatPlayerName(to))
                            .replace("%from%", CoreMessages.formatPlayerName(codyHolder)));
        setCodyPlayer(to, first);
        return true;
    }

    private void setCodyPlayer(UUID uuid, boolean first) {
        UUID codyHolder = getCodyHolder(uuid);
        if (codyHolder != null) {
            Player codyHolderPlayer = Bukkit.getPlayer(codyHolder);
            if (codyHolderPlayer != null) {
                codyHolderPlayer.getInventory().clear();
                ChampionshipTeam championshipTeam =
                        plugin.getTeamManager().getTeamByPlayer(codyHolder);
                if (championshipTeam != null)
                    codyHolderPlayer.getInventory().setBoots(championshipTeam.getBoots());
                codyHolderPlayer.playSound(codyHolderPlayer, Sound.ENTITY_ENDER_PEARL_THROW, 1, 0);
                for (PotionEffect potionEffect : codyHolderPlayer.getActivePotionEffects())
                    codyHolderPlayer.removePotionEffect(potionEffect.getType());
            }
        }
        codyHolders.put(arenaOf(uuid), uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            ItemStack cody = new ItemStack(Material.COD);
            PlayerInventory inventory = player.getInventory();
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam != null) inventory.setBoots(championshipTeam.getBoots());
            inventory.setLeggings(cody.clone());
            inventory.setChestplate(cody.clone());
            inventory.setHelmet(cody.clone());
            inventory.setItemInMainHand(cody.clone());
            inventory.setItemInOffHand(cody.clone());

            PotionEffect potionEffectBlindness =
                    new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false);
            PotionEffect potionEffectGlowing =
                    new PotionEffect(PotionEffectType.GLOWING, getTimer() * 20, 0, false, false);
            PotionEffect potionEffectSpeed =
                    new PotionEffect(PotionEffectType.SPEED, getTimer() * 20, 0, false, false);
            PotionEffect potionEffectHaste =
                    new PotionEffect(PotionEffectType.HASTE, getTimer() * 20, 0, false, false);
            if (!first) player.addPotionEffect(potionEffectBlindness);
            player.addPotionEffect(potionEffectGlowing);
            player.addPotionEffect(potionEffectSpeed);
            player.addPotionEffect(potionEffectHaste);
            player.playSound(player, Sound.ENTITY_ENDERMAN_HURT, 1, 1);
        }
    }

    private void addDeathPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        addDeathPlayer(uuid);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (championshipTeam != null) {
            addTeamDeathPlayer(championshipTeam);
        }
    }

    private void addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid)) return;

        deathPlayer.add(uuid);
        playerDeadTimes.put(uuid, System.currentTimeMillis());

        int arena = arenaOf(uuid);
        if (uuid.equals(codyHolders.get(arena))) {
            codyHolders.remove(arena);
            selectCodyHolder(arena, false);
        }
        addPointsToAllSurvivePlayers();
    }

    private void addPointsToAllSurvivePlayers() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 15);
            }
        }
    }

    private void addTeamDeathPlayer(ChampionshipTeam championshipTeam) {
        teamDeathPlayers.put(
                championshipTeam, teamDeathPlayers.getOrDefault(championshipTeam, 0) + 1);
        Integer deathPlayer = teamDeathPlayers.get(championshipTeam);
        logGame(Level.INFO, "淘汰", "队伍=" + championshipTeam.getName() + " 已淘汰人数=" + deathPlayer);
        if (deathPlayer != null) {
            if (deathPlayer == championshipTeam.getMembers().size()) {
                sendMessageToAllGamePlayers(
                        MessageConfig.HOTY_CODY_DUSKY_WHOLE_TEAM_WAS_KILLED.replace(
                                "%team%", championshipTeam.getColoredName()));
                addPointsToAllSurvivePlayers();
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        long generation = holderGeneration;
        UUID id = player.getUniqueId();
        boolean preparation = getGameStageEnum() == GameStageEnum.PREPARATION;
        scheduler.runTask(
                plugin,
                () -> {
                    if (generation != holderGeneration
                            || Bukkit.getPlayer(id) != player
                            || !gamePlayers.contains(id)
                            || !plugin.isLoaded()
                            || getGameStageEnum() == GameStageEnum.WAITING) return;
                    player.spigot().respawn();
                    if (preparation && getGameStageEnum() == GameStageEnum.PREPARATION) {
                        player.teleport(getPreparationTeleportLocation(playerSpawn(id)));
                    } else if (deathPlayer.contains(id)) {
                        player.setGameMode(GameMode.SPECTATOR);
                        plugin.getGameManager().getSpectatorManager().refreshPresentation(player);
                        player.teleport(getSpectatorSpawnLocation());
                    }
                });
        if (preparation) return;

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId())) return;

        addDeathPlayer(player);

        String message = MessageConfig.HOTY_CODY_DUSKY_PLAYER_DEATH;

        message = message.replace("%player%", CoreMessages.formatPlayerName(player));
        sendMessageToAllGamePlayers(message);
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

        if (deathPlayer.contains(player.getUniqueId())) return;

        sendMessageToAllGamePlayers(
                MessageConfig.HOTY_CODY_DUSKY_PLAYER_LEAVE.replace(
                        "%player%", CoreMessages.formatPlayerName(player)));
        addDeathPlayer(player);
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(playerSpawn(player.getUniqueId())));
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            player.teleport(playerSpawn(player.getUniqueId()));
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        player.teleport(getSpectatorSpawnLocation());
        player.setGameMode(
                getGameStageEnum() == GameStageEnum.END ? GameMode.ADVENTURE : GameMode.SPECTATOR);
    }
}
