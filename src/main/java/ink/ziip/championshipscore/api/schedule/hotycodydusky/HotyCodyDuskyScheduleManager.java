package ink.ziip.championshipscore.api.schedule.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class HotyCodyDuskyScheduleManager extends BaseManager {
    private final int hotyCodyDuskyRounds = 3;
    private final FoliaScheduler scheduler;
    private final HotyCodyDuskyScheduleHandler handler;
    @Getter
    private volatile int subRound;
    private volatile int timer;
    @Getter
    private volatile boolean enabled;
    private volatile int completedAreaNum;
    private volatile ScheduledTask firstStartTask;
    private volatile ScheduledTask startTask;

    public HotyCodyDuskyScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new HotyCodyDuskyScheduleHandler(championshipsCore, this);
        scheduler = FoliaScheduler.global(championshipsCore);
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

    public synchronized void startHotyCodyDusky() {
        if (enabled) {
            endSchedule();
            return;
        }

        addAllSpectatorsToArea();

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

    public synchronized void startHotyCodyDuskyRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > hotyCodyDuskyRounds) {
            return;
        }

        handler.register();

        arrangeHotyCodyDuskyRounds(true);
    }

    private void arrangeHotyCodyDuskyRounds(boolean showIntroduction) {
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
        for (String name : plugin.getGameManager().getHotyCodyDuskyManager().getAreaNameList()) {
            if (!areaPlayerList.hasNext())
                break;

            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = plugin.getGameManager().getHotyCodyDuskyManager().getArea(name);
            if (hotyCodyDuskyTeamArea != null) {
                plugin.getGameManager().joinSingleTeamAreaForPlayers(
                        GameTypeEnum.HotyCodyDusky, name, areaPlayerList.next(), showIntroduction);
            }
        }
    }

    public synchronized void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;

        Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
        Utils.sendTitleToAllPlayers(MessageConfig.GAME_ROUND_END_TITLE.replace("%game%", GameTypeEnum.HotyCodyDusky.toString()),
                MessageConfig.GAME_ROUND_END_SUBTITLE, 60);
        if (plugin.isLoaded()) {
            scheduler.runTaskLater(plugin,
                    () -> plugin.getRankManager().broadcastFinalRankings(GameTypeEnum.HotyCodyDusky), 40L);
        }
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
    }

    public synchronized void nextHotyCodyDuskyRound() {
        if (!enabled)
            return;

        completedAreaNum = 0;
        subRound++;
        if (subRound > hotyCodyDuskyRounds) {
            endSchedule();
            removeAllSpectatorsFromArea();
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
                arrangeHotyCodyDuskyRounds(false);
                if (startTask != null)
                    startTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public synchronized void addCompletedAreaNum() {
        completedAreaNum++;

        int hotyCodyDuskyAreas = 4;
        if (completedAreaNum == hotyCodyDuskyAreas) {
            nextHotyCodyDuskyRound();
        }
    }

    public void addAllSpectatorsToArea() {
        scheduler.runTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (championshipTeam == null) Utils.performCommand(player, "spec");
            }
        });
    }

    public void removeAllSpectatorsFromArea() {
        scheduler.runTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (championshipTeam == null) Utils.performCommand(player, "cc spectate leave");
            }
        });
    }
}
