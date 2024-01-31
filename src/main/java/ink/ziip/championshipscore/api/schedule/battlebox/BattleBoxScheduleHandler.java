package ink.ziip.championshipscore.api.schedule.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class BattleBoxScheduleHandler extends BaseListener {
    private final BattleBoxScheduleManager scheduleManager;

    protected BattleBoxScheduleHandler(ChampionshipsCore plugin, BattleBoxScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(TeamGameEndEvent event) {
        if (event.getBaseTeamArea() instanceof BattleBoxArea) {
            if (scheduleManager.isEnabled()) {
                scheduleManager.addCompletedAreaNum();
            }
        }
    }
}
