package ink.ziip.championshipscore.api.schedule.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class HotyCodyDuskyScheduleManager extends BaseManager {
    private final int hotyCodyDuskyRounds = 3;
    private final BukkitScheduler scheduler;
    private final HotyCodyDuskyScheduleHandler handler;
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;
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
    public void load() {
    }

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
        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
            plugin.getScheduleManager().showRoundPreparationCountdown(GameTypeEnum.HotyCodyDusky, 1, timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.HOTY_CODY_DUSKY));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.HOTY_CODY_DUSKY_POINTS));
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                subRound = 0;
                startHotyCodyDuskyRound();
                if (firstStartTask != null)
                    firstStartTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startHotyCodyDuskyRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > hotyCodyDuskyRounds) {
            return;
        }

        handler.register();

        if (!arrangeHotyCodyDuskyRounds(true)) abortSchedule("首轮启动失败");
    }

    private boolean arrangeHotyCodyDuskyRounds(boolean showIntroduction) {
        List<List<UUID>> areaTeams = new ArrayList<>();
        areaTeams.add(new ArrayList<>());
        areaTeams.add(new ArrayList<>());
        areaTeams.add(new ArrayList<>());
        areaTeams.add(new ArrayList<>());

        for (String team : plugin.getTeamManager().getTeamNameList()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeam(team);
            if (championshipTeam == null) {
                continue;
            }

            Collections.shuffle(areaTeams);

            Iterator<List<UUID>> areaPlayerList = areaTeams.iterator();

            for (UUID uuid : championshipTeam.getMembers()) {
                if (!areaPlayerList.hasNext())
                    areaPlayerList = areaTeams.iterator();

                areaPlayerList.next().add(uuid);
            }
        }

        Iterator<List<UUID>> areaPlayerList = areaTeams.iterator();
        boolean allStarted = true;
        int startedAreas = 0;
        for (String name : plugin.getGameManager().getHotyCodyDuskyManager().getAreaNameList()) {
            if (!areaPlayerList.hasNext())
                break;

            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = plugin.getGameManager().getHotyCodyDuskyManager().getArea(name);
            if (hotyCodyDuskyTeamArea != null) {
                if (!plugin.getGameManager().joinSingleTeamAreaForPlayers(
                        GameTypeEnum.HotyCodyDusky, name, areaPlayerList.next(), showIntroduction,
                        GameRunMode.EVENT)) {
                    allStarted = false;
                } else {
                    startedAreas++;
                }
            }
        }
        activeAreaCount = startedAreas;
        return allStarted && !areaPlayerList.hasNext();
    }

    public void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;

        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
        plugin.getGameManager().releaseEventSpectatorsForGame(GameTypeEnum.HotyCodyDusky);
    }

    public void nextHotyCodyDuskyRound() {
        if (!enabled)
            return;

        completedAreaNum = 0;
        subRound++;
        if (subRound > hotyCodyDuskyRounds) {
            endSchedule();
            return;
        }
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = 30;
        startTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
            plugin.getScheduleManager().showRoundPreparationCountdown(GameTypeEnum.HotyCodyDusky, subRound, timer);

            if (timer == 30) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                if (!arrangeHotyCodyDuskyRounds(false)) abortSchedule("下一轮启动失败");
                if (startTask != null)
                    startTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public synchronized void addCompletedAreaNum() {
        completedAreaNum++;

        if (activeAreaCount > 0 && completedAreaNum == activeAreaCount) {
            boolean hasNextRound = hasNextRound();
            plugin.getScheduleManager().settleEventRound(GameTypeEnum.HotyCodyDusky, hasNextRound, () -> {
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
        plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.HotyCodyDusky, "-",
                "调度", "中止", reason));
        endSchedule();
        plugin.getGameManager().forceEndAreas(GameTypeEnum.HotyCodyDusky);
    }

}
