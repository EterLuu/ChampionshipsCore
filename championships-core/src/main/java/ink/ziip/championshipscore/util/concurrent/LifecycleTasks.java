package ink.ziip.championshipscore.util.concurrent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/** Serial, bounded I/O work with explicit lifetime ownership for publication and continuations. */
public final class LifecycleTasks implements AutoCloseable {
    private final Object lock = new Object();
    private final String name;
    private final int capacity;
    private final ThreadLocal<Token> executing = new ThreadLocal<>();
    private Token current;

    public LifecycleTasks(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    public void start() {
        synchronized (lock) {
            if (current != null) throw new IllegalStateException(name + " already started");
            current = new Token();
        }
    }

    /** A nested submission inherits its original lifetime, even after stop/start. */
    public Token token() {
        Token inherited = executing.get();
        if (inherited != null) return inherited;
        synchronized (lock) {
            if (current == null) throw new CancellationException(name + " is stopped");
            return current;
        }
    }

    public CompletableFuture<Void> submit(Runnable action) {
        try {
            Token token = token();
            return token.dispatch(token.executor, action);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public void close() {
        Token stopped;
        Set<CompletableFuture<?>> pending;
        synchronized (lock) {
            stopped = current;
            if (stopped == null) return;
            current = null;
            pending = Set.copyOf(stopped.pending);
        }
        // Never run arbitrary completion callbacks while holding the publication lock.
        pending.forEach(future -> future.cancel(false));
        stopped.executor.shutdownNow();
    }

    public final class Token {
        private final Set<CompletableFuture<?>> pending = new HashSet<>();
        private final ExecutorService executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(capacity),
                        runnable -> {
                            Thread thread = new Thread(runnable, name);
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());

        public boolean isCurrent() {
            synchronized (lock) {
                return current == this;
            }
        }

        public void check() {
            if (!isCurrent()) throw new CancellationException(name + " lifetime ended");
        }

        /** For short memory/native-state updates only; never hold this lock across I/O. */
        public void publish(Runnable action) {
            synchronized (lock) {
                check();
                action.run();
            }
        }

        public <T> CompletableFuture<T> future() {
            CompletableFuture<T> future = new CompletableFuture<>();
            synchronized (lock) {
                if (current != this) {
                    future.cancel(false);
                    return future;
                }
                pending.add(future);
            }
            future.whenComplete(
                    (ignored, failure) -> {
                        synchronized (lock) {
                            pending.remove(future);
                        }
                    });
            return future;
        }

        /** Applies the same token to a server-thread continuation, including scheduling failure. */
        public CompletableFuture<Void> dispatch(Executor target, Runnable action) {
            CompletableFuture<Void> completion = future();
            if (completion.isDone()) return completion;
            try {
                target.execute(
                        () -> {
                            Token previous = executing.get();
                            executing.set(this);
                            try {
                                check();
                                action.run();
                                check();
                                completion.complete(null);
                            } catch (Throwable failure) {
                                completion.completeExceptionally(failure);
                            } finally {
                                if (previous == null) executing.remove();
                                else executing.set(previous);
                            }
                        });
            } catch (RuntimeException failure) {
                completion.completeExceptionally(failure);
            }
            return completion;
        }

        /** Submits work to this lifetime's single bounded executor. */
        public CompletableFuture<Void> submit(Runnable action) {
            return dispatch(executor, action);
        }
    }
}
