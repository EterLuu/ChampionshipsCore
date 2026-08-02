package ink.ziip.championshipscore.api.schedule.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class ParkourTagScheduleHandler extends BaseListener {
    private final ParkourTagScheduleManager scheduleManager;

    protected ParkourTagScheduleHandler(ChampionshipsCore plugin, ParkourTagScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        // The single Parkour Tag area now ends once per round (all its parallel matches finished).
        if (event.getBaseSingleTeamArea() instanceof ParkourTagArea) {
            FoliaScheduler.global(plugin).runTask(() -> {
                if (scheduleManager.isEnabled()) scheduleManager.onRoundComplete();
            });
        }
    }
}
