package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchEventType;
import ink.ziip.championshipscore.protocol.transport.MatchEventPublisher;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A test release requiring a fresh world is published only by the replacement Worker process. */
final class WorkerTestReleaseJournal {
    private static final int MAGIC = 0x43435231;
    private final Path directory;
    private final BinaryProtocolCodec codec = new BinaryProtocolCodec();

    record Pending(MatchEvent event, Map<String, UUID> previousWorlds) {
        Pending {
            previousWorlds = Map.copyOf(previousWorlds);
            if (event.type() != MatchEventType.RELEASED || previousWorlds.size() != 3)
                throw new IllegalArgumentException("invalid pending test release");
        }

        boolean resetObserved(Map<String, UUID> currentWorlds) {
            return previousWorlds.entrySet().stream()
                    .allMatch(
                            e ->
                                    currentWorlds.containsKey(e.getKey())
                                            && !e.getValue().equals(currentWorlds.get(e.getKey())));
        }
    }

    WorkerTestReleaseJournal(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        pending(); // Validate persisted state before any new match can be admitted.
    }

    synchronized List<Pending> pending() throws IOException {
        var values = new ArrayList<Pending>();
        try (var files = Files.list(directory)) {
            for (Path file :
                    files.filter(p -> p.getFileName().toString().endsWith(".release"))
                            .sorted()
                            .toList()) {
                try (var in = new DataInputStream(Files.newInputStream(file))) {
                    if (in.readInt() != MAGIC)
                        throw new IOException("invalid release journal version");
                    int length = in.readInt();
                    if (length < 1 || length > 4 * 1024 * 1024)
                        throw new IOException("invalid release event length");
                    byte[] bytes = in.readNBytes(length);
                    if (bytes.length != length) throw new EOFException("incomplete release event");
                    MatchEvent event = codec.decodeEvent(bytes);
                    var worlds = new LinkedHashMap<String, UUID>();
                    for (int i = 0; i < 3; i++) {
                        if (worlds.put(in.readUTF(), UUID.fromString(in.readUTF())) != null)
                            throw new IOException("duplicate release world");
                    }
                    if (in.read() != -1) throw new IOException("unexpected release journal data");
                    if (!file.equals(path(event)))
                        throw new IOException("release file identity mismatch");
                    values.add(new Pending(event, worlds));
                } catch (RuntimeException failure) {
                    throw new IOException("invalid pending test release: " + file, failure);
                }
            }
        }
        return List.copyOf(values);
    }

    synchronized void stage(Pending value) throws IOException {
        Path target = path(value.event());
        if (Files.exists(target)) {
            if (pending().stream().anyMatch(value::equals)) return;
            throw new IOException("pending test release changed without a new match");
        }
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            byte[] event = codec.encodeEvent(value.event());
            out.writeInt(event.length);
            out.write(event);
            for (var world : new TreeMap<>(value.previousWorlds()).entrySet()) {
                out.writeUTF(world.getKey());
                out.writeUTF(world.getValue().toString());
            }
        }
        Path temporary = Files.createTempFile(directory, ".release-", ".pending");
        try {
            try (var channel =
                    FileChannel.open(
                            temporary,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                var buffer = ByteBuffer.wrap(bytes.toByteArray());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    CompletionStage<Integer> resume(Map<String, UUID> currentWorlds, MatchEventPublisher events)
            throws IOException {
        CompletionStage<Integer> chain = CompletableFuture.completedFuture(0);
        for (var value : pending()) {
            if (!value.resetObserved(currentWorlds)) continue;
            chain =
                    chain.thenCompose(
                            count ->
                                    events.publishEvent(value.event())
                                            .thenApply(
                                                    receipt -> {
                                                        try {
                                                            Files.deleteIfExists(
                                                                    path(value.event()));
                                                        } catch (IOException failure) {
                                                            throw new UncheckedIOException(failure);
                                                        }
                                                        return count + 1;
                                                    }));
        }
        return chain;
    }

    private Path path(MatchEvent event) {
        return directory.resolve(event.matchId() + "-" + event.epoch() + ".release");
    }
}
