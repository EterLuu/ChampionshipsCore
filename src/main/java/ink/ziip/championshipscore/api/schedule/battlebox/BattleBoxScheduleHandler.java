package ink.ziip.championshipscore.api.schedule.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class BattleBoxScheduleHandler extends BaseListener {
    private final BattleBoxScheduleManager scheduleManager;

    protected BattleBoxScheduleHandler(ChampionshipsCore plugin, BattleBoxScheduleManager scheduleManager) {
        super(plugin);
        this.scheduleManager = scheduleManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEnd(SingleGameEndEvent event) {
        // The single Battle Box area now ends once per round (all its parallel matches finished).
        if (event.getBaseSingleTeamArea() instanceof BattleBoxArea) {
            FoliaScheduler.global(plugin).runTask(() -> {
                if (scheduleManager.isEnabled()) scheduleManager.onRoundComplete();
            });
        }
    }
}
