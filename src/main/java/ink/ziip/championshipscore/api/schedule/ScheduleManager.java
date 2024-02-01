package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourtag.ParkourTagScheduleManager;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleManager;
import ink.ziip.championshipscore.api.schedule.snowball.SnowballScheduleManager;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleManager;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;

public class ScheduleManager extends BaseManager {
    private final BukkitScheduler scheduler;
    @Getter
    private final SnowballScheduleManager snowballScheduleManager;
    @Getter
    private final SkyWarsScheduleManager skyWarsScheduleManager;
    @Getter
    private final TNTRunScheduleManager tntRunScheduleManager;
    @Getter
    private final TGTTOSScheduleManager tgttosScheduleManager;
    @Getter
    private final BattleBoxScheduleManager battleBoxScheduleManager;
    @Getter
    private final ParkourTagScheduleManager parkourTagScheduleManager;
    private int timer;

    public ScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        scheduler = championshipsCore.getServer().getScheduler();

        snowballScheduleManager = new SnowballScheduleManager(plugin);
        skyWarsScheduleManager = new SkyWarsScheduleManager(plugin);
        tntRunScheduleManager = new TNTRunScheduleManager(plugin);
        tgttosScheduleManager = new TGTTOSScheduleManager(plugin);
        battleBoxScheduleManager = new BattleBoxScheduleManager(plugin);
        parkourTagScheduleManager = new ParkourTagScheduleManager(plugin);
    }

    @Override
    public void load() {
        snowballScheduleManager.load();
        skyWarsScheduleManager.load();
        tntRunScheduleManager.load();
        tgttosScheduleManager.load();
        battleBoxScheduleManager.load();
        parkourTagScheduleManager.load();
    }

    @Override
    public void unload() {
        snowballScheduleManager.unload();
        skyWarsScheduleManager.unload();
        tntRunScheduleManager.unload();
        tgttosScheduleManager.unload();
        battleBoxScheduleManager.unload();
        parkourTagScheduleManager.unload();
    }

    public void addRound(GameTypeEnum gameTypeEnum) {
        plugin.getRankManager().addGameOrder(gameTypeEnum, plugin.getRankManager().getRound() + 1);
    }

    public void resetRound() {
        plugin.getRankManager().resetGameOrder();
    }

    public void startDragonEggCarnival(ChampionshipTeam team, ChampionshipTeam rival) {
        timer = 10;
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.DRAGON_EGG_CARNIVAL)
                        .replace("%team%", team.getColoredName())
                        .replace("%rival%", rival.getColoredName()));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.DRAGON_EGG_CARNIVAL_POINTS));
            }
            if (timer < 5 && timer > 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }
            if (timer == 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                plugin.getGameManager().joinTeamArea(GameTypeEnum.DragonEggCarnival, "area1", team, rival);
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }
}
