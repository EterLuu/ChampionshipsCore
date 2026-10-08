package ink.ziip.championshipscore.api.schedule.lasershowdown;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class LaserShowdownScheduleManager extends BaseSingleGameSchedule {

    public LaserShowdownScheduleManager(
            ChampionshipsCore championshipsCore, LaserShowdownScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.LaserShowdown);
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
