package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Resets Folia's single natural-world slot between matches.
 *
 * <p>Folia does not support runtime world creation or unloading. Once every player has left this
 * backend, the coordinator writes a durable handoff marker and shuts Folia down. The container's
 * supervisor waits for Java to exit, atomically moves the old level out of the startup path, and
 * starts Folia again against a fresh level directory. Retired levels are deleted asynchronously by
 * the replacement process so multi-gigabyte cleanup never delays its startup.</p>
 */
final class WorkerWorldResetCoordinator {
    static final String RESET_MARKER_FILE = ".championships-bingo-reset";
    private static final String RESET_MARKER_VERSION = "1";
    private static final long PLAYER_CHECK_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final Path worldContainer;
    private final Path worldRoot;
    private final Path resetMarker;
    private final AtomicBoolean resetRequested = new AtomicBoolean();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final ScheduledTask playerCheckTask;

    WorkerWorldResetCoordinator(Plugin plugin, WorkerConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        this.worldContainer = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        this.worldRoot = resolveWorldRoot(plugin, config);
        this.resetMarker = worldContainer.resolve(RESET_MARKER_FILE);
        this.playerCheckTask = new PlatformScheduler(plugin).runGlobalTimer(
                this::shutdownWhenEmpty, PLAYER_CHECK_PERIOD_TICKS, PLAYER_CHECK_PERIOD_TICKS);
        new PlatformScheduler(plugin).runAsync(this::cleanRetiredWorlds);
    }

    void request() {
        if (!resetRequested.compareAndSet(false, true)) return;
        plugin.getLogger().info("Bingo world reset queued; waiting for all players to reach the lobby");
    }

    private void shutdownWhenEmpty() {
        if (!resetRequested.get() || shutdownStarted.get()) return;
        if (!plugin.getServer().getOnlinePlayers().isEmpty()) return;
        if (!shutdownStarted.compareAndSet(false, true)) return;

        try {
            Path retired = writeResetMarker(resetMarker, worldRoot, UUID.randomUUID());
            playerCheckTask.cancel();
            plugin.getLogger().info("All Bingo players have left; reset handoff prepared for "
                    + retired.getFileName() + "; shutting down Folia");
            plugin.getServer().shutdown();
        } catch (IOException failure) {
            shutdownStarted.set(false);
            plugin.getLogger().log(Level.SEVERE,
                    "Unable to prepare Bingo world reset marker; Folia will remain online", failure);
        }
    }

    static Path writeResetMarker(Path marker, Path worldRoot, UUID resetId) throws IOException {
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(worldRoot, "worldRoot");
        Objects.requireNonNull(resetId, "resetId");

        Path container = worldRoot.getParent();
        if (container == null || !marker.toAbsolutePath().normalize().getParent().equals(container)) {
            throw new IllegalArgumentException("Reset marker must share the Bingo world container");
        }

        String worldName = worldRoot.getFileName().toString();
        String retiredName = worldName + ".cc-reset-" + resetId;
        byte[] handoff = (RESET_MARKER_VERSION + '\n' + worldName + '\n' + retiredName + '\n')
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(container, RESET_MARKER_FILE + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = ByteBuffer.wrap(handoff);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try {
                Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return container.resolve(retiredName);
    }

    private void cleanRetiredWorlds() {
        String prefix = worldRoot.getFileName() + ".cc-reset-";
        try (Stream<Path> entries = Files.list(worldContainer)) {
            for (Path entry : entries.filter(path -> path.getFileName().toString().startsWith(prefix)).toList()) {
                try {
                    deleteTree(entry);
                    plugin.getLogger().info("Deleted retired Bingo world " + entry.getFileName());
                } catch (IOException failure) {
                    plugin.getLogger().log(Level.WARNING,
                            "Unable to delete retired Bingo world " + entry.getFileName(), failure);
                }
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Unable to scan retired Bingo worlds", failure);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path resolveWorldRoot(Plugin plugin, WorkerConfig config) {
        Path container = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path root = container.resolve(config.overworld()).normalize();
        if (!container.equals(root.getParent()) || root.equals(container) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("Unsafe Bingo world root: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Bingo world root does not exist: " + root);
        }

        List<String> names = List.of(config.overworld(), config.nether(), config.end());
        for (String name : names) {
            World world = plugin.getServer().getWorld(name);
            if (world == null) throw new IllegalArgumentException("Bingo world is not loaded: " + name);
            Path dimension = world.getWorldPath().toAbsolutePath().normalize();
            if (!dimension.startsWith(root)) {
                throw new IllegalArgumentException("Bingo dimension is outside its world root: " + dimension);
            }
        }
        return root;
    }

}
