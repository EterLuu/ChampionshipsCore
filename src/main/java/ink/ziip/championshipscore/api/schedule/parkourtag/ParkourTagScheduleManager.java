package ink.ziip.championshipscore.api.schedule.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
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

public class ParkourTagScheduleManager extends BaseManager {
    //    private final List<List<String>> parkourTagRounds = new ArrayList<>();
    private final BukkitScheduler scheduler;
    private final ParkourTagScheduleHandler handler;
    private final List<Set<TwoVTwoVector>> rounds = new ArrayList<>();
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;
    private int completedAreaNum;
    private BukkitTask firstStartTask;
    private BukkitTask startTask;

    public ParkourTagScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new ParkourTagScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
        completedAreaNum = 0;
    }

    private boolean containsPair(TwoVTwoVector v) {
        for (Set<TwoVTwoVector> set : rounds) {
            if (set.contains(v)) {
                return true;
            }
        }
        return false;
    }

    private ChampionshipTeam selectTeam() {
        return plugin.getTeamManager().getTeamList().get(new Random().nextInt(plugin.getTeamManager().getTeamList().size()));
    }

    private void generatePairs(int rounds, int groups) {
        this.rounds.clear();
        for (int i = 0; i < rounds; i++) {
            Set<TwoVTwoVector> set = new HashSet<>();
            while (set.size() < groups) {
                ChampionshipTeam t1 = selectTeam();
                ChampionshipTeam t2 = selectTeam();
                if (t1.equals(t2)) {
                    continue;
                }
                TwoVTwoVector tv = new TwoVTwoVector(t1, t2);
                if (containsPair(tv)) {
                    continue;
                }
                if (set.contains(tv)) {
                    continue;
                }
                set.add(tv);
            }
            this.rounds.add(set);
        }
    }

    @Override
    public void load() {
//        ConfigurationSection configurationSection = CCConfig.PARKOUR_TAG_ROUNDS;
//        for (String key : configurationSection.getKeys(false)) {
//            parkourTagRounds.add(new ArrayList<>(configurationSection.getStringList(key)));
//        }
    }

    @Override
    public void unload() {
        if (enabled) {
            endSchedule();
        }
    }

    public void startParkourTag() {
        if (enabled) {
            endSchedule();
            return;
        }

        addAllSpectatorsToArea();

        plugin.getScheduleManager().addRound(GameTypeEnum.ParkourTag);
        enabled = true;
        timer = 10;
        subRound = 0;
        completedAreaNum = 0;

        generatePairs(9, 8);

        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

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

    public void startParkourTagRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > rounds.size()) {
            return;
        }

        handler.register();

//        for (String command : parkourTagRounds.get(subRound - 1)) {
//            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
//        }
        startRoundBattle();
    }

    private void startRoundBattle() {
        Iterator<String> parkourTagIterator = plugin.getGameManager().getParkourTagManager().getAreaNameList().iterator();
        for (TwoVTwoVector v : rounds.get(subRound - 1)) {
            if (!parkourTagIterator.hasNext()) {
                return;
            }
            plugin.getGameManager().joinTeamArea(GameTypeEnum.ParkourTag, parkourTagIterator.next(), v.getTeamOne(), v.getTeamTwo());
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
        if (plugin.isLoaded())
            scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.ParkourTag)));
        Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
    }

    public void nextParkourTagRound() {
        if (!enabled)
            return;

        completedAreaNum = 0;
        subRound++;
        if (subRound > rounds.size()) {
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
                startRoundBattle();
                if (startTask != null)
                    startTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public synchronized void addCompletedAreaNum() {
        completedAreaNum++;

        if (completedAreaNum == rounds.getFirst().size()) {
            nextParkourTagRound();
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
