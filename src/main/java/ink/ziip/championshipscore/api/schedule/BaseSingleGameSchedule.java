package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
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
            scheduleManager.showRoundPreparationCountdown(gameTypeEnum, 1, timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(scheduleManager.getScheduleStrings(gameTypeEnum));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(scheduleManager.getSchedulePointsStrings(gameTypeEnum));
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
        plugin.getGameManager().joinSingleTeamAreaForAllTeams(
                gameTypeEnum, getArea(), true, GameRunMode.EVENT);
    }

    public void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;

        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
        plugin.getGameManager().releaseEventSpectatorsForGame(gameTypeEnum);
    }

    public void nextRound() {
        if (!enabled)
            return;

        boolean hasNextRound = subRound < getTotalRounds();
        scheduleManager.settleEventRound(gameTypeEnum, hasNextRound, () -> {
            if (!enabled)
                return;
            if (!hasNextRound) {
                endSchedule();
                return;
            }
            startNextRoundCountdown();
        });
    }

    private void startNextRoundCountdown() {
        subRound++;
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = 30;
        startTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
            scheduleManager.showRoundPreparationCountdown(gameTypeEnum, subRound, timer);

            if (timer == 30) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                boolean started = plugin.getGameManager()
                        .joinSingleTeamAreaForAllTeams(gameTypeEnum, getArea(), false, GameRunMode.EVENT);
                if (startTask != null)
                    startTask.cancel();
                if (!started) {
                    plugin.getLogger().warning(Utils.formatGameLog(gameTypeEnum, getArea(),
                            "调度", "中止", "下一轮启动失败，已释放轮间玩家"));
                    endSchedule();
                }
            }
            timer--;
        }, 0, 20L);
    }

    public boolean hasNextRound() {
        return enabled && subRound < getTotalRounds();
    }

    public abstract String getArea();

    public abstract int getTotalRounds();

}
