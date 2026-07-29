package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleHandler;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleManager;
import ink.ziip.championshipscore.api.schedule.hotycodydusky.HotyCodyDuskyScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourtag.ParkourTagScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleHandler;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleManager;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleHandler;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleManager;
import ink.ziip.championshipscore.api.schedule.snowball.SnowballScheduleHandler;
import ink.ziip.championshipscore.api.schedule.snowball.SnowballScheduleManager;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleHandler;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleManager;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleHandler;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public class ScheduleManager extends BaseManager {
    private final BukkitScheduler scheduler;
    @Getter
    private SnowballScheduleManager snowballScheduleManager;
    @Getter
    private SkyWarsScheduleManager skyWarsScheduleManager;
    @Getter
    private TNTRunScheduleManager tntRunScheduleManager;
    @Getter
    private TGTTOSScheduleManager tgttosScheduleManager;
    @Getter
    private BattleBoxScheduleManager battleBoxScheduleManager;
    @Getter
    private ParkourTagScheduleManager parkourTagScheduleManager;
    @Getter
    private ParkourWarriorScheduleManager parkourWarriorScheduleManager;
    @Getter
    private HotyCodyDuskyScheduleManager hotyCodyDuskyScheduleManager;
    @Getter
    private BingoScheduleManager bingoScheduleManager;
    private int timer;

    public ScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        scheduler = championshipsCore.getServer().getScheduler();
    }

    @Override
    public void load() {
        snowballScheduleManager = new SnowballScheduleManager(plugin, new SnowballScheduleHandler(plugin));
        skyWarsScheduleManager = new SkyWarsScheduleManager(plugin, new SkyWarsScheduleHandler(plugin));
        tntRunScheduleManager = new TNTRunScheduleManager(plugin, new TNTRunScheduleHandler(plugin));
        tgttosScheduleManager = new TGTTOSScheduleManager(plugin, new TGTTOSScheduleHandler(plugin));
        battleBoxScheduleManager = new BattleBoxScheduleManager(plugin);
        parkourTagScheduleManager = new ParkourTagScheduleManager(plugin);
        parkourWarriorScheduleManager = new ParkourWarriorScheduleManager(plugin, new ParkourWarriorScheduleHandler(plugin));
        hotyCodyDuskyScheduleManager = new HotyCodyDuskyScheduleManager(plugin);
        bingoScheduleManager = new BingoScheduleManager(plugin, new BingoScheduleHandler(plugin));

        snowballScheduleManager.load();
        skyWarsScheduleManager.load();
        tntRunScheduleManager.load();
        tgttosScheduleManager.load();
        battleBoxScheduleManager.load();
        parkourTagScheduleManager.load();
        parkourWarriorScheduleManager.load();
        hotyCodyDuskyScheduleManager.load();
        bingoScheduleManager.load();
    }

    @Override
    public void unload() {
        snowballScheduleManager.unload();
        skyWarsScheduleManager.unload();
        tntRunScheduleManager.unload();
        tgttosScheduleManager.unload();
        battleBoxScheduleManager.unload();
        parkourTagScheduleManager.unload();
        parkourWarriorScheduleManager.unload();
        hotyCodyDuskyScheduleManager.unload();
        bingoScheduleManager.unload();
    }

    public void addRound(GameTypeEnum gameTypeEnum) {
        plugin.getRankManager().addGameOrder(gameTypeEnum, plugin.getRankManager().getRound() + 1);
    }

    public void resetRound() {
        plugin.getRankManager().resetGameOrder();
    }

    /** Shows a lobby/round transition in the action bar only. */
    public void showRoundPreparationCountdown(GameTypeEnum gameType, int round, int seconds) {
        String roundValue = String.valueOf(Math.max(1, round));
        String secondsValue = String.valueOf(Math.max(0, seconds));
        Utils.sendActionBarToAllPlayers(MessageConfig.GAME_ROUND_PREPARATION_ACTION_BAR
                .replace("%game%", gameType.toString())
                .replace("%round%", roundValue)
                .replace("%time%", secondsValue));
    }

    /**
     * Ends the schedule manager of the given game (cancels its countdown/round tasks and unregisters its
     * handler). Called before force-ending the area so the SingleGameEndEvent fired by endGame doesn't
     * advance the schedule via nextRound().
     */
    public void endGameSchedule(GameTypeEnum gameTypeEnum) {
        switch (gameTypeEnum) {
            case SnowballShowdown -> { if (snowballScheduleManager.isEnabled()) snowballScheduleManager.endSchedule(); }
            case SkyWars -> { if (skyWarsScheduleManager.isEnabled()) skyWarsScheduleManager.endSchedule(); }
            case TNTRun -> { if (tntRunScheduleManager.isEnabled()) tntRunScheduleManager.endSchedule(); }
            case TGTTOS -> { if (tgttosScheduleManager.isEnabled()) tgttosScheduleManager.endSchedule(); }
            case ParkourWarrior -> { if (parkourWarriorScheduleManager.isEnabled()) parkourWarriorScheduleManager.endSchedule(); }
            case BattleBox -> { if (battleBoxScheduleManager.isEnabled()) battleBoxScheduleManager.endSchedule(); }
            case ParkourTag -> { if (parkourTagScheduleManager.isEnabled()) parkourTagScheduleManager.endSchedule(); }
            case HotyCodyDusky -> { if (hotyCodyDuskyScheduleManager.isEnabled()) hotyCodyDuskyScheduleManager.endSchedule(); }
            case Bingo -> { if (bingoScheduleManager.isEnabled()) bingoScheduleManager.endSchedule(); }
            default -> { } // DragonEggCarnival / BuildMart have no schedule manager
        }
    }

    /**
     * Undo the most recently started game: stop its schedule, force-end its running areas, then (after a
     * short delay so the area's async score recording lands first) clear its status entry + point records.
     * @return the game that was undone, or null if no round exists.
     */
    public GameTypeEnum deleteLatestGame() {
        GameTypeEnum latest = plugin.getRankManager().getLatestGame();
        if (latest == null) return null;
        endGameSchedule(latest);
        plugin.getGameManager().forceEndAreas(latest);
        // force-end -> endGame -> addPlayerPoints (async). Delay the soft-delete so those INSERTs land
        // before UPDATE ... SET valid=0, otherwise late inserts survive with valid=1.
        scheduler.runTaskLaterAsynchronously(plugin, () -> plugin.getRankManager().deleteGameRecords(latest), 60L);
        return latest;
    }

    public void startDragonEggCarnival(ChampionshipTeam team, ChampionshipTeam rival) {
        plugin.getScheduleManager().addRound(GameTypeEnum.DragonEggCarnival);
        timer = 10;
        addAllSpectatorsToArea();
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);
            showRoundPreparationCountdown(GameTypeEnum.DragonEggCarnival, 1, timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.DRAGON_EGG_CARNIVAL)
                        .replace("%team%", team.getColoredName())
                        .replace("%rival%", rival.getColoredName()));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.DRAGON_EGG_CARNIVAL_POINTS));
            }
            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                plugin.getGameManager().joinTeamArea(GameTypeEnum.DragonEggCarnival, "area1", team, rival);
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void addAllSpectatorsToArea() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                player.performCommand("cc spectate leave");
                player.performCommand("cc spectate dragoneggcarnival area1");
            }
        }
    }

    public String getScheduleStrings(GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.TNTRun)
            return Utils.getMessage(ScheduleMessageConfig.TNT_RUN);
        if (gameTypeEnum == GameTypeEnum.TGTTOS)
            return Utils.getMessage(ScheduleMessageConfig.TGTTOS);
        if (gameTypeEnum == GameTypeEnum.SnowballShowdown)
            return Utils.getMessage(ScheduleMessageConfig.SNOWBALL);
        if (gameTypeEnum == GameTypeEnum.SkyWars)
            return Utils.getMessage(ScheduleMessageConfig.SKY_WARS);
        if (gameTypeEnum == GameTypeEnum.ParkourWarrior)
            return Utils.getMessage(ScheduleMessageConfig.PARKOUR_WARRIOR);
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return Utils.getMessage(ScheduleMessageConfig.BINGO);

        return "";
    }

    public String getSchedulePointsStrings(GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.TNTRun)
            return Utils.getMessage(ScheduleMessageConfig.TNT_RUN_POINTS);
        if (gameTypeEnum == GameTypeEnum.TGTTOS)
            return Utils.getMessage(ScheduleMessageConfig.TGTTOS_POINTS);
        if (gameTypeEnum == GameTypeEnum.SnowballShowdown)
            return Utils.getMessage(ScheduleMessageConfig.SNOWBALL_POINTS);
        if (gameTypeEnum == GameTypeEnum.SkyWars)
            return Utils.getMessage(ScheduleMessageConfig.SKY_WARS_POINTS);
        if (gameTypeEnum == GameTypeEnum.ParkourWarrior)
            return Utils.getMessage(ScheduleMessageConfig.PARKOUR_WARRIOR_POINTS);
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return Utils.getMessage(ScheduleMessageConfig.BINGO_POINTS);

        return "";
    }
}
