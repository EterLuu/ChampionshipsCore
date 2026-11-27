package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.ParticipantRole;

/** Transient view of the current roster entry and phase for one event. */
record WorkerParticipantAccess(
        ParticipantRole role,
        MatchState phase,
        boolean released,
        boolean coreSettlement,
        boolean roundPrepared) {
    boolean waitingPlayer() {
        return !released
                && role == ParticipantRole.PLAYER
                && coreSettlement
                && phase == MatchState.FINISHED;
    }

    boolean countdownPlayer() {
        return !released
                && role == ParticipantRole.PLAYER
                && roundPrepared
                && phase == MatchState.COUNTDOWN;
    }

    boolean owned() {
        return !released
                && role != null
                && (!phase.terminal() || coreSettlement && phase == MatchState.FINISHED);
    }

    boolean playing() {
        return !released
                && role == ParticipantRole.PLAYER
                && (phase == MatchState.ROUTING
                        || phase == MatchState.COUNTDOWN
                        || phase == MatchState.RUNNING);
    }

    boolean runningPlayer() {
        return !released && role == ParticipantRole.PLAYER && phase == MatchState.RUNNING;
    }

    boolean spectator() {
        return role == ParticipantRole.SPECTATOR && owned();
    }

    boolean protectedParticipant() {
        return owned() && (role == ParticipantRole.SPECTATOR || phase != MatchState.RUNNING);
    }
}
