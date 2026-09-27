package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.MatchState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Serializes administrator stop decisions and world mutations on the global region. */
final class WorkerAdminStop {
    private WorkerAdminStop() { }

    static CompletionStage<Boolean> request(Executor global, Supplier<MatchState> state,
            Supplier<CompletionStage<Boolean>> finish, Supplier<CompletionStage<Boolean>> abort) {
        return CompletableFuture.supplyAsync(() -> {
            MatchState current = state.get();
            if (current.terminal() || current == MatchState.SETTLING) {
                return CompletableFuture.completedFuture(false);
            }
            return current == MatchState.RUNNING ? finish.get() : abort.get();
        }, global).thenCompose(stage -> stage);
    }
}
