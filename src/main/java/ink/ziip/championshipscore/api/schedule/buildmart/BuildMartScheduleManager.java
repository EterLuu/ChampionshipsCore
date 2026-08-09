package ink.ziip.championshipscore.api.schedule.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;

public final class BuildMartScheduleManager extends BaseSingleGameSchedule {
    public BuildMartScheduleManager(ChampionshipsCore plugin, BuildMartScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.BuildMart);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return "area";
    }

    @Override
    public int getTotalRounds() {
        return 1;
    }
}
