package ink.ziip.championshipscore.api.schedule.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class ParkourTagScheduleHandler extends BaseListener {
    private final ParkourTagScheduleManager scheduleManager;

    protected ParkourTagScheduleHandler(ChampionshipsCore plugin, ParkourTagScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(TeamGameEndEvent event) {
        if (event.getBaseTeamArea() instanceof ParkourTagArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.addCompletedAreaNum();
            }
        }
    }
}
