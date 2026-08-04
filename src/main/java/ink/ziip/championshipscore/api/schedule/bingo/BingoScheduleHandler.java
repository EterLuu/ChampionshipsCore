package ink.ziip.championshipscore.api.schedule.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.bingo.BingoArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public class BingoScheduleHandler extends BaseListener {
    private BingoScheduleManager scheduleManager;

    public BingoScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof BingoArea) {
            if (event.getGameInstance().isEventRun() && scheduleManager.isEnabled()) {
                scheduleManager.nextRound();
            }
        }
    }
}
