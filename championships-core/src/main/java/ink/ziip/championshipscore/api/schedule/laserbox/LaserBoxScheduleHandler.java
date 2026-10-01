package ink.ziip.championshipscore.api.schedule.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.laserbox.LaserBoxArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public final class LaserBoxScheduleHandler extends BaseListener {
    private final LaserBoxScheduleManager schedule;

    LaserBoxScheduleHandler(ChampionshipsCore plugin, LaserBoxScheduleManager schedule) {
        super(plugin);
        this.schedule = schedule;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(TeamGameEndEvent event) {
        if (event.getGameInstance() instanceof LaserBoxArea area && area.isEventRun())
            schedule.onInstanceComplete(area);
    }
}
