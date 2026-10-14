package ink.ziip.championshipscore.api.schedule.frostbite;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public final class FrostbiteScheduleManager extends BaseSingleGameSchedule {
    private java.util.List<String> roundMaps = java.util.List.of();

    @Override
    public void startGame() {
        if (!enabled) {
            roundMaps = roundMaps(FormalEventMapResolver.maps(plugin, gameTypeEnum));
            if (roundMaps.isEmpty()) {
                plugin.getLogger()
                        .warning(MessageConfig.codeText("frostbite-schedule-manager.start-game-1"));
                return;
            }
        }
        super.startGame();
    }

    static java.util.List<String> roundMaps(java.util.List<String> maps) {
        if (maps.isEmpty()) return java.util.List.of();
        return java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> maps.get(i % maps.size()))
                .toList();
    }

    public FrostbiteScheduleManager(ChampionshipsCore plugin, FrostbiteScheduleHandler handler) {
        super(plugin, handler, GameTypeEnum.FrostbiteFrenzy);
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
