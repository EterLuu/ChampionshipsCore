package ink.ziip.championshipscore.api.schedule.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSTeamArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class TGTTOSScheduleHandler extends BaseListener {
    private TGTTOSScheduleManager scheduleManager;

    public TGTTOSScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getBaseSingleTeamArea() instanceof TGTTOSTeamArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.nextRound();
            }
        }
    }
}
