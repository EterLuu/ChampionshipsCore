package ink.ziip.championshipscore.api.schedule.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class AceRaceScheduleManager extends BaseSingleGameSchedule {
    public AceRaceScheduleManager(ChampionshipsCore plugin, AceRaceScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.AceRace);
        handler.setScheduleManager(this);
    }

    @Override public String getArea() {
        return FormalEventMapResolver.map(plugin, gameTypeEnum, subRound);
    }
    @Override public int getTotalRounds() {
        return FormalEventMapResolver.maps(plugin, gameTypeEnum).size();
    }
}
