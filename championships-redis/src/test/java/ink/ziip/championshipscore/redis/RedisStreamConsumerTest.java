package ink.ziip.championshipscore.redis;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;

import io.lettuce.core.RedisClient;
import io.lettuce.core.XReadArgs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@EnabledIfSystemProperty(named = "test.redis.uri", matches = ".+")
@Timeout(20)
class RedisStreamConsumerTest {
    private static final String URI = System.getProperty("test.redis.uri");

    @Test
    void reclaimUsesThePendingDeliveryCountAndHonorsTheAttemptLimit() throws Exception {
        String stream = "cc-test:" + UUID.randomUUID();
        RedisClient probe = RedisClient.create(URI);
        try (var connection = probe.connect()) {
            var commands = connection.sync();
            var attempts = new CopyOnWriteArrayList<Long>();
            var reclaimed = new CopyOnWriteArrayList<Boolean>();
            var failures = new CopyOnWriteArrayList<Throwable>();
            try (var consumer =
                    new RedisStreamConsumer(
                            new RedisConnectionConfig(
                                    URI, "test", "test", 100, Duration.ofSeconds(5)),
                            settings(),
                            stream,
                            "$",
                            delivery -> {
                                attempts.add(delivery.deliveryCount());
                                reclaimed.add(delivery.reclaimed());
                                return CompletableFuture.completedFuture(DeliveryDisposition.RETRY);
                            },
                            failures::add)) {
                commands.xadd(stream, Map.of("value", "old"));
                var start = consumer.start();
                var concurrentStart = consumer.start();
                CompletableFuture.allOf(
                                start.toCompletableFuture(), concurrentStart.toCompletableFuture())
                        .get(5, TimeUnit.SECONDS);
                String id = commands.xadd(stream, Map.of("value", "new"));
                var dead =
                        commands.xread(
                                new XReadArgs().block(Duration.ofSeconds(5)),
                                XReadArgs.StreamOffset.from(stream + ":dead", "0-0"));
                assertEquals(1, dead.size(), () -> failures.toString());
                var body = dead.getFirst().getBody();
                assertEquals(id, body.get("sourceEntryId"));
                assertEquals("new", body.get("original.value"));
                assertEquals("max-deliveries:3", body.get("reason"));
                assertEquals(List.of(1L, 2L), attempts);
                assertEquals(List.of(false, true), reclaimed);
                assertEquals(List.of(), failures);
            } finally {
                commands.del(stream, stream + ":dead");
            }
        } finally {
            probe.shutdown();
        }
    }

    @Test
    void matchConsumerReplaysExistingEntriesAndPreservesDecodeFailures() {
        String stream = "cc-test:" + UUID.randomUUID();
        RedisClient probe = RedisClient.create(URI);
        try (var connection = probe.connect()) {
            var commands = connection.sync();
            var failures = new CopyOnWriteArrayList<Throwable>();
            try (var consumer =
                    new RedisMatchConsumer(
                            new RedisTransportConfig(
                                    URI, "test", "worker", 100, Duration.ofSeconds(5)),
                            settings(),
                            stream,
                            ignored -> {
                                throw new AssertionError("malformed message reached handler");
                            },
                            failures::add)) {
                String id = commands.xadd(stream, Map.of("kind", "invalid"));
                consumer.start().toCompletableFuture().join();
                var dead =
                        commands.xread(
                                new XReadArgs().block(Duration.ofSeconds(5)),
                                XReadArgs.StreamOffset.from(stream + ":dead", "0-0"));
                assertEquals(1, dead.size(), () -> failures.toString());
                assertEquals(id, dead.getFirst().getBody().get("sourceEntryId"));
                assertTrue(dead.getFirst().getBody().get("reason").startsWith("decode:"));
                assertTrue(failures.isEmpty());
            } finally {
                commands.del(stream, stream + ":dead");
            }
        } finally {
            probe.shutdown();
        }
    }

    private static RedisConsumerConfig settings() {
        return new RedisConsumerConfig(
                "group", "reader", 10, Duration.ofMillis(30), Duration.ofMillis(30), 2);
    }
}
