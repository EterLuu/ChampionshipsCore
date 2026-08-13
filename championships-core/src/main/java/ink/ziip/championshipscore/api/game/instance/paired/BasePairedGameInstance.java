package ink.ziip.championshipscore.api.game.instance.paired;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import org.bukkit.Instrument;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public abstract class BasePairedGameInstance extends BaseGameInstance {
    @Nullable
    protected ChampionshipTeam rightChampionshipTeam;
    @Nullable
    protected ChampionshipTeam leftChampionshipTeam;

    public BasePairedGameInstance(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum,
                                  BaseListener gameHandler, BaseGameConfig gameConfig) {
        super(plugin, gameTypeEnum, gameHandler, gameConfig);
    }

    @Override
    public void resetBaseArea() {
        releaseStartChunks();
        resetArea();
        rightChampionshipTeam = null;
        leftChampionshipTeam = null;
    }

    public boolean tryStartGame(ChampionshipTeam rightChampionshipTeam, ChampionshipTeam leftChampionshipTeam) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        cancelPostGameRoutingBeforeStart();
        setGameStageEnum(GameStageEnum.LOADING);

        this.rightChampionshipTeam = rightChampionshipTeam;
        this.leftChampionshipTeam = leftChampionshipTeam;
        startGamePreparationAfterPreload();
        return true;
    }

    @Override
    public void addPlayerPointsToDatabase() {
        if (!isEventRun()) return;
        // The base implementation owns the abort/settlement guard and the ordinary single-team path.
        // Paired games build rival-aware rows below, so expose the same guard through this helper.
        if (!isSettlementAllowed()) return;
        List<ink.ziip.championshipscore.api.rank.RankManager.PointSubmission> submissions = new ArrayList<>();
        for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(playerPointEntry.getKey());
                if (championshipTeam != null) {
                    if (championshipTeam.equals(rightChampionshipTeam))
                        submissions.add(new ink.ziip.championshipscore.api.rank.RankManager.PointSubmission(
                                UUID.randomUUID(), playerPointEntry.getKey(), leftChampionshipTeam,
                                gameTypeEnum, gameConfig.getAreaName(), "scc", playerPointEntry.getValue()));
                    if (championshipTeam.equals(leftChampionshipTeam))
                        submissions.add(new ink.ziip.championshipscore.api.rank.RankManager.PointSubmission(
                                UUID.randomUUID(), playerPointEntry.getKey(), rightChampionshipTeam,
                                gameTypeEnum, gameConfig.getAreaName(), "scc", playerPointEntry.getValue()));
                }
            }
        }
        plugin.getRankManager().addPlayerPointsBatch(submissions).exceptionally(failure -> false);
        plugin.getRankManager().refreshAfterPendingPointWrites();
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
    protected Collection<Player> getOnlineParticipantSpectators() {
        Set<Player> players = new LinkedHashSet<>();
        if (rightChampionshipTeam != null)
            for (Player player : rightChampionshipTeam.getOnlinePlayers())
                if (isManagedSpectator(player))
                    players.add(player);
        if (leftChampionshipTeam != null)
            for (Player player : leftChampionshipTeam.getOnlinePlayers())
                if (isManagedSpectator(player))
                    players.add(player);
        return players;
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
    public void playNoteToAllGamePlayers(Instrument instrument, Note note) {
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.getOnlinePlayers()
                    .forEach(player -> player.playNote(player.getLocation(), instrument, note));
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.getOnlinePlayers()
                    .forEach(player -> player.playNote(player.getLocation(), instrument, note));
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
    public void removeAllPlayers() {
        if (rightChampionshipTeam != null) {
            for (Player player : rightChampionshipTeam.getOnlinePlayers())
                sanitizeParticipantForLobby(player, true);
        }
        if (leftChampionshipTeam != null) {
            for (Player player : leftChampionshipTeam.getOnlinePlayers())
                sanitizeParticipantForLobby(player, true);
        }
    }

    @Override
    public Collection<UUID> getParticipantUniqueIds() {
        Set<UUID> participants = new LinkedHashSet<>();
        if (rightChampionshipTeam != null) participants.addAll(rightChampionshipTeam.getMembers());
        if (leftChampionshipTeam != null) participants.addAll(leftChampionshipTeam.getMembers());
        return List.copyOf(participants);
    }
}
