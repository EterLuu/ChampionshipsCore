package ink.ziip.championshipscore.api.schedule.iamnotarobot;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.iamnotarobot.runtime.RobotArea;

import lombok.Setter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public final class RobotScheduleHandler extends BaseListener {
    private RobotScheduleManager scheduleManager;

    public RobotScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof RobotArea area
                && area.isFormalEventRun()
                && scheduleManager.isEnabled()) {
            scheduleManager.nextRound();
        }
    }
}
