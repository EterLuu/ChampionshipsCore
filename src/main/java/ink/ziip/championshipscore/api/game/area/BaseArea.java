package ink.ziip.championshipscore.api.game.area;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
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
    protected GameStageEnum gameStageEnum;

    public BaseArea(ChampionshipsCore plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
        this.gameStageEnum = GameStageEnum.END;
        baseAreaHandler = new BaseAreaHandler(plugin, this);
        baseAreaHandler.register();
    }

    public void addPlayerPoints(UUID uuid, int points) {
        playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + points);
        plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getAreaName() + "Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + ") get points " + points);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + points);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getAreaName() + "Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + ") get points " + points);
        }
    }

    public void addPlayerPointsToDatabase(GameTypeEnum gameTypeEnum) {
        for (Map.Entry<UUID, Integer> playerPointEntry : playerPoints.entrySet()) {
            plugin.getRankManager().addPlayerPoints(Bukkit.getOfflinePlayer(playerPointEntry.getKey()), gameTypeEnum, getAreaName(), playerPointEntry.getValue());
        }
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0);
        }

        return points;
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
            new BukkitRunnable() {
                @Override
                public void run() {
                    event.getEntity().spigot().respawn();
                    removeSpectator(player);
                }
            }.runTask(plugin);
        }
    }

    public void addSpectator(@NotNull Player player) {
        spectators.add(player.getUniqueId());
        player.teleport(getSpectatorSpawnLocation());
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

    public abstract Location getSpectatorSpawnLocation();

    public abstract boolean notAreaPlayer(@NotNull Player player);

    public abstract String getAreaName();

    public abstract void handlePlayerDeath(@NotNull PlayerDeathEvent event);

    public abstract void handlePlayerQuit(@NotNull PlayerQuitEvent event);

    public abstract void handlePlayerJoin(@NotNull PlayerJoinEvent event);
}
