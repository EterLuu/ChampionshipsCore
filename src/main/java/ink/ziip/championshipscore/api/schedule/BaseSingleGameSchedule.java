package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

public abstract class BaseSingleGameSchedule extends BaseManager {
    protected final BukkitScheduler scheduler;
    protected final BaseListener handler;
    protected final GameTypeEnum gameTypeEnum;
    protected final ScheduleManager scheduleManager;
    @Getter
    protected int subRound;
    protected int timer;
    @Getter
    protected boolean enabled;
    protected BukkitTask firstStartTask;
    protected BukkitTask startTask;

    public BaseSingleGameSchedule(ChampionshipsCore championshipsCore, BaseListener handler, GameTypeEnum gameTypeEnum) {
        super(championshipsCore);
        scheduleManager = plugin.getScheduleManager();
        scheduler = plugin.getServer().getScheduler();
        this.handler = handler;
        this.gameTypeEnum = gameTypeEnum;
        this.subRound = 0;
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {
        if (enabled) {
            endSchedule();
        }
    }

    public void startGame() {
        if (enabled) {
            endSchedule();
            return;
        }

        plugin.getScheduleManager().addRound(gameTypeEnum);
        enabled = true;
        timer = 10;
        subRound = 0;
        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(scheduleManager.getScheduleStrings(gameTypeEnum));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(scheduleManager.getSchedulePointsStrings(gameTypeEnum));
            }

            if (timer < 5 && timer > 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }
            if (timer == 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                subRound = 0;
                startRound();
                if (firstStartTask != null)
                    firstStartTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > getTotalRounds()) {
            return;
        }

        handler.register();
        addAllSpectatorsToArea();
        plugin.getGameManager().joinSingleTeamAreaForAllTeams(gameTypeEnum, getArea());
    }

    public void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;

        Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
        Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.ROUND_END));
        if (plugin.isLoaded()) {
            scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(gameTypeEnum)));
            Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
        }
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
    }

    public void nextRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > getTotalRounds()) {
            endSchedule();
            removeAllSpectatorsFromArea();
            return;
        }
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = 30;
        startTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 30) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
            }

            if (timer < 5 && timer > 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }
            if (timer == 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                plugin.getGameManager().joinSingleTeamAreaForAllTeams(gameTypeEnum, getArea());
                if (startTask != null)
                    startTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void addAllSpectatorsToArea() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                player.performCommand("cc spectate leave");
                player.performCommand(getSpecCommand());
            }
        }
    }

    public void removeAllSpectatorsFromArea() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                player.performCommand("cc spectate leave");
            }
        }
    }

    public abstract String getArea();

    public abstract int getTotalRounds();

    public abstract String getSpecCommand();
}
