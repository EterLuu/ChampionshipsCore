package ink.ziip.championshipscore.protocol.transport;

import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;

import java.util.Objects;
import java.util.UUID;

/** Strongly typed payload carried by a match command or event stream. */
public sealed interface MatchInboundMessage permits MatchInboundMessage.Manifest,
        MatchInboundMessage.Command, MatchInboundMessage.Event {
    UUID messageId();

    UUID matchId();

    long epoch();

    record Manifest(UUID messageId, MatchManifest manifest) implements MatchInboundMessage {
        public Manifest {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(manifest, "manifest");
        }

        @Override
        public UUID matchId() {
            return manifest.matchId();
        }

        @Override
        public long epoch() {
            return manifest.epoch();
        }
    }

    record Command(MatchCommand command) implements MatchInboundMessage {
        public Command {
            Objects.requireNonNull(command, "command");
        }

        @Override
        public UUID messageId() {
            return command.messageId();
        }

        @Override
        public UUID matchId() {
            return command.matchId();
        }

        @Override
        public long epoch() {
            return command.epoch();
        }
    }

    record Event(MatchEvent event) implements MatchInboundMessage {
        public Event {
            Objects.requireNonNull(event, "event");
        }

        @Override
        public UUID messageId() {
            return event.messageId();
        }

        @Override
        public UUID matchId() {
            return event.matchId();
        }

        @Override
        public long epoch() {
            return event.epoch();
        }
    }
}
