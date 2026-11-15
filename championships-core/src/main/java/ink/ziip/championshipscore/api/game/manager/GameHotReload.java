package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** One incremental reload, from the frozen instance plan through the last asynchronous reset. */
final class GameHotReload implements AutoCloseable {
    interface Area {
        Object configuration();

        boolean active();

        boolean reloadConfiguration();

        CompletionStage<Boolean> reset();
    }

    interface Host {
        Set<GameTypeEnum> loadedGames();

        List<? extends Area> instances(GameTypeEnum type);

        int activeRemoteMatches();

        CompletionStage<Void> stopRemoteMatches();

        boolean disable(GameTypeEnum type);

        boolean enable(GameTypeEnum type);

        void disableRemoteBingo();

        void enableRemoteBingo();

        void execute(Runnable action);
    }

    private record PlannedArea(
            Area area, Object configuration, boolean active, boolean disabling) {}

    private final Host host;
    private CompletableFuture<GameManager.ReloadReport> active;
    private boolean closed;

    GameHotReload(Host host) {
        this.host = host;
    }

    CompletionStage<GameManager.ReloadReport> reload(
            Set<GameTypeEnum> previous, Set<GameTypeEnum> current) {
        CompletableFuture<GameManager.ReloadReport> result;
        synchronized (this) {
            if (closed || active != null)
                return CompletableFuture.failedFuture(
                        new IllegalStateException(
                                closed ? "game reload closed" : "game reload already running"));
            active = result = new CompletableFuture<>();
        }
        try {
            start(result, Set.copyOf(previous), Set.copyOf(current));
        } catch (Throwable failure) {
            complete(result, null, failure);
        }
        return result;
    }

    private void start(
            CompletableFuture<GameManager.ReloadReport> result,
            Set<GameTypeEnum> previous,
            Set<GameTypeEnum> current) {
        Set<GameTypeEnum> loaded = Set.copyOf(host.loadedGames());
        Set<GameTypeEnum> disable = EnumSet.noneOf(GameTypeEnum.class);
        Set<GameTypeEnum> enable = EnumSet.noneOf(GameTypeEnum.class);
        for (GameTypeEnum type : previous)
            if (!current.contains(type) && loaded.contains(type)) disable.add(type);
        for (GameTypeEnum type : current) if (!loaded.contains(type)) enable.add(type);
        var plan = new ArrayList<PlannedArea>();
        Set<Object> activeConfigurations = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GameTypeEnum type : loaded) {
            checkCurrent(result);
            for (Area area : host.instances(type)) {
                var entry =
                        new PlannedArea(
                                area, area.configuration(), area.active(), disable.contains(type));
                plan.add(entry);
                if (entry.active() && !entry.disabling())
                    activeConfigurations.add(entry.configuration());
            }
        }
        int reused = 0, reloaded = 0, failedConfigurations = 0;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PlannedArea entry : plan) {
            checkCurrent(result);
            if (entry.active() || entry.disabling()) continue;
            reused++;
            if (!activeConfigurations.contains(entry.configuration())
                    && visited.add(entry.configuration())) {
                if (entry.area().reloadConfiguration()) reloaded++;
                else failedConfigurations++;
            }
        }
        var resets = new ArrayList<CompletableFuture<Boolean>>();
        for (PlannedArea entry : plan) {
            checkCurrent(result);
            if (entry.active()) resets.add(attempt(entry.area()::reset));
        }
        checkCurrent(result);
        int remote = host.activeRemoteMatches();
        var stop =
                remote > 0
                        ? attempt(host::stopRemoteMatches)
                        : CompletableFuture.completedFuture(null);
        int finalReused = reused,
                finalReloaded = reloaded,
                finalFailedConfigurations = failedConfigurations;
        var operations = new ArrayList<CompletableFuture<?>>(resets);
        operations.add(stop);
        CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new))
                .whenComplete(
                        (ignored, failure) -> {
                            if (!current(result)) return;
                            try {
                                host.execute(
                                        () ->
                                                finish(
                                                        result,
                                                        disable,
                                                        enable,
                                                        resets,
                                                        finalReused,
                                                        finalReloaded,
                                                        finalFailedConfigurations,
                                                        remote,
                                                        failure));
                            } catch (Throwable schedulingFailure) {
                                complete(result, null, schedulingFailure);
                            }
                        });
    }

    private void finish(
            CompletableFuture<GameManager.ReloadReport> result,
            Set<GameTypeEnum> disable,
            Set<GameTypeEnum> enable,
            List<CompletableFuture<Boolean>> resets,
            int reused,
            int reloaded,
            int failedConfigurations,
            int remote,
            Throwable operationFailure) {
        if (!current(result)) return;
        try {
            int failed =
                    (int)
                            resets.stream()
                                    .filter(
                                            f ->
                                                    f.isCompletedExceptionally()
                                                            || !Boolean.TRUE.equals(
                                                                    f.getNow(false)))
                                    .count();
            int disabled = 0, enabled = 0;
            if (disable.contains(GameTypeEnum.Bingo)) host.disableRemoteBingo();
            for (GameTypeEnum type : disable) {
                checkCurrent(result);
                if (host.disable(type)) disabled++;
            }
            for (GameTypeEnum type : enable) {
                checkCurrent(result);
                if (host.enable(type)) enabled++;
            }
            checkCurrent(result);
            if (enable.contains(GameTypeEnum.Bingo)) host.enableRemoteBingo();
            checkCurrent(result);
            complete(
                    result,
                    new GameManager.ReloadReport(
                            reused,
                            resets.size(),
                            failed,
                            reloaded,
                            failedConfigurations,
                            enabled,
                            disabled,
                            remote),
                    operationFailure);
        } catch (Throwable failure) {
            if (operationFailure != null && operationFailure != failure)
                failure.addSuppressed(operationFailure);
            complete(result, null, failure);
        }
    }

    private static <T> CompletableFuture<T> attempt(Supplier<CompletionStage<T>> action) {
        try {
            return action.get().toCompletableFuture();
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private synchronized boolean current(CompletableFuture<?> result) {
        return !closed && active == result;
    }

    private void checkCurrent(CompletableFuture<?> result) {
        if (!current(result)) throw new CancellationException("game reload is no longer active");
    }

    private void complete(
            CompletableFuture<GameManager.ReloadReport> result,
            GameManager.ReloadReport report,
            Throwable failure) {
        synchronized (this) {
            if (active != result) return;
            active = null;
        }
        if (failure == null) result.complete(report);
        else result.completeExceptionally(failure);
    }

    synchronized void open() {
        if (active != null) throw new IllegalStateException("game reload still running");
        closed = false;
    }

    @Override
    public void close() {
        CompletableFuture<GameManager.ReloadReport> pending;
        synchronized (this) {
            closed = true;
            pending = active;
            active = null;
        }
        if (pending != null)
            pending.completeExceptionally(
                    new CancellationException("game manager unloaded during reload"));
    }
}
