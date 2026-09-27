package ink.ziip.championshipscore.api.schedule.frostbite;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.frostbite.FrostbiteArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public final class FrostbiteScheduleHandler extends BaseListener {
    private FrostbiteScheduleManager scheduleManager;

    public FrostbiteScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof FrostbiteArea
                && event.getGameInstance().isEventRun() && scheduleManager.isEnabled()) {
            scheduleManager.nextRound();
        }
    }
}
