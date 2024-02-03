package ink.ziip.championshipscore.api.schedule.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public class TNTRunScheduleManager extends BaseSingleGameSchedule {
    public TNTRunScheduleManager(ChampionshipsCore championshipsCore, TNTRunScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.TNTRun);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return "area1";
    }

    @Override
    public int getTotalRounds() {
        return 3;
    }
}
