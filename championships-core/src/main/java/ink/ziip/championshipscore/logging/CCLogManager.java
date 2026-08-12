package ink.ziip.championshipscore.logging;

import ink.ziip.championshipscore.ChampionshipsCore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;

public final class CCLogManager implements AutoCloseable {
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final int MAX_ARCHIVES = 30, MAX_DAYS = 30;
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Logger pluginLogger;
    private final boolean previousParentHandlers;
    private final AsyncHandler handler;
    private CCLogManager(ChampionshipsCore plugin) throws IOException {
        pluginLogger = plugin.getLogger();
        previousParentHandlers = pluginLogger.getUseParentHandlers();
        handler = new AsyncHandler(plugin.getDataFolder().toPath().resolve("logs"), plugin.getServer().getLogger());
        pluginLogger.setUseParentHandlers(false);
        pluginLogger.addHandler(handler);
    }
    public static CCLogManager install(ChampionshipsCore plugin) {
        try { return new CCLogManager(plugin); }
        catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "CC file log initialization failed; using console", e);
            return null;
        }
    }
    public void important(String message) {
        LogRecord record = new LogRecord(Level.INFO, message);
        record.setLoggerName(pluginLogger.getName());
        handler.publish(record, true);
    }
    @Override public void close() {
        pluginLogger.removeHandler(handler);
        pluginLogger.setUseParentHandlers(previousParentHandlers);
        handler.close();
    }
    private static final class AsyncHandler extends Handler {
        private final Path current, archives;
        private final Logger console;
        private final BlockingQueue<Item> queue = new LinkedBlockingQueue<>();
        private final AtomicBoolean accepting = new AtomicBoolean(true);
        private final Thread thread;
        private BufferedWriter writer;
        private long bytes;
        AsyncHandler(Path directory, Logger console) throws IOException {
            this.console = console;
            current = directory.resolve("championshipscore.log");
            archives = directory.resolve("archive");
            Files.createDirectories(archives);
            if (Files.isRegularFile(current) && Files.size(current) > 0) archive();
            cleanup();
            open();
            thread = new Thread(this::run, "ChampionshipsCore-LogWriter");
            thread.setDaemon(true);
            thread.start();
        }
        @Override public void publish(LogRecord record) {
            publish(record, record != null && record.getLevel().intValue() >= Level.WARNING.intValue());
        }
        void publish(LogRecord record, boolean showInConsole) {
            if (record == null || !isLoggable(record) || !accepting.get()) return;
            queue.offer(new Entry(record.getInstant(), record.getLevel(), Thread.currentThread().getName(),
                    Objects.toString(record.getMessage(), ""), record.getThrown()));
            if (showInConsole)
                console.log(record.getLevel(), "[ChampionshipsCore] " + record.getMessage(), record.getThrown());
        }
        @Override public void flush() {
            if (!accepting.get()) return;
            CountDownLatch latch = new CountDownLatch(1);
            queue.offer(new Barrier(latch));
            try { latch.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        @Override public void close() {
            if (!accepting.compareAndSet(true, false)) return;
            queue.offer(Stop.INSTANCE);
            try { thread.join(10_000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (thread.isAlive()) console.severe("[ChampionshipsCore] log queue did not drain within 10 seconds");
        }
        private void run() {
            try {
                boolean stop = false;
                long lastFlush = System.nanoTime();
                while (!stop) {
                    Item item = queue.poll(1, TimeUnit.SECONDS);
                    if (item instanceof Entry entry) write(entry);
                    else if (item instanceof Barrier barrier) {
                        writer.flush();
                        lastFlush = System.nanoTime();
                        barrier.latch.countDown();
                    } else if (item == Stop.INSTANCE) stop = true;

                    long now = System.nanoTime();
                    if (!stop && (item == null || now - lastFlush >= TimeUnit.SECONDS.toNanos(1))) {
                        writer.flush();
                        lastFlush = now;
                    }
                }
                Item item;
                while ((item = queue.poll()) != null) {
                    if (item instanceof Entry entry) write(entry);
                    else if (item instanceof Barrier barrier) barrier.latch.countDown();
                }
                writer.flush();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) { console.log(Level.SEVERE, "[ChampionshipsCore] asynchronous log write failed", e); }
            finally { try { if (writer != null) writer.close(); } catch (IOException ignored) { } }
        }
        private void write(Entry entry) throws IOException {
            String text = format(entry);
            int length = text.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 0 && bytes + length > MAX_BYTES) rotate();
            writer.write(text);
            bytes += length;
        }
        private String format(Entry e) {
            StringBuilder out = new StringBuilder()
                    .append(LINE_TIME.format(e.time.atZone(ZoneId.systemDefault())))
                    .append(" | ").append(String.format("%-7s", e.level.getName()))
                    .append(" | ").append(e.thread).append(" | ").append(e.message)
                    .append(System.lineSeparator());
            if (e.thrown != null) {
                StringWriter trace = new StringWriter();
                e.thrown.printStackTrace(new PrintWriter(trace));
                trace.toString().lines().forEach(line ->
                        out.append("    ").append(line).append(System.lineSeparator()));
            }
            return out.toString();
        }
        private void open() throws IOException {
            writer = Files.newBufferedWriter(current, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            bytes = Files.size(current);
        }
        private void rotate() throws IOException { writer.flush(); writer.close(); archive(); cleanup(); open(); }
        private void archive() throws IOException {
            String stamp = FILE_TIME.format(Instant.now().atZone(ZoneId.systemDefault()));
            Path target = archives.resolve("championshipscore-" + stamp + ".log");
            for (int suffix = 1; Files.exists(target); suffix++)
                target = archives.resolve("championshipscore-" + stamp + "-" + suffix + ".log");
            try { Files.move(current, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(current, target); }
        }
        private void cleanup() throws IOException {
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(archives, "championshipscore-*.log")) {
                stream.forEach(files::add);
            }
            files.sort(Comparator.comparing(this::modified).reversed());
            Instant cutoff = Instant.now().minus(Duration.ofDays(MAX_DAYS));
            for (int i = 0; i < files.size(); i++)
                if (i >= MAX_ARCHIVES || modified(files.get(i)).toInstant().isBefore(cutoff))
                    Files.deleteIfExists(files.get(i));
        }
        private FileTime modified(Path file) {
            try { return Files.getLastModifiedTime(file); }
            catch (IOException e) { return FileTime.fromMillis(0); }
        }
    }
    private sealed interface Item permits Entry, Barrier, Stop { }
    private record Entry(Instant time, Level level, String thread, String message, Throwable thrown) implements Item { }
    private record Barrier(CountDownLatch latch) implements Item { }
    private enum Stop implements Item { INSTANCE }
}
