package ink.ziip.championshipscore.protocol;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Shared lifecycle validator; stale workers cannot invent backwards or post-terminal transitions. */
public final class MatchStateMachine {
    private static final Map<MatchState, EnumSet<MatchState>> ALLOWED = allowedTransitions();

    private MatchState state = MatchState.CREATED;
    private MatchState suspendedFrom;
    private long revision;

    public synchronized MatchState state() {
        return state;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized Transition transitionTo(MatchState next) {
        ProtocolSupport.required(next, "next");
        if (next == state) return new Transition(state, state, revision);
        if (!ALLOWED.getOrDefault(state, EnumSet.noneOf(MatchState.class)).contains(next)) {
            throw new IllegalStateException("Illegal match transition " + state + " -> " + next);
        }
        MatchState previous = state;
        if (next == MatchState.SUSPENDED) suspendedFrom = state;
        else if (state != MatchState.SUSPENDED) suspendedFrom = null;
        state = next;
        revision++;
        return new Transition(previous, state, revision);
    }

    public synchronized Transition resume() {
        if (state != MatchState.SUSPENDED || suspendedFrom == null) {
            throw new IllegalStateException("Only a suspended match can resume");
        }
        MatchState target = suspendedFrom;
        MatchState previous = state;
        state = target;
        suspendedFrom = null;
        revision++;
        return new Transition(previous, state, revision);
    }

    private static Map<MatchState, EnumSet<MatchState>> allowedTransitions() {
        EnumMap<MatchState, EnumSet<MatchState>> transitions = new EnumMap<>(MatchState.class);
        transitions.put(MatchState.CREATED, EnumSet.of(MatchState.PREPARING, MatchState.ABORTED));
        transitions.put(MatchState.PREPARING,
                EnumSet.of(MatchState.READY, MatchState.SUSPENDED, MatchState.ABORTED));
        transitions.put(MatchState.READY,
                EnumSet.of(MatchState.ROUTING, MatchState.SUSPENDED, MatchState.ABORTED));
        transitions.put(MatchState.ROUTING,
                EnumSet.of(MatchState.COUNTDOWN, MatchState.SUSPENDED, MatchState.ABORTED));
        transitions.put(MatchState.COUNTDOWN,
                EnumSet.of(MatchState.RUNNING, MatchState.SUSPENDED, MatchState.ABORTED));
        transitions.put(MatchState.RUNNING,
                EnumSet.of(MatchState.SETTLING, MatchState.SUSPENDED, MatchState.ABORTED));
        transitions.put(MatchState.SETTLING,
                EnumSet.of(MatchState.FINISHED, MatchState.SUSPENDED, MatchState.ABORTED));
        transitions.put(MatchState.SUSPENDED, EnumSet.of(MatchState.ABORTED));
        transitions.put(MatchState.FINISHED, EnumSet.noneOf(MatchState.class));
        transitions.put(MatchState.ABORTED, EnumSet.noneOf(MatchState.class));
        return Map.copyOf(transitions);
    }

    public record Transition(MatchState from, MatchState to, long revision) {
    }
}
