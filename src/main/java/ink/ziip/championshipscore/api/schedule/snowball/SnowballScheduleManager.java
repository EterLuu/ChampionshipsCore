package ink.ziip.championshipscore.api.schedule.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;

public class SnowballScheduleManager extends BaseManager {
    private final BukkitScheduler scheduler;
    private final SnowballScheduleHandler handler;
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;

    public SnowballScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new SnowballScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {

    }

    public void startSnowBall() {
        if (enabled)
            return;

        plugin.getScheduleManager().addRound(GameTypeEnum.SnowballShowdown);
        enabled = true;
        timer = 10;
        subRound = 0;
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.SNOWBALL));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.SNOWBALL_POINTS));
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
                startSnowBallRound();
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startSnowBallRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > 3) {
            return;
        }

        handler.register();
        plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.SnowballShowdown, "area1");
    }

    public void nextSnowballRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > 3) {
            Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.ROUND_END));
            scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.SnowballShowdown)));
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
                plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.SnowballShowdown, "area1");
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }
}
