package ink.ziip.championshipscore.api.schedule.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsTeamArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class SkyWarsScheduleHandler extends BaseListener {
    private SkyWarsScheduleManager scheduleManager;

    public SkyWarsScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof SkyWarsTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextRound();
            }
        }
    }
}
