package ink.ziip.championshipscore.api.schedule.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public class TGTTOSScheduleManager extends BaseSingleGameSchedule {

    public TGTTOSScheduleManager(ChampionshipsCore championshipsCore, TGTTOSScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.TGTTOS);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return plugin.getGameManager().getTgttosManager().getAreaNameList().get(subRound - 1);
    }

    @Override
    public int getTotalRounds() {
        return plugin.getGameManager().getTgttosManager().getAreaNameList().size();
    }
}
