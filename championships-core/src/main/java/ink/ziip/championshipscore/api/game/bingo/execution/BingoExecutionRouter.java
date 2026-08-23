package ink.ziip.championshipscore.api.game.bingo.execution;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Runtime execution selector. It deliberately starts in LOCAL mode; installing a remote gateway is a
 * separate bootstrap operation once Redis, proxy routing and worker readiness have all been verified.
 */
public final class BingoExecutionRouter implements BingoExecutionGateway {
    private final BingoExecutionGateway local;
    private volatile BingoExecutionGateway active;

    public BingoExecutionRouter(BingoExecutionGateway local) {
        this.local = requireMode(local, BingoExecutionMode.LOCAL);
        this.active = local;
    }

    public synchronized void activateRemote(BingoExecutionGateway remote) {
        this.active = requireMode(remote, BingoExecutionMode.REMOTE);
    }

    public synchronized void activateLocal() {
        this.active = local;
    }

    @Override
    public BingoExecutionMode mode() {
        return active.mode();
    }

    @Override
    public boolean canStart(BingoStartRequest request) {
        return active.canStart(Objects.requireNonNull(request, "request"));
    }

    @Override
    public CompletionStage<Boolean> start(BingoStartRequest request) {
        return active.start(Objects.requireNonNull(request, "request"));
    }

    @Override
    public CompletionStage<Void> forceEnd(String reason) {
        return active.forceEnd(Objects.requireNonNull(reason, "reason"));
    }

    private static BingoExecutionGateway requireMode(
            BingoExecutionGateway gateway, BingoExecutionMode expected) {
        Objects.requireNonNull(gateway, "gateway");
        if (gateway.mode() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " Bingo gateway");
        }
        return gateway;
    }

}
