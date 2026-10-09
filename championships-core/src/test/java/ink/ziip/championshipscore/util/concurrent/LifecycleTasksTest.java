package ink.ziip.championshipscore.util.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class LifecycleTasksTest {
    @Test
    void submissionsAreOrderedAndFailureDoesNotStopTheWorker() throws Exception {
        try (var tasks = new LifecycleTasks("test-serial", 8)) {
            tasks.start();
            List<Integer> observed = new ArrayList<>();
            var first = tasks.submit(() -> observed.add(1));
            var failure =
                    tasks.submit(
                            () -> {
                                throw new IllegalArgumentException("injected");
                            });
            var last = tasks.submit(() -> observed.add(2));
            first.get(2, TimeUnit.SECONDS);
            assertInstanceOf(
                    IllegalArgumentException.class,
                    assertThrows(ExecutionException.class, () -> failure.get(2, TimeUnit.SECONDS))
                            .getCause());
            last.get(2, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2), observed);
        }
    }

    @Test
    void slowOldQueryCannotPublishOrSubmitIntoReloadedLifetime() throws Exception {
        CountDownLatch querying = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicInteger cache = new AtomicInteger();
        CompletableFuture<Void> assertions = new CompletableFuture<>();
        try (var tasks = new LifecycleTasks("test-reload", 8)) {
            tasks.start();
            var old =
                    tasks.submit(
                            () -> {
                                var token = tasks.token();
                                querying.countDown();
                                // Model a JDBC call that does not honour Thread.interrupt().
                                awaitIgnoringInterrupt(release);
                                try {
                                    assertThrows(
                                            CancellationException.class,
                                            () -> token.publish(() -> cache.set(9)));
                                    assertTrue(
                                            tasks.submit(() -> cache.set(8))
                                                    .isCompletedExceptionally());
                                    assertions.complete(null);
                                } catch (Throwable failure) {
                                    assertions.completeExceptionally(failure);
                                } finally {
                                    returned.countDown();
                                }
                            });
            assertTrue(querying.await(2, TimeUnit.SECONDS));
            var queued = tasks.submit(() -> cache.set(7));
            tasks.close();
            assertTrue(old.isCancelled());
            assertTrue(queued.isCancelled());
            tasks.start();
            tasks.submit(() -> tasks.token().publish(() -> cache.set(1))).get(2, TimeUnit.SECONDS);
            release.countDown();
            assertTrue(returned.await(2, TimeUnit.SECONDS));
            assertions.get(2, TimeUnit.SECONDS);
            assertEquals(1, cache.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void overflowFailsWithoutCallerThreadExecution() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger actions = new AtomicInteger();
        try (var tasks = new LifecycleTasks("test-bounded", 1)) {
            tasks.start();
            tasks.submit(
                    () -> {
                        entered.countDown();
                        awaitIgnoringInterrupt(release);
                    });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            tasks.submit(actions::incrementAndGet);
            var rejected = tasks.submit(actions::incrementAndGet);
            assertInstanceOf(
                    RejectedExecutionException.class,
                    assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS))
                            .getCause());
            assertEquals(0, actions.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void queuedMainThreadContinuationAndExposedFutureAreCancelled() {
        try (var tasks = new LifecycleTasks("test-main", 8)) {
            tasks.start();
            var token = tasks.token();
            CompletableFuture<String> result = token.future();
            List<Runnable> mainQueue = new ArrayList<>();
            AtomicInteger state = new AtomicInteger();
            var continuation = token.dispatch(mainQueue::add, state::incrementAndGet);
            tasks.close();
            tasks.start();
            mainQueue.forEach(Runnable::run);
            assertTrue(result.isCancelled());
            assertTrue(continuation.isCancelled());
            assertEquals(0, state.get());
        }
    }

    @Test
    void schedulerRejectionCompletesFutureAndStoppedSubmissionFails() {
        try (var tasks = new LifecycleTasks("test-rejected", 8)) {
            assertTrue(
                    tasks.submit(() -> fail("stopped task executed")).isCompletedExceptionally());
            tasks.start();
            var rejected =
                    tasks.token()
                            .dispatch(
                                    task -> {
                                        throw new RejectedExecutionException();
                                    },
                                    () -> {});
            assertTrue(rejected.isCompletedExceptionally());
        }
    }

    @Test
    void closeWaitsForPublicationThenPreventsFurtherPublication() throws Exception {
        try (var tasks = new LifecycleTasks("test-publication", 8)) {
            tasks.start();
            var token = tasks.token();
            tasks.submit(() -> token.publish(() -> {})).get(2, TimeUnit.SECONDS);
            tasks.close();
            assertThrows(CancellationException.class, () -> token.publish(() -> fail("stale")));
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        for (; ; ) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
