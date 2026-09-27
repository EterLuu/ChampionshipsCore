package ink.ziip.championshipscore.api.schedule.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.riptiderush.RiptideRushArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public final class RiptideRushScheduleHandler extends BaseListener {
    private RiptideRushScheduleManager scheduleManager;

    public RiptideRushScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof RiptideRushArea
                && event.getGameInstance().isEventRun() && scheduleManager.isEnabled()) {
            scheduleManager.nextRound();
        }
    }
}
