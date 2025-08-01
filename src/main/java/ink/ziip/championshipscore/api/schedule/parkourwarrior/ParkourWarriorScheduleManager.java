package ink.ziip.championshipscore.api.schedule.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public class ParkourWarriorScheduleManager extends BaseSingleGameSchedule {

    public ParkourWarriorScheduleManager(ChampionshipsCore championshipsCore, ParkourWarriorScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.ParkourWarrior);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return "area1";
    }

    @Override
    public int getTotalRounds() {
        return 1;
    }

    @Override
    public String getSpecCommand() {
        return "cc spectate parkourwarrior " + getArea();
    }
}
