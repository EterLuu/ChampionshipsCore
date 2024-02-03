package ink.ziip.championshipscore.api.schedule.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public class SnowballScheduleManager extends BaseSingleGameSchedule {

    public SnowballScheduleManager(ChampionshipsCore championshipsCore, SnowballScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.SnowballShowdown);
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
