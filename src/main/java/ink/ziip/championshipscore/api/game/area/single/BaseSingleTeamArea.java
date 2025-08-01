package ink.ziip.championshipscore.api.game.area.single;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public abstract class BaseSingleTeamArea extends BaseArea {
    protected final List<UUID> gamePlayers = new ArrayList<>();
    protected final List<ChampionshipTeam> gameTeams = new ArrayList<>();

    public BaseSingleTeamArea(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler, BaseGameConfig gameConfig) {
        super(plugin, gameTypeEnum, gameHandler, gameConfig);
    }

    @Override
    public void resetBaseArea() {
        resetArea();
        gameTeams.clear();
        gamePlayers.clear();
    }

    public boolean tryStartGame(List<ChampionshipTeam> championshipTeams) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        gameTeams.addAll(championshipTeams);

        for (ChampionshipTeam championshipTeam : championshipTeams) {
            gamePlayers.addAll(championshipTeam.getMembers());
        }

        startGamePreparation();
        return true;
    }

    public boolean tryStartGame(List<ChampionshipTeam> championshipTeams, List<UUID> players) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        gameTeams.addAll(championshipTeams);

        gamePlayers.addAll(players);

        startGamePreparation();
        return true;
    }

    public String getTeamPointsRank() {
        Map<ChampionshipTeam, Integer> teamPoints = new ConcurrentHashMap<>();
        for (ChampionshipTeam championshipTeam : gameTeams) {
            teamPoints.put(championshipTeam, getTeamPoints(championshipTeam));
        }
        ArrayList<Map.Entry<ChampionshipTeam, Integer>> list;
        list = new ArrayList<>(teamPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_BOARD_BAR
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%area%", gameConfig.getAreaName()))
                .append("\n");

        int i = 1;
        for (Map.Entry<ChampionshipTeam, Integer> entry : list) {
            String row = MessageConfig.GAME_BOARD_RWO
                    .replace("%team_rank%", String.valueOf(i))
                    .replace("%team%", entry.getKey().getColoredName())
                    .replace("%team_point%", String.valueOf(entry.getValue()));

            stringBuilder.append(row).append("\n");

            i++;
        }

        return stringBuilder.toString();
    }

    @Override
    public void sendMessageToAllGamePlayers(String message) {
        for (UUID uuid : gamePlayers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            championshipPlayer.sendMessage(message);
        }
        sendMessageToAllSpectators(message);
    }

    @Override
    public void sendActionBarToAllGamePlayers(String message) {
        for (UUID uuid : gamePlayers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            championshipPlayer.sendActionBar(message);
        }
        sendActionBarToAllSpectators(message);
    }

    @Override
    public void sendActionBarToAllGameSpectators(String message) {
        for (UUID uuid : gamePlayers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            if (championshipPlayer.getPlayer() != null && championshipPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                championshipPlayer.sendActionBar(message);
            }
        }
        sendActionBarToAllSpectators(message);
    }

    @Override
    public void sendMessageToAllGamePlayersInActionbarAndMessage(String message) {
        sendMessageToAllGamePlayers(message);
        sendActionBarToAllGamePlayers(message);
    }

    @Override
    public void sendTitleToAllGamePlayers(String title, String subTitle) {
        for (UUID uuid : gamePlayers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            championshipPlayer.sendTitle(title, subTitle);
        }
        sendTitleToAllSpectators(title, subTitle);
    }

    @Override
    public void changeLevelForAllGamePlayers(int level) {
        for (UUID uuid : gamePlayers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            championshipPlayer.setLevel(Math.abs(level));
        }
        changeLevelToAllSpectators(level);
    }

    @Override
    public void changeGameModelForAllGamePlayers(GameMode gameMode) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(gameMode);
                });
            }
        }
    }

    @Override
    public void setHealthForAllGamePlayers(double health) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.setHealth(health);
        }
    }

    @Override
    public void setFoodLevelForAllGamePlayers(int level) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.setFoodLevel(level);
        }
    }


    @Override
    public void teleportAllPlayers(Location location) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.teleport(location);
        }
    }

    @Override
    public void clearEffectsForAllGamePlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                for (PotionEffect potionEffect : player.getActivePotionEffects())
                    player.removePotionEffect(potionEffect.getType());
        }
    }

    @Override
    public void cleanInventoryForAllGamePlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.getInventory().clear();
        }
    }

    @Override
    public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    @Override
    public void revokeAllGamePlayersAdvancements() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                Utils.revokeAllAdvancements(player);
        }
    }

    @Override
    public abstract void startGamePreparation();

    @Override
    public boolean notAreaPlayer(@NotNull Player player) {
        UUID playerUUID = player.getUniqueId();
        return !gamePlayers.contains(playerUUID);
    }

    @Override
    public void removeAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(getLobbyLocation());
                ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                });
            }
        }
    }
}
