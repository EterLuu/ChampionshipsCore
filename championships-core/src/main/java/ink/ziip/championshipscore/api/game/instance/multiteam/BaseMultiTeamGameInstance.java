package ink.ziip.championshipscore.api.game.instance.multiteam;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.spatial.TeleportPositions;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import lombok.Getter;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public abstract class BaseMultiTeamGameInstance extends BaseGameInstance {
    protected final List<UUID> gamePlayers = new ArrayList<>();
    protected final List<ChampionshipTeam> gameTeams = new ArrayList<>();

    public BaseMultiTeamGameInstance(
            ChampionshipsCore plugin,
            GameTypeEnum gameTypeEnum,
            BaseListener gameHandler,
            BaseGameConfig gameConfig) {
        super(plugin, gameTypeEnum, gameHandler, gameConfig);
    }

    @Override
    public void resetBaseArea() {
        releaseStartChunks();
        resetArea();
        gameTeams.clear();
        gamePlayers.clear();
    }

    public boolean tryStartGame(List<ChampionshipTeam> championshipTeams) {
        if (getGameStageEnum() != GameStageEnum.WAITING || !validTeams(championshipTeams))
            return false;
        cancelPostGameRoutingBeforeStart();
        setGameStageEnum(GameStageEnum.LOADING);

        gameTeams.addAll(championshipTeams);

        for (ChampionshipTeam championshipTeam : championshipTeams) {
            gamePlayers.addAll(championshipTeam.getMembers());
        }

        startGamePreparationAfterPreload();
        return true;
    }

    public boolean tryStartGame(List<ChampionshipTeam> championshipTeams, List<UUID> players) {
        if (getGameStageEnum() != GameStageEnum.WAITING
                || !validTeams(championshipTeams)
                || players == null
                || players.isEmpty()
                || players.stream().anyMatch(Objects::isNull)
                || players.size() != new HashSet<>(players).size()
                || players.stream()
                        .anyMatch(
                                player ->
                                        championshipTeams.stream()
                                                .noneMatch(
                                                        team ->
                                                                team.getMembers()
                                                                        .contains(player))))
            return false;
        cancelPostGameRoutingBeforeStart();
        setGameStageEnum(GameStageEnum.LOADING);

        gameTeams.addAll(championshipTeams);

        gamePlayers.addAll(players);

        startGamePreparationAfterPreload();
        return true;
    }

    private static boolean validTeams(List<ChampionshipTeam> teams) {
        if (teams == null || teams.isEmpty() || teams.stream().anyMatch(Objects::isNull))
            return false;
        Set<UUID> players = new HashSet<>();
        for (ChampionshipTeam team : teams) {
            if (team.getMembers().isEmpty() || !players.addAll(team.getMembers())) return false;
        }
        return true;
    }

    public String getTeamPointsRank() {
        Map<ChampionshipTeam, Double> teamPoints = new ConcurrentHashMap<>();
        for (ChampionshipTeam championshipTeam : gameTeams) {
            teamPoints.put(championshipTeam, getTeamPoints(championshipTeam));
        }
        ArrayList<Map.Entry<ChampionshipTeam, Double>> list;
        list = new ArrayList<>(teamPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder
                .append(MessageConfig.GAME_BOARD_BAR.replace("%game%", gameTypeEnum.toString()))
                .append("\n");

        int i = 1;
        for (Map.Entry<ChampionshipTeam, Double> entry : list) {
            if (i > 5) break;
            String row =
                    MessageConfig.GAME_BOARD_RWO
                            .replace("%team_rank%", String.valueOf(i))
                            .replace("%team%", entry.getKey().getColoredName())
                            .replace("%team_point%", LegacyText.formatPoints(entry.getValue()));

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
    protected Collection<Player> getOnlineParticipantSpectators() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && isManagedSpectator(player)) players.add(player);
        }
        return players;
    }

    protected void sendActionBarToActiveGamePlayers(String message) {
        for (UUID uuid : gamePlayers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            Player player = championshipPlayer == null ? null : championshipPlayer.getPlayer();
            if (player != null && !isManagedSpectator(player))
                championshipPlayer.sendActionBar(message);
        }
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
                if (Bukkit.isPrimaryThread()) {
                    player.setGameMode(gameMode);
                } else {
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore
                            .getServer()
                            .getScheduler()
                            .runTask(
                                    championshipsCore,
                                    () -> {
                                        if (player.isOnline()) player.setGameMode(gameMode);
                                    });
                }
            }
        }
    }

    @Override
    public void setHealthForAllGamePlayers(double health) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setHealth(health);
            }
        }
    }

    @Override
    public void setFoodLevelForAllGamePlayers(int level) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.setFoodLevel(level);
        }
    }

    @Override
    public void teleportAllPlayers(Location location) {
        for (int index = 0; index < gamePlayers.size(); index++) {
            UUID uuid = gamePlayers.get(index);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.teleport(
                        TeleportPositions.getCollisionSafeTeleportLocation(location, index));
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
            if (player != null) player.getInventory().clear();
        }
    }

    @Override
    public void playSoundToAllGamePlayers(Sound sound, float volume, float pitch) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    @Override
    public void playNoteToAllGamePlayers(Instrument instrument, Note note) {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.playNote(player.getLocation(), instrument, note);
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
                sanitizeParticipantForLobby(player, true);
            }
        }
    }

    /** Removes selected runtime participants without disturbing the remaining match roster. */
    public void removeRuntimePlayers(@NotNull Set<UUID> players) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removePlayerFromBossBars(player);
                sanitizeParticipantForLobby(player, true);
            }
        }
        gamePlayers.removeAll(players);
    }

    @Override
    public Collection<UUID> getParticipantUniqueIds() {
        return List.copyOf(gamePlayers);
    }
}
