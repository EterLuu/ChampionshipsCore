package ink.ziip.championshipscore.api.schedule.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class HotyCodyDuskyScheduleHandler extends BaseListener {
    private final HotyCodyDuskyScheduleManager scheduleManager;

    protected HotyCodyDuskyScheduleHandler(ChampionshipsCore plugin, HotyCodyDuskyScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof HotyCodyDuskyTeamArea) {
            FoliaScheduler.global(plugin).runTask(() -> {
                if (scheduleManager.isEnabled()) scheduleManager.addCompletedAreaNum();
            });
        }
    }
}
