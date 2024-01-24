package ink.ziip.championshipscore.api.game.area.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.player.CCPlayer;
import ink.ziip.championshipscore.api.team.Team;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class BaseTeamArea extends BaseArea {
    protected Team rightTeam;
    protected Team leftTeam;

    public BaseTeamArea(ChampionshipsCore plugin) {
        super(plugin);
    }


    public void sendMessageToAllGamePlayers(String message) {
        rightTeam.sendMessageToAll(message);
        leftTeam.sendMessageToAll(message);
        sendMessageToAllSpectators(message);
    }

    public void sendActionBarToAllGamePlayers(String message) {
        rightTeam.sendActionBarToAll(message);
        leftTeam.sendActionBarToAll(message);
        sendActionBarToAllSpectators(message);
    }

    public void sendActionBarToAllGameSpectators(String message) {
        for (CCPlayer ccPlayer : rightTeam.getOnlineCCPlayers()) {
            Player player = ccPlayer.getPlayer();
            if (player != null) {
                if (ccPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                    ccPlayer.sendActionBar(message);
                }
            }
        }
        for (CCPlayer ccPlayer : leftTeam.getOnlineCCPlayers()) {
            Player player = ccPlayer.getPlayer();
            if (player != null) {
                if (ccPlayer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                    ccPlayer.sendActionBar(message);
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
        rightTeam.sendTitleToAll(title, subTitle);
        leftTeam.sendTitleToAll(title, subTitle);
        sendTitleToAllSpectators(title, subTitle);
    }

    public void changeLevelForAllGamePlayers(int level) {
        rightTeam.changeLevelForAll(level);
        leftTeam.changeLevelForAll(level);
    }

    public void changeGameModelForAllGamePlayers(GameMode gameMode) {
        rightTeam.setGameModeForAllPlayers(gameMode);
        leftTeam.setGameModeForAllPlayers(gameMode);
    }

    public void cleanInventoryForAllGamePlayers() {
        rightTeam.cleanInventoryForAllPlayers();
        leftTeam.cleanInventoryForAllPlayers();
    }

    public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {
        rightTeam.playSoundToAllPlayers(sound, volume, pitch);
        leftTeam.playSoundToAllPlayers(sound, volume, pitch);
    }

    public boolean notAreaPlayer(@NotNull Player player) {
        UUID playerUUID = player.getUniqueId();
        if (rightTeam != null) {
            for (UUID uuid : rightTeam.getMembers()) {
                if (playerUUID.equals(uuid))
                    return false;
            }
        }
        if (leftTeam != null) {
            for (UUID uuid : leftTeam.getMembers()) {
                if (playerUUID.equals(uuid))
                    return false;
            }
        }
        return true;
    }
}
