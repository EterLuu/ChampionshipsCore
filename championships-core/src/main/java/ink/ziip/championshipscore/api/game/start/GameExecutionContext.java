package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.game.model.GameRunMode;

import java.util.Objects;
import java.util.UUID;

/** Immutable lifecycle/settlement context carried by a game instance. */
public record GameExecutionContext(
        GameRunMode runMode,
        GameFlow flow,
        SettlementPolicy settlement,
        UUID ownerSessionId,
        long generation) {
    public GameExecutionContext {
        Objects.requireNonNull(runMode, "runMode");
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(settlement, "settlement");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
    }

    public static GameExecutionContext forRunMode(GameRunMode mode) {
        return switch (mode) {
            case GAME ->
                    new GameExecutionContext(
                            mode, GameFlow.STANDALONE, SettlementPolicy.NORMAL, null, 1);
            case EVENT ->
                    new GameExecutionContext(
                            mode, GameFlow.FORMAL_EVENT, SettlementPolicy.FORMAL_RANK, null, 1);
            case DAILY ->
                    new GameExecutionContext(
                            mode, GameFlow.STANDALONE, SettlementPolicy.DAILY, null, 1);
            case TEST ->
                    new GameExecutionContext(
                            mode, GameFlow.STANDALONE, SettlementPolicy.TEST, null, 1);
            case TEST_EVENT ->
                    new GameExecutionContext(
                            mode, GameFlow.TEST_EVENT, SettlementPolicy.TEST, null, 1);
        };
    }

    public boolean formalEvent() {
        return flow == GameFlow.FORMAL_EVENT;
    }

    public boolean test() {
        return settlement == SettlementPolicy.TEST;
    }

    public boolean writesSettlement() {
        return settlement == SettlementPolicy.FORMAL_RANK || settlement == SettlementPolicy.TEST;
    }
}
