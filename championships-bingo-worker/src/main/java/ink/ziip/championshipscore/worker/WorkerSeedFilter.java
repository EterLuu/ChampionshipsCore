package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.worker.seedlab.SeedLab26_2;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Selects and persists the world seed before a fresh Bingo world is created. */
final class WorkerSeedFilter {
    private final Plugin plugin;
    private final WorkerConfig.SeedFilterConfig config;
    private final Path worldContainer;

    WorkerSeedFilter(Plugin plugin, WorkerConfig.SeedFilterConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.worldContainer =
                plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    OptionalLong selectSeed(boolean freshWorldRequired) {
        if (!freshWorldRequired) return OptionalLong.empty();

        Path seedFile = resolveSeedFile();
        if (config.enabled()) {
            try {
                long seed = runFilter(seedFile);
                plugin.getLogger().info("Selected Bingo seed " + seed + " using biome filter");
                return OptionalLong.of(seed);
            } catch (Exception failure) {
                if (config.required()) {
                    throw new IllegalStateException("Bingo biome seed filter failed", failure);
                }
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Bingo biome seed filter failed; falling back to a random seed",
                                failure);
            }
        } else {
            OptionalLong persisted = readSeed(seedFile);
            if (persisted.isPresent()) return persisted;
        }

        long seed = ThreadLocalRandom.current().nextLong();
        persistSeed(seedFile, seed);
        plugin.getLogger().info("Using generated Bingo seed " + seed);
        return OptionalLong.of(seed);
    }

    private long runFilter(Path seedFile) throws IOException {
        long timeoutNanos = config.timeout().toNanos();
        long deadline = System.nanoTime() + timeoutNanos;
        SeedLab26_2 predictor = SeedLab26_2.instance();
        SplittableRandom random = new SplittableRandom(ThreadLocalRandom.current().nextLong());
        long bestSeed = 0L;
        int bestScore = -1;
        int evaluated = 0;
        while (evaluated < config.candidates() && System.nanoTime() < deadline) {
            long candidate = random.nextLong();
            try {
                int score =
                        predictor.score(
                                candidate,
                                config.radiusBlocks(),
                                config.sampleStepBlocks(),
                                deadline);
                evaluated++;
                if (score > bestScore) {
                    bestScore = score;
                    bestSeed = candidate;
                }
            } catch (SeedLab26_2.SeedFilterTimeoutException timeout) {
                break;
            }
        }
        if (evaluated == 0) {
            throw new IOException(
                    "embedded SeedLab timed out before completing a candidate after "
                            + config.timeout().toMillis()
                            + " ms");
        }
        persistSeed(seedFile, bestSeed);
        plugin.getLogger()
                .info(
                        "SeedLab evaluated "
                                + evaluated
                                + " candidate(s); best biome score="
                                + bestScore);
        return bestSeed;
    }

    private OptionalLong readSeed(Path seedFile) {
        try {
            if (!Files.isRegularFile(seedFile)) return OptionalLong.empty();
            String value = Files.readString(seedFile, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) return OptionalLong.empty();
            return OptionalLong.of(Long.parseLong(value));
        } catch (IOException | NumberFormatException invalid) {
            plugin.getLogger()
                    .log(Level.WARNING, "Ignoring invalid Bingo seed file " + seedFile, invalid);
            return OptionalLong.empty();
        }
    }

    private void persistSeed(Path seedFile, long seed) {
        try {
            Path parent = seedFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary =
                    Files.createTempFile(
                            parent == null ? worldContainer : parent, ".bingo-seed.", ".tmp");
            try {
                Files.writeString(
                        temporary,
                        Long.toString(seed) + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                try {
                    Files.move(
                            temporary,
                            seedFile,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, seedFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to persist Bingo seed to " + seedFile, failure);
        }
    }

    private Path resolveSeedFile() {
        Path path = worldContainer.resolve(config.seedFile()).normalize();
        if (!path.startsWith(worldContainer) || path.equals(worldContainer)) {
            throw new IllegalArgumentException(
                    "worlds.seed-filter.seed-file must stay inside the world container");
        }
        return path;
    }
}
