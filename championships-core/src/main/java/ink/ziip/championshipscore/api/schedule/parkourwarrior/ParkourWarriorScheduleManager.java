package ink.ziip.championshipscore.api.schedule.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class ParkourWarriorScheduleManager extends BaseSingleGameSchedule {

    public ParkourWarriorScheduleManager(ChampionshipsCore championshipsCore, ParkourWarriorScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.ParkourWarrior);
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
