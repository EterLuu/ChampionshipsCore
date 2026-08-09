package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.DeterministicIds;
import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Validates the redundant Redis metadata against the binary payload before dispatch. */
public final class RedisMatchMessageCodec {
    private final BinaryProtocolCodec codec;

    public RedisMatchMessageCodec() {
        this(new BinaryProtocolCodec());
    }

    RedisMatchMessageCodec(BinaryProtocolCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public MatchInboundMessage decode(Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        String kind = required(fields, "kind");
        UUID metadataMessageId = uuid(fields, "messageId");
        UUID metadataMatchId = uuid(fields, "matchId");
        long metadataEpoch = number(fields, "epoch");
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(required(fields, "payload"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid Base64 payload", invalid);
        }

        MatchInboundMessage decoded = switch (kind) {
            case "manifest" -> decodeManifest(metadataMessageId, payload);
            case "command" -> new MatchInboundMessage.Command(codec.decodeCommand(payload));
            case "event" -> new MatchInboundMessage.Event(codec.decodeEvent(payload));
            default -> throw new IllegalArgumentException("Unknown match message kind " + kind);
        };
        if (!metadataMessageId.equals(decoded.messageId())
                || !metadataMatchId.equals(decoded.matchId())
                || metadataEpoch != decoded.epoch()) {
            throw new IllegalArgumentException("Redis metadata does not match binary payload identity");
        }
        return decoded;
    }

    private MatchInboundMessage.Manifest decodeManifest(UUID messageId, byte[] payload) {
        MatchManifest manifest = codec.decodeManifest(payload);
        UUID expected = DeterministicIds.uuidV5(manifest.matchId(), "manifest:" + manifest.epoch());
        if (!expected.equals(messageId)) {
            throw new IllegalArgumentException("Manifest messageId is not deterministic for its match epoch");
        }
        return new MatchInboundMessage.Manifest(messageId, manifest);
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing Redis field " + key);
        return value;
    }

    private static UUID uuid(Map<String, String> fields, String key) {
        try {
            return UUID.fromString(required(fields, key));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid UUID Redis field " + key, invalid);
        }
    }

    private static long number(Map<String, String> fields, String key) {
        try {
            return Long.parseLong(required(fields, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid long Redis field " + key, invalid);
        }
    }
}
