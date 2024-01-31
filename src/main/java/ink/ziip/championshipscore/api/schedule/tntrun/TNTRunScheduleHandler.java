package ink.ziip.championshipscore.api.schedule.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class TNTRunScheduleHandler extends BaseListener {
    private final TNTRunScheduleManager scheduleManager;

    protected TNTRunScheduleHandler(ChampionshipsCore plugin, TNTRunScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof TNTRunTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextTNTRunRound();
            }
        }
    }
}
