package ink.ziip.championshipscore.api.game.area.single;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public abstract class BaseSingleTeamArea extends BaseArea {
    protected final List<UUID> gamePlayers = new ArrayList<>();
    protected final List<ChampionshipTeam> gameTeams = new ArrayList<>();

    public BaseSingleTeamArea(ChampionshipsCore plugin) {
        super(plugin);
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

    public String getTeamPintsRank(GameTypeEnum gameTypeEnum) {
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
                        .replace("%area%", getAreaName()))
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

    public void sendMessageToAllGamePlayers(String message) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.sendMessageToAll(message);
        sendMessageToAllSpectators(message);
    }

    public void sendActionBarToAllGamePlayers(String message) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.sendActionBarToAll(message);
        sendActionBarToAllSpectators(message);
    }

    public void sendActionBarToAllGameSpectators(String message) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            for (ChampionshipPlayer championshipPlayer : championshipTeam.getOnlineCCPlayers()) {
                Player player = championshipPlayer.getPlayer();
                if (player != null) {
                    if (championshipPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                        championshipPlayer.sendActionBar(message);
                    }
                }
            }
        sendActionBarToAllSpectators(message);
    }

    public void sendMessageToAllGamePlayersInActionbarAndMessage(String message) {
        sendMessageToAllGamePlayers(message);
        sendActionBarToAllGamePlayers(message);
    }

    public void sendTitleToAllGamePlayers(String title, String subTitle) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.sendTitleToAll(title, subTitle);
        sendTitleToAllSpectators(title, subTitle);
    }

    public void changeLevelForAllGamePlayers(int level) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.changeLevelForAll(level);
    }

    public void changeGameModelForAllGamePlayers(GameMode gameMode) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.setGameModeForAllPlayers(gameMode);
    }

    public void setHealthForAllGamePlayers(double health) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.setHealthForAllPlayers(health);
    }

    public void setFoodLevelForAllGamePlayers(int level) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.setFoodLevelForAllPlayers(level);
    }


    public void teleportAllPlayers(Location location) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.teleportAllPlayers(location);
    }

    public void clearEffectsForAllGamePlayers() {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.clearEffectsForAllPlayers();
    }

    public void cleanInventoryForAllGamePlayers() {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.cleanInventoryForAllPlayers();
    }

    public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {
        for (ChampionshipTeam championshipTeam : gameTeams)
            championshipTeam.playSoundToAllPlayers(sound, volume, pitch);
    }

    public abstract void startGamePreparation();

    public boolean notAreaPlayer(@NotNull Player player) {
        UUID playerUUID = player.getUniqueId();
        return !gamePlayers.contains(playerUUID);
    }
}