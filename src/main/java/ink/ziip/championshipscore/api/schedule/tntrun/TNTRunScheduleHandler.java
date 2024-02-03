package ink.ziip.championshipscore.api.schedule.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class TNTRunScheduleHandler extends BaseListener {
    private TNTRunScheduleManager scheduleManager;

    public TNTRunScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof TNTRunTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextRound();
            }
        }
    }
}
