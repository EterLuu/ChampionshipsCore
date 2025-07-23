package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
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
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ParkourWarriorTeamArea extends BaseSingleTeamArea {
    private final Map<UUID, Location> playerSpawnLocations = new HashMap<>();
    private final Map<UUID, Integer> playerLastSubCheckpoint = new HashMap<>();
    private final Map<UUID, PKWCheckpoint> playerLastCheckpoint = new HashMap<>();
    private final Map<UUID, Map<PKWCheckpoint, Integer>> playerCheckpointProgress = new HashMap<>();
    private final List<PKWCheckpoint> checkpoints = new ArrayList<>();
    private final Map<ChampionshipTeam, Double> gamePointsMultiplier = new HashMap<>();

    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;

    public ParkourWarriorTeamArea(ChampionshipsCore plugin, ParkourWarriorConfig parkourWarriorConfig) {
        super(plugin, GameTypeEnum.ParkourWarrior, new ParkourWarriorHandler(plugin), parkourWarriorConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setParkourWarriorTeamArea(this);
        getGameHandler().register();

        loadCheckpoints();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void resetGame() {
        resetBaseArea();
        playerPoints.clear();
        playerCheckpointProgress.clear();
        playerLastSubCheckpoint.clear();
        playerLastCheckpoint.clear();
        playerSpawnLocations.clear();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public void loadCheckpoints() {
        checkpoints.clear();
        if (getSpectatorSpawnLocation() == null)
            return;

        ConfigurationSection section = getGameConfig().getCheckpoints();

        if (section == null)
            return;

        Set<String> checkpoints = section.getKeys(false);
        for (String checkpointName : checkpoints) {
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

                PKWCheckpoint checkpoint = new PKWCheckpoint(
                        checkpointSection,
                        checkpointName,
                        pkwCheckPointType,
                        subCheckpoints,
                        new CCSelection(checkpointSection.getVector("enter.pos1"), checkpointSection.getVector("enter.pos2"), getSpectatorSpawnLocation().getWorld()),
                        checkpointSection.getLocation("spawn"),
                        pkwFinalCheckPointType == null ? PKWFinalCheckPointTypeEnum.none : pkwFinalCheckPointType
                );

                this.checkpoints.add(checkpoint);
            }
        }
    }

    public synchronized void handlePlayerMove(Player player) {
        Location currentLocation = player.getLocation();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
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

                playerSpawnLocations.put(uuid, checkpoint.getSpawn());
                playerLastSubCheckpoint.put(uuid, -1);
                playerLastCheckpoint.put(uuid, checkpoint);
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
                    playerSpawnLocations.put(uuid, subCheckpoint.getLocation());
                    playerLastSubCheckpoint.put(uuid, index);
                    playerLastCheckpoint.put(uuid, checkpoint);

                    sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_ARRIVED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName())
                            .replace("%sub-checkpoint%", String.valueOf(i)));
                    player.sendMessage(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_ARRIVED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName())
                            .replace("%sub-checkpoint%", String.valueOf(i)));

                    if (index == checkpoint.getSubCheckpoints().size() - 1) {
                        if (checkpoint.getType() == PKWCheckPointTypeEnum.main) {
                            // Do nothing, player has completed the main checkpoint

                        } else if (checkpoint.getType() == PKWCheckPointTypeEnum.sub) {

                            // Sub checkpoint is the last one
                            playerSpawnLocations.put(uuid, checkpoint.getSpawn());
                            teleportPlayerToSpawnPoint(player, false);

                        } else if (checkpoint.getType() == PKWCheckPointTypeEnum.fin) {

                            // End checkpoint logic
                            player.setGameMode(GameMode.SPECTATOR);
                            ChampionshipTeam championshipTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
                            if (championshipTeam != null) {
                                gamePointsMultiplier.put(championshipTeam, gamePointsMultiplier.getOrDefault(championshipTeam, 0d) + checkpoint.getPointMultiplier(getGameConfig()));
                            }

                            sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_END_CHECKPOINT_COMPLETED.replace("%player%", player.getName()));
                            player.sendMessage(MessageConfig.PARKOUR_WARRIOR_END_CHECKPOINT_COMPLETED.replace("%player%", player.getName()));

                            return;
                        }
                        sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_COMPLETED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName()));
                        player.sendMessage(MessageConfig.PARKOUR_WARRIOR_SUB_CHECKPOINT_COMPLETED.replace("%player%", name).replace("%checkpoint%", checkpoint.getName()));
                    }
                    return;
                }
            }
        }
    }

    public int getPlayerSubCheckpoints(UUID uuid, PKWCheckPointTypeEnum type, int startIndex) {
        int count = 0;

        Map<PKWCheckpoint, Integer> singlePlayerCheckpointProgress = playerCheckpointProgress.get(uuid);
        for (Map.Entry<PKWCheckpoint, Integer> entry : singlePlayerCheckpointProgress.entrySet()) {
            PKWCheckPointTypeEnum checkpointType = entry.getKey().getType();
            if (checkpointType == PKWCheckPointTypeEnum.main && type == PKWCheckPointTypeEnum.main) {
                count++;
            } else if (checkpointType == PKWCheckPointTypeEnum.sub && type == PKWCheckPointTypeEnum.sub) {
                int subCheckpointIndex = entry.getValue();
                if (subCheckpointIndex == startIndex) {
                    count += startIndex + 1;
                }
            }
        }

        return count;
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
                    switch (subCheckpointIndex) {
                        case 0: {
                            player3Stars.put(uuid, player3Stars.getOrDefault(uuid, 0) + entry.getValue() + 1);
                        }
                        case 1: {
                            player4Stars.put(uuid, player4Stars.getOrDefault(uuid, 0) + entry.getValue() + 1);
                        }
                        case 2: {
                            player5Stars.put(uuid, player5Stars.getOrDefault(uuid, 0) + entry.getValue() + 1);
                        }
                    }
                }
            }
        }

        for (UUID uuid : getGamePlayers()) {
            int finalPoints;
            finalPoints = player2Stars.getOrDefault(uuid, 0) * getGameConfig().getPoints2() +
                    player3Stars.getOrDefault(uuid, 0) * getGameConfig().getPoints3() +
                    player4Stars.getOrDefault(uuid, 0) * getGameConfig().getPoints4() +
                    player5Stars.getOrDefault(uuid, 0) * getGameConfig().getPoints5();

            ChampionshipTeam championshipTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam != null) {
                double multiplier = gamePointsMultiplier.getOrDefault(championshipTeam, 0d) + 1;

                addPlayerPoints(uuid, finalPoints * multiplier);
            }
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        for (UUID uuid : getGamePlayers()) {
            playerLastCheckpoint.put(uuid, null);
            playerSpawnLocations.put(uuid, getGameConfig().getPlayerSpawnPoint());
            playerLastSubCheckpoint.put(uuid, 0);
            playerCheckpointProgress.put(uuid, new HashMap<>());
        }

        teleportAllPlayers(getGameConfig().getPlayerSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        giveBootToAllPlayers();

        for (UUID uuid : getGamePlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hideOnlinePlayers(player);
            }
        }

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_WARRIOR_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_START_PREPARATION_TITLE, MessageConfig.PARKOUR_WARRIOR_START_PREPARATION_SUBTITLE);

        timer = 10;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllPlayerToSpawnPoints();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_WARRIOR_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_GAME_START_SOON_TITLE, MessageConfig.PARKOUR_WARRIOR_GAME_START_SOON_SUBTITLE);

        timer = getGameConfig().getTimer() + 5;

        resetPlayerHealthFoodEffectLevelInventory();

        giveBootToAllPlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.PARKOUR_WARRIOR_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_WARRIOR_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_GAME_START_TITLE, MessageConfig.PARKOUR_WARRIOR_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.PARKOUR_WARRIOR_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);
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
        if (player != null) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                ChampionshipTeam playerTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam onlinePlayerTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(onlinePlayer);

                if (!(playerTeam != null && playerTeam.equals(onlinePlayerTeam))) {
                    player.hidePlayer(ChampionshipsCore.getInstance(), onlinePlayer);
                }
            }
        }
    }

    public void showOnlinePlayers(Player player) {
        if (player != null) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                player.showPlayer(ChampionshipsCore.getInstance(), onlinePlayer);
            }
        }
    }

    public void hideAndShowPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        if (getGamePlayers().contains(uuid)) {
            // Game player
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                ChampionshipTeam playerTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam onlinePlayerTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(onlinePlayer);

                if (!(playerTeam != null && playerTeam.equals(onlinePlayerTeam))) {
                    player.hidePlayer(ChampionshipsCore.getInstance(), onlinePlayer);
                }
            }
        } else {
            // Spectator
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                player.showPlayer(ChampionshipsCore.getInstance(), onlinePlayer);
            }
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (getGamePlayers().contains(onlinePlayer.getUniqueId())) {
                ChampionshipTeam playerTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam onlinePlayerTeam = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(onlinePlayer);

                if (!(playerTeam != null && playerTeam.equals(onlinePlayerTeam))) {
                    onlinePlayer.hidePlayer(ChampionshipsCore.getInstance(), player);
                }
            } else {
                onlinePlayer.showPlayer(ChampionshipsCore.getInstance(), player);
            }
        }
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        calculatePoints();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_WARRIOR_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_WARRIOR_GAME_END_TITLE, MessageConfig.PARKOUR_WARRIOR_GAME_END_SUBTITLE);

        for (UUID uuid : getGamePlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                showOnlinePlayers(player);
            }
        }

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        resetGame();
    }

    private void teleportAllPlayerToSpawnPoints() {
        Location location = getGameConfig().getPlayerSpawnPoint();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(location);
            }
        }
    }

    public void teleportPlayerToSpawnPoint(Player player, boolean broadcast) {
        player.teleport(playerSpawnLocations.getOrDefault(player.getUniqueId(), getGameConfig().getPlayerSpawnPoint()));
        if (broadcast) {
            String name = player.getName();
            sendMessageToAllSpectators(MessageConfig.PARKOUR_WARRIOR_FALL_INTO_VOID.replace("%player%", name));
            player.sendMessage(MessageConfig.PARKOUR_WARRIOR_FALL_INTO_VOID.replace("%player%", name));
        }
    }

    @Override
    public void resetArea() {
        startGamePreparationTask = null;
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
        return "parkourwarrior";
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {

    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {

    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {

    }
}
