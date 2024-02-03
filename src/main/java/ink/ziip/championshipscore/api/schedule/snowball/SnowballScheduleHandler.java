package ink.ziip.championshipscore.api.schedule.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class SnowballScheduleHandler extends BaseListener {
    private SnowballScheduleManager scheduleManager;

    public SnowballScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof SnowballShowdownTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextRound();
            }
        }
    }
}
