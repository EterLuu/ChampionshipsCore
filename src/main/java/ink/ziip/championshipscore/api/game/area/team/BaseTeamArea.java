package ink.ziip.championshipscore.api.game.area.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@Getter
public abstract class BaseTeamArea extends BaseArea {
    @Nullable
    protected ChampionshipTeam rightChampionshipTeam;
    @Nullable
    protected ChampionshipTeam leftChampionshipTeam;

    public BaseTeamArea(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler, BaseGameConfig gameConfig) {
        super(plugin, gameTypeEnum, gameHandler, gameConfig);
    }

    @Override
    public void resetBaseArea() {
        resetArea();
        rightChampionshipTeam = null;
        leftChampionshipTeam = null;
    }

    public boolean tryStartGame(ChampionshipTeam rightChampionshipTeam, ChampionshipTeam leftChampionshipTeam) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        this.rightChampionshipTeam = rightChampionshipTeam;
        this.leftChampionshipTeam = leftChampionshipTeam;
        startGamePreparation();
        return true;
    }

    @Override
    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Integer> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(playerPointEntry.getKey());
                if (championshipTeam != null) {
                    if (championshipTeam.equals(rightChampionshipTeam))
                        plugin.getRankManager().addPlayerPoints(playerPointEntry.getKey(), leftChampionshipTeam, gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
                    if (championshipTeam.equals(leftChampionshipTeam))
                        plugin.getRankManager().addPlayerPoints(playerPointEntry.getKey(), rightChampionshipTeam, gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
                }
            }
        }
    }

    @Override
    public void sendMessageToAllGamePlayers(String message) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.sendMessageToAll(message);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.sendMessageToAll(message);
        sendMessageToAllSpectators(message);
    }

    @Override
    public void sendActionBarToAllGamePlayers(String message) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.sendActionBarToAll(message);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.sendActionBarToAll(message);
        sendActionBarToAllSpectators(message);
    }

    @Override
    public void sendActionBarToAllGameSpectators(String message) {
        if (rightChampionshipTeam != null)
            for (ChampionshipPlayer championshipPlayer : rightChampionshipTeam.getOnlineCCPlayers()) {
                Player player = championshipPlayer.getPlayer();
                if (player != null) {
                    if (championshipPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                        championshipPlayer.sendActionBar(message);
                    }
                }
            }
        if (leftChampionshipTeam != null)
            for (ChampionshipPlayer championshipPlayer : leftChampionshipTeam.getOnlineCCPlayers()) {
                Player player = championshipPlayer.getPlayer();
                if (player != null) {
                    if (championshipPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                        championshipPlayer.sendActionBar(message);
                    }
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
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.sendTitleToAll(title, subTitle);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.sendTitleToAll(title, subTitle);
        sendTitleToAllSpectators(title, subTitle);
    }

    @Override
    public void changeLevelForAllGamePlayers(int level) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.changeLevelForAll(Math.abs(level));
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.changeLevelForAll(Math.abs(level));
        changeLevelToAllSpectators(level);
    }

    @Override
    public void changeGameModelForAllGamePlayers(GameMode gameMode) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.setGameModeForAllPlayers(gameMode);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.setGameModeForAllPlayers(gameMode);
    }

    @Override
    public void setHealthForAllGamePlayers(double health) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.setHealthForAllPlayers(health);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.setHealthForAllPlayers(health);
    }

    @Override
    public void setFoodLevelForAllGamePlayers(int level) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.setFoodLevelForAllPlayers(level);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.setFoodLevelForAllPlayers(level);
    }

    @Override
    public void teleportAllPlayers(Location location) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.teleportAllPlayers(location);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.teleportAllPlayers(location);
    }

    @Override
    public void clearEffectsForAllGamePlayers() {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.clearEffectsForAllPlayers();
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.clearEffectsForAllPlayers();
    }

    @Override
    public void cleanInventoryForAllGamePlayers() {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.cleanInventoryForAllPlayers();
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.cleanInventoryForAllPlayers();
    }

    @Override
    public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.playSoundToAllPlayers(sound, volume, pitch);
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.playSoundToAllPlayers(sound, volume, pitch);
    }

    @Override
    public boolean notAreaPlayer(@NotNull Player player) {
        UUID playerUUID = player.getUniqueId();
        if (rightChampionshipTeam != null) {
            for (UUID uuid : rightChampionshipTeam.getMembers()) {
                if (playerUUID.equals(uuid))
                    return false;
            }
        }
        if (leftChampionshipTeam != null) {
            for (UUID uuid : leftChampionshipTeam.getMembers()) {
                if (playerUUID.equals(uuid))
                    return false;
            }
        }
        return true;
    }

    @Override
    public void revokeAllGamePlayersAdvancements() {
        if (rightChampionshipTeam != null) {
            for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
                Utils.revokeAllAdvancements(player);
            }
        }
        if (leftChampionshipTeam != null) {
            for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
                Utils.revokeAllAdvancements(player);
            }
        }
    }

    @Override
    public void removeAllPlayers() {
        if (rightChampionshipTeam != null) {
            rightChampionshipTeam.teleportAllPlayers(getLobbyLocation());
            rightChampionshipTeam.setGameModeForAllPlayers(GameMode.ADVENTURE);
        }
        if (leftChampionshipTeam != null) {
            leftChampionshipTeam.teleportAllPlayers(getLobbyLocation());
            leftChampionshipTeam.setGameModeForAllPlayers(GameMode.ADVENTURE);
        }
    }
}
