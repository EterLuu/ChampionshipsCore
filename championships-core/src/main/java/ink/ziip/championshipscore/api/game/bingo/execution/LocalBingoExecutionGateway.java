package ink.ziip.championshipscore.api.game.bingo.execution;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Adapter around the existing in-process Bingo instance; used until remote mode is explicitly enabled. */
public final class LocalBingoExecutionGateway implements BingoExecutionGateway {
    private final Predicate<BingoStartRequest> starter;
    private final Consumer<String> forceEnder;

    public LocalBingoExecutionGateway(Predicate<BingoStartRequest> starter, Consumer<String> forceEnder) {
        this.starter = Objects.requireNonNull(starter, "starter");
        this.forceEnder = Objects.requireNonNull(forceEnder, "forceEnder");
    }

    @Override
    public BingoExecutionMode mode() {
        return BingoExecutionMode.LOCAL;
    }

    @Override
    public boolean canStart(BingoStartRequest request) {
        // Local start performs the authoritative arena/roster check atomically.  Returning true here
        // preserves the historical countdown behaviour; the start result is still checked by it.
        return true;
    }

    @Override
    public boolean start(BingoStartRequest request) {
        return starter.test(Objects.requireNonNull(request, "request"));
    }

    @Override
    public void forceEnd(String reason) {
        forceEnder.accept(Objects.requireNonNull(reason, "reason"));
    }
}
