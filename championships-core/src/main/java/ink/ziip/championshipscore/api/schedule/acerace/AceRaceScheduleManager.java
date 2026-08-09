package ink.ziip.championshipscore.api.schedule.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public class AceRaceScheduleManager extends BaseSingleGameSchedule {
    public AceRaceScheduleManager(ChampionshipsCore plugin, AceRaceScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.AceRace);
        handler.setScheduleManager(this);
    }

    @Override public String getArea() { return "acerace"; }
    @Override public int getTotalRounds() { return 1; }
}
