package ink.ziip.championshipscore.api.schedule.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;

public class TGTTOSScheduleManager extends BaseManager {
    private final BukkitScheduler scheduler;
    private final TGTTOSScheduleHandler handler;
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;

    public TGTTOSScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new TGTTOSScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {

    }

    public void startTGTTOS() {
        if (enabled)
            return;

        plugin.getScheduleManager().addRound(GameTypeEnum.TGTTOS);
        enabled = true;
        timer = 10;
        subRound = 0;
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.TGTTOS));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.TGTTOS_POINTS));
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
                startTGTTOSRound();
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startTGTTOSRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > 6) {
            return;
        }

        handler.register();
        plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.TGTTOS, plugin.getGameManager().getTgttosManager().getAreaNameList().get(subRound - 1));
    }

    public void nextTGTTOSRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > 6) {
            Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.ROUND_END));
            scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.TGTTOS)));
            Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
            handler.unRegister();
            return;
        }
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = 10;
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
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
                plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.TGTTOS, plugin.getGameManager().getTgttosManager().getAreaNameList().get(subRound - 1));
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }
}