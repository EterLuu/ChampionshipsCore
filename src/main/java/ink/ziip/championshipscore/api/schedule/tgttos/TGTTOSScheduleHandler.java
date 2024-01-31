package ink.ziip.championshipscore.api.schedule.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSTeamArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class TGTTOSScheduleHandler extends BaseListener {
    private final TGTTOSScheduleManager scheduleManager;

    protected TGTTOSScheduleHandler(ChampionshipsCore plugin, TGTTOSScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof TGTTOSTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextTGTTOSRound();
            }
        }
    }
}
