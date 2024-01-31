package ink.ziip.championshipscore.api.schedule.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsTeamArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class SkyWarsScheduleHandler extends BaseListener {
    private final SkyWarsScheduleManager scheduleManager;

    protected SkyWarsScheduleHandler(ChampionshipsCore plugin, SkyWarsScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof SkyWarsTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextSkyWarsRound();
            }
        }
    }
}
