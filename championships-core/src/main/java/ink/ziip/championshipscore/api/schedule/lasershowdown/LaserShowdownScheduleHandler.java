package ink.ziip.championshipscore.api.schedule.lasershowdown;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;

import lombok.Setter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class LaserShowdownScheduleHandler extends BaseListener {
    private LaserShowdownScheduleManager scheduleManager;

    public LaserShowdownScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof LaserShowdownTeamArea) {
            if (event.getGameInstance().isEventRun() && scheduleManager.isEnabled()) {
                scheduleManager.nextRound();
            }
        }
    }
}
