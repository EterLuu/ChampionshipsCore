package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Owns the result-display task and the exact arena runs it may release. */
final class EventRoundSettlement {
    private record FinishedRun(BaseGameInstance instance, long generation) {
        boolean isCurrent() {
            return instance.captureGameGeneration() == generation
                    && !instance.isDisposed()
                    && instance.getGameStageEnum() == GameStageEnum.END;
        }
    }

    private final List<FinishedRun> runs;
    private BukkitTask task;
    private boolean closed;

    EventRoundSettlement(List<BaseGameInstance> instances) {
        runs =
                instances.stream()
                        .map(
                                instance ->
                                        new FinishedRun(instance, instance.captureGameGeneration()))
                        .toList();
    }

    void attach(BukkitTask task) {
        this.task = task;
        if (closed) task.cancel();
    }

    void cancel() {
        closed = true;
        if (task != null) task.cancel();
    }

    void complete(boolean nextRound, Runnable afterSettlement) {
        if (closed) return;
        closed = true;
        boolean allCurrent = runs.stream().allMatch(FinishedRun::isCurrent);
        for (FinishedRun run : runs) {
            if (run.isCurrent()) run.instance().completePostGame(nextRound && allCurrent);
        }
        if (allCurrent) afterSettlement.run();
    }
}
