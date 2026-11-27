package ink.ziip.championshipscore.api.daily.adapter;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.*;
import ink.ziip.championshipscore.api.game.model.*;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import java.util.*;
import java.util.concurrent.*;

/** Configurable cooperative restaurant queues using the shared daily allocation rules. */
public final class CraftingFeastDailyGameAdapter implements DailyGameAdapter {
    private final ChampionshipsCore plugin;

    public CraftingFeastDailyGameAdapter(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public GameTypeEnum game() {
        return GameTypeEnum.CraftingFeast;
    }

    @Override
    public DailyRules rules() {
        return plugin.getConfigurationManager().getCCConfig().dailyGame(game()).rules();
    }

    private List<ink.ziip.championshipscore.api.game.craftingfeast.runtime.CraftingFeastArea>
            candidates() {
        return plugin.getGameManager().getCraftingFeastManager().getRuntimeInstances().stream()
                .filter(
                        a ->
                                a.getGameStageEnum() == GameStageEnum.WAITING
                                        && plugin.getDailyManager().session(a) == null)
                .filter(
                        a ->
                                plugin.getPrepareSessionManager()
                                        .canStart(game(), a.getGameConfig().getConfigName()))
                .sorted(Comparator.comparing(a -> a.getGameConfig().getConfigName()))
                .toList();
    }

    @Override
    public int availableSlots() {
        return candidates().size();
    }

    @Override
    public CompletionStage<StartResult> start(List<ChampionshipTeam> teams) {
        for (var area : candidates()) {
            if (plugin.getGameManager()
                    .joinMultiTeamInstanceForTeams(game(), area, false, GameRunMode.DAILY, teams))
                return CompletableFuture.completedFuture(
                        new StartResult(area.getGameConfig().getConfigName(), area));
        }
        return CompletableFuture.completedFuture(null);
    }
}
