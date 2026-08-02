package ink.ziip.championshipscore.api.schedule.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.*;

public class ParkourTagScheduleManager extends BaseManager {
    private final FoliaScheduler scheduler;
    private final ParkourTagScheduleHandler handler;
    private final List<Set<TwoVTwoVector>> rounds = new ArrayList<>();
    @Getter
    private volatile int subRound;
    private volatile int timer;
    @Getter
    private volatile boolean enabled;
    private volatile ScheduledTask firstStartTask;
    private volatile ScheduledTask startTask;

    public ParkourTagScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new ParkourTagScheduleHandler(championshipsCore, this);
        scheduler = FoliaScheduler.global(championshipsCore);
        subRound = 0;
    }

    private void cycleGeneratePairs() {
        this.rounds.clear();

        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());

        if (teams.size() % 2 != 0) {
            plugin.getLogger().warning(GameTypeEnum.ParkourTag + " teams size is not even, removing one team to make it even.");
            return;
        }

        int rounds = 9;
        int pairs = teams.size() / 2;

        Collections.shuffle(teams);

        ChampionshipTeam firstTeam = teams.getFirst();
        teams.remove(firstTeam);

        int teamsSize = teams.size();

        for (int i = 0; i < rounds; i++) {
            int teamIdx = i % teamsSize;

            Set<TwoVTwoVector> set = new HashSet<>();

            set.add(new TwoVTwoVector(firstTeam, teams.get(teamIdx)));

            for (int j = 1; j < pairs; j++) {
                int firstTeamNum = (i + j) % teamsSize;
                int secondTeamNum = (i + teamsSize - j) % teamsSize;
                TwoVTwoVector tv = new TwoVTwoVector(teams.get(firstTeamNum), teams.get(secondTeamNum));
                set.add(tv);
            }
            this.rounds.add(set);
        }
    }

    @Override
    public void load() {}

    @Override
    public void unload() {
        if (enabled) {
            endSchedule();
        }
    }

    public void startParkourTag() {
        scheduler.runTask(this::startParkourTagOnGlobalRegion);
    }

    private synchronized void startParkourTagOnGlobalRegion() {
        if (enabled) {
            endSchedule();
            return;
        }

        addAllSpectatorsToArea();

        plugin.getScheduleManager().addRound(GameTypeEnum.ParkourTag);
        enabled = true;
        timer = 10;
        subRound = 0;

        cycleGeneratePairs();

        firstStartTask = scheduler.runTaskTimer(() -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.PARKOUR_TAG));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.PARKOUR_TAG_POINTS));
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
                startParkourTagRound();
                if (firstStartTask != null)
                    firstStartTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public synchronized void startParkourTagRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > rounds.size()) {
            return;
        }

        handler.register();

        startRoundBattle();
    }

    private void startRoundBattle() {
        // One Parkour Tag area now hosts all of this round's matches in parallel (one per stamped copy).
        String areaName = plugin.getGameManager().getParkourTagManager().getAreaNameList()
                .stream().findFirst().orElse(null);
        if (areaName == null) {
            plugin.getLogger().warning(GameTypeEnum.ParkourTag + " has no area configured; cannot start round.");
            return;
        }

        List<TwoVTwoVector> pairs = new ArrayList<>(rounds.get(subRound - 1));

        if (plugin.getGameManager().joinParkourTagArea(areaName, pairs))
            plugin.getLogger().info(Utils.stripColorCodes(GameTypeEnum.ParkourTag + " round " + subRound + " started with " + pairs.size() + " matches in area " + areaName));
        else
            plugin.getLogger().warning(Utils.stripColorCodes(GameTypeEnum.ParkourTag + " round " + subRound + " failed to start in area " + areaName));
    }

    public synchronized void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;

        Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
        Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.ROUND_END));
        if (plugin.isLoaded()) {
            scheduler.runTaskLaterAsynchronously(() -> {
                scheduler.runTaskAsynchronously(task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.ParkourTag)));
                Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
            }, 40L);
        }
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
    }

    public synchronized void nextParkourTagRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > rounds.size()) {
            endSchedule();
            removeAllSpectatorsFromArea();
            return;
        }
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = 30;
        startTask = scheduler.runTaskTimer(() -> {

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
                startRoundBattle();
                if (startTask != null)
                    startTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    /** Called when the Parkour Tag area finishes a whole round (all its parallel matches done). */
    public synchronized void onRoundComplete() {
        nextParkourTagRound();
    }

    public void addAllSpectatorsToArea() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                Utils.performCommand(player, "spec");
            }
        }
    }

    public void removeAllSpectatorsFromArea() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                Utils.performCommand(player, "cc spectate leave");
            }
        }
    }
}
