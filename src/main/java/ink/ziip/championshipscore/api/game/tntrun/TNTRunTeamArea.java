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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
    private BukkitTask tntGeneratorTask;
    private int tntTimer;

    public TNTRunTeamArea(ChampionshipsCore plugin, TNTRunConfig tntRunConfig, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.TNTRun, new TNTRunHandler(plugin), tntRunConfig);

        getGameHandler().setTntRunTeamArea(this);
        tntRunConfig.setAreaName(areaName);

        if (!firstTime) {
            loadMap(World.Environment.NORMAL);
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    @Override
    public void resetArea() {
        playerSpawnLocations.clear();
        deathPlayer.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        handlePlayerMoveTask = null;
        tntGeneratorTask = null;

        scheduler.runTaskLater(plugin, () -> {
            loadMap(World.Environment.NORMAL);
        }, 100L);
    }

    @Override
    public void resetGame() {
        playerPoints.clear();
        resetBaseArea();
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        try {
            return Utils.getLocation(getGameConfig().getPlayerSpawnPoints().get(ThreadLocalRandom.current().nextInt(getGameConfig().getPlayerSpawnPoints().size())));
        } catch (Exception ignored) {
            return getSpectatorSpawnLocation();
        }
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
            if (player != null) {
                player.teleport(playerSpawnLocations.get(uuid));
            }
        }

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        giveElytraToAllPlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TNT_RUN_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.TNT_RUN_START_PREPARATION_TITLE, MessageConfig.TNT_RUN_START_PREPARATION_SUBTITLE);

        timer = 10;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer < 5 && timer > 0) {
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
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
            if (player == null) {
                deathPlayer.add(uuid);
                offlinePlayers++;
                plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + "Player " + playerManager.getPlayerName(uuid) + " (" + uuid + ") not online");
            }
        }
        addPointsToAllSurvivePlayers(offlinePlayers * 4);

//        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TNT_RUN_GAME_START);
        sendTitleToAllGamePlayers(MessageConfig.TNT_RUN_GAME_START_TITLE, MessageConfig.TNT_RUN_GAME_START_SUBTITLE);
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.TNT_RUN_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                changeLevelForAllGamePlayers(timer);
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            if (timer == 120 || timer == 60 || timer == 20) {
                sendMessageToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN);
                sendActionBarToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN);

                tntTimer = 9;

                tntGeneratorTask = scheduler.runTaskTimerAsynchronously(plugin, () -> {

                    int i = 0;
                    Iterator<String> locationIterator = getGameConfig().getPlayerSpawnPoints().iterator();

                    while (i < 12) {
                        if (!locationIterator.hasNext())
                            locationIterator = getGameConfig().getPlayerSpawnPoints().iterator();

                        Location location = Utils.getLocation(locationIterator.next());
                        Location tntLocation = location.clone();
                        tntLocation.add(ThreadLocalRandom.current().nextInt(-30, 30), 15, ThreadLocalRandom.current().nextInt(-30, 30));

                        while (notInArea(tntLocation)) {
                            tntLocation = location.clone();
                            tntLocation.add(ThreadLocalRandom.current().nextInt(-30, 30), 15, ThreadLocalRandom.current().nextInt(-30, 30));
                        }

                        final Location finalTNTLocation = tntLocation;
                        scheduler.runTaskLater(plugin, () -> {
                            World world = finalTNTLocation.getWorld();
                            if (world != null) {
                                TNTPrimed tntPrimed = (TNTPrimed) world.spawnEntity(finalTNTLocation, EntityType.TNT);
                                tntPrimed.setFuseTicks(200);
                                scheduler.runTaskTimer(plugin, (task) -> {
                                    if (!tntPrimed.isValid()) {
                                        task.cancel();
                                        return;
                                    }
                                    if (tntPrimed.getFuseTicks() <= 0) {
                                        task.cancel();
                                        return;
                                    }

                                    Location tntTraceLocation = tntPrimed.getLocation();
                                    if (getBlockUnderLocation(tntTraceLocation, 0.8) != null) {
                                        tntPrimed.setFuseTicks(0);
                                    }
                                    if (notInArea(tntTraceLocation)) {
                                        tntPrimed.setFuseTicks(0);
                                    }
                                }, 0, 1L);
                            }
                        }, 0L);
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
            if (player != null && !deathPlayer.contains(uuid)) {
                handlePlayerMove(player);
            }
        }), 0, 1L);
    }

    private void handlePlayerMove(@NotNull Player player) {
        destroyBlock(player.getLocation());
    }

    private void giveElytraToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !deathPlayer.contains(uuid)) {
                ItemStack elytra = new ItemStack(Material.ELYTRA);
                elytra.addEnchantment(Enchantment.UNBREAKING, 1);

                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (championshipTeam != null) {
                    player.getInventory().setChestplate(championshipTeam.getChestPlate());
                    player.getInventory().setLeggings(championshipTeam.getBoots());
                }

                player.getInventory().addItem(elytra.clone());
            }
        }
    }

    public Block getBlockUnderLocation(Location location, double bias) {
        World world = location.getWorld();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        if (world == null)
            return null;

        for (int i = 0; i <= 1; i++) {
            Block block1 = world.getBlockAt(
                    NumberConversions.floor(x + bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z - bias)
            );
            Material block1Type = block1.getType();
            if (block1Type != Material.AIR && block1Type != Material.LIGHT)
                return block1;
            Block block2 = world.getBlockAt(
                    NumberConversions.floor(x - bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z + bias)
            );
            Material block2Type = block2.getType();
            if (block2Type != Material.AIR && block2Type != Material.LIGHT)
                return block2;
            Block block3 = world.getBlockAt(
                    NumberConversions.floor(x + bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z + bias)
            );
            Material block3Type = block3.getType();
            if (block3Type != Material.AIR && block3Type != Material.LIGHT)
                return block3;
            Block block4 = world.getBlockAt(
                    NumberConversions.floor(x - bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z - bias)
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

        Block block = getBlockUnderLocation(location, 0.3);

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
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam != null)
                plugin.getGameApiClient().sendGameEvent(GameTypeEnum.TNTRun, player, championshipTeam, "Player_Fall", player.getName());
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SPECTATOR);
            });
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SPECTATOR);
            });
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

    @Override
    public TNTRunConfig getGameConfig() {
        return (TNTRunConfig) gameConfig;
    }

    @Override
    public TNTRunHandler getGameHandler() {
        return (TNTRunHandler) gameHandler;
    }

    public String getWorldName() {
        return "tntrun_" + gameConfig.getAreaName();
    }
}
