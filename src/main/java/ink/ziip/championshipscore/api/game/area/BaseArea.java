package ink.ziip.championshipscore.api.game.area;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class BaseArea {
    protected final HashSet<UUID> spectators = new HashSet<>();
    protected final Map<UUID, Integer> playerPoints = new ConcurrentHashMap<>();
    protected final ChampionshipsCore plugin;
    protected final BukkitScheduler scheduler;
    protected final BaseAreaHandler baseAreaHandler;

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseArea(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler, BaseGameConfig gameConfig) {
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

    public void addPlayerPoints(UUID uuid, int points) {
        playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + points);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + "Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + ") get points " + points);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + points);
            plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + "Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + ") get points " + points);
        }
    }

    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Integer> playerPointEntry : playerPoints.entrySet()) {
            plugin.getRankManager().addPlayerPoints(Bukkit.getOfflinePlayer(playerPointEntry.getKey()), gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
        }
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0);
        }

        return points;
    }

    public String getPlayerPointsRank() {
        ArrayList<Map.Entry<UUID, Integer>> list;
        list = new ArrayList<>(playerPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_BOARD_BAR
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%area%", gameConfig.getAreaName()))
                .append("\n");

        int i = 1;
        for (Map.Entry<UUID, Integer> entry : list) {
            // TODO changed method getting name
            String username = Bukkit.getOfflinePlayer(entry.getKey()).getName();
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

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        synchronized (this) {
            this.gameStageEnum = gameStageEnum;
        }
    }

    public GameStageEnum getGameStageEnum() {
        synchronized (this) {
            return this.gameStageEnum;
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

    public void addSpectator(@NotNull Player player) {
        spectators.add(player.getUniqueId());
        player.teleport(gameConfig.getSpectatorSpawnPoint());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        plugin.getGameManager().setPlayerVisible(player, false);
    }

    public void removeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (spectators.contains(uuid)) {
            spectators.remove(player.getUniqueId());
            player.teleport(getLobbyLocation());
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            plugin.getGameManager().setPlayerVisible(player, true);
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
    }

    public void sendActionBarToAllSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendActionBar(message);
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
        World world = getGameConfig().getSpectatorSpawnPoint().getWorld();
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

    public abstract void resetBaseArea();

    public abstract void resetArea();

    public abstract BaseGameConfig getGameConfig();

    public abstract BaseListener getGameHandler();

    public abstract void startGamePreparation();

    public abstract void sendMessageToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGameSpectators(String message);

    public abstract void sendMessageToAllGamePlayersInActionbarAndMessage(String message);

    public abstract void sendTitleToAllGamePlayers(String title, String subTitle);

    public abstract void changeLevelForAllGamePlayers(int level);

    public abstract void changeGameModelForAllGamePlayers(GameMode gameMode);

    public abstract void setHealthForAllGamePlayers(double health);

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
