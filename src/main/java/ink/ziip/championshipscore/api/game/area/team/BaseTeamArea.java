package ink.ziip.championshipscore.api.game.area.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Getter
public abstract class BaseTeamArea extends BaseArea {
    protected ChampionshipTeam rightChampionshipTeam;
    protected ChampionshipTeam leftChampionshipTeam;

    public BaseTeamArea(ChampionshipsCore plugin) {
        super(plugin);
    }

    public abstract void startGamePreparation();

    public boolean tryStartGame(ChampionshipTeam rightChampionshipTeam, ChampionshipTeam leftChampionshipTeam) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        this.rightChampionshipTeam = rightChampionshipTeam;
        this.leftChampionshipTeam = leftChampionshipTeam;
        startGamePreparation();
        return true;
    }

    public void sendMessageToAllGamePlayers(String message) {
        rightChampionshipTeam.sendMessageToAll(message);
        leftChampionshipTeam.sendMessageToAll(message);
        sendMessageToAllSpectators(message);
    }

    public void sendActionBarToAllGamePlayers(String message) {
        rightChampionshipTeam.sendActionBarToAll(message);
        leftChampionshipTeam.sendActionBarToAll(message);
        sendActionBarToAllSpectators(message);
    }

    public void sendActionBarToAllGameSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : rightChampionshipTeam.getOnlineCCPlayers()) {
            Player player = championshipPlayer.getPlayer();
            if (player != null) {
                if (championshipPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                    championshipPlayer.sendActionBar(message);
                }
            }
        }
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

    public void sendMessageToAllGamePlayersInActionbarAndMessage(String message) {
        sendMessageToAllGamePlayers(message);
        sendActionBarToAllGamePlayers(message);
    }

    public void sendTitleToAllGamePlayers(String title, String subTitle) {
        rightChampionshipTeam.sendTitleToAll(title, subTitle);
        leftChampionshipTeam.sendTitleToAll(title, subTitle);
        sendTitleToAllSpectators(title, subTitle);
    }

    public void changeLevelForAllGamePlayers(int level) {
        rightChampionshipTeam.changeLevelForAll(level);
        leftChampionshipTeam.changeLevelForAll(level);
    }

    public void changeGameModelForAllGamePlayers(GameMode gameMode) {
        rightChampionshipTeam.setGameModeForAllPlayers(gameMode);
        leftChampionshipTeam.setGameModeForAllPlayers(gameMode);
    }

    public void setHealthForAllGamePlayers(double health) {
        rightChampionshipTeam.setHealthForAllPlayers(health);
        leftChampionshipTeam.setHealthForAllPlayers(health);
    }

    public void clearEffectsForAllGamePlayers() {
        rightChampionshipTeam.clearEffectsForAllPlayers();
        leftChampionshipTeam.clearEffectsForAllPlayers();
    }

    public void cleanInventoryForAllGamePlayers() {
        rightChampionshipTeam.cleanInventoryForAllPlayers();
        leftChampionshipTeam.cleanInventoryForAllPlayers();
    }

    public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {
        rightChampionshipTeam.playSoundToAllPlayers(sound, volume, pitch);
        leftChampionshipTeam.playSoundToAllPlayers(sound, volume, pitch);
    }

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
}
