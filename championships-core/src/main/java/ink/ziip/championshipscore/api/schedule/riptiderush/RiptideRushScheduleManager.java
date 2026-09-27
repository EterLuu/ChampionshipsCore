package ink.ziip.championshipscore.api.schedule.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;

public final class RiptideRushScheduleManager extends BaseSingleGameSchedule {
    private java.util.List<String> roundMaps = java.util.List.of();

    @Override
    public void startGame() {
        if (!enabled) {
            roundMaps = roundMaps(FormalEventMapResolver.maps(plugin, gameTypeEnum));
            if (roundMaps.isEmpty()) {
                plugin.getLogger().warning("激流勇进正式比赛未启动：没有可用地图");
                return;
            }
        }
        super.startGame();
    }

    static java.util.List<String> roundMaps(java.util.List<String> maps) {
        if (maps.isEmpty()) return java.util.List.of();
        return java.util.stream.IntStream.range(0, 3).mapToObj(i -> maps.get(i % maps.size())).toList();
    }

    public RiptideRushScheduleManager(ChampionshipsCore plugin, RiptideRushScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.RiptideRush);
        handler.setScheduleManager(this);
    }

    @Override
    public String getArea() {
        return subRound >= 1 && subRound <= roundMaps.size() ? roundMaps.get(subRound - 1) : "";
    }

    @Override
    public int getTotalRounds() {
        return 3;
    }
}
