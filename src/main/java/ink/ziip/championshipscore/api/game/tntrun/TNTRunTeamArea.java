package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TNTRunTeamArea extends BaseSingleTeamArea {
    @Getter
    private final Map<UUID, Location> playerSpawnLocations = new ConcurrentHashMap<>();
    @Getter
    private final List<UUID> deathPlayer = new ArrayList<>();
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private BukkitTask handlePlayerMoveTask;
    private int tntTimer;
    private BukkitTask tntGeneratorTask;

    public TNTRunTeamArea(ChampionshipsCore plugin, TNTRunConfig tntRunConfig, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.TNTRun, new TNTRunHandler(plugin), tntRunConfig);

        getGameHandler().setTntRunTeamArea(this);

        if (!firstTime) {
            loadMap(areaName);
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    @Override
    public void resetArea() {
        deathPlayer.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        handlePlayerMoveTask = null;
        tntGeneratorTask = null;

        loadMap(getGameConfig().getAreaName());
    }

    @Override
    public void resetGame() {
        resetBaseArea();
        playerPoints.clear();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        List<Location> locations = new ArrayList<>();
        for (String stringLocation : getGameConfig().getPlayerSpawnPoints()) {
            locations.add(Utils.getLocation(stringLocation));
        }

        for (ChampionshipTeam championshipTeam : gameTeams) {
            Collections.shuffle(locations);

            Iterator<Location> locationI = locations.iterator();

            for (UUID uuid : championshipTeam.getMembers()) {
                if (!locationI.hasNext())
                    locationI = locations.iterator();

                playerSpawnLocations.put(uuid, locationI.next());
            }
        }

        for (UUID uuid : playerSpawnLocations.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.teleport(playerSpawnLocations.get(uuid));
            }
        }

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TNT_RUN_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.TNT_RUN_START_PREPARATION_TITLE, MessageConfig.TNT_RUN_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer <= 5) {
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    public void startGameProgress() {
        setGameStageEnum(GameStageEnum.PROGRESS);
        timer = getGameConfig().getTimer();

        int offlinePlayers = 0;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                deathPlayer.add(uuid);
                offlinePlayers++;
                plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + "Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + ") not online");
            }
        }
        addPointsToAllSurvivePlayers(offlinePlayers);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TNT_RUN_GAME_START);
        sendTitleToAllGamePlayers(MessageConfig.TNT_RUN_GAME_START_TITLE, MessageConfig.TNT_RUN_GAME_START_SUBTITLE);
        playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.TNT_RUN_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                changeLevelForAllGamePlayers(timer);
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            if (timer == 120 || timer == 60) {
                sendMessageToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN);
                sendActionBarToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN);

                tntTimer = 0;
                final List<UUID> gamePlayersCopy = new ArrayList<>(gamePlayers);

                tntGeneratorTask = scheduler.runTaskTimer(plugin, () -> {

                    Collections.shuffle(gamePlayersCopy);

                    int i = 0;
                    for (UUID uuid : gamePlayersCopy) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline() && !deathPlayer.contains(player.getUniqueId())) {
                            Location location = player.getLocation();
                            location.setY(getPlayerSpawnLocations().get(uuid).getY() + 15);
                            TNTPrimed tntPrimed = (TNTPrimed) player.getWorld().spawnEntity(location, EntityType.PRIMED_TNT);
                            tntPrimed.setFuseTicks(Integer.MAX_VALUE);

                            scheduler.runTaskTimerAsynchronously(plugin, (task) -> {
                                if (!tntPrimed.isValid())
                                    task.cancel();

                                Location tntLocation = tntPrimed.getLocation();
                                if (getBlockUnderLocation(tntLocation) != null) {
                                    tntPrimed.setFuseTicks(0);
                                    task.cancel();
                                }
                                if (notInArea(tntLocation)) {
                                    tntPrimed.setFuseTicks(0);
                                    task.cancel();
                                }
                            }, 0, 1L);
                        }

                        if (i >= 8)
                            break;
                        i++;
                    }

                    if (tntTimer == 0) {
                        if (tntGeneratorTask != null)
                            tntGeneratorTask.cancel();
                    }

                    tntTimer--;
                }, 0, 20L);

            }

            timer--;
        }, 0, 20L);

        final List<UUID> gamePlayersCopy = new ArrayList<>(gamePlayers);
        handlePlayerMoveTask = scheduler.runTaskTimerAsynchronously(plugin, () -> gamePlayersCopy.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !deathPlayer.contains(uuid)) {
                handlePlayerMove(player);
            }
        }), 0, 1L);
    }

    private void handlePlayerMove(@NotNull Player player) {
        destroyBlock(player.getLocation());
    }

    private Block getBlockUnderLocation(Location location) {
        World world = location.getWorld();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        if (world == null)
            return null;

        for (int i = 0; i <= 1; i++) {
            Block block1 = world.getBlockAt(
                    NumberConversions.floor(x + 0.3),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z - 0.3)
            );
            Material block1Type = block1.getType();
            if (block1Type != Material.AIR && block1Type != Material.LIGHT)
                return block1;
            Block block2 = world.getBlockAt(
                    NumberConversions.floor(x - 0.3),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z + 0.3)
            );
            Material block2Type = block2.getType();
            if (block2Type != Material.AIR && block2Type != Material.LIGHT)
                return block2;
            Block block3 = world.getBlockAt(
                    NumberConversions.floor(x + 0.3),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z + 0.3)
            );
            Material block3Type = block3.getType();
            if (block3Type != Material.AIR && block3Type != Material.LIGHT)
                return block3;
            Block block4 = world.getBlockAt(
                    NumberConversions.floor(x - 0.3),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z - 0.3)
            );
            Material block4Type = block4.getType();
            if (block4Type != Material.AIR && block4Type != Material.LIGHT)
                return block4;
        }

        return null;
    }

    private void destroyBlock(Location location) {
        World world = location.getWorld();
        if (world == null)
            return;

        Block block = getBlockUnderLocation(location);

        if (block != null) {
            final Block destroyBlock = block;
            scheduler.runTaskLater(plugin, () -> {
                world.playSound(location, Sound.BLOCK_SAND_BREAK, 3, 1);
                destroyBlock.setType(Material.AIR);
                destroyBlock.getRelative(BlockFace.DOWN).setType(Material.AIR);
            }, 8);
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
        if (handlePlayerMoveTask != null)
            handlePlayerMoveTask.cancel();
        if (tntGeneratorTask != null)
            tntGeneratorTask.cancel();

        calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TNT_RUN_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.TNT_RUN_GAME_END_TITLE, MessageConfig.TNT_RUN_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        resetGame();
    }

    protected void calculatePoints() {
        int survivedPlayerNum = 0;

        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 100);
                survivedPlayerNum++;
            }
        }

        if (survivedPlayerNum == 0) {
            if (!deathPlayer.isEmpty())
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 1), 100);
            if (deathPlayer.size() >= 2)
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 2), 70);
            if (deathPlayer.size() >= 3)
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 3), 30);
        } else if (survivedPlayerNum <= 3) {
            if (!deathPlayer.isEmpty())
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 1), 70);
            if (deathPlayer.size() >= 2)
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 2), 30);
        }

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());

        addPlayerPointsToDatabase();
    }

    public void addDeathPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (addDeathPlayer(uuid)) {
            sendMessageToAllGamePlayers(MessageConfig.TNT_RUN_FALL_INTO_VOID.replace("%player%", player.getName()));
        }
    }

    public synchronized boolean addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid))
            return false;

        deathPlayer.add(uuid);
        addPointsToAllSurvivePlayers();
        return true;
    }

    private void addPointsToAllSurvivePlayers() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 2);
            }
        }
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

    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            sendMessageToAllGamePlayers(MessageConfig.TNT_RUN_FALL_INTO_VOID.replace("%player%", player.getName()));
            addDeathPlayer(player);
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            addDeathPlayer(player);
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public void teleportPlayerToSpawnPoint(Player player) {
        Location location = playerSpawnLocations.get(player.getUniqueId());

        if (location != null) {
            player.teleport(location);
            return;
        }

        for (Location spawnLocations : playerSpawnLocations.values()) {
            player.teleport(spawnLocations);
            return;
        }
    }

    public void loadMap(String areaName) {
        if (!plugin.isLoaded())
            return;

        scheduler.runTaskAsynchronously(plugin, () -> {
            scheduler.runTask(plugin, () -> {
                setGameStageEnum(GameStageEnum.END);
                getGameHandler().unRegister();
                plugin.getLogger().log(Level.INFO, GameTypeEnum.TNTRun + ", " + areaName + ", start loading world " + getWorldName());
            });

            File target = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), "tntrun_" + areaName);

            // If already has a same world, delete it.
            if (target.isDirectory()) {
                String[] list = target.list();
                if (list != null && list.length > 0) {
                    plugin.getWorldManager().deleteWorld("tntrun_" + areaName, true);
                }
            }

            File maps = new File(plugin.getDataFolder(), "maps");
            File source = new File(maps, "tntrun_" + areaName);

            // Copy world files to destination
            plugin.getWorldManager().copyWorldFiles(source, target);

            // Load world
            plugin.getWorldManager().loadWorld("tntrun_" + areaName, World.Environment.NORMAL, false);

            scheduler.runTask(plugin, () -> {
                getGameConfig().initializeConfiguration(plugin.getFolder());
                getGameHandler().register();
                setGameStageEnum(GameStageEnum.WAITING);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.TNTRun + ", " + areaName + ", world " + getWorldName() + " loaded");
            });
        });
    }

    public void saveMap() {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return;

        scheduler.runTaskAsynchronously(plugin, () -> {
            scheduler.runTask(plugin, () -> {
                setGameStageEnum(GameStageEnum.END);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.TNTRun + ", " + gameConfig.getAreaName() + ", start saving world " + getWorldName());
            });

            World editWorld = plugin.getServer().getWorld("tntrun_" + getWorldName());
            if (editWorld != null) {
                for (Player player : editWorld.getPlayers()) {
                    player.teleport(CCConfig.LOBBY_LOCATION);
                }

                // Unload world but not remove files
                plugin.getWorldManager().unloadWorld("tntrun_" + getWorldName(), true);

                File dataDirectory = new File(plugin.getDataFolder(), "maps");
                File target = new File(dataDirectory, "tntrun_" + getWorldName());

                // Delete old world files stored in maps
                plugin.getWorldManager().deleteWorldFiles(target);

                File source = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), "tntrun_" + getWorldName());

                plugin.getWorldManager().copyWorldFiles(source, target);
                plugin.getWorldManager().deleteWorldFiles(source);
            }

            loadMap(getGameConfig().getAreaName());

            scheduler.runTask(plugin, () -> {
                getGameConfig().initializeConfiguration(plugin.getFolder());
                setGameStageEnum(GameStageEnum.WAITING);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.TNTRun + ", " + gameConfig.getAreaName() + ", saving world " + getWorldName() + " done");
            });
        });
    }

    @Override
    public TNTRunConfig getGameConfig() {
        return (TNTRunConfig) gameConfig;
    }

    @Override
    public TNTRunHandler getGameHandler() {
        return (TNTRunHandler) gameHandler;
    }

    public String getWorldName() {
        return "tntrun_" + getGameConfig().getAreaName();
    }
}
