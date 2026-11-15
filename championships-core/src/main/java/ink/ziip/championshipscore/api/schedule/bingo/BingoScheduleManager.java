package ink.ziip.championshipscore.api.schedule.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public class BingoScheduleManager extends BaseSingleGameSchedule {
    public BingoScheduleManager(ChampionshipsCore championshipsCore, BingoScheduleHandler handler) {
        super(championshipsCore, handler, GameTypeEnum.Bingo);
        handler.setScheduleManager(this);
    }

    @Override
    public boolean startGame() {
        if (!enabled) {
            String map = FormalEventMapResolver.map(plugin, gameTypeEnum, 1);
            if (map == null || !plugin.getGameManager().canStartBingo(map, true, GameRunMode.EVENT))
                return false;
        }
        return super.startGame();
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
