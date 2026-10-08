package ink.ziip.championshipscore.api.schedule.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;
import ink.ziip.championshipscore.api.schedule.BaseSingleGameSchedule;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class RiptideRushScheduleManager extends BaseSingleGameSchedule {
    private java.util.List<String> roundMaps = java.util.List.of();
    private RiptideRushArea upcomingArea;

    @Override
    protected long resultDisplayTicks(boolean hasNextRound) {
        return hasNextRound ? 0L : super.resultDisplayTicks(false);
    }

    @Override
    protected CompletionStage<Boolean> prepareNextRound() {
        upcomingArea = plugin.getGameManager().getRiptideRushManager().getArea(getArea());
        return upcomingArea == null
                ? CompletableFuture.completedFuture(false)
                : upcomingArea.prepareUpcomingEventRound();
    }

    @Override
    public void endSchedule() {
        RiptideRushArea pending = upcomingArea;
        upcomingArea = null;
        super.endSchedule();
        if (pending != null) pending.cancelUpcomingEventRound();
    }

    @Override
    public void startGame() {
        if (!enabled) {
            roundMaps = roundMaps(FormalEventMapResolver.maps(plugin, gameTypeEnum));
            if (roundMaps.isEmpty()) {
                plugin.getLogger()
                        .warning(
                                MessageConfig.codeText(
                                        "riptide-rush-schedule-manager.start-game-1"));
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

    public RiptideRushScheduleManager(
            ChampionshipsCore plugin, RiptideRushScheduleHandler handler) {
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
