package ink.ziip.championshipscore.api.schedule.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class SkyWarsScheduleManager extends BaseSingleGameSchedule {

    public SkyWarsScheduleManager(ChampionshipsCore championshipsCore, SkyWarsScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.SkyWars);
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
