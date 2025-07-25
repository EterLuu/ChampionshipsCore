package ink.ziip.championshipscore.api.game.area;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class BaseArea {
    protected final HashSet<UUID> spectators = new HashSet<>();
    protected final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    protected final ChampionshipsCore plugin;
    protected final BukkitScheduler scheduler;
    protected final BaseAreaHandler baseAreaHandler;
    protected final PlayerManager playerManager;

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseArea(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler, BaseGameConfig gameConfig) {
        this.playerManager = plugin.getPlayerManager();

        this.gameStageEnum = GameStageEnum.END;
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
        this.gameTypeEnum = gameTypeEnum;

        this.gameHandler = gameHandler;
        this.gameConfig = gameConfig;

        baseAreaHandler = new BaseAreaHandler(plugin, this);
        baseAreaHandler.register();
    }

    public void resetGame() {
        resetBaseArea();
        playerPoints.clear();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public void resetPlayerHealthFoodEffectLevelInventory() {
        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        clearEffectsForAllGamePlayers();
        cleanInventoryForAllGamePlayers();
        changeLevelForAllGamePlayers(0);
    }

    public void addPlayerPoints(UUID uuid, double points) {
        playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0d) + points);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + "Player " + plugin.getPlayerManager().getPlayerName(uuid) + " (" + uuid + ") get points " + points);
        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
        if (championshipPlayer != null)
            championshipPlayer.sendActionBar("&e[+] " + points);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0d) + points);
            plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", " + "Player " + plugin.getPlayerManager().getPlayerName(uuid) + " (" + uuid + ") get points " + points);
        }
    }

    public void sendMessageToConsole(String message) {
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", " + message);
    }

    public void addPlayerPointsToDatabase() {
        if (plugin.isLoaded()) {
            for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
                if (playerPointEntry.getValue() != 0)
                    plugin.getRankManager().addPlayerPoints(playerPointEntry.getKey(), null, gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
            }
        }
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0d);
        }

        return points;
    }

    public String getPlayerPointsRank() {
        ArrayList<Map.Entry<UUID, Double>> list;
        list = new ArrayList<>(playerPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_BOARD_BAR
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%area%", gameConfig.getAreaName()))
                .append("\n");

        int i = 1;
        for (Map.Entry<UUID, Double> entry : list) {
            String username = plugin.getPlayerManager().getPlayerName(entry.getKey());
            if (username != null) {
                String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                        .replace("%player_rank%", String.valueOf(i))
                        .replace("%player%", username)
                        .replace("%player_point%", String.valueOf(entry.getValue()));

                stringBuilder.append(row).append("\n");
                i++;
            }
        }

        return stringBuilder.toString();
    }

    public GameStageEnum getGameStageEnum() {
        synchronized (this) {
            return this.gameStageEnum;
        }
    }

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        synchronized (this) {
            this.gameStageEnum = gameStageEnum;
        }
    }

    public void loadMap(World.Environment environment) {
        if (!plugin.isLoaded())
            return;

        teleportAllSpectators(getLobbyLocation());

        setGameStageEnum(GameStageEnum.END);
        getGameHandler().unRegister();
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", start loading world " + getWorldName());

        File target = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), getWorldName());

        // If already has a same world, delete it.
        if (target.isDirectory()) {
            String[] list = target.list();
            if (list != null && list.length > 0) {
                plugin.getWorldManager().deleteWorld(getWorldName(), true);
            }
        }

        File maps = new File(plugin.getDataFolder(), "maps");
        File source = new File(maps, getWorldName());

        // Copy world files to destination
        plugin.getWorldManager().copyWorldFiles(source, target);

        // Load world
        plugin.getWorldManager().loadWorld(getWorldName(), environment, false);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", world " + getWorldName() + " loaded");

        teleportAllSpectators(getSpectatorSpawnLocation());
    }

    public void saveMap(World.Environment environment) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return;

        setGameStageEnum(GameStageEnum.END);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", start saving world " + getWorldName());
        teleportAllSpectators(getLobbyLocation());

        World editWorld = plugin.getServer().getWorld(getWorldName());
        if (editWorld != null) {
            for (Player player : editWorld.getPlayers()) {
                player.teleport(CCConfig.LOBBY_LOCATION);
            }

            // Unload world but not remove files
            plugin.getWorldManager().unloadWorld(getWorldName(), true);

            File dataDirectory = new File(plugin.getDataFolder(), "maps");
            File target = new File(dataDirectory, getWorldName());

            // Delete old world files stored in maps
            plugin.getWorldManager().deleteWorldFiles(target);

            File source = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), getWorldName());

            plugin.getWorldManager().copyWorldFiles(source, target);
            plugin.getWorldManager().deleteWorldFiles(source);

            loadMap(environment);
        }
    }

    public Location getLobbyLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    public boolean isSpectator(@NotNull Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public void handleSpectatorDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isSpectator(player)) {
            event.setDroppedExp(0);
            event.getDrops().clear();
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                removeSpectator(player);
            });
        }
    }

    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            player.teleport(getSpectatorSpawnLocation());
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.SPECTATOR));
        }
    }

    public void teleportAllSpectators(@NotNull Location location) {
        for (Player player : getOnlineSpectators()) {
            player.teleport(location);
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.SPECTATOR));
        }
    }

    public void addSpectator(@NotNull Player player) {
        spectators.add(player.getUniqueId());
        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    public void removeAllSpectator() {
        for (Player player : getOnlineSpectators()) {
            removeSpectator(player);
        }
        spectators.clear();
    }

    public void endGameFinally() {
        removeAllSpectator();
        removeAllPlayers();
        endGame();
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (spectators.contains(uuid)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);
            } else {
                onlyRemoveSpectatorFromList(uuid);
            }
        }
    }

    public void removeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (spectators.contains(uuid)) {
            spectators.remove(player.getUniqueId());
            player.teleport(getLobbyLocation());
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> player.setGameMode(GameMode.ADVENTURE));
            player.setLevel(0);
        }
    }

    public void onlyRemoveSpectatorFromList(@NotNull UUID uuid) {
        spectators.remove(uuid);
    }

    public List<Player> getOnlineSpectators() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }

        return list;
    }

    public List<ChampionshipPlayer> getOnlineCCSpectators() {
        List<ChampionshipPlayer> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
            if (championshipPlayer != null) {
                list.add(championshipPlayer);
            }
        }

        return list;
    }

    public void sendMessageToAllSpectators(String message) {
        for (Player player : getOnlineSpectators()) {
            player.sendMessage(message);
        }
        Bukkit.getServer().getLogger().log(Level.INFO, message);
    }

    public void sendActionBarToAllSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendActionBar(message);
        }
    }

    public void changeLevelToAllSpectators(int level) {
        for (Player player : getOnlineSpectators()) {
            player.setLevel(Math.abs(level));
        }
    }

    public void sendTitleToAllSpectators(String title, String subTitle) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendTitle(title, subTitle);
        }
    }

    public void cleanDroppedItems() {
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world != null) {
            world.getNearbyEntities(new BoundingBox(
                            pos1.getX(),
                            pos1.getY(),
                            pos1.getZ(),
                            pos2.getX(),
                            pos2.getY(),
                            pos2.getZ()))
                    .forEach(entity -> {
                        if (entity instanceof Item) {
                            entity.remove();
                        }
                    });
        }
    }

    public boolean notInArea(Location location) {
        return !location.toVector().isInAABB(getGameConfig().getAreaPos1(), getGameConfig().getAreaPos2());
    }

    public abstract Location getSpectatorSpawnLocation();

    public abstract void endGame();

    public abstract void resetBaseArea();

    public abstract void resetArea();

    public abstract BaseGameConfig getGameConfig();

    public abstract BaseListener getGameHandler();

    public abstract String getWorldName();

    public abstract void removeAllPlayers();

    public abstract void startGamePreparation();

    public abstract void sendMessageToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGameSpectators(String message);

    public abstract void sendMessageToAllGamePlayersInActionbarAndMessage(String message);

    public abstract void sendTitleToAllGamePlayers(String title, String subTitle);

    public abstract void changeLevelForAllGamePlayers(int level);

    public abstract void changeGameModelForAllGamePlayers(GameMode gameMode);

    public abstract void setHealthForAllGamePlayers(double health);

    public abstract void revokeAllGamePlayersAdvancements();

    public abstract void setFoodLevelForAllGamePlayers(int level);

    public abstract void teleportAllPlayers(Location location);

    public abstract void clearEffectsForAllGamePlayers();

    public abstract void cleanInventoryForAllGamePlayers();

    public abstract void playSoundToAllGamePlayers(Sound sound, float volume, float pitch);

    public abstract boolean notAreaPlayer(@NotNull Player player);

    public abstract void handlePlayerDeath(@NotNull PlayerDeathEvent event);

    public abstract void handlePlayerQuit(@NotNull PlayerQuitEvent event);

    public abstract void handlePlayerJoin(@NotNull PlayerJoinEvent event);
}
