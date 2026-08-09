package ink.ziip.championshipscore.protocol;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Canonical command/event construction and parsing; transport users never hand-write attribute keys. */
public final class MatchMessages {
    private MatchMessages() {
    }

    public static MatchCommand command(UUID matchId, long epoch, MatchCommandType type) {
        return command(matchId, epoch, type, Map.of(), Clock.systemUTC());
    }

    public static MatchCommand command(UUID matchId, long epoch, MatchCommandType type,
                                       Map<String, String> attributes, Clock clock) {
        Objects.requireNonNull(type, "type");
        String identity = "command:" + epoch + ":" + type + ":" + canonical(attributes);
        return new MatchCommand(ProtocolVersion.CURRENT, DeterministicIds.uuidV5(matchId, identity),
                matchId, epoch, clock.millis(), type, attributes);
    }

    public static MatchEvent event(UUID matchId, long epoch, long seq, MatchEventType type,
                                   Map<String, String> attributes, Clock clock) {
        Objects.requireNonNull(type, "type");
        UUID messageId = DeterministicIds.uuidV5(matchId,
                "event:" + epoch + ":" + seq + ":" + type);
        return new MatchEvent(ProtocolVersion.CURRENT, messageId, matchId, epoch, seq,
                clock.millis(), type, attributes);
    }

    public static MatchEvent taskCompleted(CompletionObservation observation, Clock clock) {
        return taskCompleted(observation, observation.seq(), clock);
    }

    /** Event sequence and completion sequence are distinct: lifecycle events also consume event IDs. */
    public static MatchEvent taskCompleted(CompletionObservation observation, long eventSeq, Clock clock) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("completionSeq", Long.toString(observation.seq()));
        attributes.put("teamId", Integer.toString(observation.teamId()));
        attributes.put("playerId", observation.playerId().toString());
        attributes.put("cellIndex", Integer.toString(observation.cellIndex()));
        attributes.put("observedGameTick", Long.toString(observation.observedGameTick()));
        return event(observation.matchId(), observation.epoch(), eventSeq,
                MatchEventType.TASK_COMPLETED, attributes, clock);
    }

    public static CompletionObservation completionObservation(MatchEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.type() != MatchEventType.TASK_COMPLETED) {
            throw new IllegalArgumentException("Expected TASK_COMPLETED but received " + event.type());
        }
        long completionSeq = event.attributes().containsKey("completionSeq")
                ? number(event.attributes(), "completionSeq") : event.seq();
        return new CompletionObservation(event.matchId(), event.epoch(), completionSeq,
                integer(event.attributes(), "teamId"), uuid(event.attributes(), "playerId"),
                integer(event.attributes(), "cellIndex"), number(event.attributes(), "observedGameTick"));
    }

    private static String canonical(Map<String, String> attributes) {
        return new java.util.TreeMap<>(attributes == null ? Map.of() : attributes).toString();
    }

    private static String required(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing attribute " + key);
        return value;
    }

    private static int integer(Map<String, String> attributes, String key) {
        try {
            return Integer.parseInt(required(attributes, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid integer attribute " + key, invalid);
        }
    }

    private static long number(Map<String, String> attributes, String key) {
        try {
            return Long.parseLong(required(attributes, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid long attribute " + key, invalid);
        }
    }

    private static UUID uuid(Map<String, String> attributes, String key) {
        try {
            return UUID.fromString(required(attributes, key));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid UUID attribute " + key, invalid);
        }
    }
}
