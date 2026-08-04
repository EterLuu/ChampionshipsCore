package ink.ziip.championshipscore.api.schedule.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class AceRaceScheduleHandler extends BaseListener {
    private AceRaceScheduleManager scheduleManager;

    public AceRaceScheduleHandler(ChampionshipsCore plugin) { super(plugin); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof AceRaceArea area && area.isEventRun()
                && scheduleManager.isEnabled()) scheduleManager.nextRound();
    }
}
