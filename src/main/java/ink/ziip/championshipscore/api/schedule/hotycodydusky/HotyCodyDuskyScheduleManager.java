package ink.ziip.championshipscore.api.schedule.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
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

        addAllSpectatorsToArea();

        plugin.getScheduleManager().addRound(GameTypeEnum.HotyCodyDusky);
        enabled = true;
        timer = 10;
        subRound = 0;
        completedAreaNum = 0;
        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.HOTY_CODY_DUSKY));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.HOTY_CODY_DUSKY_POINTS));
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

        arrangeHotyCodyDuskyRounds();
    }

    private void arrangeHotyCodyDuskyRounds() {
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
                plugin.getGameManager().joinSingleTeamAreaForPlayers(GameTypeEnum.HotyCodyDusky, name, areaPlayerList.next());
            }
        }
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
            scheduler.runTaskLaterAsynchronously(plugin, () -> {
                scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.HotyCodyDusky)));
                Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
            }, 40L);
        }
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
    }

    public void nextHotyCodyDuskyRound() {
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
                arrangeHotyCodyDuskyRounds();
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                player.performCommand("spec");
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
}