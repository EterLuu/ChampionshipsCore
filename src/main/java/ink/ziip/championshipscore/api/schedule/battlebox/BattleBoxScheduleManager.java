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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BattleBoxScheduleManager extends BaseManager {
    private final BukkitScheduler scheduler;
    private final BattleBoxScheduleHandler handler;
    private final List<Set<TwoVTwoVector>> rounds = new ArrayList<>();
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;
    private BukkitTask firstStartTask;
    private BukkitTask startTask;

    public BattleBoxScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new BattleBoxScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
    }

    private void cycleGeneratePairs() {
        this.rounds.clear();

        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());

        if (teams.size() % 2 != 0) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BattleBox, "-", "调度", "对阵",
                    "队伍数=" + teams.size() + "，无法为奇数队伍生成对阵"));
            return;
        }

        int rounds = Math.min(9, teams.size() - 1); // >=10 teams capped at 9 rounds; fewer -> full N-1 round-robin
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

        cycleGeneratePairs();

        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
            plugin.getScheduleManager().showRoundPreparationCountdown(GameTypeEnum.BattleBox, 1, timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BATTLE_BOX));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BATTLE_BOX_POINTS));
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

        startRoundBattle();
    }

    private void startRoundBattle() {
        // One Battle Box area now hosts all of this round's matches in parallel (one per stamped copy).
        String areaName = plugin.getGameManager().getBattleBoxManager().getAreaNameList()
                .stream().findFirst().orElse(null);
        if (areaName == null) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BattleBox, "-", "调度", "轮次",
                    "第 " + subRound + " 轮无法开始：未配置场地"));
            return;
        }

        List<TwoVTwoVector> pairs = new ArrayList<>(rounds.get(subRound - 1));

        if (plugin.getGameManager().joinBattleBoxArea(areaName, pairs, subRound == 1))
            plugin.getLogger().info(Utils.formatGameLog(GameTypeEnum.BattleBox, areaName, "调度", "轮次",
                    "第 " + subRound + " 轮开始，对局数=" + pairs.size()));
        else
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BattleBox, areaName, "调度", "轮次",
                    "第 " + subRound + " 轮启动失败"));
    }

    public void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;

        Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
        Utils.sendTitleToAllPlayers(MessageConfig.GAME_ROUND_END_TITLE.replace("%game%", GameTypeEnum.BattleBox.toString()),
                MessageConfig.GAME_ROUND_END_SUBTITLE, 60);
        if (plugin.isLoaded()) {
            scheduler.runTaskLater(plugin,
                    () -> plugin.getRankManager().broadcastFinalRankings(GameTypeEnum.BattleBox), 40L);
        }
        handler.unRegister();
        Utils.changeLevelForAllPlayers(0);
        rounds.clear();
    }

    public void nextBattleBoxRound() {
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
        startTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
            plugin.getScheduleManager().showRoundPreparationCountdown(GameTypeEnum.BattleBox, subRound, timer);

            if (timer == 30) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
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

    /** Called when the Battle Box area finishes a whole round (all its parallel matches done). */
    public synchronized void onRoundComplete() {
        nextBattleBoxRound();
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
