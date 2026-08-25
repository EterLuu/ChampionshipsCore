package ink.ziip.championshipscore.api.schedule.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class SnowballScheduleManager extends BaseSingleGameSchedule {

    public SnowballScheduleManager(ChampionshipsCore championshipsCore, SnowballScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.SnowballShowdown);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return FormalEventMapResolver.map(plugin, gameTypeEnum, 1);
    }

    @Override
    public int getTotalRounds() {
        return 3;
    }

}
