package ink.ziip.championshipscore.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.DefaultClientResources;
import io.netty.util.internal.ThreadExecutorMap;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Each transport owns its connections. Lettuce threads are shared until the last transport closes.
 */
final class RedisConnections implements AutoCloseable {
    private static DefaultClientResources resources;
    private static int clients;
    private final RedisClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    RedisConnections(RedisURI uri) {
        synchronized (RedisConnections.class) {
            if (resources == null) resources = DefaultClientResources.create();
            try {
                client = RedisClient.create(resources, uri);
                clients++;
            } catch (RuntimeException failure) {
                if (clients == 0) {
                    resources.shutdown();
                    resources = null;
                }
                throw failure;
            }
        }
    }

    RedisAsyncCommands<String, String> open() {
        try {
            return client.connect().async();
        } catch (RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (ThreadExecutorMap.currentExecutor() != null) {
            // An acknowledgement callback may release the last owner on Lettuce's event loop.
            Thread.ofVirtual()
                    .name("cc-redis-shutdown")
                    .start(
                            () -> {
                                try {
                                    shutdown();
                                } catch (RuntimeException failure) {
                                    Logger.getLogger(RedisConnections.class.getName())
                                            .log(Level.WARNING, "Redis shutdown failed", failure);
                                }
                            });
        } else {
            shutdown();
        }
    }

    private void shutdown() {
        try {
            client.shutdown(Duration.ofMillis(100), Duration.ofSeconds(5));
        } finally {
            DefaultClientResources retired = null;
            synchronized (RedisConnections.class) {
                if (--clients == 0) {
                    retired = resources;
                    resources = null;
                }
            }
            if (retired != null)
                retired.shutdown(100, 5000, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }
}
