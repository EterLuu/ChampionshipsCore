package ink.ziip.championshipscore.api.schedule.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class ParkourWarriorScheduleHandler extends BaseListener {
    private ParkourWarriorScheduleManager scheduleManager;

    public ParkourWarriorScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof ParkourWarriorTeamArea) {
            FoliaScheduler.global(plugin).runTask(() -> {
                if (scheduleManager.isEnabled()) scheduleManager.nextRound();
            });
        }
    }
}
