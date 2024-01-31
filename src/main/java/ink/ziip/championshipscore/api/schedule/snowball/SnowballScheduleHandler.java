package ink.ziip.championshipscore.api.schedule.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class SnowballScheduleHandler extends BaseListener {
    private final SnowballScheduleManager scheduleManager;

    protected SnowballScheduleHandler(ChampionshipsCore plugin, SnowballScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof SnowballShowdownTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextSnowballRound();
            }
        }
    }
}
