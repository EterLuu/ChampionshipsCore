package ink.ziip.championshipscore.api.schedule.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

import java.util.List;

public class TGTTOSScheduleManager extends BaseSingleGameSchedule {
    private static final List<String> EVENT_MAPS = List.of(
            "cod", "industry", "badlands", "tsf1", "cliff", "boat");

    public TGTTOSScheduleManager(ChampionshipsCore championshipsCore, TGTTOSScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.TGTTOS);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return EVENT_MAPS.get(subRound - 1);
    }

    @Override
    public int getTotalRounds() {
        return EVENT_MAPS.size();
    }

}
