package ink.ziip.championshipscore.api.test.auth;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/** Durable single-use nonce gate for signed test leases. */
public final class TestLeaseNonceStore {
    private static final String HEADER = "CC_TEST_LEASE_NONCES_V1";
    private final Path file;
    private final Set<String> inMemory = new HashSet<>();

    public TestLeaseNonceStore(Path file) {
        this.file = file == null ? null : file.toAbsolutePath();
    }

    /** Persists the nonce before a lease is exposed to the session registry. */
    public synchronized void consume(String nonce) {
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}"))
            throw new IllegalArgumentException("invalid lease nonce");
        if (file == null) {
            if (!inMemory.add(nonce)) throw new IllegalStateException("lease nonce already used");
            return;
        }
        Path lockPath = file.resolveSibling(file.getFileName() + ".lock");
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (FileChannel lockChannel =
                            FileChannel.open(
                                    lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = lockChannel.lock()) {
                Set<String> values = read();
                if (!values.add(nonce)) throw new IllegalStateException("lease nonce already used");
                persist(values);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot persist test lease nonce", failure);
        }
    }

    private Set<String> read() throws IOException {
        if (Files.notExists(file)) return new HashSet<>();
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst()))
            throw new IOException("invalid test lease nonce file");
        var values = new HashSet<String>();
        for (String line : lines.subList(1, lines.size())) {
            if (!line.matches("[A-Za-z0-9_-]{16,128}") || !values.add(line))
                throw new IOException("invalid or duplicate test lease nonce");
        }
        return values;
    }

    private void persist(Set<String> values) throws IOException {
        Path parent = file.getParent();
        Path pending =
                parent == null
                        ? Files.createTempFile(".lease-nonces-", ".pending")
                        : Files.createTempFile(parent, ".lease-nonces-", ".pending");
        try {
            StringBuilder content = new StringBuilder(HEADER).append('\n');
            values.stream().sorted().forEach(value -> content.append(value).append('\n'));
            try (FileChannel channel =
                    FileChannel.open(
                            pending,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes =
                        ByteBuffer.wrap(content.toString().getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(
                    pending,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
