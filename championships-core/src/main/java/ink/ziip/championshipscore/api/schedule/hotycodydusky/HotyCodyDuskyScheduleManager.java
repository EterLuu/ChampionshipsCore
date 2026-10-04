package ink.ziip.championshipscore.api.schedule.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import lombok.Getter;

import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class HotyCodyDuskyScheduleManager extends BaseManager {
    private static final int ROUND_TRANSITION_SECONDS = 10;
    private final int hotyCodyDuskyRounds = 3;
    private final BukkitScheduler scheduler;
    private final HotyCodyDuskyScheduleHandler handler;
    @Getter private int subRound;
    private int timer;
    @Getter private boolean enabled;
    private int completedAreaNum;
    private int activeAreaCount;
    private BukkitTask firstStartTask;
    private BukkitTask startTask;

    public HotyCodyDuskyScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new HotyCodyDuskyScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
        completedAreaNum = 0;
    }

    @Override
    public void load() {}

    @Override
    public void unload() {
        if (enabled) {
            endSchedule();
        }
    }

    public void startHotyCodyDusky() {
        if (enabled) {
            endSchedule();
            return;
        }

        plugin.getScheduleManager().addRound(GameTypeEnum.HotyCodyDusky);
        enabled = true;
        timer = 10;
        subRound = 0;
        completedAreaNum = 0;
        firstStartTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            plugin.getScheduleManager()
                                    .showRoundPreparationCountdown(
                                            GameTypeEnum.HotyCodyDusky, 1, timer);

                            if (timer == 10) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                ScheduleMessageConfig.HOTY_CODY_DUSKY));
                            }

                            if (timer == 5) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                ScheduleMessageConfig.HOTY_CODY_DUSKY_POINTS));
                            }

                            if (timer == 0) {
                                subRound = 0;
                                startHotyCodyDuskyRound();
                                if (firstStartTask != null) firstStartTask.cancel();
                            }
                            timer--;
                        },
                        0,
                        20L);
    }

    public void startHotyCodyDuskyRound() {
        if (!enabled) return;

        subRound++;
        if (subRound > hotyCodyDuskyRounds) {
            return;
        }

        handler.register();

        if (!arrangeHotyCodyDuskyRounds(true)) abortSchedule("首轮启动失败");
    }

    private boolean arrangeHotyCodyDuskyRounds(boolean showIntroduction) {
        String map =
                ink.ziip.championshipscore.api.schedule.FormalEventMapResolver.map(
                        plugin, GameTypeEnum.HotyCodyDusky, 1);
        if (map == null) return false;
        boolean started =
                plugin.getGameManager()
                        .joinSingleTeamAreaForAllTeams(
                                GameTypeEnum.HotyCodyDusky,
                                map,
                                showIntroduction,
                                GameRunMode.EVENT);
        activeAreaCount = started ? 1 : 0;
        return started;
    }

    public void endSchedule() {
        plugin.getScheduleManager().clearStartSelection(GameTypeEnum.HotyCodyDusky);
        if (firstStartTask != null) firstStartTask.cancel();
        if (startTask != null) startTask.cancel();

        enabled = false;

        handler.unRegister();
        plugin.getScheduleManager().clearRoundPreparationCountdown();
        plugin.getGameManager().releaseEventSpectatorsForGame(GameTypeEnum.HotyCodyDusky);
    }

    public void nextHotyCodyDuskyRound() {
        if (!enabled) return;

        completedAreaNum = 0;
        subRound++;
        if (subRound > hotyCodyDuskyRounds) {
            endSchedule();
            return;
        }
        CoreMessages.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = ROUND_TRANSITION_SECONDS;
        startTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            plugin.getScheduleManager()
                                    .showRoundPreparationCountdown(
                                            GameTypeEnum.HotyCodyDusky, subRound, timer);

                            if (timer == ROUND_TRANSITION_SECONDS) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                ScheduleMessageConfig.NEXT_ROUND_SOON));
                            }

                            if (timer == 0) {
                                if (!arrangeHotyCodyDuskyRounds(false)) abortSchedule("下一轮启动失败");
                                if (startTask != null) startTask.cancel();
                            }
                            timer--;
                        },
                        0,
                        20L);
    }

    public synchronized void addCompletedAreaNum() {
        completedAreaNum++;

        if (activeAreaCount > 0 && completedAreaNum == activeAreaCount) {
            boolean hasNextRound = hasNextRound();
            plugin.getScheduleManager()
                    .settleEventRound(
                            GameTypeEnum.HotyCodyDusky,
                            hasNextRound,
                            () -> {
                                if (!enabled) return;
                                if (hasNextRound) nextHotyCodyDuskyRound();
                                else endSchedule();
                            });
        }
    }

    public boolean hasNextRound() {
        return enabled && subRound < hotyCodyDuskyRounds;
    }

    private void abortSchedule(String reason) {
        plugin.getLogger()
                .warning(
                        LogText.formatGameLog(GameTypeEnum.HotyCodyDusky, "-", "调度", "中止", reason));
        endSchedule();
        plugin.getGameManager().forceEndAreas(GameTypeEnum.HotyCodyDusky);
    }
}
