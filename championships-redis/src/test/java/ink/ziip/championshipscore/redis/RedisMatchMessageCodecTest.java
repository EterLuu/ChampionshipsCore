package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.BingoRuntimeRules;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.CompletionObservation;
import ink.ziip.championshipscore.protocol.DeterministicIds;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchMessages;
import ink.ziip.championshipscore.protocol.MatchRunMode;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.ProtocolVersion;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisMatchMessageCodecTest {
    private static final UUID MATCH = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    @Test
    void decodesManifestAndChecksRedundantIdentity() {
        BinaryProtocolCodec binary = new BinaryProtocolCodec();
        MatchManifest manifest = manifest();
        UUID messageId = DeterministicIds.uuidV5(MATCH, "manifest:1");
        Map<String, String> fields = fields("manifest", messageId,
                Base64.getEncoder().encodeToString(binary.encodeManifest(manifest)));

        MatchInboundMessage decoded = new RedisMatchMessageCodec().decode(fields);
        assertEquals(manifest, assertInstanceOf(MatchInboundMessage.Manifest.class, decoded).manifest());

        fields.put("epoch", "2");
        assertThrows(IllegalArgumentException.class, () -> new RedisMatchMessageCodec().decode(fields));
    }

    @Test
    void taskCompletionFactoryRoundTripsWithoutAttributeKnowledge() {
        CompletionObservation observation = new CompletionObservation(MATCH, 1, 7, 1, PLAYER, 3, 99);
        MatchEvent event = MatchMessages.taskCompleted(observation, CLOCK);

        assertEquals(observation, MatchMessages.completionObservation(event));
        assertEquals(event.messageId(), MatchMessages.taskCompleted(observation, CLOCK).messageId());
    }

    @Test
    void rejectsMalformedPayloadBeforeDispatch() {
        Map<String, String> fields = fields("event", UUID.randomUUID(), "not-base64!");
        assertThrows(IllegalArgumentException.class, () -> new RedisMatchMessageCodec().decode(fields));
    }

    private static Map<String, String> fields(String kind, UUID messageId, String payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", kind);
        fields.put("messageId", messageId.toString());
        fields.put("matchId", MATCH.toString());
        fields.put("epoch", "1");
        fields.put("payload", payload);
        return fields;
    }

    private static MatchManifest manifest() {
        TeamSnapshot team = new TeamSnapshot(1, "red", "RED", "#ff0000", List.of(PLAYER));
        return new MatchManifest(ProtocolVersion.CURRENT, MATCH, 1, CLOCK.millis(), "worker-1",
                MatchRunMode.EVENT, 600, 42, "hash",
                new BingoScoringRules(2, List.of(60, 50), 50, 4, 25),
                new BingoRuntimeRules(10, 6, 32, 180, List.of("night_vision:1")),
                List.of(
                        new BingoTaskSpec(0, "a", "item", Map.of("material", "STONE", "count", "1")),
                        new BingoTaskSpec(1, "b", "item", Map.of("material", "DIRT", "count", "1")),
                        new BingoTaskSpec(2, "c", "item", Map.of("material", "OAK_LOG", "count", "1")),
                        new BingoTaskSpec(3, "d", "item", Map.of("material", "IRON_INGOT", "count", "1"))),
                List.of(team), List.of(new PlayerSnapshot(PLAYER, "Player", ParticipantRole.PLAYER, 1)));
    }
}
