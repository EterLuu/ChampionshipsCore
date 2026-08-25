package ink.ziip.championshipscore.api.schedule.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class TGTTOSScheduleManager extends BaseSingleGameSchedule {

    public TGTTOSScheduleManager(ChampionshipsCore championshipsCore, TGTTOSScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.TGTTOS);
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
