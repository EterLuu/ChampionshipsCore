package ink.ziip.championshipscore.api.schedule.iamnotarobot;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public final class RobotScheduleManager extends BaseSingleGameSchedule {
    public RobotScheduleManager(ChampionshipsCore plugin, RobotScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.IAmNotARobot);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return FormalEventMapResolver.map(plugin, gameTypeEnum, subRound);
    }

    @Override
    public int getTotalRounds() {
        return FormalEventMapResolver.maps(plugin, gameTypeEnum).size();
    }
}
