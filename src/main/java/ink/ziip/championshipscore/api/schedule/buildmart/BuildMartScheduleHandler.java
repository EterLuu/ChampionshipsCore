package ink.ziip.championshipscore.api.schedule.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@Setter
public final class BuildMartScheduleHandler extends BaseListener {
    private BuildMartScheduleManager scheduleManager;

    public BuildMartScheduleHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        if (event.getGameInstance() instanceof BuildMartArea area && area.isEventRun()
                && scheduleManager.isEnabled()) {
            scheduleManager.nextRound();
        }
    }
}
