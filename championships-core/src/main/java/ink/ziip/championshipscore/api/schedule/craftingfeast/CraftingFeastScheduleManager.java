package ink.ziip.championshipscore.api.schedule.craftingfeast;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public final class CraftingFeastScheduleManager extends BaseSingleGameSchedule {
    public CraftingFeastScheduleManager(
            ChampionshipsCore plugin, CraftingFeastScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.CraftingFeast);
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
