package ink.ziip.championshipscore.api.schedule.battlebox;

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
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BattleBoxScheduleManager extends BaseManager {
    //    private final List<List<String>> battleBoxRounds = new ArrayList<>();
    private final BukkitScheduler scheduler;
    private final BattleBoxScheduleHandler handler;
    private final List<Set<TwoVTwoVector>> rounds = new ArrayList<>();
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;
    private int completedAreaNum;
    private BukkitTask firstStartTask;
    private BukkitTask startTask;

    public BattleBoxScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new BattleBoxScheduleHandler(championshipsCore, this);
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

    private void generatePairs(int rounds, int pairs) {
        this.rounds.clear();
        for (int i = 0; i < rounds; i++) {
            Set<TwoVTwoVector> set = new HashSet<>();
            while (set.size() < pairs) {
                ChampionshipTeam t1 = selectTeam();
                ChampionshipTeam t2 = selectTeam();
                if (t1.equals(t2)) {
                    continue;
                }
                if (alreadyContainsTeam(t1, set))
                    continue;
                if (alreadyContainsTeam(t2, set))
                    continue;
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

    private boolean alreadyContainsTeam(ChampionshipTeam team, Set<TwoVTwoVector> pairs) {
        for (TwoVTwoVector pair : pairs) {
            if (pair.getTeamOne().equals(team) || pair.getTeamTwo().equals(team)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void load() {
//        ConfigurationSection configurationSection = CCConfig.BATTLE_BOX_ROUNDS;
//        for (String key : configurationSection.getKeys(false)) {
//            battleBoxRounds.add(new ArrayList<>(configurationSection.getStringList(key)));
//        }
    }

    @Override
    public void unload() {
        if (enabled) {
            endSchedule();
        }
    }

    public void startBattleBox() {
        if (enabled) {
            endSchedule();
            return;
        }

        addAllSpectatorsToArea();

        plugin.getScheduleManager().addRound(GameTypeEnum.BattleBox);
        enabled = true;
        timer = 10;
        subRound = 0;
        completedAreaNum = 0;

        generatePairs(9, 8);

        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BATTLE_BOX));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BATTLE_BOX_POINTS));
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
                startBattleBoxRound();
                if (firstStartTask != null)
                    firstStartTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startBattleBoxRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > rounds.size()) {
            return;
        }

        handler.register();

//        for (String command : battleBoxRounds.get(subRound - 1)) {
//            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
//        }
        startRoundBattle();
    }

    private void startRoundBattle() {
        Iterator<String> battleBoxAreaIterator = plugin.getGameManager().getBattleBoxManager().getAreaNameList().iterator();
        for (TwoVTwoVector v : rounds.get(subRound - 1)) {
            if (!battleBoxAreaIterator.hasNext()) {
                return;
            }

            String areaName = battleBoxAreaIterator.next();

            String failed = MessageConfig.GAME_TEAM_GAME_START_FAILED
                    .replace("%team%", v.getTeamOne().getName())
                    .replace("%rival%", v.getTeamTwo().getName())
                    .replace("%game%", GameTypeEnum.BattleBox.toString())
                    .replace("%area%", areaName);
            String successful = MessageConfig.GAME_TEAM_GAME_START_SUCCESSFUL
                    .replace("%team%", v.getTeamOne().getName())
                    .replace("%rival%", v.getTeamTwo().getName())
                    .replace("%game%", GameTypeEnum.BattleBox.toString())
                    .replace("%area%", areaName);

            if (plugin.getGameManager().joinTeamArea(GameTypeEnum.BattleBox, areaName, v.getTeamOne(), v.getTeamTwo()))
                plugin.getLogger().info(ChatColor.stripColor(successful));
            else
                plugin.getLogger().warning(ChatColor.stripColor(failed));
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
                scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.BattleBox)));
                Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
            }, 40L);
        }
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
        rounds.clear();
    }

    public void nextBattleBoxRound() {
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
            nextBattleBoxRound();
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
