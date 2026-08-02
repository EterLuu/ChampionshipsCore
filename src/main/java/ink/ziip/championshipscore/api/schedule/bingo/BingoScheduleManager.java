package ink.ziip.championshipscore.api.schedule.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public class BingoScheduleManager extends BaseSingleGameSchedule {
    public BingoScheduleManager(ChampionshipsCore championshipsCore, BingoScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.Bingo);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return "bingo";
    }

    @Override
    public int getTotalRounds() {
        return 1;
    }

    @Override
    public String getSpecCommand() {
        return "cc spectate bingo bingo";
    }
}
