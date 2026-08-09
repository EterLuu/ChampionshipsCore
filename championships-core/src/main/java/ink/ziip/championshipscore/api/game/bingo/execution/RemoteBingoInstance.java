package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.bingo.BingoConfig;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** Core-side ownership/settlement handle; all actual world gameplay lives on the Folia worker. */
public final class RemoteBingoInstance extends BaseMultiTeamGameInstance {
    private final UUID matchId;
    private final long epoch;
    private int timer;

    public RemoteBingoInstance(ChampionshipsCore plugin, BingoConfig config, UUID matchId, long epoch) {
        super(plugin, GameTypeEnum.Bingo, new RemoteListener(plugin), config);
        this.matchId = matchId;
        this.epoch = epoch;
        this.timer = config.getTimer();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    public UUID matchId() {
        return matchId;
    }

    public long epoch() {
        return epoch;
    }

    public boolean reserve(List<ChampionshipTeam> teams, GameRunMode runMode) {
        if (getGameStageEnum() != GameStageEnum.WAITING) return false;
        prepareRunMode(runMode);
        gameTeams.addAll(teams);
        teams.forEach(team -> gamePlayers.addAll(team.getMembers()));
        setGameStageEnum(GameStageEnum.LOADING);
        return true;
    }

    public void markReady() {
        if (getGameStageEnum() == GameStageEnum.LOADING) setGameStageEnum(GameStageEnum.PREPARATION);
    }

    public void markCountdown() {
        setGameStageEnum(GameStageEnum.COUNTDOWN);
    }

    public void markStarted() {
        setGameStageEnum(GameStageEnum.PROGRESS);
    }

    public void applyAward(UUID playerId, int points) {
        addPlayerPoints(playerId, points);
    }

    public void completeFromRemote() {
        if (getGameStageEnum() == GameStageEnum.END || getGameStageEnum() == GameStageEnum.WAITING) return;
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, List.copyOf(gameTeams)));
        finishPostGameAfterEndEvent();
    }

    public void abortFromRemote() {
        setGameStageEnum(GameStageEnum.END);
    }

    @Override
    public void startGamePreparation() {
        // Remote preparation is driven by Redis READY and START_COMMIT events.
    }

    @Override
    public void endGame() {
        abortFromRemote();
    }

    @Override
    public void resetArea() {
        timer = getGameConfig().getTimer();
    }

    @Override
    public BingoConfig getGameConfig() {
        return (BingoConfig) gameConfig;
    }

    @Override
    public BaseListener getGameHandler() {
        return gameHandler;
    }

    @Override
    public String getWorldName() {
        return "remote-bingo";
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getLobbyLocation();
    }

    @Override
    public int getTimer() {
        return timer;
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        plugin.getRemoteBingoManager().routeReconnect(event.getPlayer(), this);
    }

    @Override
    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        if (isSpectator(event.getPlayer())) {
            plugin.getRemoteBingoManager().routeReconnect(event.getPlayer(), this);
        }
    }

    @Override
    public boolean keepSpectatorAcrossReconnect() {
        return true;
    }

    @Override
    public void addSpectator(@NotNull Player player) {
        addSpectatorWithoutTeleport(player.getUniqueId());
        plugin.getRemoteBingoManager().addSpectator(player, this);
    }

    @Override
    public void addSpectator(@NotNull Player player, @NotNull Location ignored) {
        addSpectator(player);
    }

    @Override
    public void removeSpectator(@NotNull UUID playerId) {
        if (!spectators.contains(playerId)) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            removeSpectator(player);
            return;
        }
        plugin.getRemoteBingoManager().removeSpectator(playerId, this);
        onlyRemoveSpectatorFromList(playerId);
    }

    @Override
    public void removeSpectator(@NotNull Player player) {
        if (!spectators.contains(player.getUniqueId())) return;
        plugin.getRemoteBingoManager().removeSpectator(player.getUniqueId(), this);
        super.removeSpectator(player);
    }

    private static final class RemoteListener extends BaseListener {
        private RemoteListener(ChampionshipsCore plugin) {
            super(plugin);
        }
    }
}
